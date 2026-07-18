/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.util.Transformation
 *  org.joml.Quaternionf
 *  org.joml.Quaternionfc
 *  org.joml.Vector3f
 *  org.joml.Vector3fc
 */
package me.bibo.militarycraft.vehicles.pickup.model;

import me.bibo.militarycraft.vehicles.pickup.model.PartGroup;
import me.bibo.militarycraft.vehicles.pickup.model.PickupModel;
import me.bibo.militarycraft.vehicles.pickup.model.PickupPart;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

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
        Vector3f local = Transforms.articulatePosition(new Vector3f((Vector3fc)point), group, hullYaw, gunYaw, gunPitch);
        return Transforms.yawQuat(hullYaw).transform(local);
    }

    public static Transformation forPart(PickupPart part, double hullYaw, double gunYaw, double gunPitch) {
        return Transforms.forPart(part, hullYaw, gunYaw, gunPitch, 0.0, 0.0);
    }

    public static Transformation forPart(PickupPart part, double hullYaw, double gunYaw, double gunPitch, double wheelSpinDeg, double wheelSteerDeg) {
        Vector3f localCenter = Transforms.articulatePosition(new Vector3f((Vector3fc)part.offset), part.group, hullYaw, gunYaw, gunPitch);
        float pitch = part.pitch + (part.rollsWithWheel ? (float)wheelSpinDeg : 0.0f);
        float yaw = part.yaw + (part.steersWithWheel ? (float)wheelSteerDeg : 0.0f);
        Quaternionf baseRot = new Quaternionf().rotateY((float)Math.toRadians(-yaw)).rotateX((float)Math.toRadians(pitch)).rotateZ((float)Math.toRadians(part.roll));
        Quaternionf localRot = Transforms.articulateRotation(part.group, hullYaw, gunYaw, gunPitch).mul((Quaternionfc)baseRot);
        Quaternionf hull = Transforms.yawQuat(hullYaw);
        Vector3f worldOffset = hull.transform(new Vector3f((Vector3fc)localCenter));
        Quaternionf worldRot = new Quaternionf((Quaternionfc)hull).mul((Quaternionfc)localRot);
        Vector3f half = new Vector3f((Vector3fc)part.scale).mul(0.5f);
        worldRot.transform(half);
        Vector3f translation = new Vector3f((Vector3fc)worldOffset).sub((Vector3fc)half);
        return new Transformation(translation, worldRot, new Vector3f((Vector3fc)part.scale), new Quaternionf());
    }

    private static Vector3f articulatePosition(Vector3f point, PartGroup group, double hullYaw, double gunYaw, double gunPitch) {
        double dYaw = gunYaw - hullYaw;
        return switch (group) {
            default -> throw new MatchException(null, null);
            case PartGroup.HULL -> point;
            case PartGroup.MOUNT -> Transforms.rotateAbout(point, PickupModel.MOUNT_PIVOT, Transforms.yawQuat(dYaw));
            case PartGroup.BARREL -> {
                Transforms.rotateAbout(point, PickupModel.BARREL_PIVOT, Transforms.pitchQuat(gunPitch));
                yield Transforms.rotateAbout(point, PickupModel.MOUNT_PIVOT, Transforms.yawQuat(dYaw));
            }
        };
    }

    private static Quaternionf articulateRotation(PartGroup group, double hullYaw, double gunYaw, double gunPitch) {
        double dYaw = gunYaw - hullYaw;
        return switch (group) {
            default -> throw new MatchException(null, null);
            case PartGroup.HULL -> new Quaternionf();
            case PartGroup.MOUNT -> Transforms.yawQuat(dYaw);
            case PartGroup.BARREL -> Transforms.yawQuat(dYaw).mul((Quaternionfc)Transforms.pitchQuat(gunPitch));
        };
    }

    private static Vector3f rotateAbout(Vector3f point, Vector3f pivot, Quaternionf rot) {
        point.sub((Vector3fc)pivot);
        rot.transform(point);
        return point.add((Vector3fc)pivot);
    }
}

