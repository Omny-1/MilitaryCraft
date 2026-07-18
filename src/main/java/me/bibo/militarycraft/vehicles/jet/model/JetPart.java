package me.bibo.militarycraft.vehicles.jet.model;

import me.bibo.militarycraft.vehicles.jet.config.JetConfig;
import org.bukkit.Material;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One immutable piece of the jet model. Coordinates are in "jet space":
 * origin at the centre of the jet, +X right, +Y up, +Z forward (the nose).
 * The whole jet is one rigid body, so every part is rotated by the same
 * orientation quaternion each tick (see {@link Transforms}).
 */
public final class JetPart {

    /** Decides which configured material the part is rendered with. */
    public enum Role {BODY, WING, NOSE, CANOPY, ENGINE, NOZZLE, MISSILE, ACCENT}

    public final Role role;
    /** Centre of the part in jet space. */
    public final Vector3f offset;
    /** Size of the (unit) block after scaling. */
    public final Vector3f scale;
    /** Base local rotation in degrees, applied before the body orientation. */
    public final float pitch;
    public final float yaw;
    public final float roll;
    /** When non-null this part is a floating TextDisplay (the board number). */
    public final String text;
    /** Immutable-by-convention base local rotation cached for the renderer. */
    private final Quaternionf baseRotation;

    public JetPart(Role role, Vector3f offset, Vector3f scale,
                   float pitch, float yaw, float roll, String text) {
        this.role = role;
        this.offset = offset;
        this.scale = scale;
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
        this.text = text;
        this.baseRotation = new Quaternionf()
                .rotateY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(pitch))
                .rotateZ((float) Math.toRadians(roll));
    }

    /** Convenience for a solid block part with no base rotation. */
    public static JetPart block(Role role, Vector3f offset, Vector3f scale) {
        return new JetPart(role, offset, scale, 0, 0, 0, null);
    }

    /** Convenience for a solid block part with a base rotation. */
    public static JetPart block(Role role, Vector3f offset, Vector3f scale,
                                float pitch, float yaw, float roll) {
        return new JetPart(role, offset, scale, pitch, yaw, roll, null);
    }

    public boolean isText() {
        return text != null;
    }

    public Quaternionf baseRotation() {
        return baseRotation;
    }

    public Material material(JetConfig cfg) {
        return switch (role) {
            case BODY -> cfg.bodyBlock;
            case WING -> cfg.wingBlock;
            case NOSE -> cfg.noseBlock;
            case CANOPY -> cfg.canopyBlock;
            case ENGINE -> cfg.engineBlock;
            case NOZZLE -> cfg.nozzleBlock;
            case MISSILE -> cfg.missileBlock;
            case ACCENT -> cfg.accentBlock;
        };
    }
}
