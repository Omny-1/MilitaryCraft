package me.bibo.militarycraft.vehicles.moto.model;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Display-entity transform math for the articulated motorcycle model. */
public final class Transforms {

    private Transforms() {
    }

    /** Minecraft yaw: zero faces world +Z and +90 degrees faces world -X. */
    public static Quaternionf yawQuat(double yawDeg) {
        return new Quaternionf().rotateY(radians(-yawDeg));
    }

    public static Vector3f localPointToWorld(Vector3f point, double hullYaw) {
        return yawQuat(hullYaw).transform(new Vector3f(point));
    }

    public static Transformation forPart(MotorcyclePart part, double hullYaw) {
        return forPart(part, hullYaw, 0.0, 0.0);
    }

    public static Transformation forPart(MotorcyclePart part, double hullYaw,
                                         double wheelSpinDeg) {
        return forPart(part, hullYaw, wheelSpinDeg, 0.0);
    }

    /**
     * Compose articulation in motorcycle-local space, then apply hull yaw:
     * steering pivot -> wheel roll about steered local +X -> base part rotation.
     * Steer-only fork/body parts never receive wheel spin.
     */
    public static Transformation forPart(MotorcyclePart part, double hullYaw,
                                         double wheelSpinDeg, double wheelSteerDeg) {
        Quaternionf hull = yawQuat(hullYaw);
        Quaternionf steer = part.steersWithWheel
                ? yawQuat(wheelSteerDeg)
                : new Quaternionf();

        Vector3f localCenter = articulatedLocalOffset(part, steer);
        Vector3f worldCenter = hull.transform(new Vector3f(localCenter));

        Quaternionf baseRotation = new Quaternionf()
                .rotateY(radians(-part.yaw))
                .rotateX(radians(part.pitch))
                .rotateZ(radians(part.roll));
        Quaternionf localRotation = new Quaternionf(steer);
        if (part.rollsWithWheel) {
            localRotation.rotateX(radians(wheelSpinDeg));
        }
        localRotation.mul(baseRotation);

        if (part.isText()) {
            Quaternionf rotation = part.billboardCenter
                    ? new Quaternionf()
                    : new Quaternionf(hull).mul(localRotation);
            return new Transformation(worldCenter, rotation,
                    new Vector3f(part.scale), new Quaternionf());
        }

        Quaternionf worldRotation = new Quaternionf(hull).mul(localRotation);
        // BlockDisplay's untransformed cube occupies [0,1]^3. Move its transformed
        // half-extent back so part.offset remains the exact visual centre/pivot point.
        Vector3f rotatedHalfExtent = new Vector3f(part.scale).mul(0.5f);
        worldRotation.transform(rotatedHalfExtent);
        Vector3f translation = new Vector3f(worldCenter).sub(rotatedHalfExtent);
        return new Transformation(translation, worldRotation,
                new Vector3f(part.scale), new Quaternionf());
    }

    /** Package-visible for deterministic pivot tests. */
    static Vector3f articulatedLocalOffset(MotorcyclePart part, double wheelSteerDeg) {
        Quaternionf steer = part.steersWithWheel
                ? yawQuat(wheelSteerDeg)
                : new Quaternionf();
        return articulatedLocalOffset(part, steer);
    }

    private static Vector3f articulatedLocalOffset(MotorcyclePart part, Quaternionf steer) {
        Vector3f centre = new Vector3f(part.offset);
        if (!part.steersWithWheel) {
            return centre;
        }
        return centre.sub(part.steeringPivot)
                .rotate(steer)
                .add(part.steeringPivot);
    }

    private static float radians(double degrees) {
        return (float) Math.toRadians(degrees);
    }
}
