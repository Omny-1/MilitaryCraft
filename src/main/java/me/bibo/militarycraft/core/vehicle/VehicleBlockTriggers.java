package me.bibo.militarycraft.core.vehicle;

import me.bibo.militarycraft.core.model.VehicleModel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

final class VehicleBlockTriggers {

    private static final long HOLD_MS = 350L;
    private static final double SAMPLE_STEP = 0.85;
    private static final Map<BlockKey, Long> ACTIVE = new HashMap<>();
    private static long lastReleaseMs;

    private VehicleBlockTriggers() {
    }

    static void releaseExpired() {
        long now = System.currentTimeMillis();
        if (now - lastReleaseMs < 50L) {
            return;
        }
        lastReleaseMs = now;
        Iterator<Map.Entry<BlockKey, Long>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockKey, Long> entry = iterator.next();
            if (entry.getValue() > now) {
                continue;
            }
            BlockKey key = entry.getKey();
            World world = Bukkit.getWorld(key.worldId());
            if (world != null) {
                release(world.getBlockAt(key.x(), key.y(), key.z()));
            }
            iterator.remove();
        }
    }

    static void touch(DisplayVehicle vehicle) {
        if (vehicle == null || !vehicle.isActive() || vehicle.world() == null) {
            return;
        }
        VehicleModel model = vehicle.model();
        double halfWidth = Math.max(0.2, model.width() / 2.0);
        double halfLength = Math.max(0.2, model.length() / 2.0);
        double height = Math.max(0.2, model.height());
        samplePlane(vehicle, halfWidth, halfLength, -0.08);
        samplePlane(vehicle, halfWidth, halfLength, Math.min(0.55, height));
        if (height > 1.4) {
            samplePlane(vehicle, halfWidth, halfLength, Math.min(height - 0.1, 1.35));
        }
        if (height > 2.4) {
            samplePlane(vehicle, halfWidth, halfLength, height * 0.55);
        }
    }

    private static void samplePlane(DisplayVehicle vehicle, double halfWidth, double halfLength, double y) {
        for (double x = -halfWidth; x <= halfWidth + 1.0e-6; x += SAMPLE_STEP) {
            for (double z = -halfLength; z <= halfLength + 1.0e-6; z += SAMPLE_STEP) {
                touchBlockAt(vehicle, x, y, z);
            }
        }
        touchBlockAt(vehicle, halfWidth, y, halfLength);
        touchBlockAt(vehicle, halfWidth, y, -halfLength);
        touchBlockAt(vehicle, -halfWidth, y, halfLength);
        touchBlockAt(vehicle, -halfWidth, y, -halfLength);
    }

    private static void touchBlockAt(DisplayVehicle vehicle, double localX, double localY, double localZ) {
        Vector point = localToWorld(vehicle, new Vector(localX, localY, localZ));
        Block block = vehicle.world().getBlockAt(
                floor(point.getX()), floor(point.getY()), floor(point.getZ()));
        if (!isTrigger(block.getType())) {
            return;
        }
        power(block);
    }

    private static void power(Block block) {
        BlockData data = block.getBlockData();
        boolean changed = false;
        if (data instanceof AnaloguePowerable analogue) {
            if (analogue.getPower() < analogue.getMaximumPower()) {
                analogue.setPower(analogue.getMaximumPower());
                changed = true;
            }
        } else if (data instanceof Powerable powerable) {
            if (!powerable.isPowered()) {
                powerable.setPowered(true);
                changed = true;
            }
        }
        if (changed) {
            block.setBlockData(data, true);
        }
        ACTIVE.put(BlockKey.of(block), System.currentTimeMillis() + HOLD_MS);
    }

    private static void release(Block block) {
        if (!isTrigger(block.getType())) {
            return;
        }
        BlockData data = block.getBlockData();
        boolean changed = false;
        if (data instanceof AnaloguePowerable analogue) {
            if (analogue.getPower() > 0) {
                analogue.setPower(0);
                changed = true;
            }
        } else if (data instanceof Powerable powerable) {
            if (powerable.isPowered()) {
                powerable.setPowered(false);
                changed = true;
            }
        }
        if (changed) {
            block.setBlockData(data, true);
        }
    }

    private static boolean isTrigger(Material material) {
        return material == Material.TRIPWIRE || material.name().endsWith("_PRESSURE_PLATE");
    }

    private static Vector localToWorld(DisplayVehicle vehicle, Vector local) {
        Location anchor = vehicle.anchor();
        double yaw = Math.toRadians(vehicle.facingYaw());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vector(
                anchor.getX() + local.getX() * cos - local.getZ() * sin,
                anchor.getY() + local.getY(),
                anchor.getZ() + local.getX() * sin + local.getZ() * cos);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}
