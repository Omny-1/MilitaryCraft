package me.bibo.militarycraft.vehicles.tank.tank;

import me.bibo.militarycraft.vehicles.tank.TankRuntime;
import me.bibo.militarycraft.vehicles.tank.combat.Explosions;
import me.bibo.militarycraft.vehicles.tank.combat.ShellManager;
import me.bibo.militarycraft.vehicles.tank.config.TankConfig;
import me.bibo.militarycraft.vehicles.tank.control.DriveController;
import me.bibo.militarycraft.vehicles.tank.model.TankModel;
import me.bibo.militarycraft.vehicles.tank.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registry of all tanks currently in loaded chunks, plus the single per-tick
 * update loop. Tanks live as persistent entities; their wrappers are rebuilt
 * from those entities on entity-load and dropped on entity-unload, so they
 * survive chunk reloads, restarts and crashes without orphaning.
 */
public final class TankManager {

    private final TankRuntime plugin;
    private final ShellManager shells;
    private final Map<UUID, Tank> tanks = new LinkedHashMap<>();
    private final Map<UUID, UUID> driverToTank = new HashMap<>();
    private BukkitTask task;
    private boolean internalExplosion;
    private long tickCounter;
    /** Drivers whose armour is currently hidden from other players. */
    private final java.util.Set<UUID> cloaked = new java.util.HashSet<>();
    private final java.util.Set<UUID> currentDrivers = new java.util.HashSet<>();
    private final Map<UUID, Boolean> previousInvisible = new HashMap<>();
    /** Last melee-hit timestamp per attacker, for the weapon-melee cooldown. */
    private final Map<UUID, Long> meleeCd = new HashMap<>();

    public TankManager(TankRuntime plugin) {
        this.plugin = plugin;
        this.shells = new ShellManager(plugin);
    }

    public ShellManager shells() {
        return shells;
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

    /** On enable: adopt any tank entities already present in loaded chunks. */
    public void adoptExisting() {
        for (World world : Bukkit.getWorlds()) {
            onEntitiesLoad(world.getEntities());
        }
    }

    /** On disable: eject drivers and persist state, but keep the entities. */
    public void shutdown() {
        stop();
        for (Tank tank : new ArrayList<>(tanks.values())) {
            UUID d = tank.driver();
            if (d != null) {
                Player p = Bukkit.getPlayer(d);
                if (p != null) {
                    restoreDriver(p);
                }
            }
            tank.eject();
            tank.persistState();
        }
        tanks.clear();
        driverToTank.clear();
        cloaked.clear();
        currentDrivers.clear();
        previousInvisible.clear();
        meleeCd.clear();
        shells.clear();
    }

    private void tick() {
        tickCounter++;
        TankConfig cfg = plugin.config();
        // Iterate the registry directly (no per-tick copy). Nothing inside the loop
        // mutates the tanks map except the dead-tank cleanup, which uses the
        // iterator's own remove(), so this stays safe.
        java.util.Iterator<Tank> it = tanks.values().iterator();
        while (it.hasNext()) {
            Tank tank = it.next();
            if (!tank.isActive()) {
                // Entities vanished while we were tracking them (e.g. /kill); tidy up.
                UUID d = tank.driver();
                if (d != null) {
                    driverToTank.remove(d);
                    Player p = Bukkit.getPlayer(d);
                    if (p != null) {
                        restoreDriver(p);
                    }
                }
                tank.removeEntities();
                it.remove();
                final UUID tid = tank.id();
                driverToTank.values().removeIf(id -> id.equals(tid));
                continue;
            }
            if (tank.weaponLock() > 0) {
                tank.setWeaponLock(tank.weaponLock() - 1);
            }
            if (tank.reload() > 0) {
                tank.setReload(tank.reload() - 1);
            }
            if (tank.reload() == 0 && tank.overheatSwings() != 0) {
                tank.setOverheatSwings(0); // gun ready again -> spam counter clears
            }
            if (cfg.drownEnabled) {
                tank.refreshSubmerged();
                if (tank.isSubmerged()) {
                    tank.tickWater(cfg);
                    if (!tank.isActive()) {
                        continue; // flooded to death; top-of-loop tidies the rest next tick
                    }
                }
            }
            UUID driverId = tank.driver();
            if (driverId != null) {
                Player driver = Bukkit.getPlayer(driverId);
                if (driver == null || !driver.isOnline()
                        || driver.getVehicle() == null
                        || !driver.getVehicle().equals(tank.seat())) {
                    if (driver != null) {
                        restoreDriver(driver);
                    }
                    tank.clearDriver();
                    driverToTank.remove(driverId);
                } else {
                    try {
                        DriveController.drive(tank, driver, plugin.config());
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Drive tick failed: " + ex);
                    }
                    if (!tank.isActive()) {
                        continue;
                    }
                }
            }
            if (shouldSweepProjectiles(tank, cfg)) {
                sweepProjectiles(tank);
            }
            tank.tickDamageEffects(tickCounter, cfg);
            tank.tickPersist();
        }
        reconcileCloak();
        shells.tick();
    }

    private boolean shouldSweepProjectiles(Tank tank, TankConfig cfg) {
        int interval = cfg.projectileSweepIntervalTicks;
        return interval <= 1 || Math.floorMod(tickCounter + tank.id().hashCode(), interval) == 0;
    }

    /**
     * Keep seated drivers' armour hidden from other players and restore it once
     * they leave. Driven by the live driver set, so it covers every exit path
     * (dismount, disconnect, destruction, unload) without touching each of them,
     * and periodically re-sends so players who come into view also see no armour.
     */
    private void reconcileCloak() {
        currentDrivers.clear();
        for (Tank tank : tanks.values()) {
            UUID d = tank.driver();
            if (d != null) {
                currentDrivers.add(d);
            }
        }
        for (java.util.Iterator<UUID> ci = cloaked.iterator(); ci.hasNext(); ) {
            UUID u = ci.next();
            if (!currentDrivers.contains(u)) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    me.bibo.militarycraft.vehicles.tank.util.DriverCloak.show(p);
                }
                ci.remove();
            }
        }
        boolean periodic = (tickCounter % 100 == 0);
        for (UUID u : currentDrivers) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) {
                continue;
            }
            boolean firstTime = cloaked.add(u);
            if (firstTime || periodic) {
                me.bibo.militarycraft.vehicles.tank.util.DriverCloak.hide(p);
            }
        }
    }

    // --------------------------------------------------------------- chunk (de)hydration

    /** Rebuild wrappers for any untracked tank entities in this set. */
    public void onEntitiesLoad(Collection<Entity> entities) {
        Map<UUID, List<Entity>> groups = new HashMap<>();
        for (Entity e : entities) {
            if (!e.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
                continue;
            }
            String idStr = e.getPersistentDataContainer().get(Keys.TANK_ID, PersistentDataType.STRING);
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
            if (tanks.containsKey(id)) {
                continue; // already tracked
            }
            groups.computeIfAbsent(id, k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<UUID, List<Entity>> entry : groups.entrySet()) {
            Tank tank = Tank.rehydrate(plugin, entry.getKey(), entry.getValue());
            if (tank != null) {
                tanks.put(entry.getKey(), tank);
            } else {
                for (Entity e : entry.getValue()) {
                    e.remove(); // incomplete/garbage group
                }
            }
        }
    }

    /** Drop wrappers for tanks whose entities are unloading (entities persist). */
    public void onEntitiesUnload(Collection<Entity> entities) {
        for (Entity e : entities) {
            String idStr = e.getPersistentDataContainer().get(Keys.TANK_ID, PersistentDataType.STRING);
            if (idStr == null) {
                continue;
            }
            Tank tank;
            try {
                tank = tanks.get(UUID.fromString(idStr));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (tank == null) {
                continue;
            }
            UUID d = tank.driver();
            if (d != null) {
                Player p = Bukkit.getPlayer(d);
                if (p != null) {
                    restoreDriver(p);
                }
                tank.eject();
            }
            tank.persistState();
            forget(tank);
        }
    }

    // --------------------------------------------------------------- registry

    public Tank create(Location at, double yaw) {
        Tank tank = Tank.create(plugin, at, yaw);
        tanks.put(tank.id(), tank);
        return tank;
    }

    public Tank byId(UUID id) {
        return tanks.get(id);
    }

    public Tank byDriver(UUID playerId) {
        UUID tankId = driverToTank.get(playerId);
        return tankId == null ? null : tanks.get(tankId);
    }

    public Tank byEntity(Entity entity) {
        String id = entity.getPersistentDataContainer().get(Keys.TANK_ID, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        try {
            return tanks.get(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public Collection<Tank> all() {
        return tanks.values();
    }

    /** Re-apply config block colours / turret number to every loaded tank. */
    public void repaintAll() {
        TankConfig cfg = plugin.config();
        for (Tank tank : tanks.values()) {
            if (tank.isActive()) {
                tank.reapplyAppearance(cfg);
            }
        }
    }

    public int count() {
        return tanks.size();
    }

    // --------------------------------------------------------------- riding

    public boolean enter(Tank tank, Player player) {
        if (tank.isOccupied() || byDriver(player.getUniqueId()) != null) {
            return false;
        }
        if (!tank.mount(player)) {
            return false;
        }
        driverToTank.put(player.getUniqueId(), tank.id());
        previousInvisible.put(player.getUniqueId(), player.isInvisible());
        player.setInvisible(true);
        cloaked.add(player.getUniqueId());
        me.bibo.militarycraft.vehicles.tank.util.DriverCloak.hide(player); // hide armour immediately
        return true;
    }

    public void handleDismount(Player player) {
        meleeCd.remove(player.getUniqueId());
        UUID tankId = driverToTank.remove(player.getUniqueId());
        if (tankId != null) {
            restoreDriver(player);
            Tank tank = tanks.get(tankId);
            if (tank != null) {
                tank.clearDriver();
                tank.setSpeed(0);
            }
        }
    }

    private void restoreDriver(Player player) {
        UUID id = player.getUniqueId();
        Boolean wasInvisible = previousInvisible.remove(id);
        player.setInvisible(wasInvisible != null && wasInvisible);
        me.bibo.militarycraft.vehicles.tank.util.DriverCloak.show(player);
        cloaked.remove(id);
    }

    // --------------------------------------------------------------- damage

    public void damageTanksFromExplosion(Location loc, double power) {
        TankConfig cfg = plugin.config();
        for (Tank tank : new ArrayList<>(tanks.values())) {
            Explosions.applyBlastTo(tank, loc, power, cfg);
        }
    }

    /**
     * Damage from an AntiAirCraft rocket: a FLAT one-creeper hit with NO
     * distance falloff, to any tank near the blast. This is what makes an AntiAir rocket
     * break a tank identically point-blank or at the edge of the turret's range.
     */
    public void damageTanksFromAntiAir(Location loc) {
        TankConfig cfg = plugin.config();
        for (Tank tank : new ArrayList<>(tanks.values())) {
            if (!tank.isActive() || tank.world() != loc.getWorld()) {
                continue;
            }
            if (tank.anchor().clone().add(0, 1.0, 0).distance(loc) <= 8.0) {
                tank.damage(cfg.creeperDamage); // exactly 1 creeper, distance-independent
            }
        }
    }

    public void setInternalExplosion(boolean value) {
        this.internalExplosion = value;
    }

    public boolean isInternalExplosion() {
        return internalExplosion;
    }

    // --------------------------------------------------------------- weapon damage

    public void meleeFromPlayer(Player attacker) {
        if (byDriver(attacker.getUniqueId()) != null) {
            return; // a driver's swing fires the gun, it doesn't melee
        }
        World w = attacker.getWorld();
        Location eye = attacker.getEyeLocation();
        Vector dir = eye.getDirection();
        double reach = 4.5;
        Tank best = null;
        double bestDist = reach;
        Vector hitAt = null;
        for (Tank tank : tanks.values()) {
            if (!tank.isActive() || tank.world() != w) {
                continue;
            }
            RayHit r = rayTraceTankBody(tank, eye.toVector(), dir, reach, 0.35);
            if (r == null) {
                continue;
            }
            if (r.distance() < bestDist) {
                bestDist = r.distance();
                best = tank;
                hitAt = r.position();
            }
        }
        if (best == null) {
            return;
        }
        if (w.rayTraceBlocks(eye, dir, Math.max(0.1, bestDist - 0.05), FluidCollisionMode.NEVER, true) != null) {
            return;
        }
        meleeDamageFromEntity(attacker, best, new Location(w, hitAt.getX(), hitAt.getY(), hitAt.getZ()));
    }

    public void meleeDamageFromEntity(Entity attacker, Tank target, Location hitAt) {
        if (attacker == null || target == null || !target.isActive()) {
            return;
        }
        if (attacker instanceof Player player && byDriver(player.getUniqueId()) != null) {
            return; // a seated driver's swing is the fire button
        }
        TankConfig cfg = plugin.config();
        long now = System.currentTimeMillis();
        Long last = meleeCd.get(attacker.getUniqueId());
        if (last != null && now - last < cfg.weaponMeleeCooldownMs) {
            return;
        }
        meleeCd.put(attacker.getUniqueId(), now);
        weaponHitFx(hitAt);
        target.damage(target.maxHealth() * cfg.weaponMeleePercent / 100.0);
    }

    private void sweepProjectiles(Tank tank) {
        TankConfig cfg = plugin.config();
        UUID driver = tank.driver();
        for (Entity e : tank.world().getNearbyEntities(tankBodySearchBox(tank, 0.45))) {
            if (!(e instanceof Projectile proj) || !isWeaponProjectile(proj)) {
                continue;
            }
            if (!insideTankBody(tank, proj.getLocation().toVector(), 0.45)) {
                continue;
            }
            if (driver != null && proj.getShooter() instanceof Player sp
                    && sp.getUniqueId().equals(driver)) {
                continue; // the gunner's own projectiles don't count
            }
            double pct = (proj instanceof Fireball) ? cfg.weaponFireballPercent : cfg.weaponArrowPercent;
            Location at = proj.getLocation();
            proj.remove();
            weaponHitFx(at);
            if (tank.damage(tank.maxHealth() * pct / 100.0)) {
                return; // destroyed by this hit
            }
        }
    }

    public Tank rayTraceFrom(Location eye, double reach) {
        World world = eye.getWorld();
        if (world == null) {
            return null;
        }
        Vector dir = eye.getDirection();
        Tank best = null;
        double bestDist = reach;
        for (Tank tank : tanks.values()) {
            if (!tank.isActive() || tank.world() != world) {
                continue;
            }
            RayHit hit = rayTraceTankBody(tank, eye.toVector(), dir, reach, 0.35);
            if (hit == null || hit.distance() >= bestDist) {
                continue;
            }
            if (world.rayTraceBlocks(eye, dir, Math.max(0.1, hit.distance() - 0.05),
                    FluidCollisionMode.NEVER, true) != null) {
                continue;
            }
            bestDist = hit.distance();
            best = tank;
        }
        return best;
    }

    private RayHit rayTraceTankBody(Tank tank, Vector origin, Vector direction, double reach, double pad) {
        BoundingBox localBox = new BoundingBox(
                -TankModel.WIDTH / 2.0 - pad,
                -pad,
                -TankModel.LENGTH / 2.0 - pad,
                TankModel.WIDTH / 2.0 + pad,
                TankModel.HEIGHT + pad,
                TankModel.LENGTH / 2.0 + pad);
        RayTraceResult r = localBox.rayTrace(worldToTankLocal(tank, origin), worldDirToTankLocal(tank, direction), reach);
        if (r == null) {
            return null;
        }
        Vector world = tankLocalToWorld(tank, r.getHitPosition());
        return new RayHit(world, origin.distance(world));
    }

    private boolean insideTankBody(Tank tank, Vector world, double pad) {
        Vector local = worldToTankLocal(tank, world);
        return local.getX() >= -TankModel.WIDTH / 2.0 - pad
                && local.getX() <= TankModel.WIDTH / 2.0 + pad
                && local.getY() >= -pad
                && local.getY() <= TankModel.HEIGHT + pad
                && local.getZ() >= -TankModel.LENGTH / 2.0 - pad
                && local.getZ() <= TankModel.LENGTH / 2.0 + pad;
    }

    private BoundingBox tankBodySearchBox(Tank tank, double pad) {
        Location a = tank.anchor();
        double halfWidth = TankModel.WIDTH / 2.0 + pad;
        double halfLength = TankModel.LENGTH / 2.0 + pad;
        double radius = Math.sqrt(halfWidth * halfWidth + halfLength * halfLength);
        return new BoundingBox(
                a.getX() - radius, a.getY() - pad, a.getZ() - radius,
                a.getX() + radius, a.getY() + TankModel.HEIGHT + pad, a.getZ() + radius);
    }

    private Vector worldToTankLocal(Tank tank, Vector world) {
        Location a = tank.anchor();
        double dx = world.getX() - a.getX();
        double dz = world.getZ() - a.getZ();
        double yaw = Math.toRadians(tank.hullYaw());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vector(dx * cos + dz * sin, world.getY() - a.getY(), -dx * sin + dz * cos);
    }

    private Vector worldDirToTankLocal(Tank tank, Vector dir) {
        double yaw = Math.toRadians(tank.hullYaw());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vector(dir.getX() * cos + dir.getZ() * sin, dir.getY(), -dir.getX() * sin + dir.getZ() * cos);
    }

    private Vector tankLocalToWorld(Tank tank, Vector local) {
        Location a = tank.anchor();
        double yaw = Math.toRadians(tank.hullYaw());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vector(
                a.getX() + local.getX() * cos - local.getZ() * sin,
                a.getY() + local.getY(),
                a.getZ() + local.getX() * sin + local.getZ() * cos);
    }

    private record RayHit(Vector position, double distance) {
    }

    public static boolean isWeaponProjectile(Projectile p) {
        return p instanceof AbstractArrow
                || p instanceof Fireball
                || p instanceof Firework;
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

    public void remove(Tank tank, boolean effects) {
        UUID d = tank.driver();
        if (d != null) {
            Player p = Bukkit.getPlayer(d);
            if (p != null) {
                restoreDriver(p);
            }
        }
        if (effects) {
            tank.destroy(true);
        } else {
            tank.eject();
            tank.removeEntities();
        }
        forget(tank);
    }

    /** Drop the wrapper from the registry without touching its entities. */
    private void forget(Tank tank) {
        tanks.remove(tank.id());
        driverToTank.values().removeIf(id -> id.equals(tank.id()));
    }

    /**
     * Nuke every tank and any leftover cache. Tracked tanks are ejected and their
     * entities deleted, the registries and in-flight shells are cleared, then every
     * loaded world is swept for stray tank-tagged entities (orphans from older
     * builds, incomplete groups or crashes) so nothing is left behind.
     *
     * @return {@code [trackedTanksRemoved, strayEntitiesSwept]}.
     */
    public int[] purgeAll() {
        int tankCount = tanks.size();
        for (Tank tank : new ArrayList<>(tanks.values())) {
            UUID d = tank.driver();
            if (d != null) {
                Player p = Bukkit.getPlayer(d);
                if (p != null) {
                    restoreDriver(p);
                }
            }
            tank.eject();
            tank.removeEntities();
        }
        tanks.clear();
        driverToTank.clear();
        cloaked.clear();
        currentDrivers.clear();
        previousInvisible.clear();
        meleeCd.clear();
        shells.clear();

        int strays = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
                    e.remove();
                    strays++;
                }
            }
        }
        return new int[]{tankCount, strays};
    }
}
