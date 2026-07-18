package me.bibo.militarycraft.weapons.artillery;

import me.bibo.militarycraft.core.Core;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Registry-facing artillery service used by listeners, commands, sessions and ballistics. */
final class ArtilleryManager {

    private static final double PLACEMENT_HALF_WIDTH = 2.0;
    private static final double PLACEMENT_REAR = 3.8;
    private static final double PLACEMENT_FRONT = 3.1;
    private static final double WORLD_COORDINATE_LIMIT = 29_999_984.0;

    private final Core core;
    private final ArtilleryTaskTracker tasks;
    private final ArtilleryStore store;
    private final ArtilleryModelManager models;
    private final ArtillerySessionManager sessions;
    private final ArtilleryBallistics ballistics;
    private ArtillerySettings settings;
    private BukkitTask validationTask;

    ArtilleryManager(Core core, ArtillerySettings settings) {
        this.core = core;
        this.settings = settings;
        this.tasks = new ArtilleryTaskTracker(core);
        this.store = new ArtilleryStore(core, settings);
        this.models = new ArtilleryModelManager(this);
        this.sessions = new ArtillerySessionManager(this, tasks);
        this.ballistics = new ArtilleryBallistics(this, tasks);
    }

    void start() {
        store.load();
        models.start();
        sessions.start();
        validationTask = tasks.repeating(this::validateLoaded, 100L, 100L);
    }

    void shutdown() {
        RuntimeException failure = null;
        failure = cleanup("cancel validation task", failure, () -> tasks.cancel(validationTask));
        validationTask = null;
        failure = cleanup("restore operator sessions", failure, sessions::shutdown);
        failure = cleanup("stop ballistics", failure, ballistics::shutdown);
        failure = cleanup("cancel scheduled tasks", failure, tasks::cancelAll);
        failure = cleanup("remove visual models", failure, models::shutdown);
        failure = cleanup("clear operator locks", failure, store::clearOperators);
        failure = cleanup("save artillery store", failure, store::save);
        if (failure != null) {
            throw failure;
        }
    }

    private RuntimeException cleanup(String step, RuntimeException previous, Runnable action) {
        try {
            action.run();
            return previous;
        } catch (RuntimeException exception) {
            core.logger().log(Level.WARNING, "Artillery shutdown step failed: " + step, exception);
            if (previous == null) {
                return exception;
            }
            previous.addSuppressed(exception);
            return previous;
        }
    }

    Core core() {
        return core;
    }

    ArtillerySettings settings() {
        return settings;
    }

    ArtilleryModelManager models() {
        return models;
    }

    ArtillerySessionManager sessions() {
        return sessions;
    }

    void setSettings(ArtillerySettings settings) {
        this.settings = settings;
        store.setSettings(settings);
        models.refreshAll();
    }

    Artillery create(Location location, float yaw) {
        if (!canPlace(location, yaw)) {
            return null;
        }
        Block block = location.getBlock();
        BlockData previousBlock = block.getBlockData().clone();
        block.setType(Material.BARRIER, false);
        Artillery artillery = null;
        try {
            artillery = store.create(location, yaw);
            models.spawn(artillery);
            return artillery;
        } catch (RuntimeException ex) {
            boolean rolledBack = true;
            if (artillery != null) {
                models.despawn(artillery);
                rolledBack = store.remove(artillery);
            }
            if (rolledBack && block.getType() == Material.BARRIER) {
                block.setBlockData(previousBlock, false);
            } else if (!rolledBack) {
                core.logger().severe("Artillery creation rollback could not be persisted; preserving its barrier.");
            }
            core.logger().warning("Could not create artillery: " + ex.getMessage());
            return null;
        }
    }

    boolean remove(Artillery artillery, boolean clearBarrier) {
        if (!contains(artillery)) {
            return false;
        }
        if (!store.remove(artillery)) {
            core.logger().severe("Could not persist removal of artillery " + artillery.id() + ".");
            return false;
        }
        sessions.closeByArtillery(artillery);
        models.despawn(artillery);
        if (clearBarrier) {
            Location location = artillery.blockLocation();
            if (location != null && location.getBlock().getType() == Material.BARRIER) {
                location.getBlock().setType(Material.AIR, false);
            }
        }
        return true;
    }

    void hit(Artillery artillery, Player attacker) {
        if (!settings.durabilityEnabled || !contains(artillery)) {
            return;
        }
        int previousHealth = artillery.health();
        artillery.hit();
        if (!store.save()) {
            artillery.restoreHealth(previousHealth);
            ArtilleryMessages.action(attacker, "Artillery damage could not be persisted.");
            return;
        }
        if (artillery.wrecked()) {
            destroy(artillery, attacker);
            return;
        }
        Location center = artillery.blockLocation();
        if (center != null) {
            center.add(0.5, 0.8, 0.5);
            if (settings.sounds) {
                center.getWorld().playSound(center, Sound.ENTITY_IRON_GOLEM_HURT, 1.2f, 0.7f);
            }
            if (settings.particles) {
                center.getWorld().spawnParticle(Particle.CRIT, center, 14, 0.4, 0.4, 0.4, 0.1);
                center.getWorld().spawnParticle(Particle.LARGE_SMOKE, center, 5, 0.25, 0.3, 0.25, 0.01);
            }
        }
        ArtilleryMessages.action(attacker, "Durability: " + artillery.health() + "/" + settings.maxHits);
    }

    FireResult fire(Player player, double targetX, double targetZ) {
        Artillery artillery = sessions.selected(player);
        if (artillery == null || !operational(artillery)) {
            return FireResult.failure("&cRight-click an artillery installation before firing.");
        }

        ArtilleryBallistics.ValidatedTarget target = ballistics.validate(artillery, targetX, targetZ);
        if (!target.available()) {
            return FireResult.failure("&cThe selected artillery world is unavailable.");
        }
        if (!target.validation().valid()) {
            return FireResult.failure(validationMessage(target.validation()));
        }

        long now = System.currentTimeMillis();
        if (!artillery.hasAmmo()) {
            return FireResult.failure("&cThis artillery has no ammo remaining.");
        }
        long cooldown = artillery.cooldownRemaining(settings.cooldownMillis, now);
        if (cooldown > 0L) {
            return FireResult.failure("&cArtillery is cooling down for &e"
                    + ((cooldown + 999L) / 1000L) + "&c more second(s).");
        }

        ArtilleryBallistics.PreparedSalvo salvo = ballistics.prepare(target);
        if (salvo == null) {
            return FireResult.failure("&cThe target terrain could not be resolved. No ammo was consumed.");
        }

        int previousAmmo = artillery.ammo();
        long previousLastShot = artillery.lastShotMillis();
        artillery.consumeSalvo(now);
        if (!store.save()) {
            artillery.restoreFiringState(previousAmmo, previousLastShot);
            return FireResult.failure("&cThe salvo could not be persisted. No ammo was consumed.");
        }
        if (!ballistics.launch(salvo)) {
            artillery.restoreFiringState(previousAmmo, previousLastShot);
            if (!store.save()) {
                core.logger().severe("Could not persist artillery ammo rollback after salvo scheduling failed.");
            }
            return FireResult.failure("&cThe three-shell salvo could not be scheduled. No ammo was consumed.");
        }
        sessions.close(player);
        return FireResult.success("&aThree-shell salvo fired at &e"
                + String.format(Locale.ROOT, "%.2f %.2f", targetX, targetZ)
                + "&a. Ammo units remaining: &e" + artillery.ammo() + "&a.");
    }

    private String validationMessage(ArtilleryTargetValidator.Validation validation) {
        return switch (validation.error()) {
            case NOT_FINITE -> "&cX and Z must be finite real numbers.";
            case OUT_OF_RANGE -> "&cTarget is out of range. Maximum horizontal range: &e"
                    + String.format(Locale.ROOT, "%.1f", settings.maxRange) + "&c blocks.";
            case OUTSIDE_WORLD_BORDER -> "&cTarget and its dispersion area must be inside the world border.";
            case OUTSIDE_WORLD_LIMIT -> "&cTarget is outside the supported world coordinate limit.";
            case NONE -> "";
        };
    }

    boolean rotate(Artillery artillery, float yaw) {
        float previousYaw = artillery.yaw();
        artillery.setYaw(yaw);
        if (!store.save()) {
            artillery.setYaw(previousYaw);
            return false;
        }
        models.refresh(artillery);
        return true;
    }

    boolean canPlace(Location location, float yaw) {
        if (location == null || location.getWorld() == null || store.get(location) != null) {
            return false;
        }
        if (!finite(location.getX(), location.getY(), location.getZ(), yaw)) {
            return false;
        }
        if (location.getBlockY() < location.getWorld().getMinHeight()
                || location.getBlockY() + 6 >= location.getWorld().getMaxHeight()) {
            return false;
        }
        World world = location.getWorld();
        double originX = location.getBlockX() + 0.5;
        double originZ = location.getBlockZ() + 0.5;
        if (!footprintInsideBorder(world, originX, originZ, yaw)) {
            return false;
        }

        Set<Long> checked = new HashSet<>();
        for (double localX = -PLACEMENT_HALF_WIDTH; localX <= PLACEMENT_HALF_WIDTH + 0.01; localX += 0.5) {
            for (double localZ = -PLACEMENT_REAR; localZ <= PLACEMENT_FRONT + 0.01; localZ += 0.5) {
                double[] point = localToWorld(originX, originZ, yaw, localX, localZ);
                for (int yOffset = 0; yOffset <= 2; yOffset++) {
                    if (!clearBlock(world, point[0], location.getBlockY() + yOffset, point[1], checked)) {
                        return false;
                    }
                }
            }
        }

        double tilt = Math.toRadians(60.0);
        for (double length = 0.0; length <= 4.6; length += 0.35) {
            double localZ = 0.45 + Math.cos(tilt) * length;
            int y = (int) Math.floor(location.getBlockY() + 1.55 + Math.sin(tilt) * length);
            for (double localX : new double[]{-0.45, 0.0, 0.45}) {
                double[] point = localToWorld(originX, originZ, yaw, localX, localZ);
                if (!clearBlock(world, point[0], y, point[1], checked)) {
                    return false;
                }
            }
        }

        double[][] supports = {
                {-1.35, 0.0}, {1.35, 0.0}, {-0.7, -3.15}, {0.7, -3.15}, {0.0, 0.0}
        };
        for (double[] support : supports) {
            double[] point = localToWorld(originX, originZ, yaw, support[0], support[1]);
            int blockX = (int) Math.floor(point[0]);
            int blockZ = (int) Math.floor(point[1]);
            if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
                return false;
            }
            Material material = world.getBlockAt(blockX, location.getBlockY() - 1, blockZ).getType();
            if (!material.isSolid() || material == Material.BARRIER) {
                return false;
            }
        }
        return true;
    }

    private boolean footprintInsideBorder(World world, double originX, double originZ, float yaw) {
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double halfSize = border.getSize() * 0.5;
        if (!finite(center.getX(), center.getZ(), halfSize) || halfSize < 0.0) {
            return false;
        }
        double[][] corners = {
                {-PLACEMENT_HALF_WIDTH, -PLACEMENT_REAR},
                {PLACEMENT_HALF_WIDTH, -PLACEMENT_REAR},
                {-PLACEMENT_HALF_WIDTH, PLACEMENT_FRONT},
                {PLACEMENT_HALF_WIDTH, PLACEMENT_FRONT}
        };
        for (double[] corner : corners) {
            double[] point = localToWorld(originX, originZ, yaw, corner[0], corner[1]);
            if (Math.abs(point[0]) > WORLD_COORDINATE_LIMIT
                    || Math.abs(point[1]) > WORLD_COORDINATE_LIMIT
                    || Math.abs(point[0] - center.getX()) > halfSize
                    || Math.abs(point[1] - center.getZ()) > halfSize) {
                return false;
            }
        }
        return true;
    }

    private boolean clearBlock(World world, double x, int y, double z, Set<Long> checked) {
        if (!finite(x, z) || Math.abs(x) > WORLD_COORDINATE_LIMIT || Math.abs(z) > WORLD_COORDINATE_LIMIT
                || y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return false;
        }
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
            return false;
        }
        long key = ((long) (blockX & 0x3FFFFFF) << 38)
                | ((long) (blockZ & 0x3FFFFFF) << 12) | (y & 0xFFFL);
        if (!checked.add(key)) {
            return true;
        }
        Block block = world.getBlockAt(blockX, y, blockZ);
        return block.isPassable() && !block.isLiquid();
    }

    private static double[] localToWorld(double originX, double originZ, float yaw,
                                         double localX, double localZ) {
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return new double[]{
                originX - cos * localX - sin * localZ,
                originZ - sin * localX + cos * localZ
        };
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    boolean operational(Artillery artillery) {
        Location location = artillery == null ? null : artillery.blockLocation();
        if (!contains(artillery) || artillery.wrecked() || location == null) {
            return false;
        }
        Material carrier = location.getBlock().getType();
        if (carrier.isAir()) {
            return false;
        }
        if (carrier != Material.BARRIER) {
            location.getBlock().setType(Material.BARRIER, false);
        }
        return true;
    }

    Artillery selectedOrNearest(Player player, double radius) {
        Artillery selected = sessions.selected(player);
        if (selected != null) {
            return selected;
        }
        Block target = player.getTargetBlockExact((int) Math.ceil(radius));
        Artillery aimed = target == null ? null : get(target.getLocation());
        if (aimed != null) {
            return aimed;
        }
        Artillery best = null;
        double bestDistance = radius * radius;
        for (Artillery artillery : store.all()) {
            Location location = artillery.blockLocation();
            if (location == null || location.getWorld() != player.getWorld()) {
                continue;
            }
            double distance = location.clone().add(0.5, 0.5, 0.5)
                    .distanceSquared(player.getLocation());
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = artillery;
            }
        }
        return best;
    }

    int cleanup() {
        int removed = 0;
        for (Artillery artillery : new ArrayList<>(store.all())) {
            World world = artillery.world();
            if (world == null) {
                if (remove(artillery, false)) {
                    removed++;
                }
                continue;
            }
            if (!world.isChunkLoaded(artillery.x() >> 4, artillery.z() >> 4)) {
                continue;
            }
            Material carrier = world.getBlockAt(artillery.x(), artillery.y(), artillery.z()).getType();
            if (artillery.wrecked()) {
                if (remove(artillery, artillery.wrecked())) {
                    removed++;
                }
            } else if (carrier.isAir()) {
                if (remove(artillery, false)) {
                    removed++;
                }
            } else if (carrier != Material.BARRIER) {
                world.getBlockAt(artillery.x(), artillery.y(), artillery.z()).setType(Material.BARRIER, false);
                models.refresh(artillery);
            }
        }
        models.refreshAll();
        return removed;
    }

    private void validateLoaded() {
        for (Artillery artillery : new ArrayList<>(store.all())) {
            World world = artillery.world();
            if (world == null || !world.isChunkLoaded(artillery.x() >> 4, artillery.z() >> 4)) {
                continue;
            }
            Material carrier = world.getBlockAt(artillery.x(), artillery.y(), artillery.z()).getType();
            if (artillery.wrecked()) {
                remove(artillery, true);
            } else if (carrier.isAir()) {
                remove(artillery, false);
            } else if (carrier != Material.BARRIER) {
                world.getBlockAt(artillery.x(), artillery.y(), artillery.z()).setType(Material.BARRIER, false);
                models.refresh(artillery);
            }
        }
    }

    private void destroy(Artillery artillery, Player attacker) {
        Location center = artillery.blockLocation();
        if (center != null) {
            center.add(0.5, 0.7, 0.5);
            if (settings.particles) {
                center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 2, 0.4, 0.3, 0.4, 0.0);
                center.getWorld().spawnParticle(Particle.LARGE_SMOKE, center, 40, 1.0, 0.8, 1.0, 0.05);
                center.getWorld().spawnParticle(Particle.FLAME, center, 30, 0.8, 0.6, 0.8, 0.05);
            }
            if (settings.sounds) {
                center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.8f);
            }
        }
        if (remove(artillery, true)) {
            ArtilleryMessages.send(attacker, "&c" + ArtilleryMessages.NAME + " was destroyed.");
        } else {
            ArtilleryMessages.send(attacker, "&cThe destroyed artillery could not be removed from storage yet.");
        }
    }

    Artillery get(Location location) {
        return store.get(location);
    }

    Artillery byId(UUID id) {
        return store.get(id);
    }

    boolean contains(Artillery artillery) {
        return store.contains(artillery);
    }

    Collection<Artillery> all() {
        return store.all();
    }

    List<Artillery> inChunk(UUID worldId, int chunkX, int chunkZ) {
        return store.inChunk(worldId, chunkX, chunkZ);
    }

    void clearOperators() {
        store.clearOperators();
    }

    record FireResult(boolean fired, String message) {
        static FireResult success(String message) {
            return new FireResult(true, message);
        }

        static FireResult failure(String message) {
            return new FireResult(false, message);
        }
    }
}
