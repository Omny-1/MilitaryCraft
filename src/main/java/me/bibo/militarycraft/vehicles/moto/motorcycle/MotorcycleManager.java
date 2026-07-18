package me.bibo.militarycraft.vehicles.moto.motorcycle;

import me.bibo.militarycraft.vehicles.moto.MotoRuntime;
import me.bibo.militarycraft.vehicles.moto.config.MotoConfig;
import me.bibo.militarycraft.vehicles.moto.control.DriveController;
import me.bibo.militarycraft.vehicles.moto.model.MotorcycleModel;
import me.bibo.militarycraft.vehicles.moto.persistence.MotorcycleIndex;
import me.bibo.militarycraft.vehicles.moto.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Main-thread registry, lifecycle service and damage/query façade for every
 * currently loaded motorcycle. Global limits and cooldowns are backed by
 * {@link MotorcycleIndex}, so unloaded chunks cannot bypass them.
 */
public final class MotorcycleManager {

    private static final UUID UNOWNED = new UUID(0L, 0L);
    private static final double SUPPORT_EPSILON = 0.015;

    private final MotoRuntime plugin;
    private final MotorcycleIndex index;
    private final Map<UUID, Motorcycle> motorcycles = new LinkedHashMap<>();
    private final Map<UUID, UUID> driverToMotorcycle = new HashMap<>();
    private final Map<UUID, UUID> passengerToMotorcycle = new HashMap<>();
    private final Map<UUID, Long> meleeCooldown = new HashMap<>();
    private final Map<UUID, ArmorStand> pendingAdoptions = new LinkedHashMap<>();

    private BukkitTask task;
    private long tickCounter;
    private int internalExplosionDepth;

    public MotorcycleManager(MotoRuntime plugin) {
        this.plugin = plugin;
        this.index = new MotorcycleIndex(plugin.bukkitPlugin(), plugin.getDataFolder().toPath());
    }

    // --------------------------------------------------------------- lifecycle

    public void start() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin.bukkitPlugin(), this::tick, 1L, 1L);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void adoptExisting() {
        for (World world : Bukkit.getWorlds()) {
            onEntitiesLoad(world.getEntities());
        }
        index.flush();
    }

    public void shutdown() {
        stop();
        for (Motorcycle motorcycle : new ArrayList<>(motorcycles.values())) {
            unregisterRiders(motorcycle, true);
            motorcycle.persistState();
            index.updatePosition(motorcycle.id(), motorcycle.anchor());
            motorcycle.detach();
        }
        motorcycles.clear();
        driverToMotorcycle.clear();
        passengerToMotorcycle.clear();
        meleeCooldown.clear();
        pendingAdoptions.clear();
        index.close();
    }

    private void tick() {
        tickCounter++;
        MotoConfig config = plugin.config();
        for (Motorcycle motorcycle : new ArrayList<>(motorcycles.values())) {
            if (motorcycles.get(motorcycle.id()) != motorcycle) {
                continue; // a nested explosion already unregistered it
            }
            if (!motorcycle.isActive()) {
                discardDestroyed(motorcycle);
                continue;
            }

            if (config.drownEnabled) {
                if (motorcycle.isSubmerged() || tickCounter % config.waterCheckInterval == 0) {
                    motorcycle.refreshSubmerged();
                }
                if (motorcycle.isSubmerged()) {
                    motorcycle.tickWater(config);
                    if (!motorcycle.isActive()) {
                        discardDestroyed(motorcycle);
                        continue;
                    }
                }
            }

            UUID driverId = motorcycle.driver();
            if (driverId != null) {
                Player driver = Bukkit.getPlayer(driverId);
                if (driver == null || !driver.isOnline() || !motorcycle.isDriverMounted(driver)) {
                    leaveDriver(motorcycle, driverId, false);
                } else {
                    try {
                        DriveController.drive(motorcycle, driver, config);
                    } catch (RuntimeException failure) {
                        motorcycle.setSpeed(0.0);
                        plugin.getLogger().warning("Drive tick failed for " + motorcycle.id() + ": " + failure);
                    }
                    if (!motorcycle.isActive()) {
                        discardDestroyed(motorcycle);
                        continue;
                    }
                }
            }

            if (tickCounter % 20 == 0) {
                motorcycle.pruneEmptyPassengerSeats();
                motorcycle.persistState();
                index.updatePosition(motorcycle.id(), motorcycle.anchor());
            }
            if (tickCounter % config.transientRepairInterval == 0) {
                motorcycle.repairDerivedEntities();
            }
            if (tickCounter % config.projectileSweepInterval == 0) {
                sweepProjectiles(motorcycle);
                if (!motorcycle.isActive()) {
                    discardDestroyed(motorcycle);
                }
            }
        }

        if (tickCounter % 10 == 0) {
            reconcilePassengers();
        }
        if (tickCounter % 20 == 0 && !pendingAdoptions.isEmpty()) {
            retryPendingAdoptions();
        }
        if (tickCounter % 100 == 0) {
            index.flush();
        }
        if (tickCounter % 1200 == 0) {
            long cutoff = System.currentTimeMillis() - 120_000L;
            meleeCooldown.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        }
    }

    private void discardDestroyed(Motorcycle motorcycle) {
        unregisterRiders(motorcycle, false);
        motorcycle.removeEntities();
        motorcycles.remove(motorcycle.id(), motorcycle);
        index.remove(motorcycle.id());
    }

    public void onConfigReload() {
        MotoConfig config = plugin.config();
        for (Motorcycle motorcycle : motorcycles.values()) {
            if (motorcycle.isActive()) {
                motorcycle.onConfigReload(config);
                index.updatePosition(motorcycle.id(), motorcycle.anchor());
            }
        }
        index.flush();
    }

    // --------------------------------------------------------- entity load/unload

    public void onEntitiesLoad(Collection<Entity> entities) {
        Map<UUID, List<ArmorStand>> anchors = new LinkedHashMap<>();
        for (Entity entity : entities) {
            if (!entity.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
                continue;
            }
            UUID id = entityId(entity);
            if (id == null) {
                entity.remove();
                continue;
            }
            if (index.isDeleted(id)) {
                entity.remove();
                continue;
            }
            String role = entity.getPersistentDataContainer()
                    .get(Keys.ENTITY_ROLE, PersistentDataType.STRING);
            if ("anchor".equals(role) && entity instanceof ArmorStand anchor) {
                anchors.computeIfAbsent(id, ignored -> new ArrayList<>()).add(anchor);
            } else if (entity.isPersistent()) {
                // Derived entities have been transient since schema v1. Remove a
                // persisted leftover from a development build instead of adopting it.
                entity.remove();
            }
        }

        for (Map.Entry<UUID, List<ArmorStand>> entry : anchors.entrySet()) {
            UUID id = entry.getKey();
            List<ArmorStand> candidates = entry.getValue();
            if (motorcycles.containsKey(id)) {
                ArmorStand canonical = motorcycles.get(id).anchorEntity();
                for (ArmorStand candidate : candidates) {
                    if (candidate != canonical) {
                        candidate.remove();
                    }
                }
                continue;
            }
            ArmorStand canonical = candidates.get(0);
            for (int i = 1; i < candidates.size(); i++) {
                candidates.get(i).remove();
            }
            adoptAnchor(id, canonical, false);
        }
    }

    private void adoptAnchor(UUID id, ArmorStand anchor, boolean retry) {
        if (motorcycles.containsKey(id) || index.isDeleted(id)) {
            pendingAdoptions.remove(id);
            if (index.isDeleted(id) && anchor.isValid()) {
                anchor.remove();
            }
            return;
        }
        final Motorcycle motorcycle;
        try {
            motorcycle = Motorcycle.rehydrate(plugin, id, anchor);
        } catch (RuntimeException failure) {
            pendingAdoptions.put(id, anchor);
            if (!retry) {
                plugin.getLogger().warning("Deferred transient rebuild for motorcycle "
                        + id + ": " + failure);
            }
            return;
        }
        pendingAdoptions.remove(id);
        if (motorcycle == null) {
            plugin.getLogger().warning("Removing unreadable motorcycle anchor " + id);
            anchor.remove();
            index.remove(id);
            return;
        }
        motorcycles.put(id, motorcycle);
        index.record(id, indexOwner(motorcycle.ownerId()), motorcycle.anchor());
    }

    private void retryPendingAdoptions() {
        for (Map.Entry<UUID, ArmorStand> entry : new ArrayList<>(pendingAdoptions.entrySet())) {
            ArmorStand anchor = entry.getValue();
            if (anchor == null || !anchor.isValid()) {
                pendingAdoptions.remove(entry.getKey());
                continue;
            }
            adoptAnchor(entry.getKey(), anchor, true);
        }
    }

    /** Only canonical anchors control wrapper lifetime; transient parts are ignored. */
    public void onEntitiesUnload(Collection<Entity> entities) {
        Set<UUID> unloading = new java.util.HashSet<>();
        for (Entity entity : entities) {
            String role = entity.getPersistentDataContainer()
                    .get(Keys.ENTITY_ROLE, PersistentDataType.STRING);
            if (!"anchor".equals(role)) {
                continue;
            }
            UUID id = entityId(entity);
            if (id != null) {
                unloading.add(id);
            }
        }
        for (UUID id : unloading) {
            pendingAdoptions.remove(id);
            Motorcycle motorcycle = motorcycles.remove(id);
            if (motorcycle == null) {
                continue;
            }
            unregisterRiders(motorcycle, true);
            motorcycle.persistState();
            index.updatePosition(id, motorcycle.anchor());
            motorcycle.detach();
        }
    }

    // --------------------------------------------------------------- creation

    public Motorcycle create(Location at, double yaw) {
        return create(at, yaw, null, true);
    }

    public Motorcycle create(Location at, double yaw, UUID ownerId) {
        return create(at, yaw, ownerId, true);
    }

    /** Spawn transaction; callers still use {@link #validateCreate} for messages/limits. */
    public Motorcycle create(Location at, double yaw, UUID ownerId, boolean withSidecar) {
        MotoConfig config = plugin.config();
        Location spawn = snappedSpawnLocation(at, yaw, withSidecar, config);
        String physicalDenial = validateSpawnSpace(spawn, yaw, withSidecar, config);
        if (physicalDenial != null) {
            throw new IllegalArgumentException(physicalDenial);
        }
        Motorcycle motorcycle = Motorcycle.create(plugin, spawn, yaw, ownerId, withSidecar);
        try {
            index.record(motorcycle.id(), indexOwner(ownerId), spawn);
            motorcycles.put(motorcycle.id(), motorcycle);
            return motorcycle;
        } catch (RuntimeException failure) {
            motorcycle.removeEntities();
            throw failure;
        }
    }

    public String validateCreate(Player player, Location at, double yaw) {
        return validateCreate(player, at, yaw, true);
    }

    public String validateCreate(Player player, Location at, double yaw, boolean withSidecar) {
        if (at == null || at.getWorld() == null
                || !Double.isFinite(at.getX()) || !Double.isFinite(at.getY())
                || !Double.isFinite(at.getZ()) || !Double.isFinite(yaw)) {
            return "World, coordinates and yaw must be finite.";
        }
        World world = at.getWorld();
        MotoConfig config = plugin.config();
        Location spawn = snappedSpawnLocation(at, yaw, withSidecar, config);
        if (spawn.getY() < world.getMinHeight()
                || spawn.getY() + MotorcycleModel.HEIGHT > world.getMaxHeight()) {
            return "Motorcycle does not fit within world height.";
        }
        if (!world.isChunkLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4)) {
            return "Target chunk is not loaded; MotoCraft does not force-load distant chunks.";
        }

        boolean admin = player != null && player.hasPermission("motocraft.admin");
        if (!admin) {
            if (config.maxMotorcyclesTotal > 0 && index.countActive() >= config.maxMotorcyclesTotal) {
                return "Global motorcycle limit reached: " + config.maxMotorcyclesTotal + ".";
            }
            if (player != null && config.maxMotorcyclesPerPlayer > 0
                    && index.countOwned(player.getUniqueId()) >= config.maxMotorcyclesPerPlayer) {
                return "Your motorcycle limit has been reached: " + config.maxMotorcyclesPerPlayer + ".";
            }
            if (player != null && config.spawnCooldownSeconds > 0) {
                long cooldown = config.spawnCooldownSeconds * 1000L;
                long elapsed = System.currentTimeMillis() - index.lastSpawn(player.getUniqueId());
                if (elapsed >= 0 && elapsed < cooldown) {
                    long seconds = (cooldown - elapsed + 999L) / 1000L;
                    return "Wait " + seconds + " more seconds before placing another one.";
                }
            }
        }
        if (config.minMotorcycleSpacing > 0.0 && index.tooClose(world.getUID(),
                spawn.getX(), spawn.getY(), spawn.getZ(), config.minMotorcycleSpacing)) {
            return "Too close to another motorcycle.";
        }
        if (!admin && config.maxMotorcyclesPerChunk > 0
                && index.countInChunk(world.getUID(), spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4)
                >= config.maxMotorcyclesPerChunk) {
            return "Motorcycle limit in this chunk has been reached: "
                    + config.maxMotorcyclesPerChunk + ".";
        }
        return validateSpawnSpace(spawn, yaw, withSidecar, config);
    }

    public void recordCreate(Player player) {
        if (player != null && !player.hasPermission("motocraft.admin")) {
            index.recordSpawn(player.getUniqueId(), System.currentTimeMillis());
            index.flush();
        }
    }

    private String validateSpawnSpace(Location at, double yaw, boolean withSidecar, MotoConfig config) {
        if (at == null || at.getWorld() == null
                || !Double.isFinite(at.getX()) || !Double.isFinite(at.getY())
                || !Double.isFinite(at.getZ()) || !Double.isFinite(yaw)) {
            return "Invalid placement point.";
        }
        if (!worldBorderContains(at, yaw)) {
            return "Footprint crosses the world border.";
        }
        if (!footprintChunksLoaded(at, yaw)) {
            return "All chunks under the motorcycle footprint must already be loaded.";
        }
        String block = firstBlockingSpawnBlock(at, yaw);
        if (block != null) {
            return "Not enough space: " + block + ".";
        }
        if (hasBlockingSpawnEntity(at, yaw)) {
            return "An entity is inside the motorcycle volume.";
        }
        if (footprintTop(at, yaw, withSidecar, config) == null) {
            return "The motorcycle needs at least one solid support below it.";
        }
        return null;
    }

    private boolean worldBorderContains(Location at, double yaw) {
        for (double x : new double[]{MotorcycleModel.MIN_X, MotorcycleModel.MAX_X}) {
            for (double z : new double[]{MotorcycleModel.MIN_Z, MotorcycleModel.MAX_Z}) {
                Vector corner = localToWorld(at, yaw, new Vector(x, 0.0, z));
                if (!at.getWorld().getWorldBorder().isInside(
                        new Location(at.getWorld(), corner.getX(), at.getY(), corner.getZ()))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean footprintChunksLoaded(Location at, double yaw) {
        double minWorldX = Double.POSITIVE_INFINITY;
        double maxWorldX = Double.NEGATIVE_INFINITY;
        double minWorldZ = Double.POSITIVE_INFINITY;
        double maxWorldZ = Double.NEGATIVE_INFINITY;
        for (double x : new double[]{MotorcycleModel.MIN_X - 0.05,
                MotorcycleModel.MAX_X + 0.05}) {
            for (double z : new double[]{MotorcycleModel.MIN_Z - 0.05,
                    MotorcycleModel.MAX_Z + 0.05}) {
                Vector corner = localToWorld(at, yaw, new Vector(x, 0.0, z));
                minWorldX = Math.min(minWorldX, corner.getX());
                maxWorldX = Math.max(maxWorldX, corner.getX());
                minWorldZ = Math.min(minWorldZ, corner.getZ());
                maxWorldZ = Math.max(maxWorldZ, corner.getZ());
            }
        }
        int minChunkX = ((int) Math.floor(minWorldX)) >> 4;
        int maxChunkX = ((int) Math.floor(maxWorldX)) >> 4;
        int minChunkZ = ((int) Math.floor(minWorldZ)) >> 4;
        int maxChunkZ = ((int) Math.floor(maxWorldZ)) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!at.getWorld().isChunkLoaded(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private String firstBlockingSpawnBlock(Location at, double yaw) {
        double halfWidth = MotorcycleModel.WIDTH / 2.0 + 0.05;
        double halfLength = MotorcycleModel.LENGTH / 2.0 + 0.05;
        double localCentreX = (MotorcycleModel.MIN_X + MotorcycleModel.MAX_X) * 0.5;
        double localCentreZ = (MotorcycleModel.MIN_Z + MotorcycleModel.MAX_Z) * 0.5;
        Vector centre = localToWorld(at, yaw, new Vector(localCentreX, 0.0, localCentreZ));
        double radius = Math.hypot(halfWidth, halfLength);
        int minX = (int) Math.floor(centre.getX() - radius);
        int maxX = (int) Math.floor(centre.getX() + radius);
        int minY = (int) Math.floor(at.getY());
        int maxY = (int) Math.floor(at.getY() + MotorcycleModel.HEIGHT);
        int minZ = (int) Math.floor(centre.getZ() - radius);
        int maxZ = (int) Math.floor(centre.getZ() + radius);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = at.getWorld().getBlockAt(x, y, z);
                    if (block.isPassable() && !isLiquidLike(block)) {
                        continue;
                    }
                    BoundingBox box = block.getBoundingBox();
                    if (intersectsOrientedBody(at, yaw, box, 0.05)) {
                        return block.getType().name() + " @ " + x + "," + y + "," + z;
                    }
                }
            }
        }
        return null;
    }

    private boolean hasBlockingSpawnEntity(Location at, double yaw) {
        double localCentreX = (MotorcycleModel.MIN_X + MotorcycleModel.MAX_X) * 0.5;
        double localCentreZ = (MotorcycleModel.MIN_Z + MotorcycleModel.MAX_Z) * 0.5;
        Vector centre = localToWorld(at, yaw, new Vector(localCentreX, 0.0, localCentreZ));
        double radius = Math.hypot(MotorcycleModel.WIDTH / 2.0, MotorcycleModel.LENGTH / 2.0) + 0.5;
        BoundingBox query = new BoundingBox(centre.getX() - radius, at.getY(), centre.getZ() - radius,
                centre.getX() + radius, at.getY() + MotorcycleModel.HEIGHT, centre.getZ() + radius);
        for (Entity entity : at.getWorld().getNearbyEntities(query)) {
            if (entity instanceof Item || entity instanceof ExperienceOrb
                    || entity instanceof Projectile || entity instanceof AreaEffectCloud
                    || entity instanceof Display || entity instanceof Interaction
                    || entity.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
                continue;
            }
            if (intersectsOrientedBody(at, yaw, entity.getBoundingBox(), 0.05)) {
                return true;
            }
        }
        return false;
    }

    private static Location snappedSpawnLocation(Location at, double yaw, boolean withSidecar,
                                                MotoConfig config) {
        Location spawn = at.clone();
        Double ground = footprintTop(spawn, yaw, withSidecar, config);
        if (ground != null) {
            spawn.setY(ground);
        }
        return spawn;
    }

    private static Double footprintTop(Location at, double yaw, boolean withSidecar, MotoConfig config) {
        if (at == null || at.getWorld() == null
                || !Double.isFinite(at.getX()) || !Double.isFinite(at.getY())
                || !Double.isFinite(at.getZ()) || !Double.isFinite(yaw)) {
            return null;
        }
        Vector3f front = MotorcycleModel.frontWheelCenter();
        Vector3f rear = MotorcycleModel.rearWheelCenter();
        double[][] supports = withSidecar
                ? new double[][]{{front.x, front.z}, {rear.x, rear.z},
                {MotorcycleModel.sidecarWheelCenter().x, MotorcycleModel.sidecarWheelCenter().z}}
                : new double[][]{{front.x, front.z}, {rear.x, rear.z}};
        double highest = Double.NEGATIVE_INFINITY;
        for (double[] supportPoint : supports) {
            Vector point = localToWorld(at, yaw, new Vector(supportPoint[0], 0.0, supportPoint[1]));
            Double support = supportTop(at.getWorld(), point.getX(), at.getY(), point.getZ(), config);
            if (support == null) {
                continue;
            }
            highest = Math.max(highest, support);
        }
        return highest == Double.NEGATIVE_INFINITY ? null : highest;
    }

    private static Double supportTop(World world, double x, double y, double z, MotoConfig config) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return null;
        }
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
            return null;
        }
        double upward = Math.max(0.1, config.maxStepUp);
        double downward = Math.max(config.groundSnapDistance, config.maxStepUp + 0.2);
        int start = Math.min(world.getMaxHeight() - 1, (int) Math.floor(y + upward + 0.05));
        int end = Math.max(world.getMinHeight(), (int) Math.floor(y - downward) - 1);
        double best = Double.NEGATIVE_INFINITY;
        for (int by = start; by >= end; by--) {
            Block block = world.getBlockAt(bx, by, bz);
            for (BoundingBox shape : block.getCollisionShape().getBoundingBoxes()) {
                double minX = bx + shape.getMinX();
                double maxX = bx + shape.getMaxX();
                double minZ = bz + shape.getMinZ();
                double maxZ = bz + shape.getMaxZ();
                if (x >= minX - SUPPORT_EPSILON && x <= maxX + SUPPORT_EPSILON
                        && z >= minZ - SUPPORT_EPSILON && z <= maxZ + SUPPORT_EPSILON) {
                    best = Math.max(best, by + shape.getMaxY());
                }
            }
        }
        return best == Double.NEGATIVE_INFINITY ? null : best;
    }

    private boolean intersectsOrientedBody(Location at, double yaw, BoundingBox box, double padding) {
        // The collision surface exactly at anchor Y carries the wheels and is not
        // part of the body volume. This preserves slab/stair/snow placement.
        double bodyMinY = at.getY() + 0.01;
        double bodyMaxY = at.getY() + MotorcycleModel.HEIGHT + padding;
        if (box.getMaxY() <= bodyMinY + 1.0e-6 || box.getMinY() >= bodyMaxY - 1.0e-6) {
            return false;
        }
        double halfWidth = MotorcycleModel.WIDTH / 2.0 + padding;
        double halfLength = MotorcycleModel.LENGTH / 2.0 + padding;
        double localBodyCentreX = (MotorcycleModel.MIN_X + MotorcycleModel.MAX_X) * 0.5;
        double localBodyCentreZ = (MotorcycleModel.MIN_Z + MotorcycleModel.MAX_Z) * 0.5;
        Vector bodyCentre = localToWorld(at, yaw,
                new Vector(localBodyCentreX, 0.0, localBodyCentreZ));
        double centreX = (box.getMinX() + box.getMaxX()) * 0.5 - bodyCentre.getX();
        double centreZ = (box.getMinZ() + box.getMaxZ()) * 0.5 - bodyCentre.getZ();
        double extentX = (box.getMaxX() - box.getMinX()) * 0.5;
        double extentZ = (box.getMaxZ() - box.getMinZ()) * 0.5;
        double radians = Math.toRadians(yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double localX = centreX * cos + centreZ * sin;
        double localZ = -centreX * sin + centreZ * cos;
        if (Math.abs(localX) > halfWidth + extentX * Math.abs(cos) + extentZ * Math.abs(sin)) {
            return false;
        }
        if (Math.abs(localZ) > halfLength + extentX * Math.abs(sin) + extentZ * Math.abs(cos)) {
            return false;
        }
        double bodyExtentWorldX = halfWidth * Math.abs(cos) + halfLength * Math.abs(sin);
        double bodyExtentWorldZ = halfWidth * Math.abs(sin) + halfLength * Math.abs(cos);
        return Math.abs(centreX) <= bodyExtentWorldX + extentX
                && Math.abs(centreZ) <= bodyExtentWorldZ + extentZ;
    }

    private static boolean isLiquidLike(Block block) {
        return block.isLiquid()
                || block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    // ---------------------------------------------------------------- registry

    public Motorcycle byId(UUID id) {
        return motorcycles.get(id);
    }

    public Motorcycle byDriver(UUID playerId) {
        UUID id = driverToMotorcycle.get(playerId);
        return id == null ? null : motorcycles.get(id);
    }

    public Motorcycle byPassenger(UUID playerId) {
        UUID id = passengerToMotorcycle.get(playerId);
        return id == null ? null : motorcycles.get(id);
    }

    public Motorcycle byEntity(Entity entity) {
        UUID id = entityId(entity);
        return id == null ? null : motorcycles.get(id);
    }

    public Collection<Motorcycle> all() {
        return List.copyOf(motorcycles.values());
    }

    public int count() {
        return motorcycles.size();
    }

    public int knownCount() {
        return index.countActive();
    }

    // ------------------------------------------------------------------ riding

    public boolean enter(Motorcycle motorcycle, Player player) {
        if (motorcycle == null || player == null || !motorcycle.isActive()) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        Motorcycle existingDriver = byDriver(playerId);
        Motorcycle existingPassenger = byPassenger(playerId);
        if (existingDriver != null || existingPassenger != null) {
            return existingDriver == motorcycle || existingPassenger == motorcycle;
        }
        if (player.getVehicle() != null) {
            return false;
        }
        if (!motorcycle.isOccupied()) {
            if (!motorcycle.mount(player)) {
                return false;
            }
            driverToMotorcycle.put(playerId, motorcycle.id());
            return true;
        }
        int slot = motorcycle.boardPassenger(player);
        if (slot < 0) {
            return false;
        }
        passengerToMotorcycle.put(playerId, motorcycle.id());
        return true;
    }

    public void handleDismount(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        UUID driverVehicle = driverToMotorcycle.remove(playerId);
        if (driverVehicle != null) {
            Motorcycle motorcycle = motorcycles.get(driverVehicle);
            if (motorcycle != null) {
                motorcycle.clearDriver();
            }
            return;
        }
        UUID passengerVehicle = passengerToMotorcycle.remove(playerId);
        if (passengerVehicle != null) {
            Motorcycle motorcycle = motorcycles.get(passengerVehicle);
            if (motorcycle != null) {
                motorcycle.removePassenger(player);
            }
        }
    }

    private void leaveDriver(Motorcycle motorcycle, UUID playerId, boolean eject) {
        driverToMotorcycle.remove(playerId);
        if (eject) {
            motorcycle.ejectDriver();
        } else {
            motorcycle.clearDriver();
        }
    }

    private void unregisterRiders(Motorcycle motorcycle, boolean eject) {
        driverToMotorcycle.entrySet().removeIf(entry -> entry.getValue().equals(motorcycle.id()));
        passengerToMotorcycle.entrySet().removeIf(entry -> entry.getValue().equals(motorcycle.id()));
        if (eject) {
            motorcycle.ejectAllRiders();
        } else {
            motorcycle.clearDriver();
            motorcycle.removeAllPassengerSeats();
        }
    }

    private void reconcilePassengers() {
        Iterator<Map.Entry<UUID, UUID>> iterator = passengerToMotorcycle.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, UUID> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            Motorcycle motorcycle = motorcycles.get(entry.getValue());
            if (player == null || !player.isOnline() || motorcycle == null
                    || !motorcycle.isPassengerMounted(entry.getKey())) {
                if (motorcycle != null && player != null) {
                    motorcycle.removePassenger(player);
                }
                iterator.remove();
            }
        }
    }

    // ----------------------------------------------------------- blast damage

    public void damageMotorcyclesFromExplosion(Location location, double power) {
        MotoConfig config = plugin.config();
        for (Motorcycle motorcycle : new ArrayList<>(motorcycles.values())) {
            applyBlast(motorcycle, location, power, config);
        }
    }

    public void applyExplosionTo(Motorcycle motorcycle, Location location, double power) {
        if (motorcycle != null && location != null && Double.isFinite(power) && power > 0.0) {
            applyBlast(motorcycle, location, power, plugin.config());
        }
    }

    public void applyAntiAirHit(Motorcycle motorcycle) {
        if (motorcycle != null && motorcycle.damage(plugin.config().creeperDamage)) {
            forgetDestroyed(motorcycle);
        }
    }

    /**
     * Creates the visual Bukkit explosion while suppressing its synchronous event
     * from being routed a second time, then applies one controlled power value to
     * neighbouring motorcycles. Nested chain reactions are depth-safe.
     */
    public void createDestructionExplosion(Location centre, float power) {
        internalExplosionDepth++;
        try {
            centre.getWorld().createExplosion(centre, power, false, false);
        } finally {
            internalExplosionDepth--;
        }
        damageMotorcyclesFromExplosion(centre, power);
    }

    public boolean isInternalExplosionEvent() {
        return internalExplosionDepth > 0;
    }

    private void applyBlast(Motorcycle motorcycle, Location location, double power, MotoConfig config) {
        if (!motorcycle.isActive() || motorcycle.world() != location.getWorld()
                || !Double.isFinite(power) || power <= 0.0) {
            return;
        }
        Location centre = motorcycle.anchor().clone().add(0, MotorcycleModel.HEIGHT / 2.0, 0);
        double distance = centre.distance(location);
        double contact = 1.6;
        double radius = power * 2.0 + contact;
        if (distance > radius) {
            return;
        }
        double falloff = distance <= contact ? 1.0
                : Math.max(0.0, 1.0 - (distance - contact) / (radius - contact));
        if (motorcycle.damage(config.creeperDamage * (power / 3.0) * falloff)) {
            forgetDestroyed(motorcycle);
        }
    }

    public void damageMotorcyclesFromAntiAir(Location location) {
        for (Motorcycle motorcycle : new ArrayList<>(motorcycles.values())) {
            if (motorcycle.isActive() && motorcycle.world() == location.getWorld()
                    && motorcycle.anchor().clone().add(0, 1.0, 0).distance(location) <= 7.0
                    && motorcycle.damage(plugin.config().creeperDamage)) {
                forgetDestroyed(motorcycle);
            }
        }
    }

    // ---------------------------------------------------------- weapon damage

    public void meleeFromPlayer(Player attacker) {
        MotoConfig config = plugin.config();
        Location eye = attacker.getEyeLocation();
        Vector direction = eye.getDirection();
        double reach = 5.0;
        Motorcycle best = null;
        Vector hitPosition = null;
        double bestDistance = reach;

        for (Motorcycle motorcycle : motorcycles.values()) {
            if (!motorcycle.isActive() || motorcycle.world() != attacker.getWorld()) {
                continue;
            }
            RayHit hit = rayTraceBody(motorcycle, eye.toVector(), direction, reach, 0.18);
            if (hit != null && hit.distance() < bestDistance) {
                best = motorcycle;
                bestDistance = hit.distance();
                hitPosition = hit.position();
            }
        }
        if (best == null) {
            return;
        }
        if (attacker.getWorld().rayTraceBlocks(eye, direction,
                Math.max(0.1, bestDistance - 0.05), FluidCollisionMode.NEVER, true) != null) {
            return;
        }
        long now = System.currentTimeMillis();
        long minimum = Math.max(50, config.weaponMeleeCooldownMs);
        if (now - meleeCooldown.getOrDefault(attacker.getUniqueId(), 0L) < minimum) {
            return;
        }
        meleeCooldown.put(attacker.getUniqueId(), now);
        weaponHitFx(new Location(attacker.getWorld(), hitPosition.getX(), hitPosition.getY(), hitPosition.getZ()));
        if (best.damage(best.maxHealth() * config.weaponMeleePercent / 100.0)) {
            forgetDestroyed(best);
        }
    }

    private void sweepProjectiles(Motorcycle motorcycle) {
        MotoConfig config = plugin.config();
        double travelPadding = 4.0 * config.projectileSweepInterval + 0.35;
        for (Entity entity : motorcycle.world().getNearbyEntities(
                bodySearchBox(motorcycle, travelPadding))) {
            if (!(entity instanceof Projectile projectile) || !isWeaponProjectile(projectile)) {
                continue;
            }
            if (!insideBody(motorcycle, projectile.getLocation().toVector(), 0.35)
                    && !projectileCrossedBody(motorcycle, projectile,
                    config.projectileSweepInterval, 0.35)) {
                continue;
            }
            if (motorcycle.driver() != null && projectile.getShooter() instanceof Player shooter
                    && shooter.getUniqueId().equals(motorcycle.driver())) {
                continue;
            }
            double percent = projectile instanceof Fireball
                    ? config.weaponFireballPercent : config.weaponArrowPercent;
            Location hit = projectile.getLocation();
            projectile.remove();
            weaponHitFx(hit);
            if (motorcycle.damage(motorcycle.maxHealth() * percent / 100.0)) {
                forgetDestroyed(motorcycle);
                return;
            }
        }
    }

    /** Test the segment travelled since the previous configured projectile scan. */
    private boolean projectileCrossedBody(Motorcycle motorcycle, Projectile projectile,
                                          int intervalTicks, double padding) {
        Vector velocity = projectile.getVelocity();
        if (!Double.isFinite(velocity.getX()) || !Double.isFinite(velocity.getY())
                || !Double.isFinite(velocity.getZ()) || velocity.lengthSquared() < 1.0e-8) {
            return false;
        }
        Vector end = projectile.getLocation().toVector();
        Vector travelled = velocity.clone().multiply(Math.max(1, Math.min(3, intervalTicks)));
        Vector start = end.clone().subtract(travelled);
        Vector localStart = worldToLocal(motorcycle, start);
        Vector localDirection = worldDirectionToLocal(motorcycle, travelled);
        double length = localDirection.length();
        if (length < 1.0e-8) {
            return false;
        }
        localDirection.multiply(1.0 / length);
        BoundingBox localBody = new BoundingBox(
                MotorcycleModel.MIN_X - padding, -padding,
                MotorcycleModel.MIN_Z - padding,
                MotorcycleModel.MAX_X + padding,
                MotorcycleModel.HEIGHT + padding,
                MotorcycleModel.MAX_Z + padding);
        return localBody.rayTrace(localStart, localDirection, length) != null;
    }

    private RayHit rayTraceBody(Motorcycle motorcycle, Vector origin, Vector direction,
                                double reach, double padding) {
        BoundingBox local = new BoundingBox(
                MotorcycleModel.MIN_X - padding, -padding,
                MotorcycleModel.MIN_Z - padding,
                MotorcycleModel.MAX_X + padding,
                MotorcycleModel.HEIGHT + padding,
                MotorcycleModel.MAX_Z + padding);
        RayTraceResult result = local.rayTrace(worldToLocal(motorcycle, origin),
                worldDirectionToLocal(motorcycle, direction), reach);
        if (result == null) {
            return null;
        }
        Vector world = localToWorld(motorcycle.anchor(), motorcycle.hullYaw(), result.getHitPosition());
        return new RayHit(world, origin.distance(world));
    }

    private boolean insideBody(Motorcycle motorcycle, Vector world, double padding) {
        Vector local = worldToLocal(motorcycle, world);
        return local.getX() >= MotorcycleModel.MIN_X - padding
                && local.getX() <= MotorcycleModel.MAX_X + padding
                && local.getY() >= -padding && local.getY() <= MotorcycleModel.HEIGHT + padding
                && local.getZ() >= MotorcycleModel.MIN_Z - padding
                && local.getZ() <= MotorcycleModel.MAX_Z + padding;
    }

    private BoundingBox bodySearchBox(Motorcycle motorcycle, double padding) {
        Location anchor = motorcycle.anchor();
        double localCentreX = (MotorcycleModel.MIN_X + MotorcycleModel.MAX_X) * 0.5;
        double localCentreZ = (MotorcycleModel.MIN_Z + MotorcycleModel.MAX_Z) * 0.5;
        Vector centre = localToWorld(anchor, motorcycle.hullYaw(),
                new Vector(localCentreX, 0.0, localCentreZ));
        double radius = Math.hypot(MotorcycleModel.WIDTH / 2.0 + padding,
                MotorcycleModel.LENGTH / 2.0 + padding);
        return new BoundingBox(centre.getX() - radius, anchor.getY() - padding,
                centre.getZ() - radius, centre.getX() + radius,
                anchor.getY() + MotorcycleModel.HEIGHT + padding, centre.getZ() + radius);
    }

    public static boolean isWeaponProjectile(Projectile projectile) {
        return projectile instanceof org.bukkit.entity.AbstractArrow || projectile instanceof Fireball;
    }

    private void weaponHitFx(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.CRIT, at, 7, 0.2, 0.2, 0.2, 0.08);
        world.spawnParticle(Particle.ELECTRIC_SPARK, at, 4, 0.16, 0.16, 0.16, 0.04);
        world.playSound(at, Sound.ENTITY_IRON_GOLEM_HURT, 0.5f, 1.45f);
    }

    // --------------------------------------------------------------- removal

    public void remove(Motorcycle motorcycle, boolean effects) {
        if (motorcycle == null) {
            return;
        }
        unregisterRiders(motorcycle, true);
        if (effects) {
            motorcycle.destroy(true);
        } else {
            motorcycle.removeEntities();
        }
        motorcycles.remove(motorcycle.id());
        index.remove(motorcycle.id());
        index.flush();
    }

    private void forgetDestroyed(Motorcycle motorcycle) {
        unregisterRiders(motorcycle, false);
        motorcycles.remove(motorcycle.id());
        index.remove(motorcycle.id());
    }

    /** Tombstones unloaded records and removes every loaded MotoCraft entity. */
    public int[] purgeAll() {
        int loaded = motorcycles.size();
        index.markAllDeleted();
        for (Motorcycle motorcycle : new ArrayList<>(motorcycles.values())) {
            unregisterRiders(motorcycle, true);
            motorcycle.removeEntities();
        }
        motorcycles.clear();
        driverToMotorcycle.clear();
        passengerToMotorcycle.clear();

        int strays = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
                    entity.remove();
                    strays++;
                }
            }
        }
        index.flush();
        return new int[]{loaded, strays};
    }

    // ---------------------------------------------------------- coordinate math

    private Vector worldToLocal(Motorcycle motorcycle, Vector world) {
        Location anchor = motorcycle.anchor();
        double dx = world.getX() - anchor.getX();
        double dz = world.getZ() - anchor.getZ();
        double radians = Math.toRadians(motorcycle.hullYaw());
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vector(dx * cos + dz * sin, world.getY() - anchor.getY(), -dx * sin + dz * cos);
    }

    private Vector worldDirectionToLocal(Motorcycle motorcycle, Vector direction) {
        double radians = Math.toRadians(motorcycle.hullYaw());
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vector(direction.getX() * cos + direction.getZ() * sin,
                direction.getY(), -direction.getX() * sin + direction.getZ() * cos);
    }

    private static Vector localToWorld(Location anchor, double yaw, Vector local) {
        double radians = Math.toRadians(yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vector(anchor.getX() + local.getX() * cos - local.getZ() * sin,
                anchor.getY() + local.getY(),
                anchor.getZ() + local.getX() * sin + local.getZ() * cos);
    }

    private UUID entityId(Entity entity) {
        String value = entity.getPersistentDataContainer()
                .get(Keys.MOTORCYCLE_ID, PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static UUID indexOwner(UUID owner) {
        return owner == null ? UNOWNED : owner;
    }

    private record RayHit(Vector position, double distance) {
    }
}
