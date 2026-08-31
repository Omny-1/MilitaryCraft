package me.bibo.militarycraft.vehicles.pickup.model;

import me.bibo.militarycraft.vehicles.pickup.config.PickupConfig;
import org.bukkit.Material;
import org.joml.Vector3f;

/**
 * One block-display part of the model: where it sits, how big it is, and whether it spins or steers
 * with the wheels.
 */
public final class PickupPart {
    public final PartGroup group;
    public final Role role;
    public final Vector3f offset;
    public final Vector3f scale;
    public final float pitch;
    public final float yaw;
    public final float roll;
    public final boolean rollsWithWheel;
    public final boolean steersWithWheel;

    public PickupPart(PartGroup group, Role role, Vector3f offset, Vector3f scale, float pitch, float yaw, float roll) {
        this(group, role, offset, scale, pitch, yaw, roll, false, false);
    }

    private PickupPart(PartGroup group, Role role, Vector3f offset, Vector3f scale, float pitch, float yaw, float roll, boolean rollsWithWheel, boolean steersWithWheel) {
        this.group = group;
        this.role = role;
        this.offset = offset;
        this.scale = scale;
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
        this.rollsWithWheel = rollsWithWheel;
        this.steersWithWheel = steersWithWheel;
    }

    public static PickupPart block(PartGroup g, Role role, Vector3f offset, Vector3f scale) {
        return new PickupPart(g, role, offset, scale, 0.0f, 0.0f, 0.0f);
    }

    public static PickupPart block(PartGroup g, Role role, Vector3f offset, Vector3f scale, float pitch, float yaw, float roll) {
        return new PickupPart(g, role, offset, scale, pitch, yaw, roll);
    }

    public static PickupPart rolling(PartGroup g, Role role, Vector3f offset, Vector3f scale, float pitch, float yaw, float roll) {
        return new PickupPart(g, role, offset, scale, pitch, yaw, roll, true, false);
    }

    public static PickupPart steering(PartGroup g, Role role, Vector3f offset, Vector3f scale, float pitch, float yaw, float roll) {
        return new PickupPart(g, role, offset, scale, pitch, yaw, roll, true, true);
    }

    public Material material(PickupConfig cfg) {
        return switch (role) {
            case HULL -> cfg.hullBlock;
            case FRAME -> cfg.frameBlock;
            case DETAIL -> cfg.detailBlock;
            case SEAT -> cfg.seatBlock;
            case WHEEL -> cfg.wheelBlock;
            case LIGHT -> cfg.lightBlock;
            case MOUNT -> cfg.mountBlock;
            case BARREL -> cfg.barrelBlock;
        };
    }

    /** What a part is made of; the config names the block for each. */
    public enum Role {
        HULL,
        FRAME,
        DETAIL,
        SEAT,
        WHEEL,
        LIGHT,
        MOUNT,
        BARREL;

    }
}

