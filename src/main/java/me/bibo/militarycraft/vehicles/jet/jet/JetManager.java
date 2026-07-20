package me.bibo.militarycraft.vehicles.jet.jet;

import me.bibo.militarycraft.vehicles.jet.JetRuntime;
import me.bibo.militarycraft.vehicles.jet.combat.Explosions;
import me.bibo.militarycraft.vehicles.jet.combat.WeaponSystem;
import me.bibo.militarycraft.vehicles.jet.config.JetConfig;
import me.bibo.militarycraft.vehicles.jet.control.FlightController;
import me.bibo.militarycraft.vehicles.jet.util.DriverCloak;
import me.bibo.militarycraft.vehicles.jet.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registry of all jets currently in loaded chunks, plus the single per-tick
 * update loop. Jets live as persistent entities; their wrappers are rebuilt
 * from those entities on entity-load and dropped on entity-unload, so they
 * survive chunk reloads, restarts and crashes without orphaning.
 */
public final class JetManager {

    private final JetRuntime plugin;
    private final WeaponSystem weapons;
    private final Map<UUID, Jet> jets = new LinkedHashMap<>();
    private final Map<UUID, UUID> driverToJet = new HashMap<>();
    private final Map<UUID, DebrisState> debris = new HashMap<>();
    private BukkitTask task;
    private boolean internalExplosion;
    private UUID munitionImmunePilot;
    private long tickCounter;
    /** Drivers whose armour is currently hidden from other players. */
    private final java.util.Set<UUID> cloaked = new java.util.HashSet<>();
    /** Last melee-hit timestamp per attacker, for the weapon-melee cooldown. */
    private final Map<UUID, Long> meleeCd = new HashMap<>();

    private record DebrisState(Entity entity, Vector velocity) {
    }

    public JetManager(JetRuntime plugin) {
        this.plugin = plugin;
        this.weapons = new WeaponSystem(plugin);
    }

    public WeaponSystem weapons() {
        return weapons;
    }

    // --------------------------------------------------------------- lifecycle

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin.bukkitPlugin(), this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Remove leftover JetCraft display entities from loaded worlds:
     * <ul>
     *   <li>debris chunks past their expiry tick (their animation runnable was
     *       cut by a reload/crash/lag and never removed them), and any debris
     *       with no expiry stamp at all;</li>
     *   <li>jet parts whose jet is no longer tracked (an orphaned model that a
     *       crash or partial removal left floating).</li>
     * </ul>
     * Called on enable and explicit cleanup paths; live debris is handled by
     * the per-tick debris registry so normal gameplay does not scan all worlds.
     */
    public int sweepLitter() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            long worldNow = world.getFullTime();
            for (Entity e : world.getEntities()) {
                // 1) transient debris chunks past their lifetime
                if (e.getScoreboardTags().contains(Keys.DEBRIS_TAG)) {
                    Long expire = e.getPersistentDataContainer()
                            .get(Keys.DEBRIS_EXPIRE, PersistentDataType.LONG);
                    if (expire == null || worldNow >= expire) {
                        e.remove();
                        removed++;
                    }
                    continue;
                }
                // 2) orphaned jet parts (tagged, but their jet isn't tracked)
                if (e.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
                    String idStr = e.getPersistentDataContainer()
                            .get(Keys.JET_ID, PersistentDataType.STRING);
                    if (idStr == null) {
                        e.remove();
                        removed++;
                        continue;
                    }
                    UUID id;
                    try {
                        id = UUID.fromString(idStr);
                    } catch (IllegalArgumentException ex) {
                        e.remove();
                        removed++;
                        continue;
                    }
                    if (!jets.containsKey(id)) {
                        e.remove();
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    /** On enable: adopt any jet entities already present in loaded chunks. */
    public void adoptExisting() {
        for (World world : Bukkit.getWorlds()) {
            onEntitiesLoad(world.getEntities());
        }
        // After adopting real jets, sweep away leftover debris / orphaned parts
        // from a previous crash or reload.
        sweepLitter();
    }

    /** On disable: eject pilots and persist state, but keep the entities. */
    public void shutdown() {
        stop();
        for (Jet jet : new ArrayList<>(jets.values())) {
            UUID d = jet.driver();
            if (d != null) {
                restoreDriver(d);
            }
            jet.eject();
            jet.persistState();
        }
        jets.clear();
        driverToJet.clear();
        cloaked.clear();
        weapons.clear();
        // Drop any in-flight debris chunks now; the manager will no longer tick them.
        for (DebrisState state : debris.values()) {
            if (state.entity() != null) {
                state.entity().remove();
            }
        }
        debris.clear();
        // Also remove stale debris that may have survived a previous reload.
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getScoreboardTags().contains(Keys.DEBRIS_TAG)) {
                    e.remove();
                }
            }
        }
    }

    private void tick() {
        tickCounter++;
        tickDebris();
        JetConfig cfg = plugin.config();
        // Iterate the registry directly (no per-tick list copy). The only map
        // mutation inside the loop is the dead-jet removal below, done through
        // the iterator; flight/glide never touch the registry.
        Iterator<Jet> jetIt = jets.values().iterator();
        while (jetIt.hasNext()) {
            Jet jet = jetIt.next();
            if (!jet.isActive()) {
                // Entities vanished while we were tracking them (e.g. /kill); tidy up.
                UUID d = jet.driver();
                if (d != null) {
                    restoreDriver(d);
                }
                jet.removeEntities();
                jetIt.remove();
                forgetDriver(jet);
                continue;
            }
            // Atmosphere ceiling: above max-y the thin air slowly tears the jet
            // apart and warns the pilot, so players can't escape by flying too high.
            if (cfg.altitudeEnabled && (tickCounter % cfg.altitudeIntervalTicks) == 0) {
                double over = jet.anchor().getY() - cfg.altitudeMaxY;
                if (over > 0) {
                    UUID d = jet.driver();
                    if (d != null) {
                        Player p = Bukkit.getPlayer(d);
                        if (p != null) {
                            p.sendActionBar(Component.text(cfg.altitudeMessage, NamedTextColor.RED));
                        }
                    }
                    jet.world().spawnParticle(Particle.LARGE_SMOKE,
                            jet.anchor().clone().add(0, 0.6, 0), 8, 0.6, 0.4, 0.6, 0.02);
                    double dmg = cfg.altitudeDamage + cfg.altitudeDamagePer10 * (over / 10.0);
                    if (dmg > 0 && jet.damage(dmg)) {
                        UUID dd = jet.driver();
                        if (dd != null) {
                            restoreDriver(dd);
                        }
                        jetIt.remove();
                        forgetDriver(jet);
                        continue; // destroyed by altitude
                    }
                }
            }
            if (jet.weaponLock() > 0) {
                jet.setWeaponLock(jet.weaponLock() - 1);
            }
            if (jet.rocketReload() > 0) {
                jet.setRocketReload(jet.rocketReload() - 1);
            }
            if (jet.bombReload() > 0) {
                jet.setBombReload(jet.bombReload() - 1);
            }
            jet.regenAmmo(plugin.config());
            UUID driverId = jet.driver();
            if (driverId != null) {
                Player driver = Bukkit.getPlayer(driverId);
                if (driver == null || !driver.isOnline()
                        || driver.getVehicle() == null
                        || !driver.getVehicle().equals(jet.core())) {
                    if (driver != null) {
                        restoreDriver(driverId);
                    }
                    jet.clearDriver();
                    driverToJet.remove(driverId);
                    if (jet.isAirborne() && jet.speed() > 0.05) {
                        jet.setUnmanned(true); // lost its pilot mid-air: fly on, crash
                    }
                } else {
                    try {
                        FlightController.fly(jet, driver, plugin.config());
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Flight tick failed: " + ex);
                    }
                }
            } else if (jet.isUnmanned()) {
                try {
                    FlightController.glide(jet, plugin.config());
                } catch (Exception ex) {
                    plugin.getLogger().warning("Glide tick failed: " + ex);
                }
            }
            sweepProjectiles(jet);
            if (tickCounter % 20 == 0) {
                jet.persistState();
            }
        }
        reconcileCloak();
        weapons.tick();
    }

    public void trackDebris(Entity entity, Vector velocity) {
        debris.put(entity.getUniqueId(), new DebrisState(entity, velocity));
    }

    private void tickDebris() {
        Iterator<Map.Entry<UUID, DebrisState>> it = debris.entrySet().iterator();
        while (it.hasNext()) {
            DebrisState state = it.next().getValue();
            Entity e = state.entity();
            if (e == null || !e.isValid()) {
                it.remove();
                continue;
            }
            Long expire = e.getPersistentDataContainer().get(Keys.DEBRIS_EXPIRE, PersistentDataType.LONG);
            if (expire == null || e.getWorld().getFullTime() >= expire) {
                e.remove();
                it.remove();
                continue;
            }
            Vector velocity = state.velocity();
            velocity.setY(velocity.getY() - 0.04);
            Location next = e.getLocation().add(velocity);
            if (!next.getWorld().isChunkLoaded(next.getBlockX() >> 4, next.getBlockZ() >> 4)) {
                e.remove();
                it.remove();
                continue;
            }
            e.teleport(next);
        }
    }

    /**
     * Keep seated pilots' armour hidden from other players and restore it once
     * they stop driving. Driven by the live driver set, so it covers every exit
     * path (dismount, disconnect, crash, unload) without packet-spamming viewers.
     */
    private void reconcileCloak() {
        java.util.Set<UUID> current = new java.util.HashSet<>();
        for (Jet jet : jets.values()) {
            UUID d = jet.driver();
            if (d != null) {
                current.add(d);
            }
        }
        for (java.util.Iterator<UUID> it = cloaked.iterator(); it.hasNext(); ) {
            UUID u = it.next();
            if (!current.contains(u)) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    DriverCloak.show(p);
                }
                it.remove();
            }
        }
        for (UUID u : current) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) {
                continue;
            }
            boolean firstTime = cloaked.add(u);
            if (firstTime) {
                DriverCloak.hide(p);
            }
        }
    }

    public void refreshDriverCloak(Player driver) {
        if (driver != null && byDriver(driver.getUniqueId()) != null) {
            DriverCloak.hide(driver);
        }
    }

    public void hideCloakedDriversFrom(Player viewer) {
        for (UUID driverId : cloaked) {
            Player driver = Bukkit.getPlayer(driverId);
            if (driver != null) {
                DriverCloak.hideFrom(driver, viewer);
            }
        }
    }

    // --------------------------------------------------------------- chunk (de)hydration

    /** Rebuild wrappers for any untracked jet entities in this set. */
    public void onEntitiesLoad(Collection<Entity> entities) {
        Map<UUID, List<Entity>> groups = new HashMap<>();
        for (Entity e : entities) {
            if (!e.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
                continue;
            }
            String idStr = e.getPersistentDataContainer().get(Keys.JET_ID, PersistentDataType.STRING);
            if (idStr == null) {
                continue;
            }
            UUID id;
            try {
                id = UUID.fromString(idStr);
            } catch (IllegalArgumentException ex) {
                e.remove();
                continue;
            }
            if (jets.containsKey(id)) {
                continue; // already tracked
            }
            groups.computeIfAbsent(id, k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<UUID, List<Entity>> entry : groups.entrySet()) {
            Jet jet = Jet.rehydrate(plugin, entry.getKey(), entry.getValue());
            if (jet != null) {
                jets.put(entry.getKey(), jet);
            } else {
                for (Entity e : entry.getValue()) {
                    e.remove(); // incomplete/garbage group
                }
            }
        }
    }

    /** Drop wrappers for jets whose entities are unloading (entities persist). */
    public void onEntitiesUnload(Collection<Entity> entities) {
        for (Entity e : entities) {
            String idStr = e.getPersistentDataContainer().get(Keys.JET_ID, PersistentDataType.STRING);
            if (idStr == null) {
                continue;
            }
            Jet jet;
            try {
                jet = jets.get(UUID.fromString(idStr));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (jet == null) {
                continue;
            }
            UUID d = jet.driver();
            if (d != null) {
                restoreDriver(d);
                jet.eject();
            }
            jet.persistState();
            forget(jet);
        }
    }

    // --------------------------------------------------------------- registry

    public Jet create(Location at, double yaw) {
        Jet jet = Jet.create(plugin, at, yaw);
        jets.put(jet.id(), jet);
        return jet;
    }

    /** Re-apply render smoothing config to every live jet (called on /jet reload). */
    public void applyRenderSettings() {
        for (Jet jet : jets.values()) {
            jet.applyRenderSettings();
        }
    }

    public Jet byId(UUID id) {
        return jets.get(id);
    }

    public Jet byDriver(UUID playerId) {
        UUID jetId = driverToJet.get(playerId);
        return jetId == null ? null : jets.get(jetId);
    }

    public Jet byEntity(Entity entity) {
        String id = entity.getPersistentDataContainer().get(Keys.JET_ID, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        try {
            return jets.get(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public Collection<Jet> all() {
        return jets.values();
    }

    public int count() {
        return jets.size();
    }

    // --------------------------------------------------------------- riding

    public boolean enter(Jet jet, Player player) {
        if (jet.isOccupied() || byDriver(player.getUniqueId()) != null) {
            return false;
        }
        jet.mount(player);
        driverToJet.put(player.getUniqueId(), jet.id());
        player.setInvisible(true);
        cloaked.add(player.getUniqueId());
        DriverCloak.hide(player); // hide armour immediately
        return true;
    }

    public void handleDismount(Player player) {
        releaseDriver(player, true);
    }

    public void handleDriverLoss(Player player) {
        releaseDriver(player, false);
    }

    private void releaseDriver(Player player, boolean bailOutEffects) {
        UUID jetId = driverToJet.remove(player.getUniqueId());
        if (jetId != null) {
            restoreDriver(player.getUniqueId());
            Jet jet = jets.get(jetId);
            // a gentle parachute so bailing out at altitude isn't an instant death
            if (bailOutEffects) {
                int sf = plugin.config().ejectSlowFallTicks;
                if (sf > 0) {
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOW_FALLING, sf, 0, true, false, false));
                }
                Vector ejectVelocity = new Vector(0, 0.85, 0);
                if (jet != null) {
                    ejectVelocity.add(jet.forward().multiply(-0.35));
                }
                // Apply one tick later, after the dismount's own velocity transfer,
                // so the cockpit ejection vector wins.
                org.bukkit.Bukkit.getScheduler().runTask(plugin.bukkitPlugin(), () -> {
                    if (player.isOnline() && player.getVehicle() == null) {
                        player.setVelocity(ejectVelocity);
                    }
                });
            }
            if (jet != null) {
                jet.clearDriver();
                // Bailed out in the air → the jet flies on unmanned until it
                // crashes. On the ground it just parks.
                if (jet.isAirborne() && jet.speed() > 0.05) {
                    jet.setUnmanned(true);
                } else {
                    jet.setSpeed(0);
                }
            }
        }
    }

    // --------------------------------------------------------------- damage

    public void damageJetsFromExplosion(Location loc, double power) {
        JetConfig cfg = plugin.config();
        for (Jet jet : new ArrayList<>(jets.values())) {
            Explosions.applyBlastTo(jet, loc, power, cfg);
        }
    }

    /**
     * Damage from an AntiAirCraft rocket: a FLAT one-creeper hit with NO
     * distance falloff, to any jet near the blast, so an AntiAir rocket breaks a jet
     * identically point-blank or at the edge of the turret's range.
     */
    public void damageJetsFromAntiAir(Location loc) {
        JetConfig cfg = plugin.config();
        for (Jet jet : new ArrayList<>(jets.values())) {
            if (!jet.isActive() || jet.world() != loc.getWorld()) {
                continue;
            }
            if (jet.anchor().distance(loc) <= 10.0) {
                jet.damage(cfg.creeperDamage); // exactly 1 creeper, distance-independent
            }
        }
    }

    public void setInternalExplosion(boolean value) {
        this.internalExplosion = value;
    }

    public boolean isInternalExplosion() {
        return internalExplosion;
    }

    /** The pilot who fired the munition currently detonating - immune to its blast. */
    public void setMunitionImmunePilot(UUID pilot) {
        this.munitionImmunePilot = pilot;
    }

    public UUID munitionImmunePilot() {
        return munitionImmunePilot;
    }

    // --------------------------------------------------------------- weapon damage

    public void meleeFromPlayer(Player attacker) {
        if (byDriver(attacker.getUniqueId()) != null) {
            return; // a pilot's swing fires rockets, it doesn't melee
        }
        JetConfig cfg = plugin.config();
        World w = attacker.getWorld();
        Location eye = attacker.getEyeLocation();
        Vector dir = eye.getDirection();
        double reach = 4.5;
        Jet best = null;
        double bestDist = reach;
        Vector hitAt = null;
        for (Jet jet : jets.values()) {
            if (!jet.isActive() || jet.world() != w || jet.hitbox() == null || !jet.hitbox().isValid()) {
                continue;
            }
            BoundingBox box = jet.hitbox().getBoundingBox().expand(0.2);
            RayTraceResult r = box.rayTrace(eye.toVector(), dir, reach);
            if (r == null) {
                continue;
            }
            double d = eye.toVector().distance(r.getHitPosition());
            if (d < bestDist) {
                bestDist = d;
                best = jet;
                hitAt = r.getHitPosition();
            }
        }
        if (best == null) {
            return;
        }
        if (w.rayTraceBlocks(eye, dir, Math.max(0.1, bestDist - 0.05), FluidCollisionMode.NEVER, true) != null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = meleeCd.get(attacker.getUniqueId());
        if (last != null && now - last < Math.max(50, cfg.weaponMeleeCooldownMs)) {
            return;
        }
        meleeCd.put(attacker.getUniqueId(), now);
        weaponHitFx(new Location(w, hitAt.getX(), hitAt.getY(), hitAt.getZ()));
        best.damage(best.maxHealth() * cfg.weaponMeleePercent / 100.0);
    }

    private void sweepProjectiles(Jet jet) {
        Interaction hb = jet.hitbox();
        if (hb == null || !hb.isValid()) {
            return;
        }
        JetConfig cfg = plugin.config();
        UUID driver = jet.driver();
        for (Entity e : jet.world().getNearbyEntities(hb.getBoundingBox().expand(0.25))) {
            if (!(e instanceof Projectile proj) || !isWeaponProjectile(proj)) {
                continue;
            }
            if (driver != null && proj.getShooter() instanceof Player sp
                    && sp.getUniqueId().equals(driver)) {
                continue; // the pilot's own projectiles don't count
            }
            double pct = (proj instanceof Fireball) ? cfg.weaponFireballPercent : cfg.weaponArrowPercent;
            Location at = proj.getLocation();
            proj.remove();
            weaponHitFx(at);
            if (jet.damage(jet.maxHealth() * pct / 100.0)) {
                return; // destroyed by this hit
            }
        }
    }

    private static boolean isWeaponProjectile(Projectile p) {
        return !(p instanceof org.bukkit.entity.FishHook
                || p instanceof org.bukkit.entity.EnderPearl
                || p instanceof org.bukkit.entity.ThrownExpBottle
                || p instanceof org.bukkit.entity.ThrownPotion);
    }

    private void weaponHitFx(Location at) {
        World w = at.getWorld();
        if (w == null) {
            return;
        }
        w.spawnParticle(Particle.CRIT, at, 8, 0.25, 0.25, 0.25, 0.1);
        w.spawnParticle(Particle.ELECTRIC_SPARK, at, 6, 0.2, 0.2, 0.2, 0.05);
        w.playSound(at, Sound.ENTITY_IRON_GOLEM_HURT, 0.6f, 1.3f);
    }

    // --------------------------------------------------------------- removal

    /**
     * Nuke everything: destroy every tracked jet, then sweep all loaded worlds
     * for any orphaned jet entities (parts left behind by crashes/old versions)
     * and clear every cache. Returns {trackedJetsRemoved, orphanEntitiesRemoved}.
     */
    public int[] cleanupAll() {
        int jetCount = jets.size();
        for (Jet jet : new ArrayList<>(jets.values())) {
            UUID d = jet.driver();
            if (d != null) {
                restoreDriver(d);
            }
            jet.eject();
            jet.removeEntities();
        }
        jets.clear();
        driverToJet.clear();
        cloaked.clear();
        debris.clear();
        weapons.clear();
        munitionImmunePilot = null;
        internalExplosion = false;

        int orphans = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)
                        || e.getScoreboardTags().contains(Keys.DEBRIS_TAG)
                        || e.getPersistentDataContainer().has(Keys.JET_ID, PersistentDataType.STRING)) {
                    e.remove();
                    orphans++;
                }
            }
        }
        return new int[]{jetCount, orphans};
    }

    public void remove(Jet jet, boolean effects) {
        if (effects) {
            jet.destroy(true);
        } else {
            jet.eject();
            jet.removeEntities();
        }
        forget(jet);
    }

    /** Drop the wrapper from the registry without touching its entities. */
    private void forget(Jet jet) {
        jets.remove(jet.id());
        forgetDriver(jet);
    }

    private void forgetDriver(Jet jet) {
        UUID d = jet.driver();
        if (d != null) {
            driverToJet.remove(d);
        } else {
            driverToJet.values().removeIf(id -> id.equals(jet.id()));
        }
    }

    private void restoreDriver(UUID driverId) {
        Player p = Bukkit.getPlayer(driverId);
        if (p != null) {
            p.setInvisible(false);
            DriverCloak.show(p);
        }
        cloaked.remove(driverId);
    }
}
