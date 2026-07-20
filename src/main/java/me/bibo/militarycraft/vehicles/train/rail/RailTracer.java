package me.bibo.militarycraft.vehicles.train.rail;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Rail;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry turning vanilla rails into a smooth polyline.
 *
 * <p>Straights and slopes contribute a single segment per block. Curved rails
 * become quarter-circle arcs of radius 0.5 around the block corner between
 * their two connected edges - this is what makes the train FLOW through a
 * 90° corner instead of snapping into the new direction.</p>
 *
 * <p>Junction behaviour matches vanilla: the path simply follows whatever
 * shape the junction rail currently has (which is what redstone switches),
 * so a train picks a branch exactly like a minecart would.</p>
 */
public final class RailTracer {

    /** Height of the running surface above the rail block's base. */
    public static final double RAIL_Y = 0.0625;

    private static final int ARC_SEGMENTS = 6;

    private RailTracer() {
    }

    /** One end of a rail shape; {@code raised} marks the high end of a slope. */
    public record Connection(RailEdge edge, boolean raised) {
    }

    /** The polyline through one block (entry to exit) plus the exit end. */
    public record Traverse(List<Vector> points, Connection exit) {
    }

    public static boolean isRail(Block b) {
        return b.getBlockData() instanceof Rail;
    }

    public static Connection[] connections(Rail.Shape shape) {
        return switch (shape) {
            case NORTH_SOUTH -> conns(RailEdge.NORTH, false, RailEdge.SOUTH, false);
            case EAST_WEST -> conns(RailEdge.EAST, false, RailEdge.WEST, false);
            case ASCENDING_EAST -> conns(RailEdge.WEST, false, RailEdge.EAST, true);
            case ASCENDING_WEST -> conns(RailEdge.EAST, false, RailEdge.WEST, true);
            case ASCENDING_NORTH -> conns(RailEdge.SOUTH, false, RailEdge.NORTH, true);
            case ASCENDING_SOUTH -> conns(RailEdge.NORTH, false, RailEdge.SOUTH, true);
            case SOUTH_EAST -> conns(RailEdge.SOUTH, false, RailEdge.EAST, false);
            case SOUTH_WEST -> conns(RailEdge.SOUTH, false, RailEdge.WEST, false);
            case NORTH_WEST -> conns(RailEdge.NORTH, false, RailEdge.WEST, false);
            case NORTH_EAST -> conns(RailEdge.NORTH, false, RailEdge.EAST, false);
        };
    }

    private static Connection[] conns(RailEdge e1, boolean r1, RailEdge e2, boolean r2) {
        return new Connection[]{new Connection(e1, r1), new Connection(e2, r2)};
    }

    private static boolean isCurve(Rail.Shape shape) {
        return switch (shape) {
            case SOUTH_EAST, SOUTH_WEST, NORTH_WEST, NORTH_EAST -> true;
            default -> false;
        };
    }

    /** World-space point of a connection end (rail surface height). */
    public static Vector endPoint(Block block, Connection c) {
        return new Vector(
                block.getX() + c.edge().localX(),
                block.getY() + RAIL_Y + (c.raised() ? 1.0 : 0.0),
                block.getZ() + c.edge().localZ());
    }

    /**
     * Walk one rail block from the given entry side and return the polyline
     * through it. If the shape does not actually connect to {@code entryEdge}
     * (side of a switched junction), the path enters through the shape's end
     * closest to that side and rides the shape out - same spirit as vanilla.
     */
    public static Traverse traverse(Block block, RailEdge entryEdge) {
        Rail rail = (Rail) block.getBlockData();
        Rail.Shape shape = rail.getShape();
        Connection[] cs = connections(shape);

        Connection entry;
        Connection exit;
        if (cs[0].edge() == entryEdge) {
            entry = cs[0];
            exit = cs[1];
        } else if (cs[1].edge() == entryEdge) {
            entry = cs[1];
            exit = cs[0];
        } else {
            int d0 = cs[0].edge().dx * entryEdge.dx + cs[0].edge().dz * entryEdge.dz;
            int d1 = cs[1].edge().dx * entryEdge.dx + cs[1].edge().dz * entryEdge.dz;
            entry = d0 >= d1 ? cs[0] : cs[1];
            exit = entry == cs[0] ? cs[1] : cs[0];
        }

        List<Vector> pts = new ArrayList<>(ARC_SEGMENTS + 1);
        if (!isCurve(shape)) {
            pts.add(endPoint(block, entry));
            pts.add(endPoint(block, exit));
        } else {
            // Corner shared by the two connected edges; arc of radius 0.5 around it.
            boolean east = entry.edge() == RailEdge.EAST || exit.edge() == RailEdge.EAST;
            boolean south = entry.edge() == RailEdge.SOUTH || exit.edge() == RailEdge.SOUTH;
            double cornerX = block.getX() + (east ? 1.0 : 0.0);
            double cornerZ = block.getZ() + (south ? 1.0 : 0.0);
            double y = block.getY() + RAIL_Y;
            Vector a = endPoint(block, entry);
            Vector b = endPoint(block, exit);
            double a0 = Math.atan2(a.getZ() - cornerZ, a.getX() - cornerX);
            double a1 = Math.atan2(b.getZ() - cornerZ, b.getX() - cornerX);
            double sweep = a1 - a0;
            while (sweep > Math.PI) {
                sweep -= 2 * Math.PI;
            }
            while (sweep < -Math.PI) {
                sweep += 2 * Math.PI;
            }
            for (int i = 0; i <= ARC_SEGMENTS; i++) {
                double ang = a0 + sweep * i / ARC_SEGMENTS;
                pts.add(new Vector(cornerX + 0.5 * Math.cos(ang), y, cornerZ + 0.5 * Math.sin(ang)));
            }
        }
        return new Traverse(pts, exit);
    }

    /**
     * The rail block the track continues into after leaving {@code current}
     * through {@code exit}, or null at a dead end. Checks the same level first
     * (which is one block up when leaving the high end of a slope), then one
     * below - but only onto a slope that climbs back toward us, like vanilla.
     */
    public static Block nextBlock(Block current, Connection exit) {
        World w = current.getWorld();
        int nx = current.getX() + exit.edge().dx;
        int nz = current.getZ() + exit.edge().dz;
        int ny = current.getY() + (exit.raised() ? 1 : 0);

        Block same = w.getBlockAt(nx, ny, nz);
        if (isRail(same)) {
            return same;
        }
        Block down = w.getBlockAt(nx, ny - 1, nz);
        if (isRail(down)) {
            Rail r = (Rail) down.getBlockData();
            for (Connection c : connections(r.getShape())) {
                if (c.raised() && c.edge() == exit.edge().opposite()) {
                    return down;
                }
            }
        }
        return null;
    }
}
