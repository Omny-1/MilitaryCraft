package me.bibo.militarycraft.vehicles.moto.model;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Stable no-resource-pack model of a red Soviet boxer motorcycle with a right-hand
 * sidecar. All source numbers are base units multiplied by {@link #SCALE} exactly
 * once. The origin is on the ground below the midpoint of the motorcycle wheels;
 * +X is the rider's left, +Y is up and +Z is forward. The right-hand
 * sidecar therefore occupies negative local X.
 *
 * <p>The part order is persistence-sensitive. Never conditionally insert parts:
 * append a new model revision instead and migrate/rebuild saved displays.</p>
 */
public final class MotorcycleModel {

    private MotorcycleModel() {
    }

    public static final float SCALE = 0.5f;

    private static final float WHEEL_RADIUS_BASE = 1.25f;
    private static final float WHEEL_TILE_SIDE_BASE =
            (float) (Math.sqrt(2.0) * WHEEL_RADIUS_BASE);
    private static final float WHEEL_Y_BASE = WHEEL_RADIUS_BASE;

    private static final Vector3f FRONT_WHEEL_BASE = new Vector3f(0f, WHEEL_Y_BASE, 2.15f);
    private static final Vector3f REAR_WHEEL_BASE = new Vector3f(0f, WHEEL_Y_BASE, -2.15f);
    private static final Vector3f SIDECAR_WHEEL_BASE = new Vector3f(3.70f, WHEEL_Y_BASE, -0.75f);
    private static final Vector3f STEERING_HEAD_BASE = new Vector3f(0f, 2.80f, 1.08f);

    /** Exact physical extents excluding the floating nameplate. */
    public static final float MIN_X = -4.20f * SCALE;
    public static final float MAX_X = 1.50f * SCALE;
    public static final float MIN_Z = -3.45f * SCALE;
    public static final float MAX_Z = 3.45f * SCALE;
    public static final float WIDTH = MAX_X - MIN_X;
    public static final float HEIGHT = 4.30f * SCALE;
    public static final float LENGTH = MAX_Z - MIN_Z;
    public static final float WHEEL_RADIUS = WHEEL_RADIUS_BASE * SCALE;

    /** Nominal local mount anchors; config may adjust only their Y/Z coordinates. */
    private static final Vector3f DRIVER_MOUNT = new Vector3f(0f, 0.72f, -0.32f);
    private static final Vector3f PILLION_MOUNT = new Vector3f(0f, 0.76f, -0.82f);
    private static final Vector3f SIDECAR_MOUNT = new Vector3f(-2.45f * SCALE, 0.48f, -0.45f * SCALE);

    private static final List<MotorcyclePart> PARTS;
    /** Same model with every sidecar part dropped: a lone motorcycle. */
    private static final List<MotorcyclePart> SOLO_PARTS;

    static {
        List<MotorcyclePart> full = new ArrayList<>(70);
        java.util.Set<Integer> sidecar = new java.util.HashSet<>();
        java.util.Map<Integer, MotorcyclePart> soloOverride = new java.util.HashMap<>();
        buildParts(full, sidecar, soloOverride);
        if (full.size() != 67) {
            throw new IllegalStateException("Motorcycle model part count changed: " + full.size());
        }
        PARTS = List.copyOf(full);
        // Solo: drop sidecar parts, and swap in the narrower boxer-engine pieces.
        List<MotorcyclePart> solo = new ArrayList<>(full.size() - sidecar.size());
        for (int i = 0; i < full.size(); i++) {
            if (!sidecar.contains(i)) {
                solo.add(soloOverride.getOrDefault(i, full.get(i)));
            }
        }
        SOLO_PARTS = List.copyOf(solo);
    }

    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x * SCALE, y * SCALE, z * SCALE);
    }

    /** Mirror only local positions so the sidecar is on the rider's right. */
    private static Vector3f o(float x, float y, float z) {
        return new Vector3f(-x * SCALE, y * SCALE, z * SCALE);
    }

    public static Vector3f frontWheelCenter() {
        return scaledCopy(FRONT_WHEEL_BASE);
    }

    public static Vector3f rearWheelCenter() {
        return scaledCopy(REAR_WHEEL_BASE);
    }

    public static Vector3f sidecarWheelCenter() {
        return scaledCopy(SIDECAR_WHEEL_BASE);
    }

    public static Vector3f steeringHeadPivot() {
        return scaledCopy(STEERING_HEAD_BASE);
    }

    public static Vector3f driverMountOffset() {
        return new Vector3f(DRIVER_MOUNT);
    }

    public static Vector3f pillionMountOffset() {
        return new Vector3f(PILLION_MOUNT);
    }

    public static Vector3f sidecarMountOffset() {
        return new Vector3f(SIDECAR_MOUNT);
    }

    public static List<MotorcyclePart> parts() {
        return PARTS;
    }

    /** The model for the chosen variant: solo drops all sidecar parts. */
    public static List<MotorcyclePart> parts(boolean withSidecar) {
        return withSidecar ? PARTS : SOLO_PARTS;
    }

    private static void buildParts(List<MotorcyclePart> p, java.util.Set<Integer> sidecar,
                                   java.util.Map<Integer, MotorcyclePart> soloOverride) {
        // One steering axis (the steering head) shared by the WHOLE front assembly -
        // wheel, fender, fork, handlebar, lamp and mirrors - so it turns as a single
        // rigid unit instead of the wheel and bars swinging about separate pivots.
        Vector3f steerPivot = scaledCopy(STEERING_HEAD_BASE);

        // 0..8: three equal-radius wheels, three displays each. Only the front steers.
        addWheel(p, FRONT_WHEEL_BASE, steerPivot);
        addWheel(p, REAR_WHEEL_BASE, null);
        int sidecarWheel = p.size();
        addWheel(p, SIDECAR_WHEEL_BASE, null);
        markRange(sidecar, sidecarWheel, p.size()); // sidecar wheel (solo drops it)

        // 9..13: black motorcycle frame and rear swingarm.
        p.add(block(MotorcyclePart.Role.FRAME, -0.42f, 1.05f, -0.05f, 0.14f, 0.14f, 3.30f));
        p.add(block(MotorcyclePart.Role.FRAME, 0.42f, 1.05f, -0.05f, 0.14f, 0.14f, 3.30f));
        p.add(block(MotorcyclePart.Role.FRAME, 0f, 2.02f, 0.45f, 0.16f, 0.16f, 2.35f,
                -24f, 0f, 0f));
        p.add(block(MotorcyclePart.Role.FRAME, -0.32f, 1.22f, -1.40f, 0.14f, 0.14f, 1.55f));
        p.add(block(MotorcyclePart.Role.FRAME, 0.32f, 1.22f, -1.40f, 0.14f, 0.14f, 1.55f));

        // 14..21: opposed-twin engine, gearbox and four cooling-fin plates. In solo the
        // boxer cluster is pulled in to ~bike width (no sidecar to balance the wide look).
        p.add(block(MotorcyclePart.Role.ENGINE, 0f, 1.40f, 0f, 0.95f, 0.92f, 0.95f));
        p.add(block(MotorcyclePart.Role.ENGINE, 0f, 1.30f, -0.65f, 0.80f, 0.74f, 0.80f));
        int cyl = p.size();
        p.add(block(MotorcyclePart.Role.ENGINE, -0.92f, 1.46f, 0.12f, 1.10f, 0.60f, 0.78f));
        p.add(block(MotorcyclePart.Role.ENGINE, 0.92f, 1.46f, 0.12f, 1.10f, 0.60f, 0.78f));
        soloOverride.put(cyl, block(MotorcyclePart.Role.ENGINE,
                -0.44f, 1.44f, 0.12f, 0.62f, 0.58f, 0.78f));
        soloOverride.put(cyl + 1, block(MotorcyclePart.Role.ENGINE,
                0.44f, 1.44f, 0.12f, 0.62f, 0.58f, 0.78f));
        for (float side : new float[]{-1f, 1f}) {
            int innerFin = p.size();
            p.add(block(MotorcyclePart.Role.DETAIL, side * 0.70f, 1.46f, 0.12f,
                    0.10f, 0.74f, 0.92f));
            p.add(block(MotorcyclePart.Role.DETAIL, side * 1.14f, 1.46f, 0.12f,
                    0.10f, 0.74f, 0.92f));
            soloOverride.put(innerFin, block(MotorcyclePart.Role.DETAIL,
                    side * 0.34f, 1.46f, 0.12f, 0.10f, 0.74f, 0.92f));
            soloOverride.put(innerFin + 1, block(MotorcyclePart.Role.DETAIL,
                    side * 0.56f, 1.46f, 0.12f, 0.10f, 0.74f, 0.92f));
        }

        // 22..28: rounded red tank, knee pads, driver saddle and pillion saddle.
        p.add(block(MotorcyclePart.Role.BODY, 0f, 2.52f, 0.35f, 1.55f, 0.92f, 1.62f));
        p.add(block(MotorcyclePart.Role.BODY, 0f, 2.53f, 1.18f, 1.42f, 0.70f, 0.34f,
                -18f, 0f, 0f));
        p.add(block(MotorcyclePart.Role.BODY, 0f, 2.50f, -0.50f, 1.35f, 0.62f, 0.30f,
                18f, 0f, 0f));
        p.add(block(MotorcyclePart.Role.SEAT, -0.80f, 2.47f, 0.32f, 0.10f, 0.48f, 0.72f));
        p.add(block(MotorcyclePart.Role.SEAT, 0.80f, 2.47f, 0.32f, 0.10f, 0.48f, 0.72f));
        p.add(block(MotorcyclePart.Role.SEAT, 0f, 2.65f, -0.72f, 1.24f, 0.25f, 1.00f,
                -4f, 0f, 0f));
        p.add(block(MotorcyclePart.Role.SEAT, 0f, 2.62f, -1.65f, 1.08f, 0.22f, 0.72f,
                -2f, 0f, 0f));

        // 29..34: front (steering), rear and sidecar mudguards. The front fender rides
        // on the shared steering axis so it stays glued to the wheel while turning.
        p.add(steerOnly(MotorcyclePart.Role.BODY, 0f, 2.49f, 2.15f, 0.88f, 0.16f, 1.02f,
                0f, 0f, 0f, steerPivot));
        p.add(steerOnly(MotorcyclePart.Role.BODY, 0f, 2.28f, 2.78f, 0.88f, 0.16f, 0.72f,
                25f, 0f, 0f, steerPivot));
        p.add(steerOnly(MotorcyclePart.Role.BODY, 0f, 2.28f, 1.52f, 0.88f, 0.16f, 0.72f,
                -25f, 0f, 0f, steerPivot));
        p.add(block(MotorcyclePart.Role.BODY, 0f, 2.49f, -2.18f, 0.90f, 0.16f, 1.12f));
        p.add(block(MotorcyclePart.Role.BODY, 0f, 2.28f, -2.85f, 0.90f, 0.16f, 0.75f,
                -25f, 0f, 0f));
        p.add(block(MotorcyclePart.Role.BODY, 3.70f, 2.50f, -0.75f, 0.92f, 0.18f, 1.35f));
        sidecar.add(p.size() - 1); // sidecar mudguard

        // 35..44: telescopic fork, handlebar, round lamp and right mirror, all on the
        // shared steering axis (below) so they move as one with the wheel.
        Vector3f headPivot = steerPivot;
        p.add(steerOnly(MotorcyclePart.Role.DETAIL, -0.35f, 1.98f, 1.62f,
                0.14f, 1.92f, 0.14f, -34f, 0f, 0f, headPivot));
        p.add(steerOnly(MotorcyclePart.Role.DETAIL, 0.35f, 1.98f, 1.62f,
                0.14f, 1.92f, 0.14f, -34f, 0f, 0f, headPivot));
        p.add(steerOnly(MotorcyclePart.Role.DETAIL, 0f, 3.45f, 1.03f,
                2.15f, 0.12f, 0.12f, 0f, 0f, 0f, headPivot));
        p.add(steerOnly(MotorcyclePart.Role.SEAT, -1.18f, 3.45f, 1.03f,
                0.34f, 0.22f, 0.22f, 0f, 0f, 0f, headPivot));
        p.add(steerOnly(MotorcyclePart.Role.SEAT, 1.18f, 3.45f, 1.03f,
                0.34f, 0.22f, 0.22f, 0f, 0f, 0f, headPivot));
        p.add(steerOnly(MotorcyclePart.Role.FRAME, 0f, 2.98f, 1.66f,
                1.04f, 1.04f, 0.28f, 0f, 0f, 0f, headPivot));
        p.add(steerOnly(MotorcyclePart.Role.FRAME, 0f, 2.98f, 1.66f,
                1.04f, 1.04f, 0.28f, 0f, 0f, 45f, headPivot));
        p.add(steerOnly(MotorcyclePart.Role.LIGHT, 0f, 2.98f, 1.83f,
                0.84f, 0.84f, 0.09f, 0f, 0f, 0f, headPivot));
        p.add(steerOnly(MotorcyclePart.Role.DETAIL, 1.15f, 3.75f, 0.96f,
                0.10f, 0.70f, 0.10f, 0f, 0f, -55f, headPivot));
        p.add(steerOnly(MotorcyclePart.Role.GLASS, 1.46f, 3.98f, 0.96f,
                0.12f, 0.55f, 0.55f, 0f, -35f, 0f, headPivot));

        // 45..46: twin low exhaust silencers.
        p.add(block(MotorcyclePart.Role.EXHAUST, -0.62f, 0.85f, -1.20f,
                0.24f, 0.30f, 2.50f));
        p.add(block(MotorcyclePart.Role.EXHAUST, 0.62f, 0.85f, -1.20f,
                0.24f, 0.30f, 2.50f));

        // 47..55: open red sidecar tub, two chassis ties and black upholstery.
        int sidecarBody = p.size();
        p.add(block(MotorcyclePart.Role.FRAME, 1.88f, 0.82f, 0.72f,
                2.65f, 0.14f, 0.14f));
        p.add(block(MotorcyclePart.Role.FRAME, 1.88f, 0.82f, -0.72f,
                2.65f, 0.14f, 0.14f));
        p.add(block(MotorcyclePart.Role.BODY, 2.45f, 0.95f, 0.10f,
                1.90f, 0.22f, 3.50f));
        p.add(block(MotorcyclePart.Role.BODY, 3.40f, 1.55f, 0.10f,
                0.18f, 1.15f, 3.35f));
        p.add(block(MotorcyclePart.Role.BODY, 1.50f, 1.48f, 0.10f,
                0.18f, 1.00f, 3.15f));
        p.add(block(MotorcyclePart.Role.BODY, 2.45f, 1.52f, 1.78f,
                1.95f, 1.08f, 0.25f, -12f, 0f, 0f));
        p.add(block(MotorcyclePart.Role.BODY, 2.45f, 1.45f, -1.55f,
                1.95f, 0.95f, 0.20f));
        p.add(block(MotorcyclePart.Role.SEAT, 2.45f, 1.95f, -0.45f,
                1.42f, 0.20f, 1.12f));
        p.add(block(MotorcyclePart.Role.SEAT, 2.45f, 2.24f, -1.03f,
                1.42f, 0.68f, 0.18f, -8f, 0f, 0f));
        markRange(sidecar, sidecarBody, p.size()); // whole sidecar tub + seats

        // 56..59: two tail lamps, physical plate and fixed rear registration.
        p.add(block(MotorcyclePart.Role.LIGHT, 0f, 2.32f, -3.02f,
                0.42f, 0.34f, 0.16f));
        p.add(block(MotorcyclePart.Role.LIGHT, 3.42f, 1.45f, -1.63f,
                0.34f, 0.36f, 0.15f));
        sidecar.add(p.size() - 1); // sidecar tail lamp
        p.add(block(MotorcyclePart.Role.DETAIL, 0f, 1.90f, -3.10f,
                0.90f, 0.45f, 0.10f));
        p.add(MotorcyclePart.painted(o(0f, 1.90f, -3.17f), v(0.42f, 0.42f, 0.42f),
                180f, "MC 21-04", 0xFF111111));

        // 60..66: reworked front controls, all on the shared steering axis - a top yoke
        // and two risers tie the handlebar to the fork, a proper TWIN mirror set and
        // brake/clutch levers sit at the grips. Turning moves all of it with the wheel.
        p.add(steerOnly(MotorcyclePart.Role.FRAME, 0f, 3.28f, 1.20f,
                1.12f, 0.16f, 0.60f, 0f, 0f, 0f, steerPivot));       // top triple clamp / yoke
        p.add(steerOnly(MotorcyclePart.Role.FRAME, -0.32f, 3.40f, 1.11f,
                0.16f, 0.24f, 0.16f, 0f, 0f, 0f, steerPivot));       // handlebar riser (left)
        p.add(steerOnly(MotorcyclePart.Role.FRAME, 0.32f, 3.40f, 1.11f,
                0.16f, 0.24f, 0.16f, 0f, 0f, 0f, steerPivot));       // handlebar riser (right)
        p.add(steerOnly(MotorcyclePart.Role.DETAIL, -1.15f, 3.75f, 0.96f,
                0.10f, 0.70f, 0.10f, 0f, 0f, 55f, steerPivot));      // left mirror stalk
        p.add(steerOnly(MotorcyclePart.Role.GLASS, -1.46f, 3.98f, 0.96f,
                0.12f, 0.55f, 0.55f, 0f, 35f, 0f, steerPivot));      // left mirror glass
        p.add(steerOnly(MotorcyclePart.Role.DETAIL, 0.98f, 3.42f, 1.24f,
                0.46f, 0.07f, 0.10f, 0f, -22f, 0f, steerPivot));     // front brake lever (right)
        p.add(steerOnly(MotorcyclePart.Role.DETAIL, -0.98f, 3.42f, 1.24f,
                0.46f, 0.07f, 0.10f, 0f, 22f, 0f, steerPivot));      // clutch lever (left)
    }

    private static void markRange(java.util.Set<Integer> sidecar, int fromInclusive, int toExclusive) {
        for (int i = fromInclusive; i < toExclusive; i++) {
            sidecar.add(i);
        }
    }

    private static void addWheel(List<MotorcyclePart> parts, Vector3f baseCenter,
                                 Vector3f steeringPivot) {
        Vector3f center = scaledCopy(baseCenter);
        boolean steering = steeringPivot != null;
        addWheelPart(parts, MotorcyclePart.Role.WHEEL, center,
                v(0.64f, WHEEL_TILE_SIDE_BASE, WHEEL_TILE_SIDE_BASE), 0f, steering, steeringPivot);
        addWheelPart(parts, MotorcyclePart.Role.WHEEL, center,
                v(0.64f, WHEEL_TILE_SIDE_BASE, WHEEL_TILE_SIDE_BASE), 45f, steering, steeringPivot);
        addWheelPart(parts, MotorcyclePart.Role.HUB, center,
                v(0.72f, 0.95f, 0.95f), 22.5f, steering, steeringPivot);
    }

    private static void addWheelPart(List<MotorcyclePart> parts, MotorcyclePart.Role role,
                                     Vector3f offset, Vector3f scale, float pitch,
                                     boolean steering, Vector3f pivot) {
        parts.add(steering
                ? MotorcyclePart.steering(role, offset, scale, pitch, 0f, 0f, pivot)
                : MotorcyclePart.rolling(role, offset, scale, pitch, 0f, 0f));
    }

    private static MotorcyclePart block(MotorcyclePart.Role role,
                                        float x, float y, float z,
                                        float sx, float sy, float sz) {
        return MotorcyclePart.block(role, o(x, y, z), v(sx, sy, sz));
    }

    private static MotorcyclePart block(MotorcyclePart.Role role,
                                        float x, float y, float z,
                                        float sx, float sy, float sz,
                                        float pitch, float yaw, float roll) {
        return MotorcyclePart.block(role, o(x, y, z), v(sx, sy, sz), pitch, yaw, roll);
    }

    private static MotorcyclePart steerOnly(MotorcyclePart.Role role,
                                            float x, float y, float z,
                                            float sx, float sy, float sz,
                                            float pitch, float yaw, float roll,
                                            Vector3f pivot) {
        return MotorcyclePart.steerOnly(role, o(x, y, z), v(sx, sy, sz),
                pitch, yaw, roll, pivot);
    }

    private static Vector3f scaledCopy(Vector3f base) {
        return new Vector3f(-base.x * SCALE, base.y * SCALE, base.z * SCALE);
    }
}
