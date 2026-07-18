package me.bibo.militarycraft.core.util;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Shared, safe coordinate handling for admin commands that spawn things at an explicit
 * X/Y/Z (all the vehicle {@code place} commands, TCK bus placement, etc.).
 *
 * <p>The point is scale safety: an unbounded, non-finite, or far ungenerated coordinate
 * passed to {@code Location.getChunk().load()} makes the server synchronously generate
 * terrain on the main thread and freezes for everyone. {@link #resolve} <b>rejects</b>
 * (returns {@code null}) instead of silently relocating — a silent clamp both mis-places
 * the object and still generates a far chunk. Callers must treat {@code null} as "refuse
 * and tell the operator", never dereference it.
 */
public final class CommandCoords {

    private CommandCoords() {
    }

    /**
     * @return a placement {@link Location} that is safe to {@code getChunk().load()}
     *         without generating far terrain, or {@code null} if the request must be
     *         rejected because it is non-finite, outside the world border, or in a chunk
     *         that has not been generated yet. Y is clamped to build height (it never
     *         affects chunk loading); X/Z are taken as-is (never clamped) so an accepted
     *         point is exactly where the operator asked.
     */
    public static Location resolve(World world, double x, double y, double z) {
        if (world == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return null;
        }
        double clampedY = MathUtil.clamp(y, world.getMinHeight(), world.getMaxHeight() - 1);
        Location at = new Location(world, x, clampedY, z);
        if (!world.getWorldBorder().isInside(at)) {
            return null;
        }
        // Never synchronously generate far terrain on the main thread: refuse a target
        // whose chunk is not generated yet. The normal case — placing near yourself in
        // an already-generated chunk — is accepted and loads instantly.
        if (!world.isChunkGenerated(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return null;
        }
        return at;
    }
}
