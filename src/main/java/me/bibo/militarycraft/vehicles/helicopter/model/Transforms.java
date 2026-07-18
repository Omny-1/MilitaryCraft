package me.bibo.militarycraft.vehicles.helicopter.model;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pure math turning the helicopter's attitude (yaw, pitch, roll) plus a
 * {@link Part} into a display {@link Transformation}.
 *
 * <p>The helicopter is a single rigid body: one orientation quaternion is
 * shared by every part. Each model display sits at the helicopter's anchor
 * location with no rotation of its own; the part's local offset and base
 * rotation are baked into the transformation. Rotor parts additionally spin
 * about their own local axis (Y for the main rotor, X for the tail rotor) on
 * top of the shared body orientation.
 */
public final class Transforms {

    private Transforms() {
    }

    /**
     * Build the helicopter's orientation quaternion from Tait-Bryan angles.
     * Convention: yaw about +Y (Minecraft style, so +Z forward maps to the
     * Minecraft heading), then pitch about local X (positive = nose up), then
     * roll about local Z (positive = bank right side down).
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

    /** Unit world vector pointing out the top of the helicopter. */
    public static Vector3f up(Quaternionf orientation) {
        return orientation.transform(new Vector3f(0f, 1f, 0f));
    }

    /** Map a point in helicopter space to a world-space offset from the anchor. */
    public static Vector3f localPointToWorld(Vector3f point, Quaternionf orientation) {
        return orientation.transform(new Vector3f(point));
    }

    /**
     * Builds the full transformation for a static (non-spinning) part. The
     * display entity must sit at the helicopter anchor location with no
     * rotation of its own.
     */
    public static Transformation forPart(Part part, Quaternionf orientation) {
        return forPart(part, orientation, 0f);
    }

    /**
     * Builds the full transformation for a part, composing {@code spinDeg}
     * about the part's own spin axis (Y for {@link Part.Spin#Y}, X for
     * {@link Part.Spin#X}) on top of its base rotation. {@code spinDeg} is
     * ignored for non-rotor parts.
     */
    public static Transformation forPart(Part part, Quaternionf orientation, float spinDeg) {
        Vector3f scale = new Vector3f(part.scale);

        // The part's own base rotation (e.g. rotor mount yaw, canted fin),
        // then the continuous spin about the part's local axis, then the
        // shared body orientation on top to reach world space.
        Quaternionf baseRot = new Quaternionf()
                .rotateY((float) Math.toRadians(-part.yaw))
                .rotateX((float) Math.toRadians(part.pitch))
                .rotateZ((float) Math.toRadians(part.roll));
        if (part.spin == Part.Spin.Y) {
            baseRot.rotateY((float) Math.toRadians(spinDeg));
        } else if (part.spin == Part.Spin.X) {
            baseRot.rotateX((float) Math.toRadians(spinDeg));
        }
        Quaternionf worldRot = new Quaternionf(orientation).mul(baseRot);

        // World offset of the part centre from the anchor.
        Vector3f worldOffset = orientation.transform(new Vector3f(part.offset));

        // BlockDisplay scales/rotates about its min corner; shift back by the
        // rotated half-extent so the cube ends up centred on its world offset.
        // Radial rotor blades are the exception: they pivot at the hub (their
        // min-X corner), so we recentre ONLY the two thin axes and leave the
        // long axis rooted at the offset. That keeps the translation almost
        // constant as the blade spins, so the client's linear position
        // interpolation can't bow it off the hub (the old wobble).
        Vector3f half;
        if (part.radial) {
            // Recentre only the two THIN axes; leave the long (radial) axis rooted
            // at the hub. Main rotor (spin Y) extends +X; tail rotor (spin X) +Y.
            half = (part.spin == Part.Spin.X)
                    ? new Vector3f(scale.x * 0.5f, 0f, scale.z * 0.5f)
                    : new Vector3f(0f, scale.y * 0.5f, scale.z * 0.5f);
        } else {
            half = new Vector3f(scale).mul(0.5f);
        }
        worldRot.transform(half);
        Vector3f translation = new Vector3f(worldOffset).sub(half);

        return new Transformation(translation, worldRot, new Vector3f(part.scale), new Quaternionf());
    }
}
