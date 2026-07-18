package me.bibo.militarycraft.vehicles.aircraft;

import me.bibo.militarycraft.core.model.Part;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Math shared by the aircraft family: one rigid yaw/pitch/roll body plus optional local spin. */
public final class AircraftTransforms {

    public enum SpinAxis {
        NONE, X, Y, Z
    }

    private AircraftTransforms() {
    }

    public static Quaternionf orientation(double yawDeg, double pitchDeg, double rollDeg) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(-yawDeg))
                .rotateX((float) Math.toRadians(pitchDeg))
                .rotateZ((float) Math.toRadians(rollDeg));
    }

    public static Vector3f forward(Quaternionf orientation) {
        return orientation.transform(new Vector3f(0f, 0f, 1f));
    }

    public static Transformation part(Part part, Quaternionf orientation) {
        return part(part, orientation, SpinAxis.NONE, 0f, false);
    }

    public static Transformation part(Part part, Quaternionf orientation, SpinAxis spinAxis,
                                      float spinDeg, boolean radial) {
        Vector3f scale = new Vector3f(part.scale);
        Quaternionf baseRot = new Quaternionf()
                .rotateY((float) Math.toRadians(-part.yaw))
                .rotateX((float) Math.toRadians(part.pitch))
                .rotateZ((float) Math.toRadians(part.roll));
        switch (spinAxis == null ? SpinAxis.NONE : spinAxis) {
            case X -> baseRot.rotateX((float) Math.toRadians(spinDeg));
            case Y -> baseRot.rotateY((float) Math.toRadians(spinDeg));
            case Z -> baseRot.rotateZ((float) Math.toRadians(spinDeg));
            case NONE -> {
            }
        }
        Quaternionf worldRot = new Quaternionf(orientation).mul(baseRot);
        Vector3f worldOffset = orientation.transform(new Vector3f(part.offset));
        Vector3f half;
        if (radial && spinAxis == SpinAxis.X) {
            half = new Vector3f(scale.x * 0.5f, 0f, scale.z * 0.5f);
        } else if (radial && spinAxis == SpinAxis.Y) {
            half = new Vector3f(0f, scale.y * 0.5f, scale.z * 0.5f);
        } else {
            half = new Vector3f(scale).mul(0.5f);
        }
        worldRot.transform(half);
        return new Transformation(new Vector3f(worldOffset).sub(half), worldRot,
                new Vector3f(part.scale), new Quaternionf());
    }
}
