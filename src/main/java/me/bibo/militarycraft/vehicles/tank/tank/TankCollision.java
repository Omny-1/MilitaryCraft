package me.bibo.militarycraft.vehicles.tank.tank;

import me.bibo.militarycraft.vehicles.tank.model.TankModel;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import java.util.Collection;

/** Shared footprint/body checks for spawning and moving the large display model. */
public final class TankCollision {

    private static final double BODY_PAD = 0.08;
    private static final double TANK_GAP = 0.65;
    private static final double SUPPORT_DROP = 0.85;

    private TankCollision() {
    }

    public record PlacementResult(boolean ok, String message) {
    }

    public static Location anchorOnTop(Block support) {
        BoundingBox box = support.getBoundingBox();
        double y = box.getMaxY() > box.getMinY() ? box.getMaxY() : support.getY() + 1.0;
        return new Location(support.getWorld(), support.getX() + 0.5, y, support.getZ() + 0.5);
    }

    public static Location findGroundAnchor(World world, double x, double z, double aroundY,
                                            int scanUp, int scanDown) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int start = Math.min(world.getMaxHeight() - 1, (int) Math.floor(aroundY) + scanUp);
        int end = Math.max(world.getMinHeight(), (int) Math.floor(aroundY) - scanDown);
        for (int y = start; y >= end; y--) {
            Block block = world.getBlockAt(bx, y, bz);
            if (isBlocking(block)) {
                return new Location(world, Math.floor(x) + 0.5,
                        topOf(block), Math.floor(z) + 0.5);
            }
        }
        return null;
    }

    public static PlacementResult validatePlacement(Collection<Tank> tanks, Tank self,
                                                    Location anchor, double yaw) {
        if (anchor == null || anchor.getWorld() == null) {
            return new PlacementResult(false, "Invalid placement point");
        }
        World world = anchor.getWorld();
        double x = anchor.getX();
        double y = anchor.getY();
        double z = anchor.getZ();
        if (!hasFootprintSupport(world, x, y, z, yaw)) {
            return new PlacementResult(false, "No flat support under the whole tank footprint");
        }
        if (!isBodyClear(world, x, y, z, yaw, BODY_PAD)) {
            return new PlacementResult(false, "Not enough space: hull or turret intersects blocks");
        }
        if (overlapsTank(tanks, self, world, x, y, z, yaw)) {
            return new PlacementResult(false, "Too close to another tank");
        }
        return new PlacementResult(true, "");
    }

    public static boolean isBodyClear(World world, double cx, double cy, double cz,
                                      double yaw, double pad) {
        double halfW = TankModel.WIDTH / 2.0 + pad;
        double halfL = TankModel.LENGTH / 2.0 + pad;
        double[] xs = {-halfW, 0.0, halfW};
        double[] zs = {-halfL, -halfL * 0.5, 0.0, halfL * 0.5, halfL};
        double[] ys = {0.15, 1.0, 2.0, 3.2, TankModel.HEIGHT - 0.05};
        for (double y : ys) {
            for (double x : xs) {
                for (double z : zs) {
                    if (isBlocking(worldPointBlock(world, cx, cy + y, cz, yaw, x, z))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean hasFootprintSupport(World world, double cx, double cy, double cz, double yaw) {
        double halfW = TankModel.WIDTH / 2.0 - 0.35;
        double halfL = TankModel.LENGTH / 2.0 - 0.45;
        double[][] points = {
                {0.0, 0.0},
                {halfW, halfL}, {-halfW, halfL},
                {halfW, -halfL}, {-halfW, -halfL},
                {halfW, 0.0}, {-halfW, 0.0},
                {0.0, halfL}, {0.0, -halfL}
        };
        for (double[] point : points) {
            if (!hasSupportAt(world, cx, cy, cz, yaw, point[0], point[1])) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasSupportAt(World world, double cx, double cy, double cz,
                                        double yaw, double localX, double localZ) {
        double[] p = rotate(cx, cz, yaw, localX, localZ);
        int bx = (int) Math.floor(p[0]);
        int bz = (int) Math.floor(p[1]);
        int start = Math.min(world.getMaxHeight() - 1, (int) Math.floor(cy + 0.05));
        int end = Math.max(world.getMinHeight(), (int) Math.floor(cy - SUPPORT_DROP) - 1);
        for (int by = start; by >= end; by--) {
            Block block = world.getBlockAt(bx, by, bz);
            if (!isBlocking(block)) {
                continue;
            }
            double top = topOf(block);
            if (top <= cy + 0.08 && top >= cy - SUPPORT_DROP) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlapsTank(Collection<Tank> tanks, Tank self, World world,
                                        double x, double y, double z, double yaw) {
        if (tanks == null) {
            return false;
        }
        double minY = y - 0.1;
        double maxY = y + TankModel.HEIGHT + 0.1;
        for (Tank other : tanks) {
            if (other == self || other == null || !other.isActive() || other.world() != world) {
                continue;
            }
            double otherMinY = other.anchor().getY() - 0.1;
            double otherMaxY = other.anchor().getY() + TankModel.HEIGHT + 0.1;
            if (maxY < otherMinY || minY > otherMaxY) {
                continue;
            }
            if (overlapsObb2d(x, z, yaw, other.anchor().getX(), other.anchor().getZ(),
                    other.hullYaw(), TankModel.WIDTH / 2.0 + TANK_GAP,
                    TankModel.LENGTH / 2.0 + TANK_GAP)) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlapsObb2d(double ax, double az, double ayaw,
                                         double bx, double bz, double byaw,
                                         double halfW, double halfL) {
        double[][] axes = {
                rightAxis(ayaw), forwardAxis(ayaw),
                rightAxis(byaw), forwardAxis(byaw)
        };
        double[][] ac = corners(ax, az, ayaw, halfW, halfL);
        double[][] bc = corners(bx, bz, byaw, halfW, halfL);
        for (double[] axis : axes) {
            if (separated(ac, bc, axis[0], axis[1])) {
                return false;
            }
        }
        return true;
    }

    private static boolean separated(double[][] a, double[][] b, double ax, double az) {
        double amin = Double.POSITIVE_INFINITY;
        double amax = Double.NEGATIVE_INFINITY;
        double bmin = Double.POSITIVE_INFINITY;
        double bmax = Double.NEGATIVE_INFINITY;
        for (double[] p : a) {
            double v = p[0] * ax + p[1] * az;
            amin = Math.min(amin, v);
            amax = Math.max(amax, v);
        }
        for (double[] p : b) {
            double v = p[0] * ax + p[1] * az;
            bmin = Math.min(bmin, v);
            bmax = Math.max(bmax, v);
        }
        return amax < bmin || bmax < amin;
    }

    private static double[][] corners(double cx, double cz, double yaw, double halfW, double halfL) {
        return new double[][]{
                rotate(cx, cz, yaw, halfW, halfL),
                rotate(cx, cz, yaw, -halfW, halfL),
                rotate(cx, cz, yaw, halfW, -halfL),
                rotate(cx, cz, yaw, -halfW, -halfL)
        };
    }

    private static Block worldPointBlock(World world, double cx, double y, double cz,
                                         double yaw, double localX, double localZ) {
        double[] p = rotate(cx, cz, yaw, localX, localZ);
        return world.getBlockAt((int) Math.floor(p[0]), (int) Math.floor(y), (int) Math.floor(p[1]));
    }

    private static double[] rotate(double cx, double cz, double yaw, double localX, double localZ) {
        double rad = Math.toRadians(yaw);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        return new double[]{
                cx + localX * cos - localZ * sin,
                cz + localX * sin + localZ * cos
        };
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
        return box.getMaxY() > box.getMinY() ? box.getMaxY() : block.getY() + 1.0;
    }
}
