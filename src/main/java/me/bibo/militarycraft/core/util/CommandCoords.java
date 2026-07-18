package me.bibo.militarycraft.core.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/**
 * Shared, safe coordinate handling for admin commands that spawn things at explicit
 * X/Y/Z (all the vehicle {@code place} commands, artillery/nuke/airstrike targeting).
 *
 * <p>The point is scale safety: an unbounded or non-finite coordinate passed to
 * {@code Location.getChunk().load()} drives synchronous world generation at the edge of
 * the border on the main thread, which freezes the whole server. Every command routes
 * its coordinates through {@link #safeLocation} so one bad argument can hitch at most a
 * single already-near chunk instead of generating far terrain for everyone.
 */
public final class CommandCoords {

    /** Stay this many blocks inside the border so a clamped point never sits on the wall. */
    private static final double BORDER_MARGIN = 16.0;

    private CommandCoords() {
    }

    /**
     * Parse a Minecraft-style coordinate token: {@code ~} / {@code ~offset} relative to
     * {@code base} (players only), or an absolute number. Returns {@code null} on a parse
     * error or a non-finite result.
     */
    public static Double parse(String token, double base, boolean allowRelative) {
        if (token == null) {
            return null;
        }
        try {
            double value;
            if (token.startsWith("~")) {
                if (!allowRelative) {
                    return null;
                }
                String rest = token.substring(1);
                value = base + (rest.isEmpty() ? 0.0 : Double.parseDouble(rest));
            } else {
                value = Double.parseDouble(token);
            }
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Build a placement {@link Location} that is finite and clamped inside the world
     * border and build height, so it can be safely {@code getChunk().load()}-ed without
     * generating far terrain on the main thread. Returns {@code null} if the world is
     * null or any coordinate is non-finite.
     */
    public static Location safeLocation(World world, double x, double y, double z) {
        if (world == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return null;
        }
        WorldBorder border = world.getWorldBorder();
        double half = border.getSize() / 2.0 - BORDER_MARGIN;
        if (half < 0.0) {
            half = 0.0;
        }
        double cx = border.getCenter().getX();
        double cz = border.getCenter().getZ();
        double clampedX = MathUtil.clamp(x, cx - half, cx + half);
        double clampedZ = MathUtil.clamp(z, cz - half, cz + half);
        double clampedY = MathUtil.clamp(y, world.getMinHeight(), world.getMaxHeight() - 1);
        return new Location(world, clampedX, clampedY, clampedZ);
    }
}
