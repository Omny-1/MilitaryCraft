package me.bibo.militarycraft.vehicles.tank.model;

import me.bibo.militarycraft.vehicles.tank.config.TankConfig;
import org.bukkit.Material;
import org.joml.Vector3f;

/**
 * One immutable piece of the tank model. Coordinates are in "tank space":
 * origin at the centre of the tank on the ground, +X right, +Y up, +Z forward.
 */
public final class TankPart {

    /** Decides which configured material the part is rendered with. */
    public enum Role {HULL, TURRET, DETAIL, TRACK, WHEEL, BARREL}

    public final PartGroup group;
    public final Role role;
    /** Centre of the part in tank space. */
    public final Vector3f offset;
    /** Size of the (unit) block after scaling. */
    public final Vector3f scale;
    /** Base local rotation in degrees, applied before the group/hull rotation. */
    public final float pitch;
    public final float yaw;
    public final float roll;
    /** When non-null this part is a floating TextDisplay (the hull number). */
    public final String text;

    public TankPart(PartGroup group, Role role, Vector3f offset, Vector3f scale,
                    float pitch, float yaw, float roll, String text) {
        this.group = group;
        this.role = role;
        this.offset = offset;
        this.scale = scale;
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
        this.text = text;
    }

    /** Convenience for a solid block part with no base rotation. */
    public static TankPart block(PartGroup g, Role role, Vector3f offset, Vector3f scale) {
        return new TankPart(g, role, offset, scale, 0, 0, 0, null);
    }

    /** Convenience for a solid block part with a base rotation. */
    public static TankPart block(PartGroup g, Role role, Vector3f offset, Vector3f scale,
                                 float pitch, float yaw, float roll) {
        return new TankPart(g, role, offset, scale, pitch, yaw, roll, null);
    }

    public boolean isText() {
        return text != null;
    }

    public Material material(TankConfig cfg) {
        return switch (role) {
            case HULL -> cfg.hullBlock;
            case TURRET -> cfg.turretBlock;
            case DETAIL -> cfg.detailBlock;
            case TRACK -> cfg.trackBlock;
            case WHEEL -> cfg.wheelBlock;
            case BARREL -> cfg.barrelBlock;
        };
    }
}
