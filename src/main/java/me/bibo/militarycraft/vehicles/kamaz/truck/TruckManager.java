package me.bibo.militarycraft.vehicles.kamaz.truck;

import me.bibo.militarycraft.vehicles.kamaz.KamazRuntime;
import me.bibo.militarycraft.vehicles.kamaz.config.KamazConfig;
import me.bibo.militarycraft.vehicles.kamaz.control.DriveController;
import me.bibo.militarycraft.vehicles.kamaz.model.TruckModel;
import me.bibo.militarycraft.vehicles.kamaz.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
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
 * Registry of all Kamaz trucks currently in loaded chunks, plus the single per-tick
 * update loop. Trucks live as persistent entities; their wrappers are rebuilt from
 * those entities on entity-load and dropped on entity-unload, so they survive
 * chunk reloads, restarts and crashes without orphaning.
 */
public final class TruckManager {

    private final KamazRuntime plugin;
    private final Map<UUID, Truck> trucks = new LinkedHashMap<>();
    private final Map<UUID, UUID> driverToTruck = new HashMap<>();
    private final Map<UUID, UUID> passengerToTruck = new HashMap<>();
    private BukkitTask task;
    private boolean internalExplosion;
    private long tickCounter;
    /** Drivers whose armour is currently hidden from other players. */
    private final java.util.Set<UUID> cloaked = new java.util.HashSet<>();
    /** Last melee-hit timestamp per attacker, for the hit-the-truck cooldown. */
    private final Map<UUID, Long> meleeCd = new HashMap<>();
    /** Player spawn cooldowns for placer/command abuse control. */
    private final Map<UUID, Long> spawnCd = new HashMap<>();
    /** Previous invisibility state for riders, so we don't break other plugins/effects. */
    private final Map<UUID, Boolean> invisibleBeforeTruck = new HashMap<>();

    public TruckManager(KamazRuntime plugin) {
        this.plugin = plugin;
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

    /** On enable: adopt any truck entities already present in loaded chunks. */
    public void adoptExisting() {
        for (World world : Bukkit.getWorlds()) {
            onEntitiesLoad(world.getEntities());
        }
    }

    /** On disable: eject drivers and persist state, but keep the entities. */
    public void shutdown() {
        stop();
        for (Truck truck : new ArrayList<>(trucks.values())) {
            UUID d = truck.driver();
            if (d != null) {
                restoreRider(d);
            }
            releasePassengersOf(truck.id());
            truck.removeAllPassengerSeats();
            truck.eject();
            truck.persistState();
        }
        trucks.clear();
        driverToTruck.clear();
        passengerToTruck.clear();
        cloaked.clear();
        invisibleBeforeTruck.clear();
    }

    private void tick() {
        tickCounter++;
        KamazConfig cfg = plugin.config();
        java.util.Iterator<Truck> it = trucks.values().iterator();
        while (it.hasNext()) {
            Truck truck = it.next();
            if (!truck.isActive()) {
                discardInactiveTruck(it, truck);
                continue;
            }
            if (cfg.drownEnabled) {
                if (truck.isSubmerged() || tickCounter % cfg.waterCheckInterval == 0) {
                    truck.refreshSubmerged();
                }
                if (truck.isSubmerged()) {
                    truck.tickWater(cfg);
                    if (!truck.isActive()) {
                        discardInactiveTruck(it, truck);
                        continue;
                    }
                }
            }
            UUID driverId = truck.driver();
            if (driverId != null) {
                Player driver = Bukkit.getPlayer(driverId);
                if (driver == null || !driver.isOnline()
                        || driver.getVehicle() == null
                        || !driver.getVehicle().equals(truck.seat())) {
                    restoreRider(driverId);
                    truck.clearDriver();
                    driverToTruck.remove(driverId);
                } else {
                    try {
                        DriveController.drive(truck, driver, cfg);
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Drive tick failed: " + ex);
                    }
                }
            }
            if (tickCounter % 20 == 0) {
                truck.pruneEmptyPassengerSeats();
            }
            if (tickCounter % cfg.projectileSweepInterval == 0) {
                sweepProjectiles(truck);
            }
        }
        if (tickCounter % 10 == 0) {
            reconcilePassengers();
        }
        reconcileCloak();
    }

    private void discardInactiveTruck(java.util.Iterator<Truck> it, Truck truck) {
        UUID d = truck.driver();
        if (d != null) {
            driverToTruck.remove(d);
            restoreRider(d);
        }
        releasePassengersOf(truck.id());
        truck.removeEntities();
        it.remove();
        final UUID tid = truck.id();
        driverToTruck.values().removeIf(idv -> idv.equals(tid));
    }

    /**
     * Keep seated drivers' armour hidden from other players and restore it once
     * they leave. Driven by the live driver set, so it covers every exit path
     * (dismount, disconnect, destruction, unload) without touching each of them,
     * and periodically re-sends so players who come into view also see no armour.
     */
    private void reconcileCloak() {
        java.util.Set<UUID> current = new java.util.HashSet<>();
        for (Truck truck : trucks.values()) {
            UUID d = truck.driver();
            if (d != null) {
                current.add(d);
            }
        }
        current.addAll(passengerToTruck.keySet());
        for (java.util.Iterator<UUID> ci = cloaked.iterator(); ci.hasNext(); ) {
            UUID u = ci.next();
            if (!current.contains(u)) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    me.bibo.militarycraft.vehicles.kamaz.util.DriverCloak.show(p);
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
                me.bibo.militarycraft.vehicles.kamaz.util.DriverCloak.hide(p);
            }
        }
    }

    // --------------------------------------------------------------- chunk (de)hydration

    /** Rebuild wrappers for any untracked truck entities in this set. */
    public void onEntitiesLoad(Collection<Entity> entities) {
        Map<UUID, List<Entity>> groups = new HashMap<>();
        for (Entity e : entities) {
            if (!e.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
                continue;
            }
            String idStr = e.getPersistentDataContainer().get(Keys.TRUCK_ID, PersistentDataType.STRING);
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
            if (trucks.containsKey(id)) {
                continue; // already tracked
            }
            groups.computeIfAbsent(id, k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<UUID, List<Entity>> entry : groups.entrySet()) {
            Truck truck = Truck.rehydrate(plugin, entry.getKey(), entry.getValue());
            if (truck != null) {
                trucks.put(entry.getKey(), truck);
                if (truck.ownerId() != null) {
                    spawnCd.putIfAbsent(truck.ownerId(), 0L);
                }
            } else {
                for (Entity e : entry.getValue()) {
                    e.remove(); // incomplete/garbage group
                }
            }
        }
    }

    /** Drop wrappers for trucks whose entities are unloading (entities persist). */
    public void onEntitiesUnload(Collection<Entity> entities) {
        for (Entity e : entities) {
            String idStr = e.getPersistentDataContainer().get(Keys.TRUCK_ID, PersistentDataType.STRING);
            if (idStr == null) {
                continue;
            }
            Truck truck;
            try {
                truck = trucks.get(UUID.fromString(idStr));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (truck == null) {
                continue;
            }
            UUID d = truck.driver();
            if (d != null) {
                restoreRider(d);
                truck.eject();
            }
            releasePassengersOf(truck.id());
            truck.removeAllPassengerSeats();
            truck.persistState();
            forget(truck);
        }
    }

    // --------------------------------------------------------------- registry

    public Truck create(Location at, double yaw) {
        return create(at, yaw, null);
    }

    public Truck create(Location at, double yaw, UUID ownerId) {
        Truck truck = Truck.create(plugin, at, yaw, ownerId);
        trucks.put(truck.id(), truck);
        return truck;
    }

    public String validateCreate(Player player, Location at, double yaw) {
        KamazConfig cfg = plugin.config();
        if (player != null && !player.hasPermission("kamazcraft.admin")) {
            if (cfg.maxTrucksTotal > 0 && trucks.size() >= cfg.maxTrucksTotal) {
                return "Server Kamaz limit reached: " + cfg.maxTrucksTotal;
            }
            if (cfg.maxTrucksPerPlayer > 0
                    && countOwnedBy(player.getUniqueId()) >= cfg.maxTrucksPerPlayer) {
                return "You already have a Kamaz. Limit: " + cfg.maxTrucksPerPlayer;
            }
            long cooldownMs = cfg.spawnCooldownSeconds * 1000L;
            if (cooldownMs > 0) {
                Long last = spawnCd.get(player.getUniqueId());
                long now = System.currentTimeMillis();
                if (last != null && now - last < cooldownMs) {
                    long left = (cooldownMs - (now - last) + 999L) / 1000L;
                    return "Wait " + left + " more seconds before spawning again.";
                }
            }
        }
        return validateSpawnSpace(at, yaw, cfg);
    }

    public void recordCreate(Player player) {
        if (player != null && !player.hasPermission("kamazcraft.admin")) {
            spawnCd.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    private int countOwnedBy(UUID ownerId) {
        int count = 0;
        for (Truck truck : trucks.values()) {
            if (ownerId.equals(truck.ownerId())) {
                count++;
            }
        }
        return count;
    }

    private String validateSpawnSpace(Location at, double yaw, KamazConfig cfg) {
        if (at.getWorld() == null) {
            return "Spawn world not found.";
        }
        if (!worldBorderContainsTruck(at, yaw)) {
            return "Kamaz does not fit inside the world border.";
        }
        if (cfg.minTruckSpacing > 0.0) {
            double minSq = cfg.minTruckSpacing * cfg.minTruckSpacing;
            for (Truck truck : trucks.values()) {
                if (truck.world() == at.getWorld() && truck.anchor().distanceSquared(at) < minSq) {
                    return "Too close to another Kamaz.";
                }
            }
        }
        String blockedBy = firstBlockingSpawnBlock(at, yaw);
        if (blockedBy != null) {
            return "Not enough space for the Kamaz: " + blockedBy;
        }
        return null;
    }

    private boolean worldBorderContainsTruck(Location at, double yaw) {
        double hw = TruckModel.WIDTH / 2.0;
        double hl = TruckModel.LENGTH / 2.0;
        for (double x : new double[]{-hw, hw}) {
            for (double z : new double[]{-hl, hl}) {
                Vector v = truckLocalToWorld(at, yaw, new Vector(x, 0.0, z));
                if (!at.getWorld().getWorldBorder().isInside(new Location(at.getWorld(), v.getX(), at.getY(), v.getZ()))) {
                    return false;
                }
            }
        }
        return true;
    }

    private String firstBlockingSpawnBlock(Location at, double yaw) {
        double pad = 0.15;
        double halfWidth = TruckModel.WIDTH / 2.0 + pad;
        double halfLength = TruckModel.LENGTH / 2.0 + pad;
        double radius = Math.sqrt(halfWidth * halfWidth + halfLength * halfLength);
        int minX = (int) Math.floor(at.getX() - radius);
        int maxX = (int) Math.floor(at.getX() + radius);
        int minY = (int) Math.floor(at.getY());
        int maxY = (int) Math.floor(at.getY() + TruckModel.HEIGHT + pad);
        int minZ = (int) Math.floor(at.getZ() - radius);
        int maxZ = (int) Math.floor(at.getZ() + radius);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Vector local = worldToTruckLocal(at, yaw, new Vector(x + 0.5, y + 0.5, z + 0.5));
                    if (Math.abs(local.getX()) > halfWidth
                            || Math.abs(local.getZ()) > halfLength
                            || local.getY() < 0.0
                            || local.getY() > TruckModel.HEIGHT + pad) {
                        continue;
                    }
                    Block block = at.getWorld().getBlockAt(x, y, z);
                    if (blocksSpawn(block)) {
                        return block.getType().name() + " at " + x + "," + y + "," + z;
                    }
                }
            }
        }
        return null;
    }

    private static boolean blocksSpawn(Block block) {
        return !block.isPassable() || isLiquidLike(block);
    }

    private static boolean isLiquidLike(Block block) {
        return block.isLiquid()
                || (block.getBlockData() instanceof Waterlogged w && w.isWaterlogged());
    }

    public Truck byId(UUID id) {
        return trucks.get(id);
    }

    public Truck byDriver(UUID playerId) {
        UUID truckId = driverToTruck.get(playerId);
        return truckId == null ? null : trucks.get(truckId);
    }

    public Truck byEntity(Entity entity) {
        String id = entity.getPersistentDataContainer().get(Keys.TRUCK_ID, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        try {
            return trucks.get(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public Collection<Truck> all() {
        return trucks.values();
    }

    /** Re-apply config block colours / side number / name to every loaded truck. */
    public void repaintAll() {
        KamazConfig cfg = plugin.config();
        for (Truck truck : trucks.values()) {
            if (truck.isActive()) {
                truck.reapplyAppearance(cfg);
            }
        }
    }

    public int count() {
        return trucks.size();
    }

    // --------------------------------------------------------------- riding

    public Truck byPassenger(UUID playerId) {
        UUID truckId = passengerToTruck.get(playerId);
        return truckId == null ? null : trucks.get(truckId);
    }

    /**
     * Board a player: the first to enter an empty truck becomes the driver, anyone
     * boarding an already-occupied truck rides as a passenger (until the seats run
     * out).
     *
     * @return true if the player is now aboard (driving, riding, or already was),
     * false only if the truck is full.
     */
    public boolean enter(Truck truck, Player player) {
        UUID pid = player.getUniqueId();
        if (byDriver(pid) != null || passengerToTruck.containsKey(pid)) {
            return true; // already aboard a truck; no-op (no misleading message)
        }
        if (!truck.isOccupied()) {
            truck.mount(player);
            driverToTruck.put(pid, truck.id());
        } else {
            int slot = truck.boardPassenger(player, plugin.config().passengerSeatHeight);
            if (slot < 0) {
                return false; // full
            }
            passengerToTruck.put(pid, truck.id());
        }
        invisibleBeforeTruck.putIfAbsent(pid, player.isInvisible());
        player.setInvisible(true);
        cloaked.add(pid);
        me.bibo.militarycraft.vehicles.kamaz.util.DriverCloak.hide(player); // hide armour immediately
        return true;
    }

    public void handleDismount(Player player) {
        UUID pid = player.getUniqueId();
        UUID driverTruck = driverToTruck.remove(pid);
        if (driverTruck != null) {
            restoreRider(player);
            Truck truck = trucks.get(driverTruck);
            if (truck != null) {
                truck.clearDriver();
                truck.setSpeed(0);
            }
            return;
        }
        UUID passTruck = passengerToTruck.remove(pid);
        if (passTruck != null) {
            restoreRider(player);
            Truck truck = trucks.get(passTruck);
            if (truck != null) {
                truck.removePassenger(player);
            }
        }
    }

    /** Make a player visible again and restore their armour view for everyone. */
    private void restoreRider(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            restoreRider(player);
        } else {
            invisibleBeforeTruck.remove(playerId);
            cloaked.remove(playerId);
        }
    }

    private void restoreRider(Player player) {
        boolean wasInvisible = invisibleBeforeTruck.getOrDefault(player.getUniqueId(), false);
        invisibleBeforeTruck.remove(player.getUniqueId());
        player.setInvisible(wasInvisible);
        me.bibo.militarycraft.vehicles.kamaz.util.DriverCloak.show(player);
        cloaked.remove(player.getUniqueId());
    }

    /** Restore (and untrack) every passenger of the given truck. */
    private void releasePassengersOf(UUID truckId) {
        for (java.util.Iterator<Map.Entry<UUID, UUID>> it = passengerToTruck.entrySet().iterator();
             it.hasNext(); ) {
            Map.Entry<UUID, UUID> e = it.next();
            if (!e.getValue().equals(truckId)) {
                continue;
            }
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                restoreRider(p);
            } else {
                restoreRider(e.getKey());
            }
            it.remove();
        }
    }

    /**
     * Safety net for passengers who left their seat without a dismount event
     * (death, kick, a removed seat): restore them and drop the stale tracking.
     */
    private void reconcilePassengers() {
        for (java.util.Iterator<Map.Entry<UUID, UUID>> it = passengerToTruck.entrySet().iterator();
             it.hasNext(); ) {
            Map.Entry<UUID, UUID> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            Truck truck = trucks.get(e.getValue());
            boolean seated = p != null && p.isOnline() && p.getVehicle() instanceof ArmorStand;
            if (truck == null || !seated) {
                if (p != null) {
                    restoreRider(p);
                } else {
                    restoreRider(e.getKey());
                }
                if (truck != null && p != null) {
                    truck.removePassenger(p);
                }
                it.remove();
            }
        }
    }

    // --------------------------------------------------------------- explosion damage

    public void damageTrucksFromExplosion(Location loc, double power) {
        KamazConfig cfg = plugin.config();
        for (Truck truck : new ArrayList<>(trucks.values())) {
            applyBlastTo(truck, loc, power, cfg);
        }
    }

    public void applyExplosionTo(Truck truck, Location loc, double power) {
        if (truck != null && loc != null && Double.isFinite(power) && power > 0.0) {
            applyBlastTo(truck, loc, power, plugin.config());
        }
    }

    /** Apply explosion damage (scaled by distance/power) to a single truck. */
    private void applyBlastTo(Truck truck, Location loc, double power, KamazConfig cfg) {
        if (!truck.isActive() || truck.world() != loc.getWorld()) {
            return;
        }
        Location centre = truck.anchor().clone().add(0, TruckModel.HEIGHT / 2.0, 0);
        double dist = centre.distance(loc);
        double contact = 3.0;                  // within this range it is a "contact" hit
        double radius = power * 2.0 + contact; // beyond this it does nothing
        if (dist > radius) {
            return;
        }
        // Full damage up close (so creeper blasts add up to max HP), tapering with range.
        double falloff = dist <= contact ? 1.0 : Math.max(0.0, 1.0 - (dist - contact) / (radius - contact));
        double dmg = cfg.creeperDamage * (power / 3.0) * falloff;
        if (dmg > 0) {
            truck.damage(dmg);
        }
    }

    /**
     * Damage from an AntiAirCraft rocket: a FLAT one-creeper hit with NO
     * distance falloff to any truck near the blast (parity with the other vehicles).
     */
    public void damageTrucksFromAntiAir(Location loc) {
        KamazConfig cfg = plugin.config();
        for (Truck truck : new ArrayList<>(trucks.values())) {
            if (!truck.isActive() || truck.world() != loc.getWorld()) {
                continue;
            }
            if (truck.anchor().clone().add(0, TruckModel.HEIGHT / 2.0, 0).distance(loc) <= 9.0) {
                truck.damage(cfg.creeperDamage);
            }
        }
    }

    public void setInternalExplosion(boolean value) {
        this.internalExplosion = value;
    }

    public boolean isInternalExplosion() {
        return internalExplosion;
    }

    // --------------------------------------------------------------- hitting the truck

    public void meleeFromPlayer(Player attacker) {
        if (byDriver(attacker.getUniqueId()) != null) {
            return; // a driver's swing does nothing (no weapon)
        }
        KamazConfig cfg = plugin.config();
        World w = attacker.getWorld();
        Location eye = attacker.getEyeLocation();
        Vector dir = eye.getDirection();
        double reach = 5.0;
        double roughRadius = reach + Math.sqrt(
                Math.pow(TruckModel.WIDTH / 2.0 + 0.35, 2)
                        + Math.pow(TruckModel.LENGTH / 2.0 + 0.35, 2));
        double roughRadiusSq = roughRadius * roughRadius;
        Truck best = null;
        double bestDist = reach;
        Vector hitAt = null;
        for (Truck truck : trucks.values()) {
            if (!truck.isActive() || truck.world() != w) {
                continue;
            }
            if (truck.anchor().distanceSquared(eye) > roughRadiusSq) {
                continue;
            }
            RayHit r = rayTraceTruckBody(truck, eye.toVector(), dir, reach, 0.35);
            if (r == null) {
                continue;
            }
            if (r.distance() < bestDist) {
                bestDist = r.distance();
                best = truck;
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

    private void sweepProjectiles(Truck truck) {
        KamazConfig cfg = plugin.config();
        UUID driver = truck.driver();
        for (Entity e : truck.world().getNearbyEntities(truckBodySearchBox(truck, 0.45))) {
            if (!(e instanceof Projectile proj) || !isWeaponProjectile(proj)) {
                continue;
            }
            if (!insideTruckBody(truck, proj.getLocation().toVector(), 0.45)) {
                continue;
            }
            if (driver != null && proj.getShooter() instanceof Player sp
                    && sp.getUniqueId().equals(driver)) {
                continue; // the driver's own projectiles don't count
            }
            double pct = (proj instanceof Fireball) ? cfg.weaponFireballPercent : cfg.weaponArrowPercent;
            Location at = proj.getLocation();
            proj.remove();
            weaponHitFx(at);
            if (truck.damage(truck.maxHealth() * pct / 100.0)) {
                return; // destroyed by this hit
            }
        }
    }

    private RayHit rayTraceTruckBody(Truck truck, Vector origin, Vector direction, double reach, double pad) {
        BoundingBox localBox = new BoundingBox(
                -TruckModel.WIDTH / 2.0 - pad,
                -pad,
                -TruckModel.LENGTH / 2.0 - pad,
                TruckModel.WIDTH / 2.0 + pad,
                TruckModel.HEIGHT + pad,
                TruckModel.LENGTH / 2.0 + pad);
        RayTraceResult r = localBox.rayTrace(worldToTruckLocal(truck, origin), worldDirToTruckLocal(truck, direction), reach);
        if (r == null) {
            return null;
        }
        Vector world = truckLocalToWorld(truck, r.getHitPosition());
        return new RayHit(world, origin.distance(world));
    }

    private boolean insideTruckBody(Truck truck, Vector world, double pad) {
        Vector local = worldToTruckLocal(truck, world);
        return local.getX() >= -TruckModel.WIDTH / 2.0 - pad
                && local.getX() <= TruckModel.WIDTH / 2.0 + pad
                && local.getY() >= -pad
                && local.getY() <= TruckModel.HEIGHT + pad
                && local.getZ() >= -TruckModel.LENGTH / 2.0 - pad
                && local.getZ() <= TruckModel.LENGTH / 2.0 + pad;
    }

    private BoundingBox truckBodySearchBox(Truck truck, double pad) {
        Location a = truck.anchor();
        double halfWidth = TruckModel.WIDTH / 2.0 + pad;
        double halfLength = TruckModel.LENGTH / 2.0 + pad;
        double radius = Math.sqrt(halfWidth * halfWidth + halfLength * halfLength);
        return new BoundingBox(
                a.getX() - radius, a.getY() - pad, a.getZ() - radius,
                a.getX() + radius, a.getY() + TruckModel.HEIGHT + pad, a.getZ() + radius);
    }

    private Vector worldToTruckLocal(Truck truck, Vector world) {
        return worldToTruckLocal(truck.anchor(), truck.hullYaw(), world);
    }

    private Vector worldToTruckLocal(Location anchor, double hullYaw, Vector world) {
        double dx = world.getX() - anchor.getX();
        double dz = world.getZ() - anchor.getZ();
        double yaw = Math.toRadians(hullYaw);
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vector(dx * cos + dz * sin, world.getY() - anchor.getY(), -dx * sin + dz * cos);
    }

    private Vector worldDirToTruckLocal(Truck truck, Vector dir) {
        double yaw = Math.toRadians(truck.hullYaw());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vector(dir.getX() * cos + dir.getZ() * sin, dir.getY(), -dir.getX() * sin + dir.getZ() * cos);
    }

    private Vector truckLocalToWorld(Truck truck, Vector local) {
        return truckLocalToWorld(truck.anchor(), truck.hullYaw(), local);
    }

    private Vector truckLocalToWorld(Location anchor, double hullYaw, Vector local) {
        double yaw = Math.toRadians(hullYaw);
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vector(
                anchor.getX() + local.getX() * cos - local.getZ() * sin,
                anchor.getY() + local.getY(),
                anchor.getZ() + local.getX() * sin + local.getZ() * cos);
    }

    private record RayHit(Vector position, double distance) {
    }

    public static boolean isWeaponProjectile(Projectile p) {
        return p instanceof org.bukkit.entity.AbstractArrow
                || p instanceof Fireball;
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

    public void remove(Truck truck, boolean effects) {
        UUID d = truck.driver();
        if (d != null) {
            restoreRider(d);
        }
        releasePassengersOf(truck.id());
        if (effects) {
            truck.destroy(true);
        } else {
            truck.eject();
            truck.removeEntities();
        }
        forget(truck);
    }

    /** Drop the wrapper from the registry without touching its entities. */
    private void forget(Truck truck) {
        trucks.remove(truck.id());
        driverToTruck.values().removeIf(id -> id.equals(truck.id()));
    }

    /**
     * Nuke every truck and any leftover cache. Tracked trucks are ejected and their
     * entities deleted, the registries cleared, then every loaded world is swept for
     * stray truck-tagged entities (orphans from older builds, incomplete groups or
     * crashes) so nothing is left behind.
     *
     * @return {@code [trackedTrucksRemoved, strayEntitiesSwept]}.
     */
    public int[] purgeAll() {
        int truckCount = trucks.size();
        for (Truck truck : new ArrayList<>(trucks.values())) {
            UUID d = truck.driver();
            if (d != null) {
                restoreRider(d);
            }
            releasePassengersOf(truck.id());
            truck.eject();
            truck.removeEntities();
        }
        trucks.clear();
        driverToTruck.clear();
        passengerToTruck.clear();
        cloaked.clear();
        invisibleBeforeTruck.clear();

        int strays = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
                    e.remove();
                    strays++;
                }
            }
        }
        return new int[]{truckCount, strays};
    }
}
