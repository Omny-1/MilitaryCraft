package me.bibo.militarycraft.vehicles.helicopter.model;

import me.bibo.militarycraft.vehicles.helicopter.config.HelicopterConfig;
import org.bukkit.Material;
import org.joml.Vector3f;

/**
 * One immutable piece of the helicopter model. Coordinates are in "helicopter
 * space": origin at the pilot seat, +X right, +Y up, +Z forward (the nose).
 * The whole helicopter is one rigid body, so every part is rotated by the
 * same orientation quaternion each tick (see {@link Transforms}); rotor parts
 * additionally spin about their own local axis on top of that.
 */
public final class Part {

    /** Decides which configured material the part is rendered with. */
    public enum Role {
        BODY, CAMO, GLASS, ROTOR, HUB, GEAR, ENGINE, ACCENT
    }

    /** Which local axis (if any) a part continuously spins about. */
    public enum Spin {
        NONE, X, Y
    }

    public final Role role;
    /** Centre of the part in helicopter space. */
    public final Vector3f offset;
    /** Size of the (unit) block after scaling. */
    public final Vector3f scale;
    /** Base local rotation in degrees, applied before the body orientation. */
    public final float pitch;
    public final float yaw;
    public final float roll;
    /** When non-null this part is a floating TextDisplay (the tail number). */
    public final String text;
    /** NONE for static parts; X/Y for a rotor spinning about that local axis. */
    public final Spin spin;
    /**
     * When true the part pivots at its offset (its min-X corner) instead of its
     * centre - used for rotor blades so the client's position interpolation
     * can't bow the blade off the hub as it spins (it sweeps a clean disc).
     */
    public final boolean radial;

    public Part(Role role, Vector3f offset, Vector3f scale,
                float pitch, float yaw, float roll, String text) {
        this(role, offset, scale, pitch, yaw, roll, text, Spin.NONE, false);
    }

    public Part(Role role, Vector3f offset, Vector3f scale,
                float pitch, float yaw, float roll, String text, Spin spin) {
        this(role, offset, scale, pitch, yaw, roll, text, spin, false);
    }

    public Part(Role role, Vector3f offset, Vector3f scale,
                float pitch, float yaw, float roll, String text, Spin spin, boolean radial) {
        this.role = role;
        this.offset = offset;
        this.scale = scale;
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
        this.text = text;
        this.spin = spin == null ? Spin.NONE : spin;
        this.radial = radial;
    }

    /** Convenience for a solid block part with no base rotation. */
    public static Part block(Role role, Vector3f offset, Vector3f scale) {
        return new Part(role, offset, scale, 0, 0, 0, null, Spin.NONE);
    }

    /** Convenience for a solid block part with a base rotation. */
    public static Part block(Role role, Vector3f offset, Vector3f scale,
                             float pitch, float yaw, float roll) {
        return new Part(role, offset, scale, pitch, yaw, roll, null, Spin.NONE);
    }

    /** Main-rotor blade: spins about local Y (blades sweep horizontally). */
    public static Part rotorY(Role role, Vector3f offset, Vector3f scale, float baseYaw) {
        return new Part(role, offset, scale, 0, baseYaw, 0, null, Spin.Y);
    }

    /**
     * Main-rotor blade that PIVOTS AT THE HUB and extends +X (radially),
     * spinning about local Y. Pivoting at the hub (not the bar's centre) keeps
     * the client's position interpolation from bowing the blade off the hub as
     * it spins, so it sweeps a clean disc. {@code baseYaw} spaces the blades.
     */
    public static Part blade(Role role, Vector3f hub, float radius, float thickY, float thickZ, float baseYaw) {
        return new Part(role, hub, new Vector3f(radius, thickY, thickZ), 0, baseYaw, 0, null, Spin.Y, true);
    }

    /**
     * Tail-rotor blade that PIVOTS AT THE HUB and extends +Y (radially),
     * spinning about local X so it sweeps a disc facing sideways. Same anti-wobble
     * trick as {@link #blade}. {@code baseAngle} (deg about X) spaces the blades.
     */
    public static Part bladeX(Role role, Vector3f hub, float radius, float thickX, float thickZ, float baseAngle) {
        return new Part(role, hub, new Vector3f(thickX, radius, thickZ), baseAngle, 0, 0, null, Spin.X, true);
    }

    /** Tail-rotor blade: spins about local X (disc faces sideways). */
    public static Part rotorX(Role role, Vector3f offset, Vector3f scale) {
        return new Part(role, offset, scale, 0, 0, 0, null, Spin.X);
    }

    public boolean isText() {
        return text != null;
    }

    public boolean isRotor() {
        return spin != Spin.NONE;
    }

    public Material material(HelicopterConfig cfg) {
        return switch (role) {
            case BODY -> cfg.bodyBlock;
            case CAMO -> cfg.camoBlock;
            case GLASS -> cfg.glassBlock;
            case ROTOR -> cfg.rotorBlock;
            case HUB -> cfg.hubBlock;
            case GEAR -> cfg.gearBlock;
            case ENGINE -> cfg.engineBlock;
            case ACCENT -> cfg.accentBlock;
        };
    }
}
