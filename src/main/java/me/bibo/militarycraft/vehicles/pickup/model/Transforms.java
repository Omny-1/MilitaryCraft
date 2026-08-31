package me.bibo.militarycraft.vehicles.pickup.model;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/**
 * Turns a part's fixed position in model space into where it actually is in the world right now.
 *
 * <p>Every part is placed relative to the hull; the gun mount adds the gunner's yaw on top, and the
 * barrel adds pitch on top of that. Both the offset and the orientation are produced by the same
 * hull rotation, on purpose - rotate them separately and the model comes apart at any yaw but zero.
 *
 * <p>The half-extent subtracted at the end is not a fudge: a block display is positioned by its
 * corner, so the centre has to be walked back by half the (already rotated) scale.
 */
public final class Transforms {
    private Transforms() {
    }

    public static Quaternionf yawQuat(double yawDeg) {
        return new Quaternionf().rotateY((float)Math.toRadians(-yawDeg));
    }

    public static Quaternionf pitchQuat(double pitchDeg) {
        return new Quaternionf().rotateX((float)Math.toRadians(pitchDeg));
    }

    public static Vector3f localPointToWorld(Vector3f point, PartGroup group, double hullYaw, double gunYaw, double gunPitch) {
        Vector3f local = articulatePosition(new Vector3f(point), group, hullYaw, gunYaw, gunPitch);
        return yawQuat(hullYaw).transform(local);
    }

    public static Transformation forPart(PickupPart part, double hullYaw, double gunYaw, double gunPitch) {
        return forPart(part, hullYaw, gunYaw, gunPitch, 0.0, 0.0);
    }

    public static Transformation forPart(PickupPart part, double hullYaw, double gunYaw, double gunPitch, double wheelSpinDeg, double wheelSteerDeg) {
        Vector3f localCenter = articulatePosition(new Vector3f(part.offset), part.group, hullYaw, gunYaw, gunPitch);
        float pitch = part.pitch + (part.rollsWithWheel ? (float)wheelSpinDeg : 0.0f);
        float yaw = part.yaw + (part.steersWithWheel ? (float)wheelSteerDeg : 0.0f);
        Quaternionf baseRot = new Quaternionf().rotateY((float)Math.toRadians(-yaw)).rotateX((float)Math.toRadians(pitch)).rotateZ((float)Math.toRadians(part.roll));
        Quaternionf localRot = articulateRotation(part.group, hullYaw, gunYaw, gunPitch).mul(baseRot);
        Quaternionf hull = yawQuat(hullYaw);
        Vector3f worldOffset = hull.transform(new Vector3f(localCenter));
        Quaternionf worldRot = new Quaternionf(hull).mul(localRot);
        Vector3f half = new Vector3f(part.scale).mul(0.5f);
        worldRot.transform(half);
        Vector3f translation = new Vector3f(worldOffset).sub(half);
        return new Transformation(translation, worldRot, new Vector3f(part.scale), new Quaternionf());
    }

    private static Vector3f articulatePosition(Vector3f point, PartGroup group, double hullYaw, double gunYaw, double gunPitch) {
        double dYaw = gunYaw - hullYaw;
        return switch (group) {
            case HULL -> point;
            case MOUNT -> rotateAbout(point, PickupModel.MOUNT_PIVOT, yawQuat(dYaw));
            case BARREL -> {
                rotateAbout(point, PickupModel.BARREL_PIVOT, pitchQuat(gunPitch));
                yield rotateAbout(point, PickupModel.MOUNT_PIVOT, yawQuat(dYaw));
            }
        };
    }

    private static Quaternionf articulateRotation(PartGroup group, double hullYaw, double gunYaw, double gunPitch) {
        double dYaw = gunYaw - hullYaw;
        return switch (group) {
            case HULL -> new Quaternionf();
            case MOUNT -> yawQuat(dYaw);
            case BARREL -> yawQuat(dYaw).mul(pitchQuat(gunPitch));
        };
    }

    private static Vector3f rotateAbout(Vector3f point, Vector3f pivot, Quaternionf rot) {
        point.sub(pivot);
        rot.transform(point);
        return point.add(pivot);
    }
}

