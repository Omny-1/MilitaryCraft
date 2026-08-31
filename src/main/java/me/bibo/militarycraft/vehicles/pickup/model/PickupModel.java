package me.bibo.militarycraft.vehicles.pickup.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.joml.Vector3f;

/**
 * The pickup's shape: every display part, its offset, size and orientation, plus the hitbox layout.
 *
 * <p>This is a table, not logic. Changing what the vehicle looks like happens here and nowhere else.
 */
public final class PickupModel {
    public static final Vector3f MOUNT_PIVOT = new Vector3f(0.0f, 2.325f, -1.05f);
    public static final Vector3f BARREL_PIVOT = new Vector3f(0.0f, 2.325f, -0.55f);
    public static final Vector3f MUZZLE_TIP = new Vector3f(0.0f, 2.325f, 1.04f);
    public static final float WIDTH = 2.7f;
    public static final float LENGTH = 6.4f;
    public static final float HEIGHT = 3.3f;
    public static final float WHEEL_RADIUS = 0.675f;
    public static final Vector3f DRIVER_SEAT_XZ = new Vector3f(-0.5f, 0.0f, 0.55f);
    public static final Vector3f PASSENGER_SEAT_XZ = new Vector3f(0.5f, 0.0f, 0.55f);
    public static final Vector3f GUNNER_SEAT_XZ = new Vector3f(0.0f, 0.0f, -1.4f);
    public static final float[][] HITBOX_LOCAL = new float[][]{{-0.68f, 1.4f, 1.3f}, {0.0f, -1.4f, 2.8f}, {0.68f, 1.4f, 1.3f}};
    public static final float HITBOX_HEIGHT = 3.9f;
    private static final float OCT = 0.85f;
    private static List<PickupPart> cached;

    private PickupModel() {
    }

    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }

    private static void octX(List<PickupPart> p, PartGroup g, PickupPart.Role role, Vector3f off, float thickness, float diameter) {
        octX(p, g, role, off, thickness, diameter, false, false);
    }

    private static void octXRolling(List<PickupPart> p, PartGroup g, PickupPart.Role role, Vector3f off, float thickness, float diameter) {
        octX(p, g, role, off, thickness, diameter, true, false);
    }

    private static void octXSteering(List<PickupPart> p, PartGroup g, PickupPart.Role role, Vector3f off, float thickness, float diameter) {
        octX(p, g, role, off, thickness, diameter, true, true);
    }

    private static void octX(List<PickupPart> p, PartGroup g, PickupPart.Role role, Vector3f off, float thickness, float diameter, boolean rolling, boolean steering) {
        p.add(wheelAwarePart(g, role, off, v(thickness, diameter, diameter), 0.0f, 0.0f, 0.0f, rolling, steering));
        float od = diameter * 0.85f;
        p.add(wheelAwarePart(g, role, off, v(thickness, od, od), 45.0f, 0.0f, 0.0f, rolling, steering));
    }

    private static PickupPart wheelAwarePart(PartGroup g, PickupPart.Role role, Vector3f off, Vector3f scale, float pitch, float yaw, float roll, boolean rolling, boolean steering) {
        if (steering) {
            return PickupPart.steering(g, role, off, scale, pitch, yaw, roll);
        }
        if (rolling) {
            return PickupPart.rolling(g, role, off, scale, pitch, yaw, roll);
        }
        return PickupPart.block(g, role, off, scale, pitch, yaw, roll);
    }

    private static void octY(List<PickupPart> p, PartGroup g, PickupPart.Role role, Vector3f off, float diameter, float thickness) {
        p.add(PickupPart.block(g, role, off, v(diameter, thickness, diameter)));
        float od = diameter * 0.85f;
        p.add(PickupPart.block(g, role, off, v(od, thickness, od), 0.0f, 45.0f, 0.0f));
    }

    private static void octZ(List<PickupPart> p, PartGroup g, PickupPart.Role role, Vector3f off, float diameter, float length) {
        p.add(PickupPart.block(g, role, off, v(diameter, diameter, length)));
        float od = diameter * 0.85f;
        p.add(PickupPart.block(g, role, off, v(od, od, length), 0.0f, 0.0f, 45.0f));
    }

    public static synchronized List<PickupPart> parts() {
        if (cached != null) {
            return cached;
        }
        ArrayList<PickupPart> p = new ArrayList<>();
        PartGroup H = PartGroup.HULL;
        PartGroup M = PartGroup.MOUNT;
        PartGroup B = PartGroup.BARREL;
        PickupPart.Role HULL = PickupPart.Role.HULL;
        PickupPart.Role FRAME = PickupPart.Role.FRAME;
        PickupPart.Role DETAIL = PickupPart.Role.DETAIL;
        PickupPart.Role SEAT = PickupPart.Role.SEAT;
        PickupPart.Role WHEEL = PickupPart.Role.WHEEL;
        PickupPart.Role LIGHT = PickupPart.Role.LIGHT;
        PickupPart.Role MOUNT = PickupPart.Role.MOUNT;
        PickupPart.Role BARREL = PickupPart.Role.BARREL;
        p.add(PickupPart.block(H, HULL, v(0.0f, 0.75f, 0.4f), v(1.8f, 0.45f, 5.6f)));
        p.add(PickupPart.block(H, HULL, v(-1.0f, 1.35f, 0.4f), v(0.16f, 0.75f, 5.3f)));
        p.add(PickupPart.block(H, HULL, v(1.0f, 1.35f, 0.4f), v(0.16f, 0.75f, 5.3f)));
        p.add(PickupPart.block(H, HULL, v(0.0f, 1.35f, -2.45f), v(1.85f, 0.75f, 0.14f)));
        p.add(PickupPart.block(H, HULL, v(0.0f, 1.5f, 2.4f), v(1.65f, 0.45f, 2.0f)));
        p.add(PickupPart.block(H, HULL, v(0.0f, 1.575f, 1.3f), v(1.65f, 0.3f, 0.2f)));
        p.add(PickupPart.block(H, HULL, v(0.0f, 1.5f, -1.15f), v(1.65f, 0.33f, 1.5f)));
        p.add(PickupPart.block(H, HULL, v(-1.05f, 1.425f, 1.6f), v(0.55f, 0.48f, 1.05f)));
        p.add(PickupPart.block(H, HULL, v(1.05f, 1.425f, 1.6f), v(0.55f, 0.48f, 1.05f)));
        p.add(PickupPart.block(H, HULL, v(-1.05f, 1.425f, -1.6f), v(0.55f, 0.48f, 1.05f)));
        p.add(PickupPart.block(H, HULL, v(1.05f, 1.425f, -1.6f), v(0.55f, 0.48f, 1.05f)));
        p.add(PickupPart.block(H, FRAME, v(-1.0f, 1.7f, 0.4f), v(0.18f, 0.06f, 5.2f)));
        p.add(PickupPart.block(H, FRAME, v(1.0f, 1.7f, 0.4f), v(0.18f, 0.06f, 5.2f)));
        p.add(PickupPart.block(H, HULL, v(0.0f, 1.02f, -1.5f), v(1.5f, 0.09f, 1.0f)));
        p.add(PickupPart.block(H, FRAME, v(0.0f, 0.675f, -2.55f), v(2.0f, 0.3f, 0.18f)));
        p.add(PickupPart.block(H, FRAME, v(-0.8f, 0.45f, 0.4f), v(0.16f, 0.24f, 5.9f)));
        p.add(PickupPart.block(H, FRAME, v(0.8f, 0.45f, 0.4f), v(0.16f, 0.24f, 5.9f)));
        p.add(PickupPart.block(H, FRAME, v(0.0f, 1.575f, 3.48f), v(0.95f, 0.18f, 0.12f)));
        p.add(PickupPart.block(H, FRAME, v(0.0f, 1.35f, 3.48f), v(0.95f, 0.18f, 0.12f)));
        p.add(PickupPart.block(H, FRAME, v(0.0f, 1.125f, 3.48f), v(0.95f, 0.18f, 0.12f)));
        p.add(PickupPart.block(H, FRAME, v(0.0f, 1.71f, 3.48f), v(1.05f, 0.06f, 0.1f)));
        p.add(PickupPart.block(H, FRAME, v(0.0f, 0.99f, 3.48f), v(1.05f, 0.06f, 0.1f)));
        p.add(PickupPart.block(H, FRAME, v(-0.5f, 1.35f, 3.48f), v(0.06f, 0.78f, 0.1f)));
        p.add(PickupPart.block(H, FRAME, v(0.5f, 1.35f, 3.48f), v(0.06f, 0.78f, 0.1f)));
        p.add(PickupPart.block(H, FRAME, v(-1.15f, 0.675f, 0.0f), v(0.14f, 0.12f, 2.6f)));
        p.add(PickupPart.block(H, FRAME, v(1.15f, 0.675f, 0.0f), v(0.14f, 0.12f, 2.6f)));
        p.add(PickupPart.block(H, FRAME, v(-0.75f, 2.1f, 1.55f), v(0.1f, 0.9f, 0.1f)));
        p.add(PickupPart.block(H, FRAME, v(0.75f, 2.1f, 1.55f), v(0.1f, 0.9f, 0.1f)));
        p.add(PickupPart.block(H, FRAME, v(0.0f, 2.55f, 1.55f), v(1.65f, 0.15f, 0.08f)));
        octZ(p, H, FRAME, v(-0.82f, 2.475f, 1.62f), 0.12f, 0.04f);
        octZ(p, H, FRAME, v(0.82f, 2.475f, 1.62f), 0.12f, 0.04f);
        octZ(p, H, FRAME, v(-0.5f, 1.725f, 1.0f), 0.34f, 0.06f);
        p.add(PickupPart.block(H, FRAME, v(-0.82f, 2.325f, 1.58f), v(0.04f, 0.15f, 0.04f)));
        p.add(PickupPart.block(H, FRAME, v(0.82f, 2.325f, 1.58f), v(0.04f, 0.15f, 0.04f)));
        p.add(PickupPart.block(H, FRAME, v(1.1f, 2.2f, -1.55f), v(0.05f, 1.1f, 0.05f)));
        octX(p, H, FRAME, v(-0.95f, 0.45f, -2.84f), 0.4f, 0.14f);
        p.add(PickupPart.block(H, FRAME, v(-1.1f, 1.425f, 0.3f), v(0.05f, 0.09f, 0.22f)));
        p.add(PickupPart.block(H, FRAME, v(1.1f, 1.425f, 0.3f), v(0.05f, 0.09f, 0.22f)));
        octXSteering(p, H, FRAME, v(-1.28f, 0.675f, 1.6f), 0.06f, 0.48f);
        octXSteering(p, H, FRAME, v(1.28f, 0.675f, 1.6f), 0.06f, 0.48f);
        octXRolling(p, H, FRAME, v(-1.28f, 0.675f, -1.6f), 0.06f, 0.48f);
        octXRolling(p, H, FRAME, v(1.28f, 0.675f, -1.6f), 0.06f, 0.48f);
        p.add(PickupPart.block(H, FRAME, v(0.0f, 0.7f, 3.68f), v(2.3f, 0.38f, 0.26f)));
        p.add(PickupPart.block(H, FRAME, v(0.0f, 0.42f, 3.62f), v(2.0f, 0.2f, 0.18f)));
        p.add(PickupPart.block(H, FRAME, v(-0.8f, 0.5f, 3.45f), v(0.12f, 0.15f, 0.3f)));
        p.add(PickupPart.block(H, FRAME, v(0.8f, 0.5f, 3.45f), v(0.12f, 0.15f, 0.3f)));
        p.add(PickupPart.block(H, FRAME, v(-0.55f, 1.1f, 3.75f), v(0.08f, 0.7f, 0.08f)));
        p.add(PickupPart.block(H, FRAME, v(0.55f, 1.1f, 3.75f), v(0.08f, 0.7f, 0.08f)));
        p.add(PickupPart.block(H, FRAME, v(0.0f, 1.45f, 3.75f), v(1.2f, 0.08f, 0.08f)));
        p.add(PickupPart.block(H, FRAME, v(0.0f, 0.45f, 3.82f), v(0.18f, 0.12f, 0.18f)));
        p.add(PickupPart.block(H, FRAME, v(0.0f, 0.45f, -2.7f), v(0.18f, 0.12f, 0.18f)));
        p.add(PickupPart.block(H, FRAME, v(-0.7f, 1.72f, -2.45f), v(0.12f, 0.06f, 0.1f)));
        p.add(PickupPart.block(H, FRAME, v(0.7f, 1.72f, -2.45f), v(0.12f, 0.06f, 0.1f)));
        p.add(PickupPart.block(H, FRAME, v(-1.0f, 0.9f, 0.4f), v(0.2f, 0.15f, 5.3f)));
        p.add(PickupPart.block(H, FRAME, v(1.0f, 0.9f, 0.4f), v(0.2f, 0.15f, 5.3f)));
        p.add(PickupPart.block(H, FRAME, v(-1.05f, 1.15f, 1.6f), v(0.58f, 0.05f, 1.08f)));
        p.add(PickupPart.block(H, FRAME, v(1.05f, 1.15f, 1.6f), v(0.58f, 0.05f, 1.08f)));
        p.add(PickupPart.block(H, FRAME, v(-1.05f, 1.15f, -1.6f), v(0.58f, 0.05f, 1.08f)));
        p.add(PickupPart.block(H, FRAME, v(1.05f, 1.15f, -1.6f), v(0.58f, 0.05f, 1.08f)));
        p.add(PickupPart.block(H, SEAT, v(-0.5f, 1.23f, 0.55f), v(0.55f, 0.45f, 0.5f)));
        p.add(PickupPart.block(H, SEAT, v(-0.5f, 1.68f, 0.32f), v(0.55f, 0.75f, 0.14f)));
        p.add(PickupPart.block(H, SEAT, v(-0.5f, 2.13f, 0.28f), v(0.4f, 0.33f, 0.14f)));
        p.add(PickupPart.block(H, SEAT, v(0.5f, 1.23f, 0.55f), v(0.55f, 0.45f, 0.5f)));
        p.add(PickupPart.block(H, SEAT, v(0.5f, 1.68f, 0.32f), v(0.55f, 0.75f, 0.14f)));
        p.add(PickupPart.block(H, SEAT, v(0.5f, 2.13f, 0.28f), v(0.4f, 0.33f, 0.14f)));
        p.add(PickupPart.block(H, FRAME, v(0.0f, 1.535f, -1.4f), v(0.16f, 0.95f, 0.16f)));
        p.add(PickupPart.block(H, SEAT, v(0.0f, 2.1f, -1.4f), v(0.5f, 0.2f, 0.5f)));
        p.add(PickupPart.block(H, SEAT, v(0.0f, 2.5f, -1.65f), v(0.36f, 0.5f, 0.14f)));
        p.add(PickupPart.block(H, DETAIL, v(-1.12f, 1.35f, -2.05f), v(0.26f, 0.6f, 0.22f)));
        p.add(PickupPart.block(H, DETAIL, v(1.12f, 1.35f, -2.05f), v(0.26f, 0.6f, 0.22f)));
        p.add(PickupPart.block(H, DETAIL, v(1.0f, 1.275f, 0.85f), v(0.24f, 0.3f, 0.5f)));
        octXSteering(p, H, WHEEL, v(-1.05f, 0.675f, 1.6f), 0.42f, 1.35f);
        octXSteering(p, H, WHEEL, v(1.05f, 0.675f, 1.6f), 0.42f, 1.35f);
        octXRolling(p, H, WHEEL, v(-1.05f, 0.675f, -1.6f), 0.42f, 1.35f);
        octXRolling(p, H, WHEEL, v(1.05f, 0.675f, -1.6f), 0.42f, 1.35f);
        octZ(p, H, WHEEL, v(0.0f, 1.55f, -2.8f), 1.3f, 0.32f);
        p.add(PickupPart.block(H, FRAME, v(0.0f, 2.05f, -2.58f), v(0.5f, 0.08f, 0.14f)));
        p.add(PickupPart.block(H, FRAME, v(0.0f, 1.05f, -2.58f), v(0.5f, 0.08f, 0.14f)));
        p.add(PickupPart.block(H, FRAME, v(0.0f, 1.55f, -2.62f), v(0.1f, 0.1f, 0.1f)));
        octZ(p, H, LIGHT, v(-0.62f, 1.5f, 3.56f), 0.22f, 0.1f);
        octZ(p, H, LIGHT, v(0.62f, 1.5f, 3.56f), 0.22f, 0.1f);
        p.add(PickupPart.block(H, FRAME, v(-0.62f, 1.5f, 3.5f), v(0.3f, 0.3f, 0.06f)));
        p.add(PickupPart.block(H, FRAME, v(0.62f, 1.5f, 3.5f), v(0.3f, 0.3f, 0.06f)));
        octZ(p, H, LIGHT, v(-0.88f, 1.47f, 3.53f), 0.12f, 0.08f);
        octZ(p, H, LIGHT, v(0.88f, 1.47f, 3.53f), 0.12f, 0.08f);
        octZ(p, H, LIGHT, v(-0.75f, 1.425f, -2.6f), 0.18f, 0.1f);
        octZ(p, H, LIGHT, v(0.75f, 1.425f, -2.6f), 0.18f, 0.1f);
        octY(p, M, MOUNT, v(0.0f, 1.1f, -1.05f), 0.22f, 0.27f);
        octY(p, M, MOUNT, v(0.0f, 1.875f, -1.05f), 0.16f, 1.275f);
        octY(p, M, MOUNT, v(0.0f, 1.5f, -1.05f), 0.5f, 0.15f);
        p.add(PickupPart.block(M, MOUNT, v(-0.22f, 2.325f, -0.65f), v(0.08f, 0.35f, 0.45f)));
        p.add(PickupPart.block(M, MOUNT, v(0.22f, 2.325f, -0.65f), v(0.08f, 0.35f, 0.45f)));
        p.add(PickupPart.block(M, MOUNT, v(0.32f, 2.58f, -1.0f), v(0.3f, 0.39f, 0.24f)));
        p.add(PickupPart.block(M, MOUNT, v(0.27f, 2.45f, -0.85f), v(0.08f, 0.06f, 0.45f)));
        p.add(PickupPart.block(M, MOUNT, v(0.0f, 2.58f, -0.88f), v(0.65f, 0.6f, 0.06f)));
        octZ(p, B, BARREL, v(0.0f, 2.325f, -0.25f), 0.2f, 0.85f);
        octZ(p, B, BARREL, v(0.0f, 2.325f, 0.4f), 0.11f, 1.1f);
        octZ(p, B, BARREL, v(0.0f, 2.325f, 0.97f), 0.15f, 0.14f);
        p.add(PickupPart.block(B, BARREL, v(0.0f, 2.52f, -0.5f), v(0.05f, 0.08f, 0.05f)));
        p.add(PickupPart.block(B, BARREL, v(0.0f, 2.475f, 0.85f), v(0.04f, 0.08f, 0.04f)));
        p.add(PickupPart.block(B, BARREL, v(-0.16f, 2.13f, -0.62f), v(0.07f, 0.22f, 0.07f)));
        p.add(PickupPart.block(B, BARREL, v(0.16f, 2.13f, -0.62f), v(0.07f, 0.22f, 0.07f)));
        p.add(PickupPart.block(B, BARREL, v(0.16f, 2.325f, -0.2f), v(0.12f, 0.05f, 0.05f)));
        p.add(PickupPart.block(B, BARREL, v(-0.14f, 2.18f, -0.82f), v(0.05f, 0.24f, 0.05f)));
        p.add(PickupPart.block(B, BARREL, v(0.14f, 2.18f, -0.82f), v(0.05f, 0.24f, 0.05f)));
        cached = Collections.unmodifiableList(p);
        return cached;
    }
}

