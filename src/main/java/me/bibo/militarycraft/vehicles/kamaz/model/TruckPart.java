package me.bibo.militarycraft.vehicles.kamaz.model;

import me.bibo.militarycraft.vehicles.kamaz.config.KamazConfig;
import org.bukkit.Material;
import org.joml.Vector3f;

/**
 * One immutable piece of the Kamaz model. Coordinates are in "truck space":
 * origin at the centre of the truck on the ground, +X right, +Y up, +Z forward
 * (forward = the cab / front end).
 *
 * <p>Unlike the tank there is no turret or barrel - the Kamaz is a single rigid
 * body, so every part simply follows the hull yaw. That keeps the math in
 * {@link Transforms} to a single rotation.
 */
public final class TruckPart {

    /** Decides which configured material the part is rendered with. */
    public enum Role {HULL, CAB, CHASSIS, WHEEL, HUB, GLASS, DETAIL, LIGHT, BUMPER}

    public final Role role;
    /** Centre of the part in truck space. */
    public final Vector3f offset;
    /** Size of the (unit) block after scaling. */
    public final Vector3f scale;
    /** Base local rotation in degrees, applied before the hull rotation. */
    public final float pitch;
    public final float yaw;
    public final float roll;
    /** When non-null this part is a floating TextDisplay (name plate / side number). */
    public final String text;
    /** Text colour (ARGB) when {@link #text} is set. */
    public final int textArgb;
    /** Text only: true => CENTER billboard (faces viewer); false => FIXED (painted on the body). */
    public final boolean billboardCenter;
    /** True for wheel tyre/hub parts that should roll around the axle while driving. */
    public final boolean rollsWithWheel;
    /** True for front wheel parts that should yaw left/right while steering. */
    public final boolean steersWithWheel;

    public TruckPart(Role role, Vector3f offset, Vector3f scale,
                     float pitch, float yaw, float roll,
                     String text, int textArgb, boolean billboardCenter) {
        this(role, offset, scale, pitch, yaw, roll, text, textArgb, billboardCenter, false, false);
    }

    private TruckPart(Role role, Vector3f offset, Vector3f scale,
                       float pitch, float yaw, float roll,
                       String text, int textArgb, boolean billboardCenter,
                       boolean rollsWithWheel, boolean steersWithWheel) {
        this.role = role;
        this.offset = offset;
        this.scale = scale;
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
        this.text = text;
        this.textArgb = textArgb;
        this.billboardCenter = billboardCenter;
        this.rollsWithWheel = rollsWithWheel;
        this.steersWithWheel = steersWithWheel;
    }

    /** Convenience for a solid block part with no base rotation. */
    public static TruckPart block(Role role, Vector3f offset, Vector3f scale) {
        return new TruckPart(role, offset, scale, 0, 0, 0, null, 0, false);
    }

    /** Convenience for a solid block part with a base rotation. */
    public static TruckPart block(Role role, Vector3f offset, Vector3f scale,
                                  float pitch, float yaw, float roll) {
        return new TruckPart(role, offset, scale, pitch, yaw, roll, null, 0, false);
    }

    /** A solid block part that rolls around the wheel axle while the truck moves. */
    public static TruckPart rolling(Role role, Vector3f offset, Vector3f scale,
                                    float pitch, float yaw, float roll) {
        return new TruckPart(role, offset, scale, pitch, yaw, roll, null, 0, false, true, false);
    }

    /** A front wheel part that both rolls and visually steers left/right. */
    public static TruckPart steering(Role role, Vector3f offset, Vector3f scale,
                                     float pitch, float yaw, float roll) {
        return new TruckPart(role, offset, scale, pitch, yaw, roll, null, 0, false, true, true);
    }

    /** A FIXED text part painted on the body (e.g. the side number). */
    public static TruckPart painted(Vector3f offset, Vector3f scale, float yaw, String text, int argb) {
        return new TruckPart(Role.DETAIL, offset, scale, 0, yaw, 0, text, argb, false);
    }

    /** A CENTER-billboard floating name plate that always faces the viewer. */
    public static TruckPart nameplate(Vector3f offset, Vector3f scale, String text, int argb) {
        return new TruckPart(Role.DETAIL, offset, scale, 0, 0, 0, text, argb, true);
    }

    public boolean isText() {
        return text != null;
    }

    public Material material(KamazConfig cfg) {
        return switch (role) {
            case HULL -> cfg.hullBlock;
            case CAB -> cfg.cabBlock;
            case CHASSIS -> cfg.chassisBlock;
            case WHEEL -> cfg.wheelBlock;
            case HUB -> cfg.hubBlock;
            case GLASS -> cfg.glassBlock;
            case DETAIL -> cfg.detailBlock;
            case LIGHT -> cfg.lightBlock;
            case BUMPER -> cfg.bumperBlock;
        };
    }
}
