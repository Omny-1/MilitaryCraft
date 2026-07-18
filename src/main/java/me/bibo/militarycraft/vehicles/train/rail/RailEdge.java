package me.bibo.militarycraft.vehicles.train.rail;

/** One of the four sides of a rail block through which track can connect. */
public enum RailEdge {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    public final int dx;
    public final int dz;

    RailEdge(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    /** X of the edge midpoint in block-local coordinates (0..1). */
    public double localX() {
        return 0.5 + dx * 0.5;
    }

    /** Z of the edge midpoint in block-local coordinates (0..1). */
    public double localZ() {
        return 0.5 + dz * 0.5;
    }

    public RailEdge opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }
}
