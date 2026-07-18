package me.bibo.militarycraft.vehicles.kamaz.model;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pure math turning the truck's hull yaw plus a {@link TruckPart} into a display
 * {@link Transformation}.
 *
 * <p>Every model display entity is teleported to the truck's anchor location and
 * carries no yaw of its own; the part's world offset and rotation are baked into
 * the transformation. The Kamaz is a single rigid body, so the only articulation
 * is the hull yaw — there is no turret or barrel chain.
 */
public final class Transforms {

    private Transforms() {
    }

    /** Rotation about the world Y axis matching Minecraft's yaw convention. */
    public static Quaternionf yawQuat(double yawDeg) {
        return new Quaternionf().rotateY((float) Math.toRadians(-yawDeg));
    }

    /**
     * Maps a point in truck space to a world-space offset from the anchor.
     * Used for hitbox placement so each box tracks the rotated hull exactly.
     */
    public static Vector3f localPointToWorld(Vector3f point, double hullYaw) {
        return yawQuat(hullYaw).transform(new Vector3f(point));
    }

    /**
     * Builds the full transformation for a part. The display entity must sit at
     * the truck anchor location with no rotation of its own.
     */
    public static Transformation forPart(TruckPart part, double hullYaw) {
        return forPart(part, hullYaw, 0.0);
    }

    /**
     * Builds the full transformation and optionally rolls wheel parts around the
     * local X axle. Non-wheel parts ignore {@code wheelSpinDeg}.
     */
    public static Transformation forPart(TruckPart part, double hullYaw, double wheelSpinDeg) {
        return forPart(part, hullYaw, wheelSpinDeg, 0.0);
    }

    /**
     * Builds the full transformation and optionally rolls/steers wheel parts.
     * Steering is applied as a local yaw only for front wheel parts.
     */
    public static Transformation forPart(TruckPart part, double hullYaw,
                                         double wheelSpinDeg, double wheelSteerDeg) {
        Quaternionf hull = yawQuat(hullYaw);
        Vector3f worldOffset = hull.transform(new Vector3f(part.offset));

        // The part's own base rotation, applied before the hull yaw.
        float pitch = part.pitch + (part.rollsWithWheel ? (float) wheelSpinDeg : 0f);
        float yaw = part.yaw + (part.steersWithWheel ? (float) wheelSteerDeg : 0f);
        Quaternionf baseRot = new Quaternionf()
                .rotateY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(pitch))
                .rotateZ((float) Math.toRadians(part.roll));

        if (part.isText()) {
            // Text glyphs render centred on the display position, so no half-extent
            // shift. A CENTER-billboard plate ignores rotation (it always faces the
            // camera); a FIXED painted number bakes hull yaw + its base yaw.
            Quaternionf rot = part.billboardCenter
                    ? new Quaternionf()
                    : new Quaternionf(hull).mul(baseRot);
            return new Transformation(new Vector3f(worldOffset), rot,
                    new Vector3f(part.scale), new Quaternionf());
        }

        Quaternionf worldRot = new Quaternionf(hull).mul(baseRot);
        // Centre the unit cube on its world offset: shift back by the rotated half-extent.
        Vector3f half = new Vector3f(part.scale).mul(0.5f);
        worldRot.transform(half);
        Vector3f translation = new Vector3f(worldOffset).sub(half);
        return new Transformation(translation, worldRot, new Vector3f(part.scale), new Quaternionf());
    }
}
