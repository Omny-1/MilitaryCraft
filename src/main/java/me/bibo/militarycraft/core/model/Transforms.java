package me.bibo.militarycraft.core.model;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pure math primitives for turning a vehicle's own articulation angles into a
 * display {@link Transformation} (§5.5). Deliberately PRIMITIVES ONLY — no
 * per-group dispatch (no HULL/TURRET/BARREL switch) lives here; each vehicle's own
 * package composes these into its {@code transformFor(Part)} using whatever angles
 * it owns (tank: hullYaw/turretYaw/barrelPitch; jet: yaw/pitch/roll as one rigid
 * body; kamaz: hullYaw only; ...).
 */
public final class Transforms {

    private Transforms() {
    }

    /** Rotation about the world Y axis matching Minecraft's yaw convention. */
    public static Quaternionf yawQuat(double yawDeg) {
        return new Quaternionf().rotateY((float) Math.toRadians(-yawDeg));
    }

    /** Rotation about the world X axis; positive raises the nose/muzzle (Minecraft pitch convention). */
    public static Quaternionf pitchQuat(double pitchDeg) {
        return new Quaternionf().rotateX((float) Math.toRadians(pitchDeg));
    }

    /** Rotation about the world Z axis; positive banks right. */
    public static Quaternionf rollQuat(double rollDeg) {
        return new Quaternionf().rotateZ((float) Math.toRadians(rollDeg));
    }

    /** Rotate {@code point} about {@code pivot} by {@code rot}, in place. Returns {@code point}. */
    public static Vector3f rotateAbout(Vector3f point, Vector3f pivot, Quaternionf rot) {
        point.sub(pivot);
        rot.transform(point);
        return point.add(pivot);
    }

    /** Maps a vehicle-local point to a world-space offset from the anchor, given only a hull yaw. */
    public static Vector3f localPointToWorld(Vector3f point, double hullYaw) {
        return yawQuat(hullYaw).transform(new Vector3f(point));
    }

    /** Maps a vehicle-local point to a world-space offset from the anchor, given a full orientation. */
    public static Vector3f localPointToWorld(Vector3f point, Quaternionf orientation) {
        return orientation.transform(new Vector3f(point));
    }

    /**
     * Builds the display {@link Transformation} for a part once the caller (the owning
     * vehicle) has already worked out where the part's centre sits in world space
     * ({@code worldOffset}, relative to the anchor the display entity is teleported to)
     * and how it's oriented ({@code worldRot}). Centres the unit cube on that offset —
     * the same final step as TankCraft's {@code Transforms.forPart} — so the display
     * entity itself always sits at the anchor with no rotation of its own.
     */
    public static Transformation build(Part part, Vector3f worldOffset, Quaternionf worldRot) {
        Quaternionf rot = new Quaternionf(worldRot);
        Vector3f half = new Vector3f(part.scale).mul(0.5f);
        rot.transform(half);
        Vector3f translation = new Vector3f(worldOffset).sub(half);
        return new Transformation(translation, rot, new Vector3f(part.scale), new Quaternionf());
    }
}
