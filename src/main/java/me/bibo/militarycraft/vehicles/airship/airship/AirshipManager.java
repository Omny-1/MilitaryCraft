package me.bibo.militarycraft.vehicles.airship.airship;

import me.bibo.militarycraft.vehicles.airship.AirshipRuntime;
import me.bibo.militarycraft.vehicles.airship.combat.Explosions;
import me.bibo.militarycraft.vehicles.airship.combat.WeaponSystem;
import me.bibo.militarycraft.vehicles.airship.config.AirshipConfig;
import me.bibo.militarycraft.vehicles.airship.control.AirshipController;
import me.bibo.militarycraft.vehicles.airship.util.Keys;
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
 * Registry of all airships in loaded chunks, plus the single per-tick update
 * loop. Airships live as persistent entities; their wrappers are rebuilt from
 * those entities on entity-load and dropped on entity-unload, so they survive
 * chunk reloads, restarts and crashes without orphaning.
 */
public final class AirshipManager {

    private final AirshipRuntime plugin;
    private final WeaponSystem weapons;
    private final Map<UUID, Airship> ships = new LinkedHashMap<>();
    private final Map<UUID, UUID> riderToShip = new HashMap<>(); // every rider -> ship
    private BukkitTask task;
    private boolean internalExplosion;
    private UUID munitionImmunePilot;
    private long tickCounter;
    /** Riders whose armour is currently hidden from other players. */
    private final java.util.Set<UUID> cloaked = new java.util.HashSet<>();
    /** Last melee-hit timestamp per attacker, for the weapon-melee cooldown. */
    private final Map<UUID, Long> meleeCd = new HashMap<>();

    public AirshipManager(AirshipRuntime plugin) {
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
        for (Airship ship : new ArrayList<>(ships.values())) {
            showRiders(ship);
            ship.ejectAll();
            ship.persistState();
        }
        ships.clear();
        riderToShip.clear();
        weapons.clear();
    }

    private void tick() {
        tickCounter++;
        AirshipConfig acfg = plugin.config();
        Iterator<Airship> it = ships.values().iterator();
        while (it.hasNext()) {
            Airship ship = it.next();
            if (!ship.isActive()) {
                showRiders(ship);
                riderToShip.values().removeIf(id -> id.equals(ship.id()));
                ship.removeEntities();
                it.remove();
                continue;
            }
            ship.stabilize(); // undo any external knockback on a hovering rig

            // Atmosphere ceiling: above max-y the thin air slowly tears the airship
            // apart and warns the pilot, so players can't float away too high.
            if (acfg.altitudeEnabled && (tickCounter % acfg.altitudeIntervalTicks) == 0) {
                double over = ship.anchor().getY() - acfg.altitudeMaxY;
                if (over > 0) {
                    UUID d = ship.driver();
                    if (d != null) {
                        Player p = Bukkit.getPlayer(d);
                        if (p != null) {
                            p.sendActionBar(Component.text(acfg.altitudeMessage, NamedTextColor.RED));
                        }
                    }
                    ship.world().spawnParticle(Particle.LARGE_SMOKE,
                            ship.anchor().clone().add(0, 6, 0), 12, 2.0, 1.2, 2.0, 0.02);
                    double dmg = acfg.altitudeDamage + acfg.altitudeDamagePer10 * (over / 10.0);
                    if (dmg > 0 && ship.damage(dmg)) {
                        showRiders(ship);
                        riderToShip.values().removeIf(id -> id.equals(ship.id()));
                        it.remove();
                        continue; // destroyed by altitude
                    }
                }
            }
            if (ship.weaponLock() > 0) {
                ship.setWeaponLock(ship.weaponLock() - 1);
            }
            if (ship.bombCooldown() > 0) {
                ship.setBombCooldown(ship.bombCooldown() - 1);
            }
            ship.regenAmmo(plugin.config());

            UUID driverId = ship.driver();
            if (driverId != null) {
                Player driver = Bukkit.getPlayer(driverId);
                if (driver == null || !driver.isOnline()
                        || driver.getVehicle() == null
                        || !driver.getVehicle().equals(ship.core())) {
                    if (driver != null) {
                        driver.setInvisible(false);
                    }
                    ship.clearDriver();
                    riderToShip.remove(driverId);
                    if (ship.isAirborne()) {
                        ship.setUnmanned(true); // lost its pilot aloft: drift down
                    }
                } else {
                    try {
                        AirshipController.fly(ship, driver, plugin.config());
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Airship flight tick failed: " + ex);
                    }
                }
            } else if (ship.isUnmanned()) {
                try {
                    AirshipController.glide(ship, plugin.config());
                } catch (Exception ex) {
                    plugin.getLogger().warning("Airship drift tick failed: " + ex);
                }
            }
            sweepProjectiles(ship);
        }
        reconcileCloak();
        weapons.tick();
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
                    me.bibo.militarycraft.vehicles.airship.util.DriverCloak.show(p);
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
                me.bibo.militarycraft.vehicles.airship.util.DriverCloak.hide(p);
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
                me.bibo.militarycraft.vehicles.airship.util.DriverCloak.hideFrom(rider, viewer);
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
                continue; // e.g. a bomb display (tagged but no ship id) - ignore
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
            Airship ship = Airship.rehydrate(plugin, entry.getKey(), entry.getValue());
            if (ship != null) {
                ships.put(entry.getKey(), ship);
                // crash recovery: re-adopt any players still seated after a restart
                for (UUID rid : ship.reclaimRiders()) {
                    riderToShip.put(rid, ship.id());
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
        for (Entity e : entities) {
            String idStr = e.getPersistentDataContainer().get(Keys.SHIP_ID, PersistentDataType.STRING);
            if (idStr == null) {
                continue;
            }
            Airship ship;
            try {
                ship = ships.get(UUID.fromString(idStr));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (ship == null) {
                continue;
            }
            showRiders(ship);
            ship.ejectAll();
            riderToShip.values().removeIf(id -> id.equals(ship.id()));
            ship.persistState();
            forget(ship);
        }
    }

    // --------------------------------------------------------------- registry

    public Airship create(Location at, double yaw, UUID owner) {
        Airship ship = Airship.create(plugin, at, yaw, owner);
        ships.put(ship.id(), ship);
        return ship;
    }

    /** How many currently-loaded airships were placed by this player. */
    public int countByOwner(UUID owner) {
        if (owner == null) {
            return 0;
        }
        int n = 0;
        for (Airship ship : ships.values()) {
            if (owner.equals(ship.owner())) {
                n++;
            }
        }
        return n;
    }

    public Airship byId(UUID id) {
        return id == null ? null : ships.get(id);
    }

    /** The ship this player is riding (as pilot OR passenger), if any. */
    public Airship byRider(UUID playerId) {
        UUID shipId = riderToShip.get(playerId);
        return shipId == null ? null : ships.get(shipId);
    }

    /** The ship this player is piloting (only if they are the driver). */
    public Airship byDriver(UUID playerId) {
        Airship ship = byRider(playerId);
        return (ship != null && ship.isDriver(playerId)) ? ship : null;
    }

    public void forgetRider(UUID playerId) {
        if (playerId == null) {
            return;
        }
        riderToShip.remove(playerId);
        cloaked.remove(playerId);
    }

    public Airship byEntity(Entity entity) {
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

    public Collection<Airship> all() {
        return ships.values();
    }

    public int count() {
        return ships.size();
    }

    // --------------------------------------------------------------- riding

    public boolean enter(Airship ship, Player player) {
        if (ship.isFull() || byRider(player.getUniqueId()) != null) {
            return false;
        }
        if (!ship.addRider(player)) {
            return false;
        }
        riderToShip.put(player.getUniqueId(), ship.id());
        player.setInvisible(true);
        cloaked.add(player.getUniqueId());
        me.bibo.militarycraft.vehicles.airship.util.DriverCloak.hide(player); // hide armour immediately
        return true;
    }

    public void handleDismount(Player player) {
        UUID uuid = player.getUniqueId();
        UUID shipId = riderToShip.remove(uuid);
        if (shipId == null) {
            return;
        }
        player.setInvisible(false);
        me.bibo.militarycraft.vehicles.airship.util.DriverCloak.show(player);
        cloaked.remove(uuid);
        int sf = plugin.config().ejectSlowFallTicks;
        if (sf > 0) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOW_FALLING, sf, 0, true, false, false));
        }
        Airship ship = ships.get(shipId);
        if (ship == null) {
            return;
        }
        boolean wasDriver = ship.isDriver(uuid);
        ship.removeRider(uuid);
        if (wasDriver) {
            // the pilot left: drift the whole craft down gently (passengers ride
            // it down) if aloft, otherwise just park it.
            if (ship.isAirborne()) {
                ship.setUnmanned(true);
            } else {
                ship.setSpeed(0);
                ship.setVSpeed(0);
            }
        }
    }

    /** Make every current rider of this ship visible again (cleanup helper). */
    private void showRiders(Airship ship) {
        for (UUID id : ship.allRiders()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.setInvisible(false);
                me.bibo.militarycraft.vehicles.airship.util.DriverCloak.show(p);
            }
            cloaked.remove(id);
        }
    }

    // --------------------------------------------------------------- damage

    public void damageShipsFromExplosion(Location loc, double power) {
        AirshipConfig cfg = plugin.config();
        for (Airship ship : new ArrayList<>(ships.values())) {
            Explosions.applyBlastTo(ship, loc, power, cfg);
        }
    }

    /**
     * Damage from an AntiAirCraft rocket: a FLAT one-creeper hit with NO
     * distance falloff, to any airship near the blast, so an AntiAir rocket breaks an
     * airship identically point-blank or at the edge of the turret's range.
     */
    public void damageShipsFromAntiAir(Location loc) {
        AirshipConfig cfg = plugin.config();
        for (Airship ship : new ArrayList<>(ships.values())) {
            if (!ship.isActive() || ship.world() != loc.getWorld()) {
                continue;
            }
            if (ship.minBlastDistance(loc) <= 10.0) {
                ship.damage(cfg.creeperDamage); // exactly 1 creeper, distance-independent
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
            return; // an onboard player's swing controls the airship weapon, it doesn't melee
        }
        AirshipConfig cfg = plugin.config();
        World w = attacker.getWorld();
        Location eye = attacker.getEyeLocation();
        Vector dir = eye.getDirection();
        double reach = 4.5;
        Airship best = null;
        double bestDist = reach;
        Vector hitAt = null;
        for (Airship ship : ships.values()) {
            if (!ship.isActive() || ship.world() != w) {
                continue;
            }
            RayHit r = rayTraceAirshipHitboxes(ship, eye.toVector(), dir, reach, 0.2);
            if (r == null) {
                continue;
            }
            if (r.distance() < bestDist) {
                bestDist = r.distance();
                best = ship;
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

    private void sweepProjectiles(Airship ship) {
        AirshipConfig cfg = plugin.config();
        UUID driver = ship.driver();
        List<BoundingBox> boxes = new ArrayList<>(ship.hitboxes().size());
        BoundingBox broad = null;
        for (Interaction hb : ship.hitboxes()) {
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
        for (Entity e : ship.world().getNearbyEntities(broad)) {
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
            if (ship.damage(ship.maxHealth() * pct / 100.0)) {
                return; // destroyed by this hit
            }
        }
    }

    private RayHit rayTraceAirshipHitboxes(Airship ship, Vector origin, Vector dir, double reach, double pad) {
        RayHit best = null;
        for (Interaction hb : ship.hitboxes()) {
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
        for (Airship ship : new ArrayList<>(ships.values())) {
            showRiders(ship);
            ship.ejectAll();
            ship.removeEntities();
        }
        ships.clear();
        riderToShip.clear();
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

    public void remove(Airship ship, boolean effects) {
        showRiders(ship);
        if (effects) {
            ship.destroy(true);
        } else {
            ship.ejectAll();
            ship.removeEntities();
        }
        forget(ship);
    }

    private void forget(Airship ship) {
        ships.remove(ship.id());
        riderToShip.values().removeIf(id -> id.equals(ship.id()));
    }
}
