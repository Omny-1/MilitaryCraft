package me.bibo.militarycraft.vehicles.pickup.vehicle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import me.bibo.militarycraft.vehicles.pickup.PickupRuntime;
import me.bibo.militarycraft.vehicles.pickup.combat.Explosions;
import me.bibo.militarycraft.vehicles.pickup.config.PickupConfig;
import me.bibo.militarycraft.vehicles.pickup.control.DriveController;
import me.bibo.militarycraft.vehicles.pickup.control.GunnerController;
import me.bibo.militarycraft.vehicles.pickup.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Every pickup on the server: spawning, the per-tick loop that drives them, crew bookkeeping, and
 * the sweep that removes parts left behind by a crash or a manually deleted entity.
 */
public final class PickupManager {
    private static final double HALF_WIDTH = 1.35f;
    private static final double HALF_LENGTH = 3.2f;
    private final PickupRuntime plugin;
    private final Map<UUID, Pickup> pickups = new LinkedHashMap<>();
    private final Map<UUID, UUID> driverToPickup = new HashMap<>();
    private final Map<UUID, UUID> passengerToPickup = new HashMap<>();
    private final Map<UUID, UUID> gunnerToPickup = new HashMap<>();
    private BukkitTask task;
    private boolean internalExplosion;
    private long tickCounter;
    private final Map<UUID, Long> meleeCd = new HashMap<>();

    public PickupManager(PickupRuntime plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (this.task != null && !this.task.isCancelled()) {
            this.task.cancel();
        }
        this.task = Bukkit.getScheduler().runTaskTimer(this.plugin.bukkitPlugin(), this::tickSafely, 1L, 1L);
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    private void tickSafely() {
        try {
            this.tick();
        }
        catch (Throwable t) {
            this.plugin.getLogger().log(Level.SEVERE, "Unexpected error in PickupCraft tick loop", t);
        }
    }

    public void adoptExisting() {
        for (World world : Bukkit.getWorlds()) {
            this.onEntitiesLoad(world.getEntities());
        }
    }

    public void shutdown() {
        this.stop();
        for (Pickup pickup : new ArrayList<>(this.pickups.values())) {
            pickup.ejectDriver();
            pickup.ejectPassenger();
            pickup.ejectGunner();
            pickup.persistState();
        }
        this.pickups.clear();
        this.driverToPickup.clear();
        this.passengerToPickup.clear();
        this.gunnerToPickup.clear();
        this.meleeCd.clear();
    }

    private void tick() {
        ++this.tickCounter;
        PickupConfig cfg = this.plugin.config();
        Iterator<Pickup> it = this.pickups.values().iterator();
        while (it.hasNext()) {
            UUID gunnerId;
            Player passenger;
            UUID passengerId;
            UUID driverId;
            Pickup pickup = it.next();
            if (!pickup.isActive()) {
                this.forgetInactive(pickup);
                it.remove();
                continue;
            }
            if (!pickup.validateEntities()) {
                this.plugin.getLogger().warning("Pickup " + String.valueOf(pickup.id()) + " has an incomplete entity set; unloading it from the active registry.");
                pickup.persistState();
                this.forgetInactive(pickup);
                it.remove();
                continue;
            }
            if (pickup.gunLock() > 0) {
                pickup.setGunLock(pickup.gunLock() - 1);
            }
            if (pickup.gunCooldown() > 0) {
                pickup.setGunCooldown(pickup.gunCooldown() - 1);
            }
            if (pickup.overheatTicks() > 0) {
                pickup.setOverheatTicks(pickup.overheatTicks() - 1);
            }
            if (cfg.drownEnabled) {
                pickup.refreshSubmerged();
                if (pickup.isSubmerged()) {
                    pickup.tickWater(cfg);
                    if (!pickup.isActive()) continue;
                }
            }
            if ((driverId = pickup.driver()) != null) {
                Player driver = Bukkit.getPlayer(driverId);
                if (driver == null || !driver.isOnline() || driver.getVehicle() == null || !driver.getVehicle().equals(pickup.driverSeat())) {
                    pickup.clearDriver();
                    this.driverToPickup.remove(driverId);
                } else {
                    try {
                        DriveController.drive(pickup, driver, cfg);
                    }
                    catch (Exception ex) {
                        this.plugin.getLogger().warning("Drive tick failed: " + String.valueOf(ex));
                    }
                    if (!pickup.isActive()) {
                        this.forgetInactive(pickup);
                        it.remove();
                        continue;
                    }
                }
            }
            if (!((passengerId = pickup.passenger()) == null || (passenger = Bukkit.getPlayer(passengerId)) != null && passenger.isOnline() && passenger.getVehicle() != null && passenger.getVehicle().equals(pickup.passengerSeat()))) {
                pickup.clearPassenger();
                this.passengerToPickup.remove(passengerId);
            }
            if ((gunnerId = pickup.gunner()) != null) {
                Player gunner = Bukkit.getPlayer(gunnerId);
                if (gunner == null || !gunner.isOnline() || gunner.getVehicle() == null || !gunner.getVehicle().equals(pickup.gunnerSeat())) {
                    pickup.clearGunner();
                    this.gunnerToPickup.remove(gunnerId);
                } else {
                    try {
                        GunnerController.aim(this.plugin, pickup, gunner, cfg);
                    }
                    catch (Exception ex) {
                        this.plugin.getLogger().warning("Gunner tick failed: " + String.valueOf(ex));
                    }
                    if (!pickup.isActive()) {
                        this.forgetInactive(pickup);
                        it.remove();
                        continue;
                    }
                }
            }
            pickup.refreshModel();
            if (this.shouldSweepProjectiles(pickup, cfg)) {
                this.sweepProjectiles(pickup);
            }
            pickup.tickDamageEffects(this.tickCounter, cfg);
            pickup.tickPersist();
        }
        if (this.tickCounter % 100L == 0L) {
            this.pruneMeleeCooldowns(System.currentTimeMillis());
        }
    }

    private void forgetInactive(Pickup pickup) {
        UUID d = pickup.driver();
        UUID pa = pickup.passenger();
        UUID g = pickup.gunner();
        if (d != null) {
            this.driverToPickup.remove(d);
        }
        if (pa != null) {
            this.passengerToPickup.remove(pa);
        }
        if (g != null) {
            this.gunnerToPickup.remove(g);
        }
        UUID jid = pickup.id();
        this.driverToPickup.values().removeIf(id -> id.equals(jid));
        this.passengerToPickup.values().removeIf(id -> id.equals(jid));
        this.gunnerToPickup.values().removeIf(id -> id.equals(jid));
    }

    private void pruneMeleeCooldowns(long now) {
        long ttl = Math.max(250L, (long)this.plugin.config().weaponMeleeCooldownMs * 8L);
        this.meleeCd.entrySet().removeIf(entry -> now - (Long)entry.getValue() > ttl);
    }

    private boolean shouldSweepProjectiles(Pickup pickup, PickupConfig cfg) {
        int interval = cfg.projectileSweepIntervalTicks;
        return interval <= 1 || Math.floorMod(this.tickCounter + (long)pickup.id().hashCode(), interval) == 0;
    }

    public void onEntitiesLoad(Collection<Entity> entities) {
        HashMap<UUID, List<Entity>> groups = new HashMap<>();
        for (Entity entity : entities) {
            UUID id;
            String idStr;
            if (!entity.getScoreboardTags().contains("pickupcraft_entity") || (idStr = entity.getPersistentDataContainer().get(Keys.PICKUP_ID, PersistentDataType.STRING)) == null) continue;
            try {
                id = UUID.fromString(idStr);
            }
            catch (IllegalArgumentException ex) {
                entity.remove();
                continue;
            }
            if (this.pickups.containsKey(id)) continue;
            groups.computeIfAbsent(id, k -> new ArrayList<>()).add(entity);
        }
        for (Map.Entry<UUID, List<Entity>> entry : groups.entrySet()) {
            List<Entity> group = this.collectLoadedGroup(entry.getKey(), entry.getValue());
            Pickup pickup = Pickup.rehydrate(this.plugin, entry.getKey(), group);
            if (pickup != null) {
                this.pickups.put(entry.getKey(), pickup);
                continue;
            }
            this.plugin.getLogger().fine("Skipping partial PickupCraft entity group " + String.valueOf(entry.getKey()) + " (" + group.size() + " loaded entities).");
        }
    }

    private List<Entity> collectLoadedGroup(UUID id, List<Entity> seed) {
        if (seed.isEmpty()) {
            return seed;
        }
        String wanted = id.toString();
        World world = seed.get(0).getWorld();
        LinkedHashMap<UUID, Entity> found = new LinkedHashMap<>();
        for (Entity e : seed) {
            found.put(e.getUniqueId(), e);
        }
        for (Entity e : world.getEntities()) {
            String idStr;
            if (found.containsKey(e.getUniqueId()) || !e.getScoreboardTags().contains("pickupcraft_entity") || !wanted.equals(idStr = e.getPersistentDataContainer().get(Keys.PICKUP_ID, PersistentDataType.STRING))) continue;
            found.put(e.getUniqueId(), e);
        }
        return new ArrayList<>(found.values());
    }

    public void onEntitiesUnload(Collection<Entity> entities) {
        for (Entity e : entities) {
            Pickup pickup;
            String idStr;
            String role = e.getPersistentDataContainer().get(Keys.PICKUP_PART, PersistentDataType.STRING);
            if (!"driver_seat".equals(role) || (idStr = e.getPersistentDataContainer().get(Keys.PICKUP_ID, PersistentDataType.STRING)) == null) continue;
            try {
                pickup = this.pickups.get(UUID.fromString(idStr));
            }
            catch (IllegalArgumentException ex) {
                continue;
            }
            if (pickup == null) continue;
            pickup.persistState();
            this.forget(pickup);
        }
    }

    public Pickup create(Location at, double yaw) {
        Pickup pickup = Pickup.create(this.plugin, at, yaw);
        this.pickups.put(pickup.id(), pickup);
        return pickup;
    }

    public Pickup byId(UUID id) {
        return this.pickups.get(id);
    }

    public Pickup byDriver(UUID playerId) {
        UUID id = this.driverToPickup.get(playerId);
        return id == null ? null : this.pickups.get(id);
    }

    public Pickup byPassenger(UUID playerId) {
        UUID id = this.passengerToPickup.get(playerId);
        return id == null ? null : this.pickups.get(id);
    }

    public Pickup byGunner(UUID playerId) {
        UUID id = this.gunnerToPickup.get(playerId);
        return id == null ? null : this.pickups.get(id);
    }

    public boolean isCrew(UUID playerId) {
        return this.driverToPickup.containsKey(playerId) || this.passengerToPickup.containsKey(playerId) || this.gunnerToPickup.containsKey(playerId);
    }

    public Pickup byEntity(Entity entity) {
        String id = entity.getPersistentDataContainer().get(Keys.PICKUP_ID, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        try {
            return this.pickups.get(UUID.fromString(id));
        }
        catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public Collection<Pickup> all() {
        return this.pickups.values();
    }

    public void repaintAll() {
        PickupConfig cfg = this.plugin.config();
        for (Pickup pickup : this.pickups.values()) {
            if (!pickup.isActive()) continue;
            pickup.reapplyAppearance(cfg);
            pickup.forceRefresh();
            pickup.persistState();
        }
    }

    public int count() {
        return this.pickups.size();
    }

    public boolean enterDriver(Pickup pickup, Player player) {
        if (pickup.isDriverSeatOccupied() || this.isCrew(player.getUniqueId())) {
            return false;
        }
        if (!pickup.mountDriver(player)) {
            return false;
        }
        this.driverToPickup.put(player.getUniqueId(), pickup.id());
        return true;
    }

    public boolean enterPassenger(Pickup pickup, Player player) {
        if (pickup.isPassengerSeatOccupied() || this.isCrew(player.getUniqueId())) {
            return false;
        }
        if (!pickup.mountPassenger(player)) {
            return false;
        }
        this.passengerToPickup.put(player.getUniqueId(), pickup.id());
        return true;
    }

    public boolean enterGunner(Pickup pickup, Player player) {
        if (pickup.isGunnerSeatOccupied() || this.isCrew(player.getUniqueId())) {
            return false;
        }
        if (!pickup.mountGunner(player)) {
            return false;
        }
        this.gunnerToPickup.put(player.getUniqueId(), pickup.id());
        return true;
    }

    public void handleDismount(Player player) {
        Pickup pickup;
        UUID gunnerPickupId;
        Pickup pickup2;
        UUID passengerPickupId;
        Pickup pickup3;
        this.meleeCd.remove(player.getUniqueId());
        UUID driverPickupId = this.driverToPickup.remove(player.getUniqueId());
        if (driverPickupId != null && (pickup3 = this.pickups.get(driverPickupId)) != null) {
            pickup3.clearDriver();
        }
        if ((passengerPickupId = this.passengerToPickup.remove(player.getUniqueId())) != null && (pickup2 = this.pickups.get(passengerPickupId)) != null) {
            pickup2.clearPassenger();
        }
        if ((gunnerPickupId = this.gunnerToPickup.remove(player.getUniqueId())) != null && (pickup = this.pickups.get(gunnerPickupId)) != null) {
            pickup.clearGunner();
        }
    }

    public void damagePickupsFromExplosion(Location loc, double power) {
        this.damagePickupsFromExplosion(loc, power, null);
    }

    public void damagePickupsFromExplosion(Location loc, double power, UUID ignoredPickupId) {
        PickupConfig cfg = this.plugin.config();
        for (Pickup pickup : new ArrayList<>(this.pickups.values())) {
            if (ignoredPickupId != null && ignoredPickupId.equals(pickup.id())) continue;
            Explosions.applyBlastTo(pickup, loc, power, cfg);
        }
    }

    public void damagePickupsFromAntiAir(Location loc) {
        PickupConfig cfg = this.plugin.config();
        for (Pickup pickup : new ArrayList<>(this.pickups.values())) {
            if (!pickup.isActive() || pickup.world() != loc.getWorld() || !(pickup.anchor().clone().add(0.0, 1.0, 0.0).distance(loc) <= 8.0)) continue;
            pickup.damage(cfg.creeperDamage);
        }
    }

    public void setInternalExplosion(boolean value) {
        this.internalExplosion = value;
    }

    public boolean isInternalExplosion() {
        return this.internalExplosion;
    }

    public void meleeFromPlayer(Player attacker) {
        if (this.isCrew(attacker.getUniqueId())) {
            return;
        }
        World w = attacker.getWorld();
        Location eye = attacker.getEyeLocation();
        Vector dir = eye.getDirection();
        double reach = 4.5;
        Pickup best = null;
        double bestDist = reach;
        Vector hitAt = null;
        for (Pickup pickup : this.pickups.values()) {
            RayHit r;
            if (!pickup.isActive() || pickup.world() != w || (r = this.rayTracePickupBody(pickup, eye.toVector(), dir, reach, 0.3)) == null || !(r.distance() < bestDist)) continue;
            bestDist = r.distance();
            best = pickup;
            hitAt = r.position();
        }
        if (best == null) {
            return;
        }
        if (w.rayTraceBlocks(eye, dir, Math.max(0.1, bestDist - 0.05), FluidCollisionMode.NEVER, true) != null) {
            return;
        }
        this.meleeDamageFromEntity(attacker, best, new Location(w, hitAt.getX(), hitAt.getY(), hitAt.getZ()));
    }

    public void meleeDamageFromEntity(Entity attacker, Pickup target, Location hitAt) {
        Player player;
        if (attacker == null || target == null || !target.isActive()) {
            return;
        }
        if (attacker instanceof Player && this.isCrew((player = (Player)attacker).getUniqueId())) {
            return;
        }
        PickupConfig cfg = this.plugin.config();
        long now = System.currentTimeMillis();
        Long last = this.meleeCd.get(attacker.getUniqueId());
        if (last != null && now - last < (long)cfg.weaponMeleeCooldownMs) {
            return;
        }
        this.meleeCd.put(attacker.getUniqueId(), now);
        this.weaponHitFx(hitAt);
        target.damage(target.maxHealth() * cfg.weaponMeleePercent / 100.0);
    }

    private void sweepProjectiles(Pickup pickup) {
        PickupConfig cfg = this.plugin.config();
        for (Entity e : pickup.world().getNearbyEntities(this.pickupBodySearchBox(pickup, 0.4))) {
            Projectile proj;
            if (!(e instanceof Projectile) || !isWeaponProjectile(proj = (Projectile)e) || !this.insidePickupBody(pickup, proj.getLocation().toVector(), 0.4) || this.isOwnCrewShot(pickup, proj)) continue;
            double pct = proj instanceof Fireball ? cfg.weaponFireballPercent : cfg.weaponArrowPercent;
            Location at = proj.getLocation();
            proj.remove();
            this.weaponHitFx(at);
            if (!pickup.damage(pickup.maxHealth() * pct / 100.0)) continue;
            return;
        }
    }

    private boolean isOwnCrewShot(Pickup pickup, Projectile proj) {
        ProjectileSource src = proj.getShooter();
        if (!(src instanceof Player)) {
            return false;
        }
        Player p = (Player)src;
        UUID id = p.getUniqueId();
        return id.equals(pickup.driver()) || id.equals(pickup.passenger()) || id.equals(pickup.gunner());
    }

    public Pickup rayTraceFrom(Location eye, double reach) {
        World world = eye.getWorld();
        if (world == null) {
            return null;
        }
        Vector dir = eye.getDirection();
        Pickup best = null;
        double bestDist = reach;
        for (Pickup pickup : this.pickups.values()) {
            RayHit hit;
            if (!pickup.isActive() || pickup.world() != world || (hit = this.rayTracePickupBody(pickup, eye.toVector(), dir, reach, 0.3)) == null || hit.distance() >= bestDist || world.rayTraceBlocks(eye, dir, Math.max(0.1, hit.distance() - 0.05), FluidCollisionMode.NEVER, true) != null) continue;
            bestDist = hit.distance();
            best = pickup;
        }
        return best;
    }

    private RayHit rayTracePickupBody(Pickup pickup, Vector origin, Vector direction, double reach, double pad) {
        BoundingBox localBox = new BoundingBox((double)-1.35f - pad, -pad, (double)-3.2f - pad, 1.35f + pad, 3.3f + pad, 3.2f + pad);
        RayTraceResult r = localBox.rayTrace(this.worldToPickupLocal(pickup, origin), this.worldDirToPickupLocal(pickup, direction), reach);
        if (r == null) {
            return null;
        }
        Vector world = this.pickupLocalToWorld(pickup, r.getHitPosition());
        return new RayHit(world, origin.distance(world));
    }

    private boolean insidePickupBody(Pickup pickup, Vector world, double pad) {
        Vector local = this.worldToPickupLocal(pickup, world);
        return local.getX() >= (double)-1.35f - pad && local.getX() <= 1.35f + pad && local.getY() >= -pad && local.getY() <= 3.3f + pad && local.getZ() >= (double)-3.2f - pad && local.getZ() <= 3.2f + pad;
    }

    private BoundingBox pickupBodySearchBox(Pickup pickup, double pad) {
        Location a = pickup.anchor();
        double halfWidth = 1.35f + pad;
        double halfLength = 3.2f + pad;
        double radius = Math.sqrt(halfWidth * halfWidth + halfLength * halfLength);
        return new BoundingBox(a.getX() - radius, a.getY() - pad, a.getZ() - radius, a.getX() + radius, a.getY() + 3.3f + pad, a.getZ() + radius);
    }

    private Vector worldToPickupLocal(Pickup pickup, Vector world) {
        Location a = pickup.anchor();
        double dx = world.getX() - a.getX();
        double dz = world.getZ() - a.getZ();
        double yaw = Math.toRadians(pickup.hullYaw());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vector(dx * cos + dz * sin, world.getY() - a.getY(), -dx * sin + dz * cos);
    }

    private Vector worldDirToPickupLocal(Pickup pickup, Vector dir) {
        double yaw = Math.toRadians(pickup.hullYaw());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vector(dir.getX() * cos + dir.getZ() * sin, dir.getY(), -dir.getX() * sin + dir.getZ() * cos);
    }

    private Vector pickupLocalToWorld(Pickup pickup, Vector local) {
        Location a = pickup.anchor();
        double yaw = Math.toRadians(pickup.hullYaw());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vector(a.getX() + local.getX() * cos - local.getZ() * sin, a.getY() + local.getY(), a.getZ() + local.getX() * sin + local.getZ() * cos);
    }

    public static boolean isWeaponProjectile(Projectile p) {
        return p instanceof AbstractArrow || p instanceof Fireball || p instanceof Firework;
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

    public void remove(Pickup pickup, boolean effects) {
        if (effects) {
            pickup.destroy(true);
        } else {
            pickup.ejectDriver();
            pickup.ejectPassenger();
            pickup.ejectGunner();
            pickup.removeEntities();
        }
        this.forget(pickup);
    }

    private void forget(Pickup pickup) {
        this.pickups.remove(pickup.id());
        this.driverToPickup.values().removeIf(id -> id.equals(pickup.id()));
        this.passengerToPickup.values().removeIf(id -> id.equals(pickup.id()));
        this.gunnerToPickup.values().removeIf(id -> id.equals(pickup.id()));
    }

    public int[] migrateStale() {
        HashMap<UUID, List<Entity>> legacyGroups = new HashMap<>();
        HashMap<UUID, List<Entity>> staleGroups = new HashMap<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                UUID id;
                if (e.getScoreboardTags().contains("jeepcraft_entity")) {
                    id = safeUuid(e.getPersistentDataContainer().get(Keys.LEGACY_PICKUP_ID, PersistentDataType.STRING));
                    if (id == null) continue;
                    legacyGroups.computeIfAbsent(id, k -> new ArrayList<>()).add(e);
                    continue;
                }
                if (!e.getScoreboardTags().contains("pickupcraft_entity") || (id = safeUuid(e.getPersistentDataContainer().get(Keys.PICKUP_ID, PersistentDataType.STRING))) == null || this.pickups.containsKey(id)) continue;
                staleGroups.computeIfAbsent(id, k -> new ArrayList<>()).add(e);
            }
        }
        int rebuilt = 0;
        int removedEntities = 0;
        for (List<Entity> group : legacyGroups.values()) {
            removedEntities += this.rebuildFromStaleGroup(group, true);
            ++rebuilt;
        }
        for (List<Entity> group : staleGroups.values()) {
            removedEntities += this.rebuildFromStaleGroup(group, false);
            ++rebuilt;
        }
        return new int[]{rebuilt, removedEntities};
    }

    private int rebuildFromStaleGroup(List<Entity> group, boolean legacy) {
        NamespacedKey partKey = legacy ? Keys.LEGACY_PICKUP_PART : Keys.PICKUP_PART;
        NamespacedKey hullKey = legacy ? Keys.LEGACY_STATE_HULL_YAW : Keys.STATE_HULL_YAW;
        NamespacedKey ax = legacy ? Keys.LEGACY_STATE_ANCHOR_X : Keys.STATE_ANCHOR_X;
        NamespacedKey ay = legacy ? Keys.LEGACY_STATE_ANCHOR_Y : Keys.STATE_ANCHOR_Y;
        NamespacedKey az = legacy ? Keys.LEGACY_STATE_ANCHOR_Z : Keys.STATE_ANCHOR_Z;
        Entity stateHolder = group.get(0);
        for (Entity e : group) {
            if (!"driver_seat".equals(e.getPersistentDataContainer().get(partKey, PersistentDataType.STRING))) continue;
            stateHolder = e;
            break;
        }
        PersistentDataContainer pdc = stateHolder.getPersistentDataContainer();
        double hullYaw = pdc.getOrDefault(hullKey, PersistentDataType.DOUBLE, (double)stateHolder.getLocation().getYaw());
        Double x = (Double)pdc.get(ax, PersistentDataType.DOUBLE);
        Double y = (Double)pdc.get(ay, PersistentDataType.DOUBLE);
        Double z = (Double)pdc.get(az, PersistentDataType.DOUBLE);
        Location loc = x != null && y != null && z != null ? new Location(stateHolder.getWorld(), x, y, z) : stateHolder.getLocation();
        int count = group.size();
        for (Entity e : group) {
            e.remove();
        }
        this.create(loc, hullYaw);
        return count;
    }

    private static UUID safeUuid(String s) {
        if (s == null) {
            return null;
        }
        try {
            return UUID.fromString(s);
        }
        catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public int[] purgeAll() {
        int pickupCount = this.pickups.size();
        for (Pickup pickup : new ArrayList<>(this.pickups.values())) {
            pickup.ejectDriver();
            pickup.ejectPassenger();
            pickup.ejectGunner();
            pickup.removeEntities();
        }
        this.pickups.clear();
        this.driverToPickup.clear();
        this.passengerToPickup.clear();
        this.gunnerToPickup.clear();
        this.meleeCd.clear();
        int strays = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (!e.getScoreboardTags().contains("pickupcraft_entity")) continue;
                e.remove();
                ++strays;
            }
        }
        return new int[]{pickupCount, strays};
    }

    private record RayHit(Vector position, double distance) {
    }
}
