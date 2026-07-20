package me.bibo.militarycraft.vehicles.tank.model;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pure math turning the tank's articulation angles (hull yaw, turret yaw,
 * barrel pitch) plus a {@link TankPart} into a display {@link Transformation}.
 *
 * <p>Every model display entity is teleported to the tank's anchor location and
 * carries no yaw of its own; the part's world offset and rotation are baked into
 * the transformation. This keeps all the orientation logic in one place: if the
 * model ever looks mirrored, flip the signs in {@link #yawQuat}/{@link #pitchQuat}.
 *
 * <p><b>Articulation chain.</b> A part is positioned by a small kinematic chain so
 * each sub-assembly turns about the right axis:
 * <ul>
 *   <li>HULL - fixed in hull space.</li>
 *   <li>TURRET - yaws about the turret ring ({@link TankModel#TURRET_PIVOT}).</li>
 *   <li>BARREL - first elevates about the gun trunnion
 *       ({@link TankModel#BARREL_PIVOT}), then rides the turret yaw about the
 *       turret ring. Doing the yaw about the turret axis (not the gun's own point)
 *       is what keeps the gun's base attached to the turret as it traverses.</li>
 * </ul>
 */
public final class Transforms {

    private Transforms() {
    }

    /** Rotation about the world Y axis matching Minecraft's yaw convention. */
    public static Quaternionf yawQuat(double yawDeg) {
        return new Quaternionf().rotateY((float) Math.toRadians(-yawDeg));
    }

    /** Rotation about the world X axis; positive raises the muzzle. */
    public static Quaternionf pitchQuat(double pitchDeg) {
        return new Quaternionf().rotateX((float) Math.toRadians(pitchDeg));
    }

    /**
     * Maps a point in tank space to a world-space offset from the anchor,
     * respecting which group it moves with. Used for the muzzle tip so the shell
     * spawn point and flash track the articulated barrel exactly.
     */
    public static Vector3f localPointToWorld(Vector3f point, PartGroup group,
                                             double hullYaw, double turretYaw, double barrelPitch) {
        Vector3f local = articulatePosition(new Vector3f(point), group, hullYaw, turretYaw, barrelPitch);
        return yawQuat(hullYaw).transform(local);
    }

    /**
     * Direction (unit world vector) the gun is pointing, from the articulation.
     * Equivalent to a Minecraft direction built from (turretYaw, barrelPitch);
     * independent of any pivot, so it needs no change when the pivots do.
     */
    public static Vector3f barrelDirection(double turretYaw, double barrelPitch) {
        // forward in tank space is +Z; rotate by barrel pitch then turret yaw.
        Vector3f forward = new Vector3f(0f, 0f, 1f);
        pitchQuat(barrelPitch).transform(forward);
        yawQuat(turretYaw).transform(forward);
        return forward.normalize();
    }

    /**
     * Builds the full transformation for a part. The display entity must sit at
     * the tank anchor location with no rotation of its own.
     */
    public static Transformation forPart(TankPart part,
                                         double hullYaw, double turretYaw, double barrelPitch) {
        // Position and orientation of the part in hull-local space (before hull yaw).
        Vector3f localCenter = articulatePosition(new Vector3f(part.offset), part.group,
                hullYaw, turretYaw, barrelPitch);

        // Local rotation = group articulation * the part's own base rotation.
        Quaternionf baseRot = new Quaternionf()
                .rotateY((float) Math.toRadians(-part.yaw))
                .rotateX((float) Math.toRadians(part.pitch))
                .rotateZ((float) Math.toRadians(part.roll));
        Quaternionf localRot = articulateRotation(part.group, hullYaw, turretYaw, barrelPitch)
                .mul(baseRot);

        // Apply hull yaw to reach world space (offset relative to anchor).
        Quaternionf hull = yawQuat(hullYaw);
        Vector3f worldOffset = hull.transform(new Vector3f(localCenter));
        Quaternionf worldRot = new Quaternionf(hull).mul(localRot);

        // Centre the unit cube on its world offset: shift back by the rotated half-extent.
        Vector3f half = new Vector3f(part.scale).mul(0.5f);
        worldRot.transform(half);
        Vector3f translation = new Vector3f(worldOffset).sub(half);

        return new Transformation(translation, worldRot, new Vector3f(part.scale), new Quaternionf());
    }

    /**
     * Position of a tank-space point after its group's articulation, in hull-local
     * space (hull yaw is applied separately). Mutates and returns {@code point}.
     */
    private static Vector3f articulatePosition(Vector3f point, PartGroup group,
                                               double hullYaw, double turretYaw, double barrelPitch) {
        double dYaw = turretYaw - hullYaw;
        switch (group) {
            case HULL -> {
                return point;
            }
            case TURRET -> {
                return rotateAbout(point, TankModel.TURRET_PIVOT, yawQuat(dYaw));
            }
            case BARREL -> {
                // 1) elevate about the gun trunnion, 2) traverse about the turret ring.
                rotateAbout(point, TankModel.BARREL_PIVOT, pitchQuat(barrelPitch));
                return rotateAbout(point, TankModel.TURRET_PIVOT, yawQuat(dYaw));
            }
            default -> {
                return point;
            }
        }
    }

    /** Orientation contributed by a group's articulation (no part base rotation). */
    private static Quaternionf articulateRotation(PartGroup group,
                                                  double hullYaw, double turretYaw, double barrelPitch) {
        double dYaw = turretYaw - hullYaw;
        return switch (group) {
            case HULL -> new Quaternionf();
            case TURRET -> yawQuat(dYaw);
            case BARREL -> yawQuat(dYaw).mul(pitchQuat(barrelPitch));
        };
    }

    /** Rotate {@code point} about {@code pivot} by {@code rot}, in place. */
    private static Vector3f rotateAbout(Vector3f point, Vector3f pivot, Quaternionf rot) {
        point.sub(pivot);
        rot.transform(point);
        return point.add(pivot);
    }
}
