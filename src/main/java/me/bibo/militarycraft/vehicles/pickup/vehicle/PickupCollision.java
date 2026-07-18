/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.util.BoundingBox
 */
package me.bibo.militarycraft.vehicles.pickup.vehicle;

import java.util.Collection;
import me.bibo.militarycraft.vehicles.pickup.vehicle.Pickup;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

public final class PickupCollision {
    private static final double HALF_WIDTH = (double)1.35f;
    private static final double HALF_LENGTH = (double)3.2f;

    private PickupCollision() {
    }

    public static Location anchorOnTop(Block support) {
        BoundingBox box = support.getBoundingBox();
        double y = box.getMaxY() > box.getMinY() ? box.getMaxY() : (double)support.getY() + 1.0;
        return new Location(support.getWorld(), (double)support.getX() + 0.5, y, (double)support.getZ() + 0.5);
    }

    public static PlacementResult validatePlacement(Collection<Pickup> pickups, Pickup self, Location anchor, double yaw) {
        double z;
        double y;
        double x;
        if (anchor == null || anchor.getWorld() == null) {
            return new PlacementResult(false, "Invalid placement location");
        }
        World world = anchor.getWorld();
        if (!PickupCollision.hasAnchorSupport(world, x = anchor.getX(), y = anchor.getY(), z = anchor.getZ())) {
            return new PlacementResult(false, "The pickup needs level support under all wheels");
        }
        if (!PickupCollision.isBodyClear(world, x, y, z, yaw, 0.06)) {
            return new PlacementResult(false, "Not enough room: the pickup body intersects blocks");
        }
        if (PickupCollision.overlapsPickup(pickups, self, world, x, y, z, yaw)) {
            return new PlacementResult(false, "Too close to another pickup");
        }
        return new PlacementResult(true, "");
    }

    public static boolean isBodyClear(World world, double cx, double cy, double cz, double yaw, double pad) {
        double[] ys;
        double halfW = (double)1.35f + pad;
        double halfL = (double)3.2f + pad;
        double[] xs = new double[]{-halfW, 0.0, halfW};
        double[] zs = new double[]{-halfL, -halfL * 0.5, 0.0, halfL * 0.5, halfL};
        for (double y : ys = new double[]{0.1, 0.7, 1.4, 2.1}) {
            for (double x : xs) {
                for (double z : zs) {
                    if (!PickupCollision.isBlocking(PickupCollision.worldPointBlock(world, cx, cy + y, cz, yaw, x, z))) continue;
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasAnchorSupport(World world, double cx, double cy, double cz) {
        int bx = (int)Math.floor(cx);
        int bz = (int)Math.floor(cz);
        int start = Math.min(world.getMaxHeight() - 1, (int)Math.floor(cy + 0.05));
        int end = Math.max(world.getMinHeight(), (int)Math.floor(cy - 1.25) - 1);
        for (int by = start; by >= end; --by) {
            double top;
            Block block = world.getBlockAt(bx, by, bz);
            if (!PickupCollision.isBlocking(block) || !((top = PickupCollision.topOf(block)) <= cy + 0.08) || !(top >= cy - 1.25)) continue;
            return true;
        }
        return false;
    }

    private static boolean overlapsPickup(Collection<Pickup> pickups, Pickup self, World world, double x, double y, double z, double yaw) {
        if (pickups == null) {
            return false;
        }
        double minY = y - 0.1;
        double maxY = y + (double)3.3f + 0.1;
        for (Pickup other : pickups) {
            if (other == self || other == null || !other.isActive() || other.world() != world) continue;
            double otherMinY = other.anchor().getY() - 0.1;
            double otherMaxY = other.anchor().getY() + (double)3.3f + 0.1;
            if (maxY < otherMinY || minY > otherMaxY || !PickupCollision.overlapsObb2d(x, z, yaw, other.anchor().getX(), other.anchor().getZ(), other.hullYaw(), 1.700000023841858, 3.550000047683716)) continue;
            return true;
        }
        return false;
    }

    private static boolean overlapsObb2d(double ax, double az, double ayaw, double bx, double bz, double byaw, double halfW, double halfL) {
        double[][] axes = new double[][]{PickupCollision.rightAxis(ayaw), PickupCollision.forwardAxis(ayaw), PickupCollision.rightAxis(byaw), PickupCollision.forwardAxis(byaw)};
        double[][] ac = PickupCollision.corners(ax, az, ayaw, halfW, halfL);
        double[][] bc = PickupCollision.corners(bx, bz, byaw, halfW, halfL);
        for (double[] axis : axes) {
            if (!PickupCollision.separated(ac, bc, axis[0], axis[1])) continue;
            return false;
        }
        return true;
    }

    private static boolean separated(double[][] a, double[][] b, double ax, double az) {
        double v;
        double amin = Double.POSITIVE_INFINITY;
        double amax = Double.NEGATIVE_INFINITY;
        double bmin = Double.POSITIVE_INFINITY;
        double bmax = Double.NEGATIVE_INFINITY;
        for (double[] p : a) {
            v = p[0] * ax + p[1] * az;
            amin = Math.min(amin, v);
            amax = Math.max(amax, v);
        }
        for (double[] p : b) {
            v = p[0] * ax + p[1] * az;
            bmin = Math.min(bmin, v);
            bmax = Math.max(bmax, v);
        }
        return amax < bmin || bmax < amin;
    }

    private static double[][] corners(double cx, double cz, double yaw, double halfW, double halfL) {
        return new double[][]{PickupCollision.rotate(cx, cz, yaw, halfW, halfL), PickupCollision.rotate(cx, cz, yaw, -halfW, halfL), PickupCollision.rotate(cx, cz, yaw, halfW, -halfL), PickupCollision.rotate(cx, cz, yaw, -halfW, -halfL)};
    }

    private static Block worldPointBlock(World world, double cx, double y, double cz, double yaw, double localX, double localZ) {
        double[] p = PickupCollision.rotate(cx, cz, yaw, localX, localZ);
        return world.getBlockAt((int)Math.floor(p[0]), (int)Math.floor(y), (int)Math.floor(p[1]));
    }

    private static double[] rotate(double cx, double cz, double yaw, double localX, double localZ) {
        double rad = Math.toRadians(yaw);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new double[]{cx + localX * cos - localZ * sin, cz + localX * sin + localZ * cos};
    }

    private static double[] rightAxis(double yaw) {
        double rad = Math.toRadians(yaw);
        return new double[]{Math.cos(rad), Math.sin(rad)};
    }

    private static double[] forwardAxis(double yaw) {
        double rad = Math.toRadians(yaw);
        return new double[]{-Math.sin(rad), Math.cos(rad)};
    }

    private static boolean isBlocking(Block block) {
        return block.getType().isSolid() && !block.isPassable();
    }

    private static double topOf(Block block) {
        BoundingBox box = block.getBoundingBox();
        return box.getMaxY() > box.getMinY() ? box.getMaxY() : (double)block.getY() + 1.0;
    }

    public record PlacementResult(boolean ok, String message) {
    }
}
