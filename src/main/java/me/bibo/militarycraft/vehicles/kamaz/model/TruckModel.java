package me.bibo.militarycraft.vehicles.kamaz.model;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Static description of the Kamaz "Pushinka" - a Typhoon-style 6x6 MRAP: a tall
 * armoured troop capsule on a deep hull, a stepped armoured cab (taller than the
 * rear box) with a steeply raked windshield, six big off-road wheels on three
 * axles (the front one pushed forward under the nose so it never eats the cab
 * windows), a heavy front bumper with a winch, plus hatches, vents, firing ports,
 * mirrors and lights. A continuous lower hull skirt ties the body and cab down to
 * the wheels so there is no floating gap underneath.
 *
 * <p>All coordinates are in truck space (origin on the ground at the truck
 * centre, +X right, +Y up, +Z forward = the cab/front). Edit the numbers here to
 * reshape the truck.
 *
 * <p>Every number below is written in <em>base</em> units and multiplied by
 * {@link #SCALE} on the way out, so the whole truck (geometry, wheels, hitbox)
 * grows uniformly about the ground-centre origin from a single knob.
 */
public final class TruckModel {

    private TruckModel() {
    }

    /** Uniform size multiplier for the entire model. 0.5 = half the base size. */
    public static final float SCALE = 0.5f;

    /** Helper: a scaled truck-space vector (used for both offsets and sizes). */
    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x * SCALE, y * SCALE, z * SCALE);
    }

    /** Approximate bounding box (full width / height / length) for the hitbox & run-over body. */
    public static final float WIDTH = 6.4f * SCALE;
    public static final float HEIGHT = 7.2f * SCALE;
    public static final float LENGTH = 17.4f * SCALE;
    /** World-space wheel radius, used to convert travelled distance into wheel spin. */
    public static final float WHEEL_RADIUS = 1.4f * SCALE;

    private static List<TruckPart> cached;

    public static synchronized List<TruckPart> parts() {
        if (cached != null) {
            return cached;
        }
        List<TruckPart> p = new ArrayList<>();
        TruckPart.Role HULL = TruckPart.Role.HULL;
        TruckPart.Role CAB = TruckPart.Role.CAB;
        TruckPart.Role CHASSIS = TruckPart.Role.CHASSIS;
        TruckPart.Role GLASS = TruckPart.Role.GLASS;
        TruckPart.Role DETAIL = TruckPart.Role.DETAIL;
        TruckPart.Role LIGHT = TruckPart.Role.LIGHT;
        TruckPart.Role BUMPER = TruckPart.Role.BUMPER;
        TruckPart.Role HUB = TruckPart.Role.HUB;

        // ============ LOWER HULL (ties body & cab down to the wheels, no gap) ============
        p.add(TruckPart.block(HULL, v(0f, 2.05f, -0.5f), v(4.9f, 1.7f, 15.6f)));   // skirt y1.2..2.9, nose-to-tail
        // MRAP V-belly (dark, angled) tucked under the skirt
        p.add(TruckPart.block(CHASSIS, v(-1.2f, 1.6f, 0.5f), v(2.6f, 0.6f, 9.0f), 0f, 0f, 26f));
        p.add(TruckPart.block(CHASSIS, v(1.2f, 1.6f, 0.5f), v(2.6f, 0.6f, 9.0f), 0f, 0f, -26f));
        // dark chassis cross-members front & rear
        p.add(TruckPart.block(CHASSIS, v(0f, 1.45f, -6.8f), v(4.2f, 0.7f, 1.4f)));
        p.add(TruckPart.block(CHASSIS, v(0f, 1.45f, 7.35f), v(4.2f, 0.7f, 1.4f)));

        // ============================ WHEELS (3 axles) ============================
        // Front axle pushed forward under the nose; rear tandem at the back.
        float[] axleZ = {6.4f, -2.8f, -6.0f};
        for (int i = 0; i < axleZ.length; i++) {
            float z = axleZ[i];
            boolean steering = i == 0;
            wheel(p, -2.7f, z, steering);
            wheel(p, 2.7f, z, steering);
        }
        // fenders / mud guards over the wheels
        p.add(TruckPart.block(DETAIL, v(-2.55f, 3.2f, 6.4f), v(1.5f, 0.3f, 3.2f)));
        p.add(TruckPart.block(DETAIL, v(2.55f, 3.2f, 6.4f), v(1.5f, 0.3f, 3.2f)));
        p.add(TruckPart.block(DETAIL, v(-2.55f, 3.2f, -4.4f), v(1.5f, 0.3f, 5.2f)));
        p.add(TruckPart.block(DETAIL, v(2.55f, 3.2f, -4.4f), v(1.5f, 0.3f, 5.2f)));

        // ===================== MAIN ARMOURED BODY (rear capsule) =====================
        p.add(TruckPart.block(HULL, v(0f, 3.5f, -2.9f), v(5.4f, 2.5f, 10.8f)));   // lower body
        p.add(TruckPart.block(HULL, v(0f, 5.35f, -3.0f), v(5.0f, 1.4f, 10.0f)));  // upper body
        p.add(TruckPart.block(HULL, v(0f, 6.05f, -3.0f), v(4.7f, 0.4f, 9.4f)));   // roof slab
        // canted upper side bevels (armour look)
        p.add(TruckPart.block(HULL, v(-2.5f, 5.2f, -3.0f), v(0.5f, 1.7f, 9.8f), 0f, 0f, 20f));
        p.add(TruckPart.block(HULL, v(2.5f, 5.2f, -3.0f), v(0.5f, 1.7f, 9.8f), 0f, 0f, -20f));
        // belt / panel line between lower & upper body (slightly proud)
        p.add(TruckPart.block(DETAIL, v(0f, 4.78f, -3.0f), v(5.5f, 0.18f, 10.2f)));
        // sloped rear armoured door + panel + tail lights
        p.add(TruckPart.block(HULL, v(0f, 3.6f, -8.25f), v(4.8f, 2.7f, 0.5f), 10f, 0f, 0f));
        p.add(TruckPart.block(DETAIL, v(0f, 3.3f, -8.5f), v(2.6f, 2.4f, 0.25f)));
        p.add(TruckPart.block(LIGHT, v(-2.0f, 2.6f, -8.4f), v(0.4f, 0.4f, 0.2f)));
        p.add(TruckPart.block(LIGHT, v(2.0f, 2.6f, -8.4f), v(0.4f, 0.4f, 0.2f)));
        // grille panel on the box front face (the gap behind the cab)
        p.add(TruckPart.block(DETAIL, v(0f, 4.6f, 2.55f), v(3.6f, 1.8f, 0.4f)));

        // ---- roof hatches ----
        float[][] hatches = {{-1.3f, 0.5f}, {1.3f, 0.5f}, {-1.3f, -2.0f}, {1.3f, -2.0f}, {0f, -4.6f}};
        for (float[] h : hatches) {
            p.add(TruckPart.block(DETAIL, v(h[0], 6.45f, h[1]), v(1.4f, 0.4f, 1.4f)));
        }

        // ---- side firing ports / windows (dark glass), both sides ----
        for (float z : new float[]{0.6f, -1.6f, -3.8f, -6.0f}) {
            p.add(TruckPart.block(GLASS, v(-2.73f, 4.0f, z), v(0.16f, 0.85f, 0.95f)));
            p.add(TruckPart.block(GLASS, v(2.73f, 4.0f, z), v(0.16f, 0.85f, 0.95f)));
        }
        // ---- side stowage strips ----
        p.add(TruckPart.block(DETAIL, v(-2.74f, 2.55f, -3.5f), v(0.2f, 0.7f, 4.5f)));
        p.add(TruckPart.block(DETAIL, v(2.74f, 2.55f, -3.5f), v(0.2f, 0.7f, 4.5f)));

        // ====================== CAB (front, cleaner armoured driver section) ======================
        p.add(TruckPart.block(CAB, v(0f, 3.15f, 4.85f), v(4.85f, 2.25f, 4.5f)));   // lower cab shell
        p.add(TruckPart.block(CAB, v(0f, 5.2f, 4.25f), v(4.6f, 1.85f, 2.9f)));      // upright crew cell
        p.add(TruckPart.block(CAB, v(0f, 6.25f, 4.18f), v(4.85f, 0.55f, 3.15f)));   // attached roof cap
        p.add(TruckPart.block(DETAIL, v(0f, 6.58f, 4.1f), v(3.6f, 0.25f, 2.0f)));   // raised roof armour

        // Nose and windshield are stepped into the cab so the front no longer looks detached.
        p.add(TruckPart.block(CAB, v(0f, 4.25f, 6.55f), v(4.55f, 0.85f, 2.15f), -10f, 0f, 0f));
        p.add(TruckPart.block(CAB, v(0f, 3.08f, 7.55f), v(4.55f, 1.85f, 0.65f)));
        p.add(TruckPart.block(CAB, v(0f, 5.0f, 6.22f), v(4.5f, 1.9f, 0.42f), -33f, 0f, 0f));
        p.add(TruckPart.block(GLASS, v(-1.12f, 5.05f, 6.36f), v(1.75f, 1.35f, 0.18f), -33f, 0f, 0f));
        p.add(TruckPart.block(GLASS, v(1.12f, 5.05f, 6.36f), v(1.75f, 1.35f, 0.18f), -33f, 0f, 0f));
        p.add(TruckPart.block(CAB, v(0f, 5.05f, 6.36f), v(0.28f, 1.65f, 0.24f), -33f, 0f, 0f));

        // Front grille, headlights and cheek armour.
        p.add(TruckPart.block(DETAIL, v(0f, 3.15f, 7.92f), v(3.3f, 1.2f, 0.16f)));
        p.add(TruckPart.block(DETAIL, v(0f, 3.85f, 7.95f), v(2.7f, 0.16f, 0.18f)));
        p.add(TruckPart.block(LIGHT, v(-1.55f, 2.85f, 7.98f), v(0.6f, 0.48f, 0.18f)));
        p.add(TruckPart.block(LIGHT, v(1.55f, 2.85f, 7.98f), v(0.6f, 0.48f, 0.18f)));
        // Side glass, door armour and pillars on both sides.
        for (float side : new float[]{-1f, 1f}) {
            float x = side * 2.48f;
            p.add(TruckPart.block(GLASS, v(x, 4.82f, 4.15f), v(0.16f, 1.12f, 1.25f)));
            p.add(TruckPart.block(GLASS, v(x, 4.78f, 5.38f), v(0.16f, 1.02f, 0.85f)));
            p.add(TruckPart.block(DETAIL, v(x, 3.35f, 4.25f), v(0.18f, 1.65f, 2.55f)));
            p.add(TruckPart.block(CAB, v(x, 4.78f, 3.48f), v(0.22f, 1.6f, 0.18f)));
            p.add(TruckPart.block(CAB, v(x, 4.78f, 5.04f), v(0.22f, 1.62f, 0.18f)));
            p.add(TruckPart.block(CAB, v(x, 4.65f, 5.9f), v(0.2f, 1.48f, 0.18f), -18f, 0f, 0f));
            p.add(TruckPart.block(DETAIL, v(x, 2.35f, 4.55f), v(0.22f, 0.25f, 2.1f)));
        }
        // wing mirrors - short arm hugging the cab, mirror just outside the body
        p.add(TruckPart.block(DETAIL, v(-2.65f, 5.08f, 6.05f), v(0.55f, 0.14f, 0.14f)));
        p.add(TruckPart.block(DETAIL, v(2.65f, 5.08f, 6.05f), v(0.55f, 0.14f, 0.14f)));
        p.add(TruckPart.block(GLASS, v(-2.98f, 4.9f, 6.2f), v(0.2f, 0.9f, 0.45f)));
        p.add(TruckPart.block(GLASS, v(2.98f, 4.9f, 6.2f), v(0.2f, 0.9f, 0.45f)));
        // exhaust stack behind the cab
        p.add(TruckPart.block(DETAIL, v(2.45f, 4.35f, 2.35f), v(0.42f, 2.5f, 0.42f)));
        p.add(TruckPart.block(HUB, v(2.45f, 5.72f, 2.35f), v(0.48f, 0.24f, 0.48f)));

        // ============================ FRONT BUMPER / WINCH ============================
        p.add(TruckPart.block(BUMPER, v(0f, 2.15f, 8.35f), v(5.0f, 0.95f, 0.7f)));
        p.add(TruckPart.block(DETAIL, v(0f, 2.6f, 8.15f), v(1.5f, 0.7f, 0.5f)));     // winch
        p.add(TruckPart.block(BUMPER, v(-1.7f, 2.0f, 8.55f), v(0.35f, 0.6f, 0.35f)));
        p.add(TruckPart.block(BUMPER, v(1.7f, 2.0f, 8.55f), v(0.35f, 0.6f, 0.35f)));

        cached = java.util.Collections.unmodifiableList(p);
        return cached;
    }

    /**
     * Adds one big off-road wheel at the given side/axle: an octagonal tyre (an
     * axis-aligned cube plus a 45°-about-X cube so the side silhouette is rounded)
     * and a lighter hub cap pushed just outboard.
     */
    private static void wheel(List<TruckPart> p, float x, float z, boolean steering) {
        TruckPart.Role WHEEL = TruckPart.Role.WHEEL;
        TruckPart.Role HUB = TruckPart.Role.HUB;
        p.add(wheelPart(steering, WHEEL, v(x, 1.4f, z), v(0.95f, 2.8f, 2.8f), 0f));
        p.add(wheelPart(steering, WHEEL, v(x, 1.4f, z), v(0.95f, 2.8f, 2.8f), 45f));
        float hubX = x + Math.signum(x) * 0.56f;
        p.add(wheelPart(steering, HUB, v(hubX, 1.4f, z), v(0.22f, 1.25f, 1.25f), 0f));
        p.add(wheelPart(steering, HUB, v(hubX + Math.signum(x) * 0.04f, 1.4f, z), v(0.12f, 1.25f, 1.25f), 45f));
    }

    private static TruckPart wheelPart(boolean steering, TruckPart.Role role,
                                       Vector3f offset, Vector3f scale, float pitch) {
        return steering
                ? TruckPart.steering(role, offset, scale, pitch, 0f, 0f)
                : TruckPart.rolling(role, offset, scale, pitch, 0f, 0f);
    }
}
