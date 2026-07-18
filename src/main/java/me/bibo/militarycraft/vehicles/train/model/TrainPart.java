package me.bibo.militarycraft.vehicles.train.model;

import org.bukkit.Material;
import org.joml.Vector3f;

/**
 * One immutable piece of a car model, in "car space": origin at the centre of
 * the car on the rail surface, +X right, +Y up, +Z forward (direction of
 * travel). Base rotations are in degrees; positive pitch tilts the +Z edge
 * DOWN (JOML rotateX convention).
 *
 * <p>Most parts are static ({@link Anim#NONE}); a few animate every tick,
 * driven by how far the train has rolled (see {@link CarTransforms.WheelPhases}):
 * <ul>
 *   <li>{@link Anim#WHEEL} — spins about its own local X axis (the axle),
 *       angle = distance/radius, exactly like a rolling wheel.</li>
 *   <li>{@link Anim#ROD} — a coupling/side rod: translates (without rotating)
 *       in a small circle in the local Y-Z plane, matching where the crank
 *       pin it's pinned to would be on the driving wheel.</li>
 *   <li>{@link Anim#PISTON} — a piston/valve rod: slides back and forth along
 *       local Z only (Scotch-yoke approximation of the crank).</li>
 * </ul>
 */
public final class TrainPart {

    public enum Anim {NONE, WHEEL, ROD, PISTON}

    /** Which rolling-distance counter drives this part's animation. */
    public enum PhaseChannel {NONE, DRIVER, LEADING, BOGIE}

    public final Material material;
    /** Centre of the part in car space (rest position, before animation). */
    public final Vector3f offset;
    /** Size of the (unit) block after scaling. */
    public final Vector3f scale;
    public final float pitch;
    public final float yaw;
    public final float roll;

    public final Anim anim;
    public final PhaseChannel channel;
    /** Wheel radius (WHEEL) or crank throw (ROD/PISTON), in blocks. */
    public final float animRadius;
    /** Extra phase offset in degrees — used to "quarter" left/right cranks. */
    public final float animPhaseOffsetDeg;

    public TrainPart(Material material, Vector3f offset, Vector3f scale,
                     float pitch, float yaw, float roll,
                     Anim anim, PhaseChannel channel, float animRadius, float animPhaseOffsetDeg) {
        this.material = material;
        this.offset = offset;
        this.scale = scale;
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
        this.anim = anim;
        this.channel = channel;
        this.animRadius = animRadius;
        this.animPhaseOffsetDeg = animPhaseOffsetDeg;
    }

    /** Convenience for a solid block part with no base rotation. */
    public static TrainPart p(Material m, double x, double y, double z,
                              double sx, double sy, double sz) {
        return new TrainPart(m, new Vector3f((float) x, (float) y, (float) z),
                new Vector3f((float) sx, (float) sy, (float) sz), 0f, 0f, 0f,
                Anim.NONE, PhaseChannel.NONE, 0f, 0f);
    }

    /** Convenience for a solid block part with a base rotation. */
    public static TrainPart rot(Material m, double x, double y, double z,
                                double sx, double sy, double sz,
                                double pitch, double yaw, double roll) {
        return new TrainPart(m, new Vector3f((float) x, (float) y, (float) z),
                new Vector3f((float) sx, (float) sy, (float) sz),
                (float) pitch, (float) yaw, (float) roll,
                Anim.NONE, PhaseChannel.NONE, 0f, 0f);
    }

    /** A wheel/hub facet that spins about local X as the train rolls. */
    public static TrainPart wheel(Material m, double x, double y, double z,
                                  double sx, double sy, double sz, float basePitch,
                                  PhaseChannel channel, float radius) {
        return new TrainPart(m, new Vector3f((float) x, (float) y, (float) z),
                new Vector3f((float) sx, (float) sy, (float) sz),
                basePitch, 0f, 0f, Anim.WHEEL, channel, radius, 0f);
    }

    /** A coupling/side rod that orbits in the Y-Z plane, in phase with a wheel. */
    public static TrainPart rod(Material m, double x, double y, double z,
                                double sx, double sy, double sz,
                                PhaseChannel channel, float crankRadius, float phaseOffsetDeg) {
        return new TrainPart(m, new Vector3f((float) x, (float) y, (float) z),
                new Vector3f((float) sx, (float) sy, (float) sz),
                0f, 0f, 0f, Anim.ROD, channel, crankRadius, phaseOffsetDeg);
    }

    /** A piston/valve rod that slides back and forth along local Z. */
    public static TrainPart piston(Material m, double x, double y, double z,
                                   double sx, double sy, double sz,
                                   PhaseChannel channel, float throwDist, float phaseOffsetDeg) {
        return new TrainPart(m, new Vector3f((float) x, (float) y, (float) z),
                new Vector3f((float) sx, (float) sy, (float) sz),
                0f, 0f, 0f, Anim.PISTON, channel, throwDist, phaseOffsetDeg);
    }

    public boolean animated() {
        return anim != Anim.NONE;
    }
}
