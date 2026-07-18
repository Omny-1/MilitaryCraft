package me.bibo.militarycraft.core.model;

import org.bukkit.Material;
import org.joml.Vector3f;

/**
 * One immutable piece of a vehicle model, in vehicle-local space (§5.5): origin at
 * the vehicle's ground anchor, +X right, +Y up, +Z forward.
 *
 * <p>Generalises TankCraft's {@code TankPart}: there is no baked-in {@code Role} enum
 * mapping to a config material — each vehicle module already resolves its own config
 * to a concrete {@link Material} before building its part list, so {@link #material}
 * is simply the resolved block (or {@code null} for a {@link #isText()} part). And
 * {@link #group} is a vehicle-defined articulation channel (an {@code int}, see
 * {@link PartGroup}) instead of a hardcoded HULL/TURRET/BARREL enum — the base never
 * switches on it, only the owning vehicle's own articulation logic does.
 */
public final class Part {

    /** Vehicle-defined articulation channel (0 = static/body; see {@link PartGroup}). */
    public final int group;
    /** Centre of the part in vehicle-local space. */
    public final Vector3f offset;
    /** Size of the (unit) block/text after scaling. */
    public final Vector3f scale;
    /** Base local rotation in degrees, applied before the vehicle's own articulation. */
    public final float pitch;
    public final float yaw;
    public final float roll;
    /** Resolved block material, or {@code null} for a {@link #isText()} part. */
    public final Material material;
    /** Non-null makes this a floating TextDisplay part instead of a BlockDisplay. */
    public final String text;

    public Part(int group, Vector3f offset, Vector3f scale, float pitch, float yaw, float roll,
                Material material, String text) {
        this.group = group;
        this.offset = offset;
        this.scale = scale;
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
        this.material = material;
        this.text = text;
    }

    /** A solid block part with no base rotation. */
    public static Part block(int group, Vector3f offset, Vector3f scale, Material material) {
        return new Part(group, offset, scale, 0f, 0f, 0f, material, null);
    }

    /** A solid block part with a base rotation. */
    public static Part block(int group, Vector3f offset, Vector3f scale, Material material,
                              float pitch, float yaw, float roll) {
        return new Part(group, offset, scale, pitch, yaw, roll, material, null);
    }

    /** A floating text part (e.g. a hull/tail number). */
    public static Part text(int group, Vector3f offset, String text) {
        return new Part(group, offset, new Vector3f(1f, 1f, 1f), 0f, 0f, 0f, null, text);
    }

    public boolean isText() {
        return text != null;
    }
}
