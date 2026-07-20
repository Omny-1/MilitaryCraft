package me.bibo.militarycraft.vehicles.train.model;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pure math turning a car's pose (yaw, pitch) plus a {@link TrainPart} into a
 * display {@link Transformation}. BlockDisplay block geometry starts at its
 * min corner, so block parts need a rotated half-extent compensation. Wheel
 * ItemDisplays are centered by the item renderer, so they can rotate around
 * their own pivot without that compensation.
 */
public final class CarTransforms {

    /**
     * The train's rolling-distance counters, one per wheel class, in degrees
     * (distance/radius converted to degrees). Shared by every car every tick
     * so all wheels/rods of the same class stay perfectly in phase.
     */
    public record WheelPhases(double driverDeg, double leadingDeg, double bogieDeg) {
        public static final WheelPhases ZERO = new WheelPhases(0, 0, 0);

        double forChannel(TrainPart.PhaseChannel channel) {
            return switch (channel) {
                case DRIVER -> driverDeg;
                case LEADING -> leadingDeg;
                case BOGIE -> bogieDeg;
                case NONE -> 0.0;
            };
        }
    }

    private CarTransforms() {
    }

    /**
     * Car orientation: Minecraft yaw (0 = +Z/south) plus pitch, where positive
     * pitch LIFTS the nose (+Z) - hence the sign flip on rotateX.
     */
    public static Quaternionf rotation(double yawDeg, double pitchDeg) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(-yawDeg))
                .rotateX((float) Math.toRadians(-pitchDeg));
    }

    /** Car-space point to world-space offset from the car centre. */
    public static Vector3f localPointToWorld(Vector3f point, double yawDeg, double pitchDeg) {
        return rotation(yawDeg, pitchDeg).transform(new Vector3f(point));
    }

    public static Transformation forBlockPart(TrainPart part, double yawDeg, double pitchDeg) {
        return forBlockPart(part, yawDeg, pitchDeg, WheelPhases.ZERO);
    }

    /**
     * As above, but applies the part's animation (if any): a WHEEL part gets
     * its spin angle added to its base pitch (rotating about its own local X
     * - the axle), a ROD part orbits in the local Y-Z plane and a PISTON part
     * slides along local Z - all driven by {@code phases}, so every part of
     * the same wheel class stays in lock-step no matter which car it's on.
     */
    public static Transformation forBlockPart(TrainPart part, double yawDeg, double pitchDeg, WheelPhases phases) {
        Vector3f worldOffset = rotation(yawDeg, pitchDeg).transform(animatedCenter(part, phases));
        Quaternionf worldRot = partRotation(part, yawDeg, pitchDeg, phases);

        Vector3f half = new Vector3f(part.scale).mul(0.5f);
        worldRot.transform(half);
        Vector3f translation = new Vector3f(worldOffset).sub(half);

        return new Transformation(translation, worldRot, new Vector3f(part.scale), new Quaternionf());
    }

    /**
     * For ItemDisplay wheel parts: block-item models are centered by Minecraft's
     * item renderer, so the display translation can be the actual wheel pivot.
     */
    public static Transformation forCenteredPart(TrainPart part, double yawDeg, double pitchDeg, WheelPhases phases) {
        Vector3f worldOffset = rotation(yawDeg, pitchDeg).transform(animatedCenter(part, phases));
        Quaternionf worldRot = partRotation(part, yawDeg, pitchDeg, phases);
        return new Transformation(worldOffset, worldRot, new Vector3f(part.scale), new Quaternionf());
    }

    private static Vector3f animatedCenter(TrainPart part, WheelPhases phases) {
        Vector3f localCenter = new Vector3f(part.offset);
        if (part.anim == TrainPart.Anim.ROD) {
            double rad = Math.toRadians(phases.forChannel(part.channel) + part.animPhaseOffsetDeg);
            localCenter.y += (float) (part.animRadius * Math.cos(rad));
            localCenter.z += (float) (part.animRadius * Math.sin(rad));
        } else if (part.anim == TrainPart.Anim.PISTON) {
            double rad = Math.toRadians(phases.forChannel(part.channel) + part.animPhaseOffsetDeg);
            localCenter.z += (float) (part.animRadius * Math.cos(rad));
        }
        return localCenter;
    }

    private static Quaternionf partRotation(TrainPart part, double yawDeg, double pitchDeg, WheelPhases phases) {
        float pitch = part.pitch;
        if (part.anim == TrainPart.Anim.WHEEL) {
            pitch += (float) phases.forChannel(part.channel);
        }
        Quaternionf car = rotation(yawDeg, pitchDeg);

        Quaternionf baseRot = new Quaternionf()
                .rotateY((float) Math.toRadians(-part.yaw))
                .rotateX((float) Math.toRadians(pitch))
                .rotateZ((float) Math.toRadians(part.roll));
        return new Quaternionf(car).mul(baseRot);
    }
}
