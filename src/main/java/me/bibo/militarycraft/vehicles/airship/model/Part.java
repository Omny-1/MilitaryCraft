package me.bibo.militarycraft.vehicles.airship.model;

import me.bibo.militarycraft.vehicles.airship.config.AirshipConfig;
import org.bukkit.Material;
import org.joml.Vector3f;

/**
 * One immutable piece of the airship model. Coordinates are in "airship space":
 * origin at the centre of the gondola (where the pilot sits), +X right, +Y up,
 * +Z forward (the nose). The whole airship is one rigid body, so every part is
 * rotated by the same orientation quaternion each tick (see {@link Transforms}).
 */
public final class Part {

    /** Decides which configured material the part is rendered with. */
    public enum Role {
        ENVELOPE, RIB, FIN, FITTING, GONDOLA, TRIM, WINDOW, RIGGING, ENGINE, PROPELLER, BALLAST
    }

    public final Role role;
    /** Centre of the part in airship space. */
    public final Vector3f offset;
    /** Size of the (unit) block after scaling. */
    public final Vector3f scale;
    /** Base local rotation in degrees, applied before the body orientation. */
    public final float pitch;
    public final float yaw;
    public final float roll;
    /** When non-null this part is a floating TextDisplay (the ship name). */
    public final String text;

    public Part(Role role, Vector3f offset, Vector3f scale,
                float pitch, float yaw, float roll, String text) {
        this.role = role;
        this.offset = offset;
        this.scale = scale;
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
        this.text = text;
    }

    /** Convenience for a solid block part with no base rotation. */
    public static Part block(Role role, Vector3f offset, Vector3f scale) {
        return new Part(role, offset, scale, 0, 0, 0, null);
    }

    /** Convenience for a solid block part with a base rotation. */
    public static Part block(Role role, Vector3f offset, Vector3f scale,
                             float pitch, float yaw, float roll) {
        return new Part(role, offset, scale, pitch, yaw, roll, null);
    }

    public boolean isText() {
        return text != null;
    }

    public boolean isPropeller() {
        return role == Role.PROPELLER;
    }

    public Material material(AirshipConfig cfg) {
        return switch (role) {
            case ENVELOPE -> cfg.envelopeBlock;
            case RIB -> cfg.ribBlock;
            case FIN -> cfg.finBlock;
            case FITTING -> cfg.fittingBlock;
            case GONDOLA -> cfg.gondolaBlock;
            case TRIM -> cfg.trimBlock;
            case WINDOW -> cfg.windowBlock;
            case RIGGING -> cfg.riggingBlock;
            case ENGINE -> cfg.engineBlock;
            case PROPELLER -> cfg.fittingBlock;
            case BALLAST -> cfg.ballastBlock;
        };
    }
}
