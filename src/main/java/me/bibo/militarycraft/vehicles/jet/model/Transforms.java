package me.bibo.militarycraft.vehicles.jet.model;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pure math turning the jet's attitude (yaw, pitch, roll) plus a {@link JetPart}
 * into a display {@link Transformation}.
 *
 * <p>The jet is a single rigid body: one orientation quaternion is shared by
 * every part. Each model display sits at the jet's anchor location with no
 * rotation of its own; the part's local offset and base rotation are baked into
 * the transformation. If the model ever looks mirrored or rotated 180°, flip the
 * signs in {@link #orientation} here — it's the single source of truth.
 */
public final class Transforms {

    private Transforms() {
    }

    /**
     * Build the jet's orientation quaternion from Tait-Bryan angles.
     * Convention: yaw about +Y (Minecraft style, so +Z forward maps to the
     * Minecraft heading), then pitch about local X (Minecraft pitch: positive =
     * nose down), then roll about local Z (positive = bank right wing down).
     */
    public static Quaternionf orientation(double yawDeg, double pitchDeg, double rollDeg) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(-yawDeg))
                .rotateX((float) Math.toRadians(pitchDeg))
                .rotateZ((float) Math.toRadians(rollDeg));
    }

    /** Unit world vector the nose points along, for the given attitude. */
    public static Vector3f forward(Quaternionf orientation) {
        return orientation.transform(new Vector3f(0f, 0f, 1f));
    }

    /** Unit world vector pointing out the top of the jet (canopy up). */
    public static Vector3f up(Quaternionf orientation) {
        return orientation.transform(new Vector3f(0f, 1f, 0f));
    }

    /** Map a point in jet space to a world-space offset from the anchor. */
    public static Vector3f localPointToWorld(Vector3f point, Quaternionf orientation) {
        return orientation.transform(new Vector3f(point));
    }

    /**
     * Builds the full transformation for a part. The display entity must sit at
     * the jet anchor location with no rotation of its own.
     */
    public static Transformation forPart(JetPart part, Quaternionf orientation) {
        Vector3f scale = new Vector3f(part.scale);

        // The part's own base rotation (e.g. wing sweep, canted tail), then the
        // shared body orientation on top to reach world space.
        Quaternionf worldRot = new Quaternionf(orientation).mul(part.baseRotation());

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
