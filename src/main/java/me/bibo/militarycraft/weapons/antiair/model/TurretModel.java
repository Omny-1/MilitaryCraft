package me.bibo.militarycraft.weapons.antiair.model;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Procedural description of a Phalanx-style CIWS anti-air turret, shaped after
 * the reference image: a grey deck pedestal, a boxy traversing body, the iconic
 * large WHITE radar dome leaning forward on top, and a six-barrel rotary cannon
 * (the M61 Vulcan) that sticks out the front and elevates.
 *
 * <p>All coordinates are in turret space (origin at the base centre on the
 * ground, +X right, +Y up, +Z forward = the bore at yaw/pitch 0). Reshape the
 * turret by editing the numbers here; the motion math lives in {@link Transforms}.
 */
public final class TurretModel {

    private TurretModel() {
    }

    /** Trunnion: the gun barrels elevate (pitch) about this point. Must have x=0. */
    public static final Vector3f GUN_PIVOT = new Vector3f(0f, 2.05f, 0.15f);

    /** Muzzle point in turret space (barrel tips) - bullets are born here. */
    public static final Vector3f MUZZLE = new Vector3f(0f, 2.05f, 2.7f);

    /** A point roughly at the gun's optical centre, used to aim FROM when tracking. */
    public static final Vector3f SIGHT = new Vector3f(0f, 2.05f, 0.15f);

    // ---- hitbox (axis-aligned Interaction proxy: clickable + damageable) ----
    public static final Vector3f HITBOX_CENTER = new Vector3f(0f, 2.5f, 0f);
    public static final float HITBOX_WIDTH = 2.9f;
    public static final float HITBOX_HEIGHT = 5.0f;

    // ---- approximate key points for explosion-distance measurement ----
    public static final List<Vector3f> BLAST_POINTS = List.of(
            new Vector3f(0f, 0.5f, 0f),   // pedestal
            new Vector3f(0f, 1.7f, 0f),   // body
            new Vector3f(0f, 3.4f, 0f),   // dome
            new Vector3f(0f, 2.05f, 1.6f) // gun
    );

    private static String cacheKey;
    private static List<Part> cached;

    /**
     * Build (and cache) the turret's parts. The {@code rounded} flag is baked into
     * the cache key; the part list must stay stable for a given config so persisted
     * turrets rehydrate correctly.
     */
    public static synchronized List<Part> parts(boolean rounded) {
        String key = String.valueOf(rounded);
        if (cached != null && key.equals(cacheKey)) {
            return cached;
        }
        List<Part> p = new ArrayList<>();
        buildBase(p);
        buildBody(p, rounded);
        buildRadome(p, rounded);
        buildGun(p, rounded);
        cached = Collections.unmodifiableList(p);
        cacheKey = key;
        return cached;
    }

    // ------------------------------------------------------------------- base

    private static void buildBase(List<Part> p) {
        // Deck pedestal (fixed) - a wide grey mounting box with a trim ring.
        p.add(Part.of(Part.Role.BASE, Part.Pivot.BASE,
                new Vector3f(0f, 0.45f, 0f), new Vector3f(2.3f, 0.9f, 2.3f)));
        p.add(Part.of(Part.Role.DETAIL, Part.Pivot.BASE,
                new Vector3f(0f, 0.92f, 0f), new Vector3f(1.8f, 0.18f, 1.8f)));
        // Anchor bolts at the four corners.
        for (float sx : new float[]{-1f, 1f}) {
            for (float sz : new float[]{-1f, 1f}) {
                p.add(Part.of(Part.Role.DETAIL, Part.Pivot.BASE,
                        new Vector3f(sx * 0.95f, 0.18f, sz * 0.95f), new Vector3f(0.25f, 0.36f, 0.25f)));
            }
        }
    }

    // ------------------------------------------------------------------- body

    private static void buildBody(List<Part> p, boolean rounded) {
        // Rotating column the whole assembly sits on.
        p.add(Part.of(Part.Role.HOUSING, Part.Pivot.TURRET,
                new Vector3f(0f, 1.15f, 0f), new Vector3f(1.35f, 0.55f, 1.35f)));
        if (rounded) {
            p.add(Part.of(Part.Role.HOUSING, Part.Pivot.TURRET,
                    new Vector3f(0f, 1.15f, 0f), new Vector3f(1.0f, 0.55f, 1.0f), 0f, 45f, 0f));
        }

        // Main traversing body (the grey box that swings left/right).
        p.add(Part.of(Part.Role.HOUSING, Part.Pivot.TURRET,
                new Vector3f(0f, 1.75f, -0.05f), new Vector3f(2.0f, 1.35f, 2.1f)));
        // Sloped front plate (gun comes out below it).
        p.add(Part.of(Part.Role.HOUSING, Part.Pivot.TURRET,
                new Vector3f(0f, 1.55f, 1.05f), new Vector3f(1.7f, 1.0f, 0.55f), -22f, 0f, 0f));
        // Rear avionics box.
        p.add(Part.of(Part.Role.HOUSING, Part.Pivot.TURRET,
                new Vector3f(0f, 1.85f, -1.05f), new Vector3f(1.6f, 1.15f, 0.6f)));
        // Riveted side bands.
        for (float sx : new float[]{-1f, 1f}) {
            p.add(Part.of(Part.Role.DETAIL, Part.Pivot.TURRET,
                    new Vector3f(sx * 1.01f, 1.75f, 0f), new Vector3f(0.14f, 1.0f, 1.7f)));
        }
        // Neck supporting the dome.
        p.add(Part.of(Part.Role.HOUSING, Part.Pivot.TURRET,
                new Vector3f(0f, 2.5f, -0.1f), new Vector3f(1.25f, 0.7f, 1.25f)));
    }

    // ------------------------------------------------------------------ radome

    private static void buildRadome(List<Part> p, boolean rounded) {
        // The big white radar dome, leaning forward like the photo. Built as a
        // stack of tapering white drums; each drum leans further forward (+Z) and
        // is tilted, so the whole dome cants over the gun.
        float[][] drums = {
                // y,    z,     rx/rz,  ry,   pitch
                {2.95f, -0.05f, 1.5f, 0.85f, -12f},
                {3.55f, 0.05f, 1.55f, 0.85f, -12f},
                {4.12f, 0.2f, 1.3f, 0.75f, -12f},
                {4.55f, 0.34f, 0.95f, 0.55f, -12f},
                {4.85f, 0.45f, 0.55f, 0.45f, -12f},
        };
        for (float[] d : drums) {
            float y = d[0], z = d[1], r = d[2], ry = d[3], pitch = d[4];
            p.add(Part.of(Part.Role.RADOME, Part.Pivot.TURRET,
                    new Vector3f(0f, y, z), new Vector3f(r, ry, r), pitch, 0f, 0f));
            if (rounded && r > 0.7f) {
                p.add(Part.of(Part.Role.RADOME, Part.Pivot.TURRET,
                        new Vector3f(0f, y, z), new Vector3f(r * 0.8f, ry, r * 0.8f), pitch, 45f, 0f));
            }
        }
        // Dark sensor band / aperture on the front of the dome.
        p.add(Part.of(Part.Role.MUZZLE, Part.Pivot.TURRET,
                new Vector3f(0f, 3.5f, 1.05f), new Vector3f(1.0f, 0.5f, 0.25f), -12f, 0f, 0f));
    }

    // -------------------------------------------------------------------- gun

    private static void buildGun(List<Part> p, boolean rounded) {
        // Gun cradle (the housing the barrels are mounted in) - elevates.
        p.add(Part.of(Part.Role.GUNBODY, Part.Pivot.GUN,
                new Vector3f(0f, 2.05f, 0.55f), new Vector3f(1.1f, 0.85f, 1.0f)));
        // Cylindrical barrel shroud near the body.
        p.add(Part.of(Part.Role.GUNBODY, Part.Pivot.GUN,
                new Vector3f(0f, 2.05f, 1.15f), new Vector3f(0.95f, 0.95f, 0.8f)));
        if (rounded) {
            p.add(Part.of(Part.Role.GUNBODY, Part.Pivot.GUN,
                    new Vector3f(0f, 2.05f, 1.15f), new Vector3f(0.7f, 0.7f, 0.8f), 0f, 0f, 45f));
        }
        // Central hub the six barrels rotate around.
        p.add(Part.of(Part.Role.GUNBODY, Part.Pivot.GUN,
                new Vector3f(0f, 2.05f, 1.7f), new Vector3f(0.42f, 0.42f, 1.5f)));
        // Six barrels in a ring.
        int barrels = 6;
        float ringR = 0.3f;
        for (int i = 0; i < barrels; i++) {
            double a = (Math.PI * 2.0 * i) / barrels;
            float bx = (float) (Math.cos(a) * ringR);
            float by = (float) (Math.sin(a) * ringR);
            p.add(Part.of(Part.Role.BARREL, Part.Pivot.GUN,
                    new Vector3f(bx, 2.05f + by, 1.85f), new Vector3f(0.17f, 0.17f, 1.7f)));
        }
        // Muzzle ring at the tips.
        p.add(Part.of(Part.Role.MUZZLE, Part.Pivot.GUN,
                new Vector3f(0f, 2.05f, 2.62f), new Vector3f(0.82f, 0.82f, 0.28f)));
        if (rounded) {
            p.add(Part.of(Part.Role.MUZZLE, Part.Pivot.GUN,
                    new Vector3f(0f, 2.05f, 2.62f), new Vector3f(0.6f, 0.6f, 0.28f), 0f, 0f, 45f));
        }
    }
}
