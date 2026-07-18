package me.bibo.militarycraft.vehicles.aircraft;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import java.util.Objects;

public final class AircraftPlacement {

    private AircraftPlacement() {
    }

    public static Location anchorOnTop(Block support, double extraHeight) {
        Objects.requireNonNull(support, "support");
        BoundingBox box = support.getBoundingBox();
        double y = box.getMaxY() > box.getMinY() ? box.getMaxY() : support.getY() + 1.0;
        return new Location(support.getWorld(), support.getX() + 0.5, y + extraHeight, support.getZ() + 0.5);
    }

    public static boolean isFinite(Location location) {
        return location != null && location.getWorld() != null
                && AircraftSafety.coordinatesFinite(location.getX(), location.getY(), location.getZ());
    }

    public static boolean isSafeSpawn(Location location) {
        if (!isWithinSpawnBounds(location)) {
            return false;
        }
        World world = location.getWorld();
        int chunkX = ((int) Math.floor(location.getX())) >> 4;
        int chunkZ = ((int) Math.floor(location.getZ())) >> 4;
        return world.isChunkLoaded(chunkX, chunkZ);
    }

    public static boolean isWithinSpawnBounds(Location location) {
        if (!isFinite(location)) {
            return false;
        }
        World world = location.getWorld();
        return Math.abs(location.getX()) <= 29_999_984.0
                && Math.abs(location.getZ()) <= 29_999_984.0
                && location.getY() >= world.getMinHeight() - 64.0
                && location.getY() <= world.getMaxHeight() + 64.0
                && world.getWorldBorder().isInside(location);
    }

    public static void requireSafeSpawn(Location location) {
        if (!isSafeSpawn(location)) {
            throw new IllegalArgumentException("Aircraft spawn location must be finite and in a loaded chunk");
        }
    }

    public static boolean isFiniteInWorld(Location location, World world) {
        return isFinite(location) && location.getWorld() == world;
    }

    public static boolean isAreaLoaded(Location center, double radius) {
        if (!isFinite(center) || !Double.isFinite(radius) || radius < 0.0 || radius > 64.0) {
            return false;
        }
        World world = center.getWorld();
        int minX = ((int) Math.floor(center.getX() - radius)) >> 4;
        int maxX = ((int) Math.floor(center.getX() + radius)) >> 4;
        int minZ = ((int) Math.floor(center.getZ() - radius)) >> 4;
        int maxZ = ((int) Math.floor(center.getZ() + radius)) >> 4;
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }
}
