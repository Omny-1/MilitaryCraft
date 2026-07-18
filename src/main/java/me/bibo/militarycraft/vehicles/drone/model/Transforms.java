package me.bibo.militarycraft.vehicles.drone.model;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pure math turning the drone's attitude (yaw, pitch, roll) plus a
 * {@link DronePart} into a display {@link Transformation}.
 *
 * <p>The drone is a single rigid body: one orientation quaternion is shared by
 * every part. Each model display sits at the drone's anchor location with no
 * rotation of its own; the part's local offset and base rotation are baked into
 * the transformation. Propeller parts get an extra fast spin folded in so the
 * blades blur as the drone flies.
 */
public final class Transforms {

    private Transforms() {
    }

    /**
     * Build the drone's orientation quaternion from Tait-Bryan angles.
     * Convention: yaw about +Y (Minecraft style, so +Z forward maps to the
     * Minecraft heading), then pitch about local X (positive = nose down, which
     * matches Minecraft's look pitch so "look up = climb"), then roll about
     * local Z (positive = left wing down).
     */
    public static Quaternionf orientation(double yawDeg, double pitchDeg, double rollDeg) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(-yawDeg))
                .rotateX((float) Math.toRadians(pitchDeg))
                .rotateZ((float) Math.toRadians(rollDeg));
    }

    /** Unit world vector the camera points along, for the given attitude. */
    public static Vector3f forward(Quaternionf orientation) {
        return orientation.transform(new Vector3f(0f, 0f, 1f));
    }

    /** Unit world vector pointing out the top of the drone. */
    public static Vector3f up(Quaternionf orientation) {
        return orientation.transform(new Vector3f(0f, 1f, 0f));
    }

    /** Map a point in drone space to a world-space offset from the anchor. */
    public static Vector3f localPointToWorld(Vector3f point, Quaternionf orientation) {
        return orientation.transform(new Vector3f(point));
    }

    /**
     * Builds the full transformation for a part. The display entity must sit at
     * the drone anchor location with no rotation of its own.
     *
     * @param propPhaseDeg current propeller spin angle in degrees (applied only
     *                     to {@link DronePart#spin} parts)
     */
    public static Transformation forPart(DronePart part, Quaternionf orientation, float propPhaseDeg) {
        Vector3f scale = new Vector3f(part.scale);

        // The part's own base rotation (camera tilt, arm sweep), then the shared
        // body orientation on top to reach world space. Spinning props get a fast
        // local Y rotation prepended so the blades whirl.
        Quaternionf baseRot = new Quaternionf()
                .rotateY((float) Math.toRadians(-part.yaw))
                .rotateX((float) Math.toRadians(part.pitch))
                .rotateZ((float) Math.toRadians(part.roll));
        if (part.spin) {
            if (part.spinZ) {
                baseRot.rotateZ((float) Math.toRadians(propPhaseDeg));
            } else {
                baseRot.rotateY((float) Math.toRadians(propPhaseDeg));
            }
        }
        Quaternionf worldRot = new Quaternionf(orientation).mul(baseRot);

        // World offset of the part centre from the anchor.
        Vector3f worldOffset = orientation.transform(new Vector3f(part.offset));

        // BlockDisplay scales/rotates about its min corner; shift back by the
        // rotated half-extent so the cube ends up centred on its world offset.
        Vector3f half = new Vector3f(scale).mul(0.5f);
        worldRot.transform(half);
        Vector3f translation = new Vector3f(worldOffset).sub(half);

        return new Transformation(translation, worldRot, new Vector3f(part.scale), new Quaternionf());
    }
}
