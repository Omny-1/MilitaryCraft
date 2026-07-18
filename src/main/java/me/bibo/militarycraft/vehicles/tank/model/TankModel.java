package me.bibo.militarycraft.vehicles.tank.model;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Static description of the tank model, shaped after a T-34: a long hull on two
 * tracks, sloped front, a faceted turret offset to the rear and a long gun.
 *
 * <p>All coordinates are in tank space (origin on the ground at the tank centre,
 * +X right, +Y up, +Z forward). Edit the numbers here to reshape the tank.
 *
 * <p>Every number below is written in <em>base</em> units and multiplied by
 * {@link #SCALE} on the way out, so the whole tank (geometry, pivots, muzzle and
 * hitbox) grows uniformly about the ground-centre origin from a single knob —
 * which keeps it sitting flat on the ground at any scale.
 */
public final class TankModel {

    private TankModel() {
    }

    /** Uniform size multiplier for the entire model. 2.0 = double the base T-34. */
    public static final float SCALE = 2.0f;

    /** Helper: a scaled tank-space vector (used for both offsets and sizes). */
    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x * SCALE, y * SCALE, z * SCALE);
    }

    /** Point (tank space) the turret rotates about. */
    public static final Vector3f TURRET_PIVOT = v(0f, 1.55f, -0.15f);
    /** Point (tank space) the barrel elevates about (where the gun meets the mantlet). */
    public static final Vector3f BARREL_PIVOT = v(0f, 1.75f, 0.55f);
    /** Tip of the gun (tank space) where shells spawn and the muzzle flash appears. */
    public static final Vector3f MUZZLE_TIP = v(0f, 1.75f, 4.25f);
    /** Approximate bounding box (full width / height / length) for the hitbox. */
    public static final float WIDTH = 2.6f * SCALE;
    public static final float HEIGHT = 2.4f * SCALE;
    public static final float LENGTH = 5.2f * SCALE;

    private static String cachedNumber;
    private static List<TankPart> cached;

    public static synchronized List<TankPart> parts(String turretNumber) {
        if (cached != null && java.util.Objects.equals(cachedNumber, turretNumber)) {
            return cached;
        }
        List<TankPart> p = new ArrayList<>();
        PartGroup H = PartGroup.HULL;
        PartGroup T = PartGroup.TURRET;
        PartGroup B = PartGroup.BARREL;
        TankPart.Role HULL = TankPart.Role.HULL;
        TankPart.Role TURRET = TankPart.Role.TURRET;
        TankPart.Role DETAIL = TankPart.Role.DETAIL;
        TankPart.Role TRACK = TankPart.Role.TRACK;
        TankPart.Role WHEEL = TankPart.Role.WHEEL;
        TankPart.Role BARREL = TankPart.Role.BARREL;

        // ============================ HULL ============================
        // main lower hull
        p.add(TankPart.block(H, HULL, v(0f, 0.80f, 0f), v(2.30f, 0.70f, 4.30f)));
        // upper deck, a bit narrower
        p.add(TankPart.block(H, HULL, v(0f, 1.16f, -0.20f), v(1.95f, 0.20f, 3.60f)));
        // sloped glacis (front)
        p.add(TankPart.block(H, HULL, v(0f, 0.92f, 2.30f), v(2.30f, 0.95f, 0.45f), -35f, 0f, 0f));
        // lower nose bevel under the glacis (rounds the front)
        p.add(TankPart.block(H, HULL, v(0f, 0.48f, 2.30f), v(2.20f, 0.55f, 0.40f), 32f, 0f, 0f));
        // sloped rear plate
        p.add(TankPart.block(H, HULL, v(0f, 0.92f, -2.30f), v(2.30f, 0.85f, 0.45f), 20f, 0f, 0f));

        // ---- Tracks (outer skin) ----
        p.add(TankPart.block(H, TRACK, v(-1.15f, 0.50f, 0f), v(0.50f, 0.98f, 5.55f)));
        p.add(TankPart.block(H, TRACK, v(1.15f, 0.50f, 0f), v(0.50f, 0.98f, 5.55f)));
        // Front idler + rear sprocket: a touch larger than the road wheels and
        // sitting at the very ends. All wheels are narrow and pushed just proud of
        // the track skin (x=1.35 vs track outer 1.40) so no wheel face is coplanar
        // with the track or another wheel — that is what stops the z-fighting.
        p.add(TankPart.block(H, WHEEL, v(-1.35f, 0.44f, 2.15f), v(0.34f, 0.72f, 0.72f)));
        p.add(TankPart.block(H, WHEEL, v(1.35f, 0.44f, 2.15f), v(0.34f, 0.72f, 0.72f)));
        p.add(TankPart.block(H, WHEEL, v(-1.35f, 0.44f, -2.15f), v(0.34f, 0.72f, 0.72f)));
        p.add(TankPart.block(H, WHEEL, v(1.35f, 0.44f, -2.15f), v(0.34f, 0.72f, 0.72f)));
        // Five evenly spaced road wheels per side (rims clear of each other and of
        // the end wheels, so nothing overlaps).
        for (float z : new float[]{-1.45f, -0.725f, 0f, 0.725f, 1.45f}) {
            p.add(TankPart.block(H, WHEEL, v(-1.35f, 0.40f, z), v(0.34f, 0.60f, 0.60f)));
            p.add(TankPart.block(H, WHEEL, v(1.35f, 0.40f, z), v(0.34f, 0.60f, 0.60f)));
        }
        // fenders / mud guards proud over the wheels (outermost => no shared faces)
        p.add(TankPart.block(H, DETAIL, v(-1.22f, 1.02f, 0f), v(0.70f, 0.10f, 4.75f)));
        p.add(TankPart.block(H, DETAIL, v(1.22f, 1.02f, 0f), v(0.70f, 0.10f, 4.75f)));

        // ---- Hull details ----
        // driver's hatch on the glacis
        p.add(TankPart.block(H, DETAIL, v(-0.55f, 1.28f, 1.92f), v(0.64f, 0.12f, 0.64f), -35f, 0f, 0f));
        // hull machine-gun ball mount + stub barrel
        p.add(TankPart.block(H, DETAIL, v(0.62f, 1.10f, 2.40f), v(0.36f, 0.36f, 0.30f)));
        p.add(TankPart.block(H, BARREL, v(0.62f, 1.10f, 2.66f), v(0.12f, 0.12f, 0.5f)));
        // headlight on the left fender
        p.add(TankPart.block(H, DETAIL, v(-0.72f, 1.30f, 2.30f), v(0.20f, 0.22f, 0.18f)));
        // rear-fender fuel drums
        p.add(TankPart.block(H, DETAIL, v(-1.18f, 1.22f, -2.05f), v(0.48f, 0.48f, 0.95f)));
        p.add(TankPart.block(H, DETAIL, v(1.18f, 1.22f, -2.05f), v(0.48f, 0.48f, 0.95f)));

        // =========================== TURRET ===========================
        // core (a touch narrower; the angled cheeks/walls add the width)
        p.add(TankPart.block(T, TURRET, v(0f, 1.55f, -0.15f), v(1.55f, 0.70f, 2.05f)));
        // sloped left/right walls (T-34 turret sides lean outward at the bottom)
        p.add(TankPart.block(T, TURRET, v(-0.92f, 1.55f, -0.12f), v(0.18f, 0.74f, 1.95f), 0f, 0f, 16f));
        p.add(TankPart.block(T, TURRET, v(0.92f, 1.55f, -0.12f), v(0.18f, 0.74f, 1.95f), 0f, 0f, -16f));
        // faceted front cheeks (hexagonal nose)
        p.add(TankPart.block(T, TURRET, v(-0.70f, 1.55f, 0.78f), v(0.55f, 0.70f, 0.80f), 0f, -35f, 0f));
        p.add(TankPart.block(T, TURRET, v(0.70f, 1.55f, 0.78f), v(0.55f, 0.70f, 0.80f), 0f, 35f, 0f));
        // rear bustle
        p.add(TankPart.block(T, TURRET, v(0f, 1.60f, -1.18f), v(1.30f, 0.56f, 0.45f)));
        // chamfered top
        p.add(TankPart.block(T, TURRET, v(0f, 1.97f, -0.20f), v(1.30f, 0.22f, 1.50f)));
        // commander cupola (rounded): base ring + hatch lid
        p.add(TankPart.block(T, DETAIL, v(0f, 2.12f, -0.50f), v(0.66f, 0.18f, 0.66f)));
        p.add(TankPart.block(T, DETAIL, v(0f, 2.26f, -0.50f), v(0.50f, 0.12f, 0.50f)));

        // Hull side numbers. Always present (empty string => invisible) so the
        // part count never depends on the number, keeping rehydration stable.
        String number = (turretNumber == null) ? "" : turretNumber;
        p.add(new TankPart(T, TURRET, v(0.98f, 1.62f, -0.20f),
                v(1f, 1f, 1f), 0f, -90f, 0f, number));
        p.add(new TankPart(T, TURRET, v(-0.98f, 1.62f, -0.20f),
                v(1f, 1f, 1f), 0f, 90f, 0f, number));

        // ===================== GUN (barrel group) =====================
        // mantlet collar at the gun base
        p.add(TankPart.block(B, BARREL, v(0f, 1.75f, 0.95f), v(0.55f, 0.55f, 0.45f)));
        // mid sleeve
        p.add(TankPart.block(B, BARREL, v(0f, 1.75f, 1.30f), v(0.36f, 0.36f, 0.55f)));
        // barrel
        p.add(TankPart.block(B, BARREL, v(0f, 1.75f, 2.20f), v(0.26f, 0.26f, 3.05f)));
        // muzzle brake + tip ring
        p.add(TankPart.block(B, BARREL, v(0f, 1.75f, 3.85f), v(0.40f, 0.40f, 0.50f)));
        p.add(TankPart.block(B, BARREL, v(0f, 1.75f, 4.12f), v(0.30f, 0.30f, 0.18f)));

        cached = java.util.Collections.unmodifiableList(p);
        cachedNumber = turretNumber;
        return cached;
    }
}
