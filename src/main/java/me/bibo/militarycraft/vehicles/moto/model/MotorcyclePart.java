package me.bibo.militarycraft.vehicles.moto.model;

import me.bibo.militarycraft.vehicles.moto.config.MotoConfig;
import org.bukkit.Material;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * One immutable display-entity part of the motorcycle-and-sidecar model.
 * Coordinates use motorcycle space: the origin is the ground point below the
 * midpoint of the motorcycle's two wheels, +X is the rider's left, +Y is up and
 * +Z is forward. The right-hand sidecar uses negative local X.
 *
 * <p>Offsets, scales and steering pivots are defensively copied on construction.
 * They remain public for the hot render loop, so callers must treat the vectors as
 * read-only.</p>
 */
public final class MotorcyclePart {

    /** Maps a visual function to one of the existing configurable materials. */
    public enum Role {
        BODY, FRAME, ENGINE, WHEEL, HUB, GLASS, SEAT, DETAIL,
        LIGHT, INDICATOR, EXHAUST
    }

    /** Independent articulation flags; steering never implicitly means rolling. */
    public enum Motion {
        RIGID(false, false),
        ROLLING(true, false),
        STEERING(false, true),
        ROLLING_AND_STEERING(true, true);

        private final boolean rolls;
        private final boolean steers;

        Motion(boolean rolls, boolean steers) {
            this.rolls = rolls;
            this.steers = steers;
        }
    }

    public final Role role;
    /** Centre of the part in world-scaled motorcycle coordinates. */
    public final Vector3f offset;
    /** Size of the unit BlockDisplay/TextDisplay after scaling. */
    public final Vector3f scale;
    /** Base local Euler rotation in degrees. */
    public final float pitch;
    public final float yaw;
    public final float roll;
    /** Non-null for a TextDisplay part. */
    public final String text;
    public final int textArgb;
    /** Text only: CENTER billboard when true, FIXED body text when false. */
    public final boolean billboardCenter;
    public final Motion motion;
    /** Compatibility fields consumed by the existing render dirty-check. */
    public final boolean rollsWithWheel;
    public final boolean steersWithWheel;
    /** World-scaled motorcycle-local pivot; null for non-steering parts. */
    public final Vector3f steeringPivot;

    public MotorcyclePart(Role role, Vector3f offset, Vector3f scale,
                          float pitch, float yaw, float roll,
                          String text, int textArgb, boolean billboardCenter) {
        this(role, offset, scale, pitch, yaw, roll, text, textArgb,
                billboardCenter, Motion.RIGID, null);
    }

    private MotorcyclePart(Role role, Vector3f offset, Vector3f scale,
                           float pitch, float yaw, float roll,
                           String text, int textArgb, boolean billboardCenter,
                           Motion motion, Vector3f steeringPivot) {
        this.role = Objects.requireNonNull(role, "role");
        this.offset = copyFinite(offset, "offset");
        this.scale = copyPositive(scale);
        if (!Float.isFinite(pitch) || !Float.isFinite(yaw) || !Float.isFinite(roll)) {
            throw new IllegalArgumentException("part rotation must be finite");
        }
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
        this.text = text;
        this.textArgb = textArgb;
        this.billboardCenter = billboardCenter;
        this.motion = Objects.requireNonNull(motion, "motion");
        this.rollsWithWheel = motion.rolls;
        this.steersWithWheel = motion.steers;
        if (motion.steers) {
            this.steeringPivot = copyFinite(
                    Objects.requireNonNull(steeringPivot, "steeringPivot"), "steeringPivot");
        } else {
            this.steeringPivot = null;
        }
    }

    public static MotorcyclePart block(Role role, Vector3f offset, Vector3f scale) {
        return block(role, offset, scale, 0f, 0f, 0f);
    }

    public static MotorcyclePart block(Role role, Vector3f offset, Vector3f scale,
                                       float pitch, float yaw, float roll) {
        return solid(role, offset, scale, pitch, yaw, roll, Motion.RIGID, null);
    }

    /** A rear or sidecar wheel part: rolls around local +X but never steers. */
    public static MotorcyclePart rolling(Role role, Vector3f offset, Vector3f scale,
                                         float pitch, float yaw, float roll) {
        return solid(role, offset, scale, pitch, yaw, roll, Motion.ROLLING, null);
    }

    /** Fork, handlebar, lamp or fender part: steers around a pivot but never rolls. */
    public static MotorcyclePart steerOnly(Role role, Vector3f offset, Vector3f scale,
                                           float pitch, float yaw, float roll,
                                           Vector3f steeringPivot) {
        return solid(role, offset, scale, pitch, yaw, roll,
                Motion.STEERING, steeringPivot);
    }

    /** A front wheel tyre/hub part: rolls and steers around an explicit pivot. */
    public static MotorcyclePart steering(Role role, Vector3f offset, Vector3f scale,
                                          float pitch, float yaw, float roll,
                                          Vector3f steeringPivot) {
        return solid(role, offset, scale, pitch, yaw, roll,
                Motion.ROLLING_AND_STEERING, steeringPivot);
    }

    /** Compatibility overload: steer in place around the part's own centre. */
    public static MotorcyclePart steering(Role role, Vector3f offset, Vector3f scale,
                                          float pitch, float yaw, float roll) {
        return steering(role, offset, scale, pitch, yaw, roll, offset);
    }

    private static MotorcyclePart solid(Role role, Vector3f offset, Vector3f scale,
                                        float pitch, float yaw, float roll,
                                        Motion motion, Vector3f steeringPivot) {
        return new MotorcyclePart(role, offset, scale, pitch, yaw, roll,
                null, 0, false, motion, steeringPivot);
    }

    /** A FIXED TextDisplay painted on the body. */
    public static MotorcyclePart painted(Vector3f offset, Vector3f scale,
                                         float yaw, String text, int argb) {
        return new MotorcyclePart(Role.DETAIL, offset, scale, 0f, yaw, 0f,
                Objects.requireNonNull(text, "text"), argb, false);
    }

    /** A CENTER-billboard floating vehicle name. */
    public static MotorcyclePart nameplate(Vector3f offset, Vector3f scale,
                                           String text, int argb) {
        return new MotorcyclePart(Role.DETAIL, offset, scale, 0f, 0f, 0f,
                Objects.requireNonNull(text, "text"), argb, true);
    }

    public boolean isText() {
        return text != null;
    }

    public Material material(MotoConfig cfg) {
        Objects.requireNonNull(cfg, "cfg");
        return switch (role) {
            case BODY -> cfg.bodyBlock;
            case FRAME -> cfg.frameBlock;
            case ENGINE -> cfg.engineBlock;
            case WHEEL -> cfg.wheelBlock;
            case HUB -> cfg.hubBlock;
            case GLASS -> cfg.glassBlock;
            case SEAT -> cfg.seatBlock;
            case DETAIL -> cfg.detailBlock;
            case LIGHT -> cfg.lightBlock;
            case INDICATOR -> cfg.indicatorBlock;
            case EXHAUST -> cfg.exhaustBlock;
        };
    }

    private static Vector3f copyFinite(Vector3f value, String label) {
        Objects.requireNonNull(value, label);
        if (!Float.isFinite(value.x) || !Float.isFinite(value.y) || !Float.isFinite(value.z)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
        return new Vector3f(value);
    }

    private static Vector3f copyPositive(Vector3f value) {
        Vector3f result = copyFinite(value, "scale");
        if (result.x <= 0f || result.y <= 0f || result.z <= 0f) {
            throw new IllegalArgumentException("part scale must be positive");
        }
        return result;
    }
}
