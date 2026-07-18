package me.bibo.militarycraft.weapons.antiair.model;

import org.joml.Vector3f;

/**
 * One immutable cube of the turret model. Coordinates are in "turret space":
 * origin at the centre of the base on the ground, +X right, +Y up, +Z forward
 * (the direction the gun points at yaw=0, pitch=0).
 *
 * <p>Unlike a rigid body, a turret has THREE motion groups (see {@link Pivot}):
 * the base is fixed, the housing/dome traverse with yaw, and the gun barrels
 * additionally elevate with pitch about the trunnion. {@link Transforms} turns a
 * part + the turret's (yaw, pitch) into a display {@link org.bukkit.util.Transformation}.
 */
public final class Part {

    /** Decides which configured material the part is rendered with. */
    public enum Role {
        BASE, HOUSING, RADOME, GUNBODY, BARREL, MUZZLE, DETAIL
    }

    /** Which motion group the part belongs to. */
    public enum Pivot {
        /** Never moves (the deck pedestal). */
        BASE,
        /** Traverses with yaw about the vertical column. */
        TURRET,
        /** Traverses with yaw AND elevates with pitch about the trunnion. */
        GUN
    }

    public final Role role;
    public final Pivot pivot;
    /** Centre of the part in turret space. */
    public final Vector3f offset;
    /** Size of the (unit) block after scaling. */
    public final Vector3f scale;
    /** Base local rotation in degrees, applied before the group rotation. */
    public final float pitch;
    public final float yaw;
    public final float roll;

    public Part(Role role, Pivot pivot, Vector3f offset, Vector3f scale,
                float pitch, float yaw, float roll) {
        this.role = role;
        this.pivot = pivot;
        this.offset = offset;
        this.scale = scale;
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
    }

    /** Convenience for a part with no base rotation. */
    public static Part of(Role role, Pivot pivot, Vector3f offset, Vector3f scale) {
        return new Part(role, pivot, offset, scale, 0, 0, 0);
    }

    /** Convenience for a part with a base rotation. */
    public static Part of(Role role, Pivot pivot, Vector3f offset, Vector3f scale,
                          float pitch, float yaw, float roll) {
        return new Part(role, pivot, offset, scale, pitch, yaw, roll);
    }
}
