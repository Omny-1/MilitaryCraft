package me.bibo.militarycraft.vehicles.airship.model;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Procedural description of a giant steampunk dirigible, shaped after the
 * reference image: a huge tan ovoid envelope with copper rib-bands and rivets,
 * brass portholes down the sides, a fan of tail fins, a brass nose cap,
 * a wooden gondola with brass trim and lit windows slung beneath on chains, two
 * engine pods, and a pair of hanging golden "acorn" ballast weights.
 *
 * <p>All coordinates are in airship space (origin at the gondola seat where the
 * pilot sits, +X right, +Y up, +Z forward = nose). Reshape the airship by
 * editing the numbers here; the orientation math lives entirely in
 * {@link Transforms}.
 */
public final class AirshipModel {

    private AirshipModel() {
    }

    // ---- Overall envelope dimensions (this is a VERY large airship) ----
    public static final float ENV_LENGTH = 30.0f;          // along Z
    public static final float ENV_RX = 6.0f;               // max half-width
    public static final float ENV_RY = 6.0f;               // max half-height
    public static final float ENV_CY = 13.0f;              // envelope centre height above gondola
    private static final float HALF_LEN = ENV_LENGTH / 2f; // = 15

    /**
     * A clickable / hittable Interaction proxy. Interaction entities are
     * axis-aligned boxes positioned (not rotated) at a local centre that follows
     * the airship's orientation, so we spread several along the envelope to make
     * the whole balloon — not just the gondola — boardable and damageable.
     */
    public record HitboxSpec(Vector3f center, float width, float height) {
    }

    public static final List<HitboxSpec> HITBOXES = List.of(
            new HitboxSpec(new Vector3f(0f, 0.5f, 0f), 6.0f, 5.0f),       // gondola (boarding)
            new HitboxSpec(new Vector3f(0f, ENV_CY, 8.5f), 9.0f, 10.0f),  // envelope fore
            new HitboxSpec(new Vector3f(0f, ENV_CY, 0f), 11.0f, 11.0f),   // envelope mid
            new HitboxSpec(new Vector3f(0f, ENV_CY, -8.5f), 9.0f, 10.0f)  // envelope aft
    );

    // ---- Approximate bounding box, used for collision sampling ----
    public static final float TOTAL_LENGTH = ENV_LENGTH + 4f;
    public static final float TOTAL_HEIGHT = ENV_CY + ENV_RY + 4f;
    public static final float GONDOLA_BOTTOM_Y = -2.0f;
    private static final float RIB_LAYER_OFFSET = 0.0125f;

    /** Belly point (airship space) bombs are released from. */
    public static final Vector3f BOMB_BAY = new Vector3f(0f, -1.6f, 0.6f);

    /** Top-of-gondola point the gas burner steam puffs from while boosting. */
    public static final Vector3f BURNER_POINT = new Vector3f(0f, 2.2f, 0f);

    /**
     * Passenger seat offsets (airship space), behind the pilot. The pilot rides
     * the core at the origin (the helm); these are the extra rider seats. The
     * total capacity is 1 (pilot) + SEAT_OFFSETS.size().
     */
    public static final List<Vector3f> SEAT_OFFSETS = List.of(
            new Vector3f(0f, 0.0f, -1.7f),
            new Vector3f(0f, 0.0f, -3.1f)
    );

    /**
     * Engine pod centres (the steam/smoke trail streams from here too). Held well
     * OUTBOARD of the envelope's widest point (ENV_RX) on a pylon, so the pods sit
     * on the outside of the balloon instead of buried in its skin.
     */
    public static final List<Vector3f> ENGINE_POINTS = List.of(
            new Vector3f(-(ENV_RX + 1.7f), ENV_CY - 2.0f, -HALF_LEN * 0.40f),
            new Vector3f(ENV_RX + 1.7f, ENV_CY - 2.0f, -HALF_LEN * 0.40f)
    );

    /**
     * Key points across the whole hull (airship space) used to measure how close
     * an explosion is to the (huge) airship — the closest of these is the
     * effective contact distance, so a creeper next to the envelope counts even
     * though the anchor is way down at the gondola.
     */
    public static final List<Vector3f> BLAST_POINTS = List.of(
            new Vector3f(0f, 0f, 0f),                 // gondola
            new Vector3f(0f, ENV_CY, 0f),             // envelope centre
            new Vector3f(0f, ENV_CY, HALF_LEN * 0.7f),// forward envelope
            new Vector3f(0f, ENV_CY, -HALF_LEN * 0.7f),// aft envelope
            new Vector3f(0f, ENV_CY - ENV_RY, 0f)     // envelope belly
    );

    private static String cacheKey;
    private static List<Part> cached;

    /** Envelope cross-section radius profile at u in [-1, 1] (0 = centre). */
    private static float profile(float u) {
        float v = 1f - u * u;
        if (v <= 0f) {
            return 0f;
        }
        return (float) Math.pow(v, 0.58);
    }

    /**
     * Build (and cache) the airship's parts. The {@code rounded} flag and
     * {@code shipName} are baked into the cache key; the part list must stay
     * stable for a given config so persisted airships rehydrate correctly.
     */
    public static synchronized List<Part> parts(boolean rounded, String shipName) {
        String key = rounded + "|" + (shipName == null ? "" : shipName);
        if (cached != null && Objects.equals(cacheKey, key)) {
            return cached;
        }
        List<Part> p = new ArrayList<>();

        buildEnvelope(p, rounded);
        buildRibs(p, rounded);
        buildNose(p);
        buildTail(p);
        buildPortholes(p);
        buildEngines(p);
        buildGondola(p);
        buildRigging(p);
        buildBallast(p);
        buildName(p, shipName);

        cached = Collections.unmodifiableList(p);
        cacheKey = key;
        return cached;
    }

    // ----------------------------------------------------------------- envelope

    private static void buildEnvelope(List<Part> p, boolean rounded) {
        final int rings = 16;
        float segLen = (ENV_LENGTH / rings) * 1.18f; // slight overlap, no gaps
        for (int i = 0; i < rings; i++) {
            float t = i / (float) (rings - 1);
            float u = -1f + 2f * t;
            float prof = profile(u);
            if (prof < 0.16f) {
                continue; // bare tips are handled by the nose/tail caps
            }
            float rx = ENV_RX * prof;
            float ry = ENV_RY * prof;
            float z = u * HALF_LEN;
            p.add(Part.block(Part.Role.ENVELOPE,
                    new Vector3f(0f, ENV_CY, z), new Vector3f(2f * rx, 2f * ry, segLen)));
            if (rounded && prof > 0.40f) {
                // a 45°-rotated, slightly smaller duplicate -> octagonal, rounder
                p.add(Part.block(Part.Role.ENVELOPE,
                        new Vector3f(0f, ENV_CY, z),
                        new Vector3f(2f * rx * 0.80f, 2f * ry * 0.80f, segLen),
                        0f, 0f, 45f));
            }
        }
    }

    private static void buildRibs(List<Part> p, boolean rounded) {
        float[] stations = {-0.66f, -0.33f, 0f, 0.33f, 0.66f};
        for (float u : stations) {
            float prof = profile(u);
            float rx = ENV_RX * prof + 0.28f;
            float ry = ENV_RY * prof + 0.28f;
            float z = u * HALF_LEN;
            p.add(Part.block(Part.Role.RIB,
                    new Vector3f(0f, ENV_CY, z - RIB_LAYER_OFFSET), new Vector3f(2f * rx, 2f * ry, 0.5f)));
            if (rounded) {
                p.add(Part.block(Part.Role.RIB,
                        new Vector3f(0f, ENV_CY, z + RIB_LAYER_OFFSET),
                        new Vector3f(2f * rx * 0.80f, 2f * ry * 0.80f, 0.5f),
                        0f, 0f, 45f));
            }
        }
    }

    private static void buildNose(List<Part> p) {
        // rounded brass nose boss at the very front
        p.add(Part.block(Part.Role.FITTING,
                new Vector3f(0f, ENV_CY, HALF_LEN - 0.6f), new Vector3f(2.6f, 2.6f, 1.6f)));
        p.add(Part.block(Part.Role.FITTING,
                new Vector3f(0f, ENV_CY, HALF_LEN + 0.3f), new Vector3f(1.6f, 1.6f, 1.2f)));
    }

    private static void buildTail(List<Part> p) {
        float tailZ = -HALF_LEN + 1.2f;
        // tapered tail cone
        p.add(Part.block(Part.Role.FITTING,
                new Vector3f(0f, ENV_CY, -HALF_LEN - 0.2f), new Vector3f(2.0f, 2.0f, 1.8f)));

        float armV = ENV_RY * 0.45f + 1.6f; // how far the vertical fins stand out
        float armH = ENV_RX * 0.45f + 1.6f;
        Vector3f finV = new Vector3f(0.5f, 3.6f, 4.2f);
        Vector3f finH = new Vector3f(3.6f, 0.5f, 4.2f);

        // top + bottom vertical fins
        p.add(Part.block(Part.Role.FIN,
                new Vector3f(0f, ENV_CY + armV, tailZ - 1.0f), finV));
        p.add(Part.block(Part.Role.FIN,
                new Vector3f(0f, ENV_CY - armV, tailZ - 1.0f), finV));
        // left + right horizontal fins
        p.add(Part.block(Part.Role.FIN,
                new Vector3f(-armH, ENV_CY, tailZ - 1.0f), finH));
        p.add(Part.block(Part.Role.FIN,
                new Vector3f(armH, ENV_CY, tailZ - 1.0f), finH));
        // copper leading-edge accents on the fins (the "fan" ribbing)
        p.add(Part.block(Part.Role.RIB,
                new Vector3f(0f, ENV_CY + armV + 0.4f, tailZ + 0.4f), new Vector3f(0.6f, 1.4f, 0.6f)));
        p.add(Part.block(Part.Role.RIB,
                new Vector3f(-armH - 0.4f, ENV_CY, tailZ + 0.4f), new Vector3f(1.4f, 0.6f, 0.6f)));
        p.add(Part.block(Part.Role.RIB,
                new Vector3f(armH + 0.4f, ENV_CY, tailZ + 0.4f), new Vector3f(1.4f, 0.6f, 0.6f)));
    }

    private static void buildPortholes(List<Part> p) {
        float[] stations = {-0.35f, 0.35f};
        for (float u : stations) {
            float prof = profile(u);
            float rx = ENV_RX * prof;
            float z = u * HALF_LEN;
            float y = ENV_CY - 0.8f;
            p.add(Part.block(Part.Role.FITTING,
                    new Vector3f(rx - 0.15f, y, z), new Vector3f(1.0f, 1.0f, 1.0f)));
            p.add(Part.block(Part.Role.FITTING,
                    new Vector3f(-(rx - 0.15f), y, z), new Vector3f(1.0f, 1.0f, 1.0f)));
        }
    }

    private static void buildEngines(List<Part> p) {
        for (Vector3f e : ENGINE_POINTS) {
            float sx = Math.signum(e.x);
            // brass pylon: reaches from inside the envelope flank out to the pod,
            // so the pod hangs on the OUTSIDE of the balloon instead of inside it
            p.add(Part.block(Part.Role.FITTING,
                    new Vector3f(e.x - sx * 1.6f, e.y, e.z), new Vector3f(3.0f, 0.4f, 0.5f)));
            p.add(Part.block(Part.Role.RIB,
                    new Vector3f(e.x - sx * 1.6f, e.y - 0.6f, e.z), new Vector3f(2.6f, 0.3f, 0.3f), 0f, 0f, sx * 22f));
            // engine pod
            p.add(Part.block(Part.Role.ENGINE,
                    new Vector3f(e.x, e.y, e.z), new Vector3f(1.6f, 1.6f, 2.4f)));
            // propeller hub + blades behind the pod
            p.add(Part.block(Part.Role.FITTING,
                    new Vector3f(e.x, e.y, e.z - 1.5f), new Vector3f(0.5f, 0.5f, 0.5f)));
            p.add(Part.block(Part.Role.PROPELLER,
                    new Vector3f(e.x, e.y, e.z - 1.6f), new Vector3f(0.25f, 2.8f, 0.25f)));
            p.add(Part.block(Part.Role.PROPELLER,
                    new Vector3f(e.x, e.y, e.z - 1.6f), new Vector3f(2.8f, 0.25f, 0.25f)));
        }
    }

    // ----------------------------------------------------------------- gondola

    private static void buildGondola(List<Part> p) {
        final float len = 8.0f, w = 3.4f, h = 2.2f, cy = -0.2f;
        final float top = cy + h / 2f;     // deck level
        final float side = w / 2f;
        Part.Role HULL = Part.Role.GONDOLA;   // copper hull
        Part.Role TRIM = Part.Role.TRIM;      // brass cut-copper trim
        Part.Role GLASS = Part.Role.WINDOW;
        Part.Role BRASS = Part.Role.FITTING;  // gold/brass fittings
        Part.Role STACK = Part.Role.ENGINE;   // dark steam funnel

        // ---- copper hull: boat-like with tapered bow & stern ----
        p.add(Part.block(HULL, new Vector3f(0f, cy, 0f), new Vector3f(w, h, len * 0.72f)));
        p.add(Part.block(HULL, new Vector3f(0f, cy, len * 0.40f), new Vector3f(w * 0.72f, h * 0.86f, len * 0.26f)));
        p.add(Part.block(HULL, new Vector3f(0f, cy, -len * 0.40f), new Vector3f(w * 0.72f, h * 0.86f, len * 0.26f)));
        // rounded lower hull + deep keel ridge
        p.add(Part.block(HULL, new Vector3f(0f, cy - 1.05f, 0f), new Vector3f(w * 0.72f, 0.7f, len * 0.86f)));
        p.add(Part.block(HULL, new Vector3f(0f, cy - 1.5f, 0f), new Vector3f(w * 0.42f, 0.5f, len * 0.62f)));
        p.add(Part.block(TRIM, new Vector3f(0f, cy - 1.55f, 0f), new Vector3f(w * 0.26f, 0.22f, len * 0.6f)));

        // ---- riveted brass plating bands around the hull ----
        for (float z : new float[]{-2.2f, 0f, 2.2f}) {
            p.add(Part.block(TRIM, new Vector3f(0f, cy, z), new Vector3f(w + 0.14f, h + 0.14f, 0.35f)));
        }

        // ---- gunwale cap + railing (posts + top rails) ----
        p.add(Part.block(TRIM, new Vector3f(0f, top + 0.15f, 0f), new Vector3f(w + 0.3f, 0.25f, len + 0.2f)));
        for (float z : new float[]{-3.0f, 0f, 3.0f}) {
            p.add(Part.block(BRASS, new Vector3f(side + 0.05f, top + 0.6f, z), new Vector3f(0.16f, 0.8f, 0.16f)));
            p.add(Part.block(BRASS, new Vector3f(-(side + 0.05f), top + 0.6f, z), new Vector3f(0.16f, 0.8f, 0.16f)));
        }
        p.add(Part.block(TRIM, new Vector3f(side + 0.05f, top + 0.98f, 0f), new Vector3f(0.18f, 0.18f, len - 0.6f)));
        p.add(Part.block(TRIM, new Vector3f(-(side + 0.05f), top + 0.98f, 0f), new Vector3f(0.18f, 0.18f, len - 0.6f)));

        // ---- round brass portholes + glass down each side ----
        for (float z : new float[]{-1.9f, 0f, 1.9f}) {
            p.add(Part.block(BRASS, new Vector3f(side - 0.02f, cy + 0.1f, z), new Vector3f(0.2f, 0.95f, 0.95f)));
            p.add(Part.block(BRASS, new Vector3f(-(side - 0.02f), cy + 0.1f, z), new Vector3f(0.2f, 0.95f, 0.95f)));
            p.add(Part.block(GLASS, new Vector3f(side - 0.06f, cy + 0.1f, z), new Vector3f(0.14f, 0.6f, 0.6f)));
            p.add(Part.block(GLASS, new Vector3f(-(side - 0.06f), cy + 0.1f, z), new Vector3f(0.14f, 0.6f, 0.6f)));
        }

        // ---- forward wheelhouse / bridge ----
        float bz = len * 0.27f;
        p.add(Part.block(HULL, new Vector3f(0f, top + 0.55f, bz), new Vector3f(w * 0.82f, 1.1f, 1.9f)));
        p.add(Part.block(TRIM, new Vector3f(0f, top + 1.18f, bz), new Vector3f(w * 0.9f, 0.2f, 2.0f)));
        p.add(Part.block(GLASS, new Vector3f(0f, top + 0.6f, bz + 0.98f), new Vector3f(w * 0.66f, 0.7f, 0.12f)));
        p.add(Part.block(GLASS, new Vector3f(w * 0.40f, top + 0.6f, bz), new Vector3f(0.12f, 0.7f, 1.4f)));
        p.add(Part.block(GLASS, new Vector3f(-w * 0.40f, top + 0.6f, bz), new Vector3f(0.12f, 0.7f, 1.4f)));
        // ship's wheel at the helm
        p.add(Part.block(BRASS, new Vector3f(0f, top + 0.35f, bz + 0.7f), new Vector3f(0.95f, 0.95f, 0.18f)));

        // ---- bow ornament / prow ----
        p.add(Part.block(BRASS, new Vector3f(0f, cy, len * 0.54f), new Vector3f(0.6f, 1.0f, 1.2f)));
        p.add(Part.block(BRASS, new Vector3f(0f, cy + 0.25f, len * 0.62f), new Vector3f(0.32f, 0.32f, 1.1f), -10f, 0f, 0f));
        // ---- stern cap + rudder fin ----
        p.add(Part.block(BRASS, new Vector3f(0f, cy, -len * 0.54f), new Vector3f(0.7f, 1.1f, 0.9f)));
        p.add(Part.block(TRIM, new Vector3f(0f, cy + 0.35f, -len * 0.6f), new Vector3f(0.25f, 1.4f, 0.5f)));

        // ---- steam funnel behind the bridge ----
        p.add(Part.block(STACK, new Vector3f(0f, top + 1.0f, -0.6f), new Vector3f(0.7f, 1.5f, 0.7f)));
        p.add(Part.block(TRIM, new Vector3f(0f, top + 1.75f, -0.6f), new Vector3f(0.88f, 0.25f, 0.88f)));

        // ---- hanging brass lanterns at bow & stern ----
        p.add(Part.block(BRASS, new Vector3f(0f, top + 0.2f, len * 0.5f + 0.3f), new Vector3f(0.3f, 0.5f, 0.3f)));
        p.add(Part.block(BRASS, new Vector3f(0f, top + 0.2f, -len * 0.5f - 0.3f), new Vector3f(0.3f, 0.5f, 0.3f)));
    }

    private static void buildRigging(List<Part> p) {
        float top = 1.2f;                       // gondola deck
        float bottom = ENV_CY - ENV_RY + 0.2f;  // envelope belly
        float midY = (top + bottom) / 2f;
        float height = (bottom - top);
        float[][] corners = {{2.3f, 2.6f}, {-2.3f, 2.6f}, {2.3f, -2.6f}, {-2.3f, -2.6f}};
        for (float[] c : corners) {
            p.add(Part.block(Part.Role.RIGGING,
                    new Vector3f(c[0], midY, c[1]), new Vector3f(0.3f, height, 0.3f)));
        }
        // two central support cables, slightly fore and aft
        p.add(Part.block(Part.Role.RIGGING,
                new Vector3f(0f, midY, 1.4f), new Vector3f(0.3f, height, 0.3f)));
        p.add(Part.block(Part.Role.RIGGING,
                new Vector3f(0f, midY, -1.4f), new Vector3f(0.3f, height, 0.3f)));
    }

    private static void buildBallast(List<Part> p) {
        float[] zs = {2.6f, -2.6f};
        for (float z : zs) {
            // short chain hanging from the keel
            p.add(Part.block(Part.Role.RIGGING,
                    new Vector3f(0f, -2.2f, z), new Vector3f(0.25f, 1.0f, 0.25f)));
            // golden acorn body + tip
            p.add(Part.block(Part.Role.BALLAST,
                    new Vector3f(0f, -3.0f, z), new Vector3f(1.1f, 1.4f, 1.1f)));
            p.add(Part.block(Part.Role.FITTING,
                    new Vector3f(0f, -3.9f, z), new Vector3f(0.5f, 0.7f, 0.5f)));
        }
    }

    private static void buildName(List<Part> p, String shipName) {
        // Always present (empty => invisible) so the part count never depends on
        // the name, keeping rehydration stable. Painted on both envelope flanks.
        String name = (shipName == null) ? "" : shipName;
        float y = ENV_CY + 0.5f;
        p.add(new Part(Part.Role.FITTING, new Vector3f(ENV_RX - 0.2f, y, 0f),
                new Vector3f(1f, 1f, 1f), 0f, -90f, 0f, name));
        p.add(new Part(Part.Role.FITTING, new Vector3f(-(ENV_RX - 0.2f), y, 0f),
                new Vector3f(1f, 1f, 1f), 0f, 90f, 0f, name));
    }
}
