package me.bibo.militarycraft.vehicles.helicopter.model;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Procedural description of a military transport helicopter shaped after the
 * reference image (an olive-green Mi-8/Mi-17): a rounded, two-tone fuselage with
 * a continuous glazed nose, an engine deck with intakes / dust filters / canted
 * exhausts, a 5-blade MAIN ROTOR that pivots at the hub and spins about the
 * vertical axis, a tapering tail boom sweeping up into a swept fin with a side
 * TAIL ROTOR, stub sponsons carrying the rocket pods and the wheels, tricycle
 * gear, and red-star markings.
 *
 * <p>All coordinates are in helicopter space: origin at the pilot seat, +X right,
 * +Y up, +Z forward = nose. Anti-flicker rules baked in here: structural boxes
 * OVERLAP (never share an exact coplanar face) and every decal (glass, star,
 * stripe, door frame) sits PROUD of the surface it decorates — two coplanar
 * faces are what z-fight. Roundness uses a smaller 45deg-rolled twin at the same
 * centre (its diagonal faces never line up with the main box's axis-aligned
 * ones). The orientation/spin math lives entirely in {@link Transforms}.
 */
public final class HelicopterModel {

    private HelicopterModel() {
    }

    // ---- overall size / collision sampling constants ----
    public static final float TOTAL_LENGTH = 21.0f;        // nose (~+5) to tail rotor (~-11)
    public static final float ENV_RX = 2.8f;               // half-width incl. sponsons/pods (wall sampling)
    public static final float ENV_RY = 1.3f;               // half-height of the upper hull (ceiling)
    public static final float ENV_CY = 2.1f;               // hull "up" centre = engine/rotor deck height
    public static final float GONDOLA_BOTTOM_Y = -2.0f;    // wheels: the craft rests on the ground here

    /** Belly point (heli space) bombs are released from. */
    public static final Vector3f BOMB_BAY = new Vector3f(0f, -1.55f, -0.8f);
    /** Point under the rotor the downwash puffs from while boosting / hovering low. */
    public static final Vector3f BURNER_POINT = new Vector3f(0f, -2.0f, -0.8f);

    /** Passenger seats behind the pilot (who rides the core at the origin). */
    public static final List<Vector3f> SEAT_OFFSETS = List.of(
            new Vector3f(0.75f, 0.0f, -1.6f),
            new Vector3f(-0.75f, 0.0f, -1.6f),
            new Vector3f(0.0f, -0.05f, -3.0f)
    );

    /** Rocket pods on the stub sponsons, fired left/right in turn. */
    public static final List<Vector3f> HARDPOINTS = List.of(
            new Vector3f(-2.08f, -0.5f, 1.7f),
            new Vector3f(2.08f, -0.5f, 1.7f)
    );

    /** Engine-deck exhaust points the smoke trail streams from. */
    public static final List<Vector3f> ENGINE_POINTS = List.of(
            new Vector3f(-0.82f, 1.85f, -1.2f),
            new Vector3f(0.82f, 1.85f, -1.2f)
    );

    /** Key hull points used to measure how close an explosion is to the craft. */
    public static final List<Vector3f> BLAST_POINTS = List.of(
            new Vector3f(0f, -0.3f, 4.0f),   // nose
            new Vector3f(0f, 0.35f, -1.2f),  // cabin
            new Vector3f(0f, 1.4f, -8.2f),   // tail boom
            new Vector3f(0f, 3.25f, 0.1f)    // rotor
    );

    /** A clickable / hittable Interaction proxy (axis-aligned, follows position). */
    public record HitboxSpec(Vector3f center, float width, float height) {
    }

    public static final List<HitboxSpec> HITBOXES = List.of(
            new HitboxSpec(new Vector3f(0f, 0.6f, -1.0f), 4.4f, 4.0f),   // cabin (boarding)
            new HitboxSpec(new Vector3f(0f, 0.0f, 3.2f), 3.2f, 3.0f),    // cockpit / nose
            new HitboxSpec(new Vector3f(0f, 1.4f, -8.0f), 2.0f, 2.4f),   // tail boom
            // Main-rotor disc: deliberately BIG and RAISED to the rotor level —
            // out past the blade tips (~2x the old footprint, so another player can
            // actually hit the rotor) and sitting up at the disc, not dipped down to
            // cabin level. It stays tall enough that a pilot enlarged by the
            // VehicleCamera scale (eye ~5 up) still has that eye INSIDE it, so an
            // empty-hand right-click lands here and drops a bomb.
            new HitboxSpec(new Vector3f(0f, 4.5f, 0.1f), 12.0f, 3.5f)    // rotor disc (raised, ~blade span)
    );

    private static String cacheKey;
    private static List<Part> cached;

    /**
     * Build (and cache) the helicopter's parts. {@code tailNumber} is baked into
     * the cache key; the part list must stay stable for a given config so
     * persisted helicopters rehydrate correctly (append new parts, never reorder).
     */
    public static synchronized List<Part> parts(String tailNumber) {
        String key = (tailNumber == null ? "" : tailNumber);
        if (cached != null && Objects.equals(cacheKey, key)) {
            return cached;
        }
        List<Part> p = new ArrayList<>();

        buildFuselage(p);
        buildCockpit(p);
        buildCabin(p);
        buildEngineDeck(p);
        buildTailBoom(p);
        buildTail(p);
        buildSponsons(p);
        buildGear(p);
        buildMainRotor(p);   // rotors last: they render over the body
        buildTailRotor(p);
        buildName(p, tailNumber);

        cached = Collections.unmodifiableList(p);
        cacheKey = key;
        return cached;
    }

    // ----------------------------------------------------------------- fuselage

    private static void buildFuselage(List<Part> p) {
        Part.Role BODY = Part.Role.BODY;
        Part.Role CAMO = Part.Role.CAMO;
        // cabin core + smaller 45deg-rolled twin -> rounded (octagonal) section
        p.add(Part.block(BODY, new Vector3f(0f, 0.35f, -1.2f), new Vector3f(2.5f, 2.5f, 6.6f)));
        p.add(Part.block(BODY, new Vector3f(0f, 0.45f, -1.2f), new Vector3f(1.9f, 1.9f, 6.6f), 0f, 0f, 45f));
        // rounded underside + keel (overlaps the core, no shared face)
        p.add(Part.block(BODY, new Vector3f(0f, -0.9f, -1.1f), new Vector3f(2.15f, 1.1f, 6.2f)));
        p.add(Part.block(CAMO, new Vector3f(0f, -1.35f, -1.1f), new Vector3f(1.3f, 0.5f, 5.0f)));
        // camo roof spine (narrower than the core -> pokes out the top only)
        p.add(Part.block(CAMO, new Vector3f(0f, 1.75f, -1.4f), new Vector3f(1.9f, 0.5f, 5.2f)));
        // rear fuselage tapering into the boom (overlapping segments)
        p.add(Part.block(BODY, new Vector3f(0f, 0.5f, -4.6f), new Vector3f(2.0f, 2.0f, 1.6f)));
        p.add(Part.block(BODY, new Vector3f(0f, 0.85f, -5.5f), new Vector3f(1.5f, 1.5f, 1.4f)));
    }

    private static void buildCockpit(List<Part> p) {
        Part.Role BODY = Part.Role.BODY;
        Part.Role GLASS = Part.Role.GLASS;
        // forward fuselage: a solid block that OVERLAPS the cabin front (z=2.1)
        // and the belly, so the nose is one continuous body, not a floating snout
        p.add(Part.block(BODY, new Vector3f(0f, 0.3f, 2.7f), new Vector3f(2.4f, 2.35f, 2.2f)));
        p.add(Part.block(BODY, new Vector3f(0f, 0.4f, 2.7f), new Vector3f(1.8f, 1.8f, 2.2f), 0f, 0f, 45f));
        p.add(Part.block(BODY, new Vector3f(0f, -0.85f, 2.4f), new Vector3f(2.0f, 1.05f, 2.0f))); // belly bridge
        p.add(Part.block(BODY, new Vector3f(0f, 1.15f, 2.7f), new Vector3f(2.2f, 0.7f, 2.0f)));   // roof cap
        // drooping rounded nose (overlaps the forward fuselage front)
        p.add(Part.block(BODY, new Vector3f(0f, -0.55f, 3.7f), new Vector3f(1.9f, 1.25f, 1.6f), -10f, 0f, 0f));
        p.add(Part.block(BODY, new Vector3f(0f, -0.95f, 4.5f), new Vector3f(1.4f, 0.85f, 1.1f), -18f, 0f, 0f));
        // wrap-around greenhouse — all PROUD of the surface so it never z-fights
        // windscreen at its ORIGINAL -30 pitch and height, lowered slightly so
        // the bottom edge sits deeper into the nose. Angle unchanged.
        p.add(Part.block(GLASS, new Vector3f(0f, 0.25f, 3.35f), new Vector3f(1.95f, 1.25f, 1.5f), -30f, 0f, 0f));
        p.add(Part.block(BODY, new Vector3f(0f, 0.3f, 4.0f), new Vector3f(0.16f, 1.25f, 0.34f), -30f, 0f, 0f));
        p.add(Part.block(GLASS, new Vector3f(1.32f, 0.4f, 3.05f), new Vector3f(0.14f, 0.72f, 1.05f)));
        p.add(Part.block(GLASS, new Vector3f(-1.32f, 0.4f, 3.05f), new Vector3f(0.14f, 0.72f, 1.05f)));
    }

    private static void buildCabin(List<Part> p) {
        Part.Role GLASS = Part.Role.GLASS;
        Part.Role CAMO = Part.Role.CAMO;
        // round porthole windows down each flank, proud of the rounded side
        for (float z : new float[]{0.5f, -0.9f, -2.3f}) {
            p.add(Part.block(GLASS, new Vector3f(1.40f, 0.55f, z), new Vector3f(0.14f, 0.7f, 0.7f)));
            p.add(Part.block(GLASS, new Vector3f(-1.40f, 0.55f, z), new Vector3f(0.14f, 0.7f, 0.7f)));
        }
        // sliding-door frame outline on the left flank (proud, thin) — dark-green
        // camo, not red
        p.add(Part.block(CAMO, new Vector3f(-1.41f, 0.15f, -0.9f), new Vector3f(0.06f, 1.7f, 0.14f)));
        p.add(Part.block(CAMO, new Vector3f(-1.41f, 0.15f, 0.9f), new Vector3f(0.06f, 1.7f, 0.14f)));
        p.add(Part.block(CAMO, new Vector3f(-1.41f, 0.95f, 0.0f), new Vector3f(0.06f, 0.14f, 1.9f)));
        p.add(Part.block(CAMO, new Vector3f(-1.41f, -0.65f, 0.0f), new Vector3f(0.06f, 0.14f, 1.9f)));
    }

    private static void buildEngineDeck(List<Part> p) {
        Part.Role ENGINE = Part.Role.ENGINE;
        Part.Role CAMO = Part.Role.CAMO;
        Part.Role HUB = Part.Role.HUB;
        // turboshaft cowl on the roof (overlaps the roof, stacked)
        p.add(Part.block(ENGINE, new Vector3f(0f, 1.8f, 0.6f), new Vector3f(2.0f, 0.9f, 2.8f)));
        p.add(Part.block(ENGINE, new Vector3f(0f, 2.2f, 0.5f), new Vector3f(1.5f, 0.5f, 2.2f)));
        // round intakes + proud metal lips
        p.add(Part.block(ENGINE, new Vector3f(0.55f, 2.05f, 1.9f), new Vector3f(0.72f, 0.8f, 0.9f)));
        p.add(Part.block(ENGINE, new Vector3f(-0.55f, 2.05f, 1.9f), new Vector3f(0.72f, 0.8f, 0.9f)));
        p.add(Part.block(HUB, new Vector3f(0.55f, 2.1f, 2.45f), new Vector3f(0.62f, 0.64f, 0.2f)));
        p.add(Part.block(HUB, new Vector3f(-0.55f, 2.1f, 2.45f), new Vector3f(0.62f, 0.64f, 0.2f)));
        // "elephant-ear" dust filters (Mi-17)
        p.add(Part.block(CAMO, new Vector3f(0.98f, 2.1f, 1.75f), new Vector3f(0.5f, 0.75f, 1.1f)));
        p.add(Part.block(CAMO, new Vector3f(-0.98f, 2.1f, 1.75f), new Vector3f(0.5f, 0.75f, 1.1f)));
        // canted exhausts
        p.add(Part.block(HUB, new Vector3f(0.82f, 1.85f, -0.7f), new Vector3f(0.45f, 0.45f, 1.3f), 0f, 16f, 0f));
        p.add(Part.block(HUB, new Vector3f(-0.82f, 1.85f, -0.7f), new Vector3f(0.45f, 0.45f, 1.3f), 0f, -16f, 0f));
    }

    // ----------------------------------------------------------------- tail

    private static void buildTailBoom(List<Part> p) {
        Part.Role BODY = Part.Role.BODY;
        Part.Role CAMO = Part.Role.CAMO;
        // three tapering, OVERLAPPING segments sweeping gently up toward the fin
        p.add(Part.block(BODY, new Vector3f(0f, 0.95f, -5.8f), new Vector3f(1.35f, 1.35f, 2.4f)));
        p.add(Part.block(BODY, new Vector3f(0f, 1.25f, -7.6f), new Vector3f(1.05f, 1.05f, 2.2f)));
        p.add(Part.block(BODY, new Vector3f(0f, 1.55f, -9.2f), new Vector3f(0.82f, 0.82f, 2.0f)));
        // 45deg rounding twin (no flat camo stripe here — on the rising round boom
        // it kept ending up coplanar with the skin and z-fighting)
        p.add(Part.block(BODY, new Vector3f(0f, 1.2f, -7.0f), new Vector3f(1.0f, 1.0f, 3.2f), 0f, 0f, 45f));
    }

    private static void buildTail(List<Part> p) {
        Part.Role BODY = Part.Role.BODY;
        Part.Role CAMO = Part.Role.CAMO;
        Part.Role HUB = Part.Role.HUB;
        // swept vertical fin: a lower block that sinks INTO the boom (volume
        // overlap, no coplanar face) + an upper block offset back. The pitched
        // fillet is gone — that sloped face was the tail's z-fighting surface.
        p.add(Part.block(CAMO, new Vector3f(0f, 2.5f, -9.9f), new Vector3f(0.5f, 2.6f, 1.6f)));
        p.add(Part.block(CAMO, new Vector3f(0f, 3.5f, -10.5f), new Vector3f(0.5f, 1.2f, 1.05f)));
        // tail-rotor gearbox fairing
        p.add(Part.block(HUB, new Vector3f(0f, 3.35f, -10.25f), new Vector3f(0.7f, 0.7f, 0.95f)));
        // horizontal stabilizers with slight anhedral + end plates
        p.add(Part.block(BODY, new Vector3f(1.0f, 1.55f, -8.8f), new Vector3f(1.7f, 0.24f, 1.1f), 0f, 0f, -6f));
        p.add(Part.block(BODY, new Vector3f(-1.0f, 1.55f, -8.8f), new Vector3f(1.7f, 0.24f, 1.1f), 0f, 0f, 6f));
        p.add(Part.block(CAMO, new Vector3f(1.78f, 1.62f, -8.8f), new Vector3f(0.22f, 0.75f, 0.9f)));
        p.add(Part.block(CAMO, new Vector3f(-1.78f, 1.62f, -8.8f), new Vector3f(0.22f, 0.75f, 0.9f)));
    }

    // ----------------------------------------------------------------- wings / gear

    private static void buildSponsons(List<Part> p) {
        Part.Role CAMO = Part.Role.CAMO;
        Part.Role ENGINE = Part.Role.ENGINE;
        Part.Role HUB = Part.Role.HUB;
        for (float sx : new float[]{-1f, 1f}) {
            p.add(Part.block(CAMO, new Vector3f(sx * 1.5f, -0.55f, -0.7f), new Vector3f(1.15f, 0.7f, 2.7f)));
            p.add(Part.block(CAMO, new Vector3f(sx * 1.78f, -0.35f, 0.3f), new Vector3f(0.5f, 0.45f, 1.0f)));
            // rocket pod (HARDPOINTS fire from its front tube face)
            p.add(Part.block(ENGINE, new Vector3f(sx * 2.08f, -0.5f, 0.5f), new Vector3f(0.72f, 0.72f, 2.2f)));
            p.add(Part.block(HUB, new Vector3f(sx * 2.08f, -0.5f, 1.65f), new Vector3f(0.64f, 0.64f, 0.25f)));
        }
    }

    private static void buildGear(List<Part> p) {
        Part.Role GEAR = Part.Role.GEAR;
        // nose wheel — the wheel disc is THIN in X (axle sideways) so it rolls
        // forward/back, not sideways
        p.add(Part.block(GEAR, new Vector3f(0f, -1.2f, 3.0f), new Vector3f(0.2f, 0.95f, 0.2f)));
        p.add(Part.block(GEAR, new Vector3f(0f, -1.68f, 3.0f), new Vector3f(0.26f, 0.62f, 0.62f)));
        // main wheels under the sponsons (also thin in X)
        for (float sx : new float[]{-1f, 1f}) {
            p.add(Part.block(GEAR, new Vector3f(sx * 1.45f, -1.25f, -1.0f), new Vector3f(0.22f, 0.95f, 0.22f)));
            p.add(Part.block(GEAR, new Vector3f(sx * 1.5f, -1.7f, -1.0f), new Vector3f(0.28f, 0.72f, 0.72f)));
        }
    }

    // ----------------------------------------------------------------- rotors

    private static void buildMainRotor(List<Part> p) {
        Part.Role HUB = Part.Role.HUB;
        Part.Role ROTOR = Part.Role.ROTOR;
        Vector3f hub = new Vector3f(0f, 3.2f, 0.1f);
        // mast + swashplate + hub
        p.add(Part.block(HUB, new Vector3f(0f, 2.5f, 0.1f), new Vector3f(0.45f, 1.1f, 0.45f)));
        p.add(Part.block(HUB, new Vector3f(0f, 2.75f, 0.1f), new Vector3f(0.95f, 0.2f, 0.95f)));
        p.add(Part.block(HUB, new Vector3f(0f, 3.15f, 0.1f), new Vector3f(1.1f, 0.5f, 1.1f)));
        // 5 radial blades (+ short root grips) evenly spaced, pivoting at the hub
        // -> they sweep a clean disc about the mast with no wobble.
        for (float baseYaw : new float[]{0f, 72f, 144f, 216f, 288f}) {
            p.add(Part.blade(HUB, hub, 1.3f, 0.34f, 0.44f, baseYaw));
            p.add(Part.blade(ROTOR, new Vector3f(0f, 3.25f, 0.1f), 6.4f, 0.13f, 0.52f, baseYaw));
        }
    }

    private static void buildTailRotor(List<Part> p) {
        Part.Role HUB = Part.Role.HUB;
        Part.Role ROTOR = Part.Role.ROTOR;
        Vector3f hub = new Vector3f(0.72f, 2.5f, -10.35f);
        // hub on the right face of the fin
        p.add(Part.block(HUB, new Vector3f(0.5f, 2.5f, -10.3f), new Vector3f(0.5f, 0.5f, 0.5f)));
        // 4 radial blades pivoting at the hub, spinning about X -> a clean disc
        // facing sideways. Radial pivot (like the main rotor) = no wobble.
        for (float baseAngle : new float[]{0f, 90f, 180f, 270f}) {
            p.add(Part.bladeX(ROTOR, hub, 1.8f, 0.14f, 0.42f, baseAngle));
        }
    }

    private static void buildName(List<Part> p, String tailNumber) {
        // Always present (empty => invisible) so the part count never depends on
        // the number, keeping rehydration stable. Painted on both boom flanks.
        String name = (tailNumber == null) ? "" : tailNumber;
        p.add(new Part(Part.Role.ACCENT, new Vector3f(0.55f, 1.85f, -7.6f),
                new Vector3f(1f, 1f, 1f), 0f, -90f, 0f, name));
        p.add(new Part(Part.Role.ACCENT, new Vector3f(-0.55f, 1.85f, -7.6f),
                new Vector3f(1f, 1f, 1f), 0f, 90f, 0f, name));
    }
}
