package me.bibo.militarycraft.weapons.tckbus;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Procedural description of a Volkswagen-T4-style cargo van, modelled after the
 * reference photo: a long olive panel van with a short raked nose, a tall cargo
 * box, steel wheels, side mirrors, a roof rack and pale + black pixel-camo patches.
 *
 * <p>All coordinates are in "TckBusRig space": origin at the centre of the footprint on
 * the ground, +X right, +Y up, +Z forward (the nose / grille). The whole van is
 * rotated by the placement yaw at render time ({@link #forPart}); the model itself
 * is a fixed list of cubes, built once and cached. Reshape the van by editing the
 * numbers here — nothing else needs to change.
 */
public final class TckBusModel {

    private TckBusModel() {
    }

    /** Which configured material a part is rendered with. */
    public enum Role {
        BODY, CAMO_LIGHT, CAMO_MID, CAMO_BLACK, GLASS, WHEEL, TRIM, CHROME, LIGHT
    }

    /** One immutable cube of the model, in TckBusRig space. */
    public static final class Part {
        public final Role role;
        public final Vector3f offset;
        public final Vector3f scale;
        public final float pitch;
        public final float yaw;
        public final float roll;

        Part(Role role, Vector3f offset, Vector3f scale, float pitch, float yaw, float roll) {
            this.role = role;
            this.offset = offset;
            this.scale = scale;
            this.pitch = pitch;
            this.yaw = yaw;
            this.roll = roll;
        }
    }

    /** A clickable / damageable Interaction segment (square footprint). */
    public record HitBox(Vector3f center, float width, float height) {
    }

    /** A side sign ("TCK"): a TckBusRig-space anchor point and the outward yaw to face. */
    public record TextSide(Vector3f pos, float baseYaw) {
    }

    // ---- capture geometry ----------------------------------------------------

    /** Where a captured player is dragged to: the right-hand sliding door mouth. */
    public static final Vector3f DOOR = new Vector3f(1.35f, 1.0f, -0.3f);
    /** Where the two escorting workers end up, flanking the door. */
    public static final Vector3f DOOR_FLANK_A = new Vector3f(2.0f, 0.0f, 0.5f);
    public static final Vector3f DOOR_FLANK_B = new Vector3f(2.0f, 0.0f, -1.2f);

    /** Interaction proxies tiled along the long van so it's clickable end-to-end. */
    public static final List<HitBox> HITBOXES = List.of(
            new HitBox(new Vector3f(0f, 1.25f, 1.7f), 2.5f, 2.4f),
            new HitBox(new Vector3f(0f, 1.25f, -0.2f), 2.5f, 2.4f),
            new HitBox(new Vector3f(0f, 1.25f, -2.1f), 2.5f, 2.4f)
    );

    /** Key points used to measure an explosion's distance to the van. */
    public static final List<Vector3f> BLAST_POINTS = List.of(
            new Vector3f(0f, 1.2f, 2.6f),
            new Vector3f(0f, 1.2f, 0.8f),
            new Vector3f(0f, 1.2f, -1.0f),
            new Vector3f(0f, 1.2f, -2.8f),
            new Vector3f(0f, 2.2f, -0.7f)
    );

    /** The two "TCK" signs, one per side, facing outward. */
    public static final List<TextSide> TEXT_SIDES = List.of(
            new TextSide(new Vector3f(1.19f, 1.72f, -0.45f), -90f),
            new TextSide(new Vector3f(-1.19f, 1.72f, -0.45f), 90f)
    );

    private static String cacheKey;
    private static List<Part> cached;

    /**
     * Build (and cache) the van's parts. {@code rounded} is baked into the cache
     * key; the list must stay stable for a given setting so persisted buses
     * rehydrate against the same indices.
     */
    public static synchronized List<Part> parts(boolean rounded, boolean camo) {
        String key = rounded + "|" + camo;
        if (cached != null && key.equals(cacheKey)) {
            return cached;
        }
        List<Part> p = new ArrayList<>();
        buildBody(p);
        buildGlass(p);
        buildFront(p);
        buildWheels(p, rounded);
        buildTrim(p);
        buildRoofRack(p);
        if (camo) {
            buildCamo(p);
        }
        cached = Collections.unmodifiableList(p);
        cacheKey = key;
        return cached;
    }

    // ----------------------------------------------------------------- helpers

    private static void box(List<Part> p, Role role, float cx, float cy, float cz,
                            float sx, float sy, float sz) {
        p.add(new Part(role, new Vector3f(cx, cy, cz), new Vector3f(sx, sy, sz), 0, 0, 0));
    }

    private static void boxR(List<Part> p, Role role, float cx, float cy, float cz,
                             float sx, float sy, float sz, float pitch, float yaw, float roll) {
        p.add(new Part(role, new Vector3f(cx, cy, cz), new Vector3f(sx, sy, sz), pitch, yaw, roll));
    }

    // -------------------------------------------------------------------- body

    private static void buildBody(List<Part> p) {
        // Lower body / sills (a long olive box).
        box(p, Role.BODY, 0f, 0.95f, -0.1f, 2.2f, 0.85f, 5.9f);
        // Upper cargo box, slightly wider, set back from the nose.
        box(p, Role.BODY, 0f, 1.78f, -0.55f, 2.26f, 1.0f, 4.7f);
        // Roof skin.
        box(p, Role.BODY, 0f, 2.3f, -0.7f, 2.18f, 0.16f, 4.5f);
        // Rear wall (where the loot drops from) with two slim windows.
        // Top sits flush with the roof (~y 2.38), bottom on the sills.
        box(p, Role.BODY, 0f, 1.56f, -3.0f, 2.18f, 1.63f, 0.18f);
        box(p, Role.GLASS, 0.5f, 1.95f, -3.07f, 0.6f, 0.5f, 0.06f);
        box(p, Role.GLASS, -0.5f, 1.95f, -3.07f, 0.6f, 0.5f, 0.06f);
        // Rear bumper handled in trim. Cab floor pan under the windscreen.
        box(p, Role.BODY, 0f, 1.25f, 2.1f, 2.1f, 0.7f, 1.3f);
    }

    private static void buildGlass(List<Part> p) {
        // Raked windscreen between the nose and the roof.
        boxR(p, Role.GLASS, 0f, 1.98f, 1.96f, 1.98f, 1.02f, 0.16f, -33f, 0f, 0f);
        // Front-door windows (one each side of the cab).
        box(p, Role.GLASS, 1.085f, 1.86f, 1.32f, 0.1f, 0.56f, 0.95f);
        box(p, Role.GLASS, -1.085f, 1.86f, 1.32f, 0.1f, 0.56f, 0.95f);
        // A-pillar/cab corner glass.
        box(p, Role.GLASS, 1.06f, 1.86f, 0.55f, 0.1f, 0.5f, 0.5f);
        box(p, Role.GLASS, -1.06f, 1.86f, 0.55f, 0.1f, 0.5f, 0.5f);
    }

    private static void buildFront(List<Part> p) {
        // Short, near-flat hood from the windscreen base out to the nose.
        boxR(p, Role.BODY, 0f, 1.62f, 2.4f, 2.05f, 0.3f, 1.25f, -8f, 0f, 0f);
        // Upright front face of the van - no big raked wedge.
        box(p, Role.BODY, 0f, 1.2f, 2.98f, 2.05f, 0.95f, 0.32f);
        // Slim grille + VW badge, just proud of the face.
        box(p, Role.TRIM, 0f, 1.3f, 3.16f, 1.2f, 0.3f, 0.06f);
        box(p, Role.CHROME, 0f, 1.4f, 3.2f, 0.24f, 0.24f, 0.05f);
        // Round headlights.
        box(p, Role.LIGHT, 0.78f, 1.46f, 3.16f, 0.4f, 0.26f, 0.08f);
        box(p, Role.LIGHT, -0.78f, 1.46f, 3.16f, 0.4f, 0.26f, 0.08f);

        // ---- bumper: LOW, full-width bar at the very front, clearly proud ----
        box(p, Role.TRIM, 0f, 0.6f, 3.06f, 2.12f, 0.34f, 0.38f);
        box(p, Role.CHROME, 0f, 0.64f, 3.27f, 0.5f, 0.2f, 0.04f);         // number plate

        // Side mirrors (arm + glass head).
        box(p, Role.TRIM, 1.2f, 1.78f, 2.0f, 0.28f, 0.08f, 0.1f);
        box(p, Role.CHROME, 1.4f, 1.82f, 1.94f, 0.1f, 0.36f, 0.22f);
        box(p, Role.TRIM, -1.2f, 1.78f, 2.0f, 0.28f, 0.08f, 0.1f);
        box(p, Role.CHROME, -1.4f, 1.82f, 1.94f, 0.1f, 0.36f, 0.22f);
    }

    private static void buildWheels(List<Part> p, boolean rounded) {
        float[] axles = {1.95f, -1.95f};
        for (float az : axles) {
            for (float sx : new float[]{1.02f, -1.02f}) {
                // Tyre: a disc in the Y-Z plane, thin along X.
                box(p, Role.WHEEL, sx, 0.5f, az, 0.32f, 0.92f, 0.92f);
                if (rounded) {
                    boxR(p, Role.WHEEL, sx, 0.5f, az, 0.3f, 0.66f, 0.66f, 45f, 0f, 0f);
                }
                // Steel hubcap, just outboard of the tyre.
                float hub = sx > 0 ? sx + 0.13f : sx - 0.13f;
                box(p, Role.CHROME, hub, 0.5f, az, 0.08f, 0.44f, 0.44f);
                if (rounded) {
                    boxR(p, Role.CHROME, hub, 0.5f, az, 0.08f, 0.32f, 0.32f, 45f, 0f, 0f);
                }
                // Wheel arch flare above the tyre.
                box(p, Role.BODY, sx > 0 ? 1.13f : -1.13f, 1.0f, az, 0.12f, 0.5f, 1.2f);
            }
        }
    }

    private static void buildTrim(List<Part> p) {
        // Rear bumper.
        box(p, Role.TRIM, 0f, 0.76f, -3.06f, 2.12f, 0.42f, 0.3f);
        // Side rub strips along each flank.
        box(p, Role.TRIM, 1.135f, 1.32f, -0.4f, 0.06f, 0.14f, 5.0f);
        box(p, Role.TRIM, -1.135f, 1.32f, -0.4f, 0.06f, 0.14f, 5.0f);
        // Door-seam hints on the right (sliding) and left flanks.
        box(p, Role.TRIM, 1.14f, 1.7f, 0.55f, 0.04f, 0.95f, 0.06f);
        box(p, Role.TRIM, 1.14f, 1.7f, -1.5f, 0.04f, 0.95f, 0.06f);
        box(p, Role.TRIM, -1.14f, 1.7f, 0.55f, 0.04f, 0.95f, 0.06f);
        // Exhaust tip.
        box(p, Role.TRIM, 0.8f, 0.42f, -3.12f, 0.14f, 0.14f, 0.28f);
        // Rear lights.
        box(p, Role.LIGHT, 0.85f, 1.0f, -3.08f, 0.3f, 0.5f, 0.06f);
        box(p, Role.LIGHT, -0.85f, 1.0f, -3.08f, 0.3f, 0.5f, 0.06f);
    }

    private static void buildRoofRack(List<Part> p) {
        // Two long rails.
        box(p, Role.TRIM, 0.82f, 2.46f, -0.7f, 0.1f, 0.14f, 4.3f);
        box(p, Role.TRIM, -0.82f, 2.46f, -0.7f, 0.1f, 0.14f, 4.3f);
        // Cross bars.
        for (float z : new float[]{1.3f, 0.2f, -1.0f, -2.2f}) {
            box(p, Role.TRIM, 0f, 2.48f, z, 1.86f, 0.08f, 0.12f);
        }
    }

    private static void buildCamo(List<Part> p) {
        // Pale + secondary-green + black patches, slightly proud of the body so
        // they read as painted pixel camo. Right flank (+X), left flank (-X),
        // roof, hood and rear get a scattered, asymmetric spread.
        float rx = 1.155f, lx = -1.155f;
        // right flank
        sidePatch(p, Role.CAMO_LIGHT, rx, 1.55f, 1.0f, 0.7f, 0.9f);
        sidePatch(p, Role.CAMO_BLACK, rx, 1.95f, 0.35f, 0.35f, 0.35f);
        sidePatch(p, Role.CAMO_MID, rx, 1.45f, -0.9f, 0.85f, 0.7f);
        sidePatch(p, Role.CAMO_LIGHT, rx, 2.0f, -1.7f, 0.5f, 0.5f);
        sidePatch(p, Role.CAMO_BLACK, rx, 1.6f, -2.5f, 0.3f, 0.5f);
        // left flank (mirror-ish, but shifted so it isn't identical)
        sidePatch(p, Role.CAMO_LIGHT, lx, 1.65f, -0.5f, 0.8f, 0.85f);
        sidePatch(p, Role.CAMO_BLACK, lx, 1.4f, 0.8f, 0.35f, 0.45f);
        sidePatch(p, Role.CAMO_MID, lx, 1.95f, 1.6f, 0.6f, 0.7f);
        sidePatch(p, Role.CAMO_LIGHT, lx, 1.5f, -2.2f, 0.55f, 0.6f);
        sidePatch(p, Role.CAMO_BLACK, lx, 2.0f, -1.3f, 0.3f, 0.35f);
        // roof
        roofPatch(p, Role.CAMO_LIGHT, 0.4f, 0.6f, 0.9f, 1.0f);
        roofPatch(p, Role.CAMO_BLACK, -0.5f, -1.4f, 0.6f, 0.6f);
        roofPatch(p, Role.CAMO_MID, 0.3f, -2.3f, 0.8f, 0.7f);
        // hood / nose
        boxR(p, Role.CAMO_LIGHT, 0.35f, 1.66f, 2.55f, 0.7f, 0.06f, 0.7f, -16f, 0f, 0f);
        boxR(p, Role.CAMO_BLACK, -0.5f, 1.55f, 3.02f, 0.4f, 0.5f, 0.06f, -12f, 0f, 0f);
    }

    /** A thin camo slab lying on a side flank (thin along X). */
    private static void sidePatch(List<Part> p, Role role, float x, float y, float z, float h, float w) {
        box(p, role, x, y, z, 0.06f, h, w);
    }

    /** A thin camo slab lying on the roof (thin along Y). */
    private static void roofPatch(List<Part> p, Role role, float x, float z, float sx, float sz) {
        box(p, role, x, 2.39f, z, sx, 0.06f, sz);
    }

    // ----------------------------------------------------------------- transforms

    /** Yaw-only rotation about +Y, matching Minecraft's yaw sense. */
    public static Quaternionf yawQ(double yawDeg) {
        return new Quaternionf().rotateY((float) Math.toRadians(-yawDeg));
    }

    /** Map a TckBusRig-space point to a world-space offset from the anchor, for a yaw. */
    public static Vector3f pointToWorld(Vector3f local, double busYaw) {
        return yawQ(busYaw).transform(new Vector3f(local));
    }

    /**
     * Build the display transformation for a part. The display sits at the TckBusRig
     * anchor (footprint centre on the ground) with no rotation of its own; the
     * part's offset, base rotation and the overall TckBusRig yaw are baked in here.
     */
    public static Transformation forPart(Part part, double busYaw) {
        Quaternionf baseRot = new Quaternionf()
                .rotateY((float) Math.toRadians(-part.yaw))
                .rotateX((float) Math.toRadians(part.pitch))
                .rotateZ((float) Math.toRadians(part.roll));
        Quaternionf y = yawQ(busYaw);
        Vector3f worldOffset = y.transform(new Vector3f(part.offset));
        Quaternionf worldRot = new Quaternionf(y).mul(baseRot);

        // A BlockDisplay scales/rotates about its min corner; shift back by the
        // rotated half-extent so the cube ends up centred on its world offset.
        Vector3f half = new Vector3f(part.scale).mul(0.5f);
        new Quaternionf(worldRot).transform(half);
        Vector3f translation = new Vector3f(worldOffset).sub(half);

        return new Transformation(translation, worldRot, new Vector3f(part.scale), new Quaternionf());
    }
}


