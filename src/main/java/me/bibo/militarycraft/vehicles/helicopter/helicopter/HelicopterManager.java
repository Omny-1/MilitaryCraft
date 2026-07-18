package me.bibo.militarycraft.vehicles.helicopter.helicopter;

import me.bibo.militarycraft.vehicles.helicopter.HelicopterRuntime;
import me.bibo.militarycraft.vehicles.helicopter.combat.Explosions;
import me.bibo.militarycraft.vehicles.helicopter.combat.WeaponSystem;
import me.bibo.militarycraft.vehicles.helicopter.config.HelicopterConfig;
import me.bibo.militarycraft.vehicles.helicopter.control.HelicopterController;
import me.bibo.militarycraft.vehicles.helicopter.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
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
 * Registry of all helicopters in loaded chunks, plus the single per-tick
 * update loop. Helicopters live as persistent entities; their wrappers are
 * rebuilt from those entities on entity-load and dropped on entity-unload,
 * so they survive chunk reloads, restarts and crashes without orphaning.
 */
public final class HelicopterManager {

    private static final int DRIVER_DESYNC_GRACE_TICKS = 8;

    private final HelicopterRuntime plugin;
    private final WeaponSystem weapons;
    private final Map<UUID, Helicopter> ships = new LinkedHashMap<>();
    private final Map<UUID, UUID> riderToShip = new HashMap<>(); // every rider -> helicopter
    private final Map<UUID, Integer> driverDesyncTicks = new HashMap<>();
    private BukkitTask task;
    private boolean internalExplosion;
    private UUID munitionImmunePilot;
    private long tickCounter;
    /** Riders whose armour is currently hidden from other players. */
    private final java.util.Set<UUID> cloaked = new java.util.HashSet<>();
    /** Last melee-hit timestamp per attacker, for the weapon-melee cooldown. */
    private final Map<UUID, Long> meleeCd = new HashMap<>();

    public HelicopterManager(HelicopterRuntime plugin) {
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

    public void adoptExisting() {
        for (World world : Bukkit.getWorlds()) {
            List<Entity> candidates = new ArrayList<>();
            candidates.addAll(world.getEntitiesByClass(ArmorStand.class));
            candidates.addAll(world.getEntitiesByClass(Interaction.class));
            candidates.addAll(world.getEntitiesByClass(BlockDisplay.class));
            candidates.addAll(world.getEntitiesByClass(TextDisplay.class));
            onEntitiesLoad(candidates);
        }
    }

    public void shutdown() {
        stop();
        for (Helicopter heli : new ArrayList<>(ships.values())) {
            showRiders(heli);
            heli.ejectAll();
            heli.persistState();
        }
        ships.clear();
        riderToShip.clear();
        driverDesyncTicks.clear();
        weapons.clear();
    }

    private void tick() {
        tickCounter++;
        HelicopterConfig hcfg = plugin.config();
        Iterator<Helicopter> it = ships.values().iterator();
        while (it.hasNext()) {
            Helicopter heli = it.next();
            if (!heli.isActive()) {
                showRiders(heli);
                riderToShip.values().removeIf(id -> id.equals(heli.id()));
                heli.removeEntities();
                it.remove();
                continue;
            }
            heli.stabilize(); // undo any external knockback on a hovering rig

            // Atmosphere ceiling: above max-y the thin air slowly tears the helicopter
            // apart and warns the pilot, so players can't float away too high.
            if (hcfg.altitudeEnabled && (tickCounter % hcfg.altitudeIntervalTicks) == 0) {
                double over = heli.anchor().getY() - hcfg.altitudeMaxY;
                if (over > 0) {
                    UUID d = heli.driver();
                    if (d != null) {
                        Player p = Bukkit.getPlayer(d);
                        if (p != null) {
                            p.sendActionBar(Component.text(hcfg.altitudeMessage, NamedTextColor.RED));
                        }
                    }
                    heli.world().spawnParticle(Particle.LARGE_SMOKE,
                            heli.anchor().clone().add(0, 6, 0), 12, 2.0, 1.2, 2.0, 0.02);
                    double dmg = hcfg.altitudeDamage + hcfg.altitudeDamagePer10 * (over / 10.0);
                    if (dmg > 0 && heli.damage(dmg)) {
                        showRiders(heli);
                        riderToShip.values().removeIf(id -> id.equals(heli.id()));
                        it.remove();
                        continue; // destroyed by altitude
                    }
                }
            }
            if (heli.weaponLock() > 0) {
                heli.setWeaponLock(heli.weaponLock() - 1);
            }
            if (heli.bombCooldown() > 0) {
                heli.setBombCooldown(heli.bombCooldown() - 1);
            }
            if (heli.rocketReload() > 0) {
                heli.setRocketReload(heli.rocketReload() - 1);
            }
            heli.regenAmmo(plugin.config());

            UUID driverId = heli.driver();
            if (driverId != null) {
                Player driver = Bukkit.getPlayer(driverId);
                if (!driverStillMounted(heli, driver)) {
                    handleDriverDesync(heli, driverId, driver);
                } else {
                    driverDesyncTicks.remove(heli.id());
                    try {
                        HelicopterController.fly(heli, driver, plugin.config());
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Helicopter flight tick failed: " + ex);
                    }
                }
            } else if (heli.isUnmanned()) {
                try {
                    HelicopterController.glide(heli, plugin.config());
                } catch (Exception ex) {
                    plugin.getLogger().warning("Helicopter drift tick failed: " + ex);
                }
            }
            sweepProjectiles(heli);
        }
        reconcileCloak();
        weapons.tick();
    }

    private boolean driverStillMounted(Helicopter heli, Player driver) {
        return driver != null
                && driver.isOnline()
                && driver.getVehicle() != null
                && driver.getVehicle().equals(heli.core());
    }

    private void handleDriverDesync(Helicopter heli, UUID driverId, Player driver) {
        int misses = driverDesyncTicks.merge(heli.id(), 1, Integer::sum);
        if (driver != null && driver.isOnline()
                && heli.core() != null && heli.core().isValid()
                && driver.getWorld().equals(heli.core().getWorld())
                && misses <= DRIVER_DESYNC_GRACE_TICKS) {
            if (driver.getVehicle() == null) {
                heli.core().addPassenger(driver);
            }
            if (plugin.config().debug && misses == 1) {
                plugin.getLogger().warning("Helicopter driver temporarily lost passenger link; waiting before ejecting.");
            }
            return;
        }

        driverDesyncTicks.remove(heli.id());
        if (driver != null) {
            driver.setInvisible(false);
        }
        heli.clearDriver();
        riderToShip.remove(driverId);
        if (heli.isAirborne()) {
            heli.setUnmanned(true); // lost its pilot aloft: drift down
        }
    }

    /**
     * Keep every rider's armour hidden from other players and restore it once
     * they leave. Driven by the live rider set, so it covers all exit paths
     * (dismount, disconnect, destruction, unload) without touching each of them,
     * and periodically re-sends so players who come into view also see no armour.
     */
    private void reconcileCloak() {
        java.util.Set<UUID> current = riderToShip.keySet();
        for (java.util.Iterator<UUID> ci = cloaked.iterator(); ci.hasNext(); ) {
            UUID u = ci.next();
            if (!current.contains(u)) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    me.bibo.militarycraft.vehicles.helicopter.util.DriverCloak.show(p);
                }
                ci.remove();
            }
        }
        boolean periodic = (tickCounter % 20 == 0);
        for (UUID u : current) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) {
                continue;
            }
            boolean firstTime = cloaked.add(u);
            if (firstTime || periodic) {
                me.bibo.militarycraft.vehicles.helicopter.util.DriverCloak.hide(p);
            }
        }
    }

    public void refreshCloakFor(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        for (UUID u : cloaked) {
            Player rider = Bukkit.getPlayer(u);
            if (rider != null) {
                me.bibo.militarycraft.vehicles.helicopter.util.DriverCloak.hideFrom(rider, viewer);
            }
        }
    }

    // --------------------------------------------------------------- chunk (de)hydration

    public void onEntitiesLoad(Collection<Entity> entities) {
        Map<UUID, List<Entity>> groups = new HashMap<>();
        for (Entity e : entities) {
            if (!e.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
                continue;
            }
            String idStr = e.getPersistentDataContainer().get(Keys.SHIP_ID, PersistentDataType.STRING);
            if (idStr == null) {
                continue; // e.g. a munition display (tagged but no helicopter id) — ignore
            }
            UUID id;
            try {
                id = UUID.fromString(idStr);
            } catch (IllegalArgumentException ex) {
                e.remove();
                continue;
            }
            if (ships.containsKey(id)) {
                continue;
            }
            groups.computeIfAbsent(id, k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<UUID, List<Entity>> entry : groups.entrySet()) {
            Helicopter heli = Helicopter.rehydrate(plugin, entry.getKey(), entry.getValue());
            if (heli != null) {
                ships.put(entry.getKey(), heli);
                // crash recovery: re-adopt any players still seated after a restart
                for (UUID rid : heli.reclaimRiders()) {
                    riderToShip.put(rid, heli.id());
                    Player p = Bukkit.getPlayer(rid);
                    if (p != null) {
                        p.setInvisible(true);
                    }
                }
            } else {
                for (Entity e : entry.getValue()) {
                    e.remove(); // incomplete/garbage group
                }
            }
        }
    }

    public void onEntitiesUnload(Collection<Entity> entities) {
        Map<UUID, Boolean> coreUnloaded = new HashMap<>();
        for (Entity e : entities) {
            String idStr = e.getPersistentDataContainer().get(Keys.SHIP_ID, PersistentDataType.STRING);
            if (idStr == null) {
                continue;
            }
            UUID id;
            try {
                id = UUID.fromString(idStr);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            String role = e.getPersistentDataContainer().get(Keys.SHIP_PART, PersistentDataType.STRING);
            coreUnloaded.merge(id, "core".equals(role), Boolean::logicalOr);
        }

        for (Map.Entry<UUID, Boolean> entry : coreUnloaded.entrySet()) {
            if (!entry.getValue()) {
                continue;
            }
            Helicopter heli = ships.get(entry.getKey());
            if (heli == null) {
                continue;
            }
            if (plugin.config().debug) {
                plugin.getLogger().warning("Helicopter core entity unloaded; dropping active wrapper.");
            }
            showRiders(heli);
            heli.ejectAll();
            riderToShip.values().removeIf(id -> id.equals(heli.id()));
            driverDesyncTicks.remove(heli.id());
            heli.persistState();
            forget(heli);
        }
    }

    // --------------------------------------------------------------- registry

    public Helicopter create(Location at, double yaw, UUID owner) {
        Helicopter heli = Helicopter.create(plugin, at, yaw, owner);
        ships.put(heli.id(), heli);
        return heli;
    }

    /** How many currently-loaded helicopters were placed by this player. */
    public int countByOwner(UUID owner) {
        if (owner == null) {
            return 0;
        }
        int n = 0;
        for (Helicopter heli : ships.values()) {
            if (owner.equals(heli.owner())) {
                n++;
            }
        }
        return n;
    }

    public Helicopter byId(UUID id) {
        return id == null ? null : ships.get(id);
    }

    /** The helicopter this player is riding (as pilot OR passenger), if any. */
    public Helicopter byRider(UUID playerId) {
        UUID shipId = riderToShip.get(playerId);
        return shipId == null ? null : ships.get(shipId);
    }

    /** The helicopter this player is piloting (only if they are the driver). */
    public Helicopter byDriver(UUID playerId) {
        Helicopter heli = byRider(playerId);
        return (heli != null && heli.isDriver(playerId)) ? heli : null;
    }

    public void forgetRider(UUID playerId) {
        if (playerId == null) {
            return;
        }
        riderToShip.remove(playerId);
        cloaked.remove(playerId);
    }

    public Helicopter byEntity(Entity entity) {
        String id = entity.getPersistentDataContainer().get(Keys.SHIP_ID, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        try {
            return ships.get(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public Collection<Helicopter> all() {
        return ships.values();
    }

    public int count() {
        return ships.size();
    }

    // --------------------------------------------------------------- riding

    public boolean enter(Helicopter heli, Player player) {
        if (heli.isFull() || byRider(player.getUniqueId()) != null) {
            return false;
        }
        if (!heli.addRider(player)) {
            return false;
        }
        riderToShip.put(player.getUniqueId(), heli.id());
        player.setInvisible(true);
        cloaked.add(player.getUniqueId());
        me.bibo.militarycraft.vehicles.helicopter.util.DriverCloak.hide(player); // hide armour immediately
        return true;
    }

    public void handleDismount(Player player) {
        UUID uuid = player.getUniqueId();
        UUID shipId = riderToShip.remove(uuid);
        if (shipId == null) {
            return;
        }
        driverDesyncTicks.remove(shipId);
        player.setInvisible(false);
        me.bibo.militarycraft.vehicles.helicopter.util.DriverCloak.show(player);
        cloaked.remove(uuid);
        int sf = plugin.config().ejectSlowFallTicks;
        if (sf > 0) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOW_FALLING, sf, 0, true, false, false));
        }
        Helicopter heli = ships.get(shipId);
        if (heli == null) {
            return;
        }
        boolean wasDriver = heli.isDriver(uuid);
        heli.removeRider(uuid);
        if (wasDriver) {
            // the pilot left: drift the whole craft down gently (passengers ride
            // it down) if aloft, otherwise just park it.
            if (heli.isAirborne()) {
                heli.setUnmanned(true);
            } else {
                heli.setSpeed(0);
                heli.setVSpeed(0);
            }
        }
    }

    /** Make every current rider of this helicopter visible again (cleanup helper). */
    private void showRiders(Helicopter heli) {
        for (UUID id : heli.allRiders()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setInvisible(false);
                me.bibo.militarycraft.vehicles.helicopter.util.DriverCloak.show(p);
            }
            cloaked.remove(id);
        }
    }

    // --------------------------------------------------------------- damage

    public void damageShipsFromExplosion(Location loc, double power) {
        HelicopterConfig cfg = plugin.config();
        for (Helicopter heli : new ArrayList<>(ships.values())) {
            Explosions.applyBlastTo(heli, loc, power, cfg);
        }
    }

    /**
     * Damage from an AntiAirCraft rocket: a FLAT one-creeper hit with NO
     * distance falloff, to any helicopter near the blast, so an AntiAir rocket
     * breaks a helicopter identically point-blank or at the edge of the turret's range.
     */
    public void damageShipsFromAntiAir(Location loc) {
        HelicopterConfig cfg = plugin.config();
        for (Helicopter heli : new ArrayList<>(ships.values())) {
            if (!heli.isActive() || heli.world() != loc.getWorld()) {
                continue;
            }
            if (heli.minBlastDistance(loc) <= 10.0) {
                heli.damage(cfg.creeperDamage); // exactly 1 creeper, distance-independent
            }
        }
    }

    public void setInternalExplosion(boolean value) {
        this.internalExplosion = value;
    }

    public boolean isInternalExplosion() {
        return internalExplosion;
    }

    public void setMunitionImmunePilot(UUID pilot) {
        this.munitionImmunePilot = pilot;
    }

    public UUID munitionImmunePilot() {
        return munitionImmunePilot;
    }

    // --------------------------------------------------------------- weapon damage

    public void meleeFromPlayer(Player attacker) {
        if (byRider(attacker.getUniqueId()) != null) {
            return; // an onboard player's swing controls the helicopter weapon, it doesn't melee
        }
        HelicopterConfig cfg = plugin.config();
        World w = attacker.getWorld();
        Location eye = attacker.getEyeLocation();
        Vector dir = eye.getDirection();
        double reach = 4.5;
        Helicopter best = null;
        double bestDist = reach;
        Vector hitAt = null;
        for (Helicopter heli : ships.values()) {
            if (!heli.isActive() || heli.world() != w) {
                continue;
            }
            RayHit r = rayTraceHitboxes(heli, eye.toVector(), dir, reach, 0.2);
            if (r == null) {
                continue;
            }
            if (r.distance() < bestDist) {
                bestDist = r.distance();
                best = heli;
                hitAt = r.position();
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

    private void sweepProjectiles(Helicopter heli) {
        HelicopterConfig cfg = plugin.config();
        UUID driver = heli.driver();
        List<BoundingBox> boxes = new ArrayList<>(heli.hitboxes().size());
        BoundingBox broad = null;
        for (Interaction hb : heli.hitboxes()) {
            if (hb == null || !hb.isValid()) {
                continue;
            }
            BoundingBox box = hb.getBoundingBox().expand(0.25);
            boxes.add(box);
            broad = broad == null ? box.clone() : broad.union(box);
        }
        if (broad == null) {
            return;
        }
        for (Entity e : heli.world().getNearbyEntities(broad)) {
            if (!(e instanceof Projectile proj) || !isWeaponProjectile(proj)) {
                continue;
            }
            Vector pos = proj.getLocation().toVector();
            boolean hit = false;
            for (BoundingBox box : boxes) {
                if (box.contains(pos)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
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
            if (heli.damage(heli.maxHealth() * pct / 100.0)) {
                return; // destroyed by this hit
            }
        }
    }

    private RayHit rayTraceHitboxes(Helicopter heli, Vector origin, Vector dir, double reach, double pad) {
        RayHit best = null;
        for (Interaction hb : heli.hitboxes()) {
            if (hb == null || !hb.isValid()) {
                continue;
            }
            RayTraceResult r = hb.getBoundingBox().expand(pad).rayTrace(origin, dir, reach);
            if (r == null) {
                continue;
            }
            double dist = origin.distance(r.getHitPosition());
            if (best == null || dist < best.distance()) {
                best = new RayHit(r.getHitPosition(), dist);
            }
        }
        return best;
    }

    private record RayHit(Vector position, double distance) {
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

    public int[] cleanupAll() {
        int shipCount = ships.size();
        for (Helicopter heli : new ArrayList<>(ships.values())) {
            showRiders(heli);
            heli.ejectAll();
            heli.removeEntities();
        }
        ships.clear();
        riderToShip.clear();
        driverDesyncTicks.clear();
        weapons.clear();
        munitionImmunePilot = null;
        internalExplosion = false;

        int orphans = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)
                        || e.getPersistentDataContainer().has(Keys.SHIP_ID, PersistentDataType.STRING)) {
                    e.remove();
                    orphans++;
                }
            }
        }
        return new int[]{shipCount, orphans};
    }

    public void remove(Helicopter heli, boolean effects) {
        showRiders(heli);
        if (effects) {
            heli.destroy(true);
        } else {
            heli.ejectAll();
            heli.removeEntities();
        }
        forget(heli);
    }

    private void forget(Helicopter heli) {
        ships.remove(heli.id());
        riderToShip.values().removeIf(id -> id.equals(heli.id()));
        driverDesyncTicks.remove(heli.id());
    }
}
