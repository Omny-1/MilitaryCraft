package me.bibo.militarycraft.vehicles.drone.model;

import me.bibo.militarycraft.vehicles.drone.config.DroneConfig;
import org.bukkit.Material;
import org.joml.Vector3f;

/**
 * One immutable piece of the UAV model. Coordinates are in "drone space":
 * origin at the centre of the airframe, +X right, +Y up, +Z forward (the nose).
 * The whole UAV is one rigid body, so every part is rotated by the same
 * orientation quaternion each tick (see {@link Transforms}).
 */
public final class DronePart {

    /** Decides which configured material the part is rendered with. */
    public enum Role {FRAME, ARM, MOTOR, PROP, CAMERA, WARHEAD, ACCENT}

    public final Role role;
    /** Centre of the part in drone space. */
    public final Vector3f offset;
    /** Size of the (unit) block after scaling. */
    public final Vector3f scale;
    /** Base local rotation in degrees, applied before the body orientation. */
    public final float pitch;
    public final float yaw;
    public final float roll;
    /** When true, this part spins continuously (a propeller). */
    public final boolean spin;
    /** Spin axis: true = about local Z (a rear pusher prop), false = about local Y. */
    public final boolean spinZ;

    public DronePart(Role role, Vector3f offset, Vector3f scale,
                     float pitch, float yaw, float roll, boolean spin, boolean spinZ) {
        this.role = role;
        this.offset = offset;
        this.scale = scale;
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
        this.spin = spin;
        this.spinZ = spinZ;
    }

    /** Convenience for a solid block part with no base rotation. */
    public static DronePart block(Role role, Vector3f offset, Vector3f scale) {
        return new DronePart(role, offset, scale, 0, 0, 0, false, false);
    }

    /** Convenience for a solid block part with a base rotation. */
    public static DronePart block(Role role, Vector3f offset, Vector3f scale,
                                  float pitch, float yaw, float roll) {
        return new DronePart(role, offset, scale, pitch, yaw, roll, false, false);
    }

    /** A spinning propeller. aboutZ = rear pusher prop (spins around the fuselage). */
    public static DronePart prop(Vector3f offset, Vector3f scale, boolean aboutZ) {
        return new DronePart(Role.PROP, offset, scale, 0, 0, 0, true, aboutZ);
    }

    public Material material(DroneConfig cfg) {
        return switch (role) {
            case FRAME -> cfg.frameBlock;
            case ARM -> cfg.armBlock;
            case MOTOR -> cfg.motorBlock;
            case PROP -> cfg.propBlock;
            case CAMERA -> cfg.cameraBlock;
            case WARHEAD -> cfg.warheadBlock;
            case ACCENT -> cfg.accentBlock;
        };
    }
}
