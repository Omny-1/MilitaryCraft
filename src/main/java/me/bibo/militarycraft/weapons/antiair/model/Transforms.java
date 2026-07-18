package me.bibo.militarycraft.weapons.antiair.model;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pure math turning the turret's attitude (yaw = traverse, pitch = elevation)
 * plus a {@link Part} into a display {@link Transformation}.
 *
 * <p>Conventions (matching Minecraft yaw):
 * <ul>
 *   <li>yaw rotates about +Y via {@code rotateY(-yaw)}, so yaw=0 aims +Z (south),
 *       yaw=90 aims -X (west) — i.e. {@code forward = (-cosφ·sinY, -sinφ, cosφ·cosY)}.
 *   <li>pitch rotates about local X via {@code rotateX(pitch)}; a NEGATIVE pitch
 *       raises the muzzle (barrels up). Aim code therefore uses
 *       {@code pitch = toDegrees(asin(-dirY))}.
 * </ul>
 * The gun group pitches about the trunnion pivot (a point on the column, x=0),
 * then the whole upper assembly yaws about the column.
 */
public final class Transforms {

    private Transforms() {
    }

    /** Yaw-only rotation about +Y. */
    public static Quaternionf yawQ(double yawDeg) {
        return new Quaternionf().rotateY((float) Math.toRadians(-yawDeg));
    }

    /** Full aim orientation: yaw then pitch. Its forward vector is the bore line. */
    public static Quaternionf aimQ(double yawDeg, double pitchDeg) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(-yawDeg))
                .rotateX((float) Math.toRadians(pitchDeg));
    }

    /** Unit world vector the gun bore points along for the given attitude. */
    public static Vector3f forward(double yawDeg, double pitchDeg) {
        return aimQ(yawDeg, pitchDeg).transform(new Vector3f(0f, 0f, 1f));
    }

    /**
     * Map a turret-space point to a world-space offset from the anchor, for the
     * GUN group (pitch about the trunnion, then yaw about the column).
     */
    public static Vector3f gunPointToWorld(Vector3f local, double yawDeg, double pitchDeg, Vector3f pivot) {
        Quaternionf pitchR = new Quaternionf().rotateX((float) Math.toRadians(pitchDeg));
        Vector3f p = new Vector3f(local).sub(pivot);
        pitchR.transform(p);
        p.add(pivot);
        yawQ(yawDeg).transform(p);
        return p;
    }

    /** Map a turret-space point to a world-space offset for the TURRET group (yaw only). */
    public static Vector3f turretPointToWorld(Vector3f local, double yawDeg) {
        return yawQ(yawDeg).transform(new Vector3f(local));
    }

    /**
     * Build the full transformation for a part. The display entity must sit at the
     * turret anchor location (base centre, on the ground) with no rotation of its
     * own; the part's offset, group motion and base rotation are baked in here.
     */
    public static Transformation forPart(Part part, double yawDeg, double pitchDeg, Vector3f gunPivot) {
        Quaternionf baseRot = new Quaternionf()
                .rotateY((float) Math.toRadians(-part.yaw))
                .rotateX((float) Math.toRadians(part.pitch))
                .rotateZ((float) Math.toRadians(part.roll));

        Vector3f worldOffset;
        Quaternionf worldRot;
        switch (part.pivot) {
            case TURRET -> {
                Quaternionf y = yawQ(yawDeg);
                worldOffset = y.transform(new Vector3f(part.offset));
                worldRot = new Quaternionf(y).mul(baseRot);
            }
            case GUN -> {
                Quaternionf pitchR = new Quaternionf().rotateX((float) Math.toRadians(pitchDeg));
                Quaternionf y = yawQ(yawDeg);
                Vector3f p = new Vector3f(part.offset).sub(gunPivot);
                pitchR.transform(p);
                p.add(gunPivot);
                worldOffset = y.transform(p);
                worldRot = new Quaternionf(y).mul(pitchR).mul(baseRot);
            }
            default -> { // BASE
                worldOffset = new Vector3f(part.offset);
                worldRot = baseRot;
            }
        }

        // BlockDisplay scales/rotates about its min corner; shift back by the
        // rotated half-extent so the cube ends up centred on its world offset.
        Vector3f half = new Vector3f(part.scale).mul(0.5f);
        new Quaternionf(worldRot).transform(half);
        Vector3f translation = new Vector3f(worldOffset).sub(half);

        return new Transformation(translation, worldRot, new Vector3f(part.scale), new Quaternionf());
    }
}
