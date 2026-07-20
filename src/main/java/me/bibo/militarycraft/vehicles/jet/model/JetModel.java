package me.bibo.militarycraft.vehicles.jet.model;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Static description of the jet model, shaped after a Su-30/Su-35 "Flanker":
 * long blended fuselage, pointed radome, bubble canopy, canards, twin swept
 * wings with wingtip rails, twin canted vertical tails, two engine nacelles
 * with dark nozzles, intakes, and a few underwing missiles.
 *
 * <p>All coordinates are in jet space (origin at the jet centre, +X right,
 * +Y up, +Z forward = nose). Edit the numbers here to reshape the jet - the
 * orientation math lives entirely in {@link Transforms}.
 */
public final class JetModel {

    private JetModel() {
    }

    /** Approximate bounding box (full width / length) for the click/hit hitbox. */
    public static final float WIDTH = 6.0f;
    /**
     * Click/hit hitbox height. Deliberately taller than the ~3-block model: when a
     * pilot is enlarged by the VehicleCamera scale attribute (the F5 zoom-out), their
     * eye-point rises well above the jet. Keeping the hitbox this tall means the eye
     * stays inside it, so an empty-hand right-click still lands on the hitbox and
     * registers as a bomb release. Sized to cover a pilot scale of up to ~4.
     */
    public static final float HEIGHT = 7.0f;
    public static final float LENGTH = 13.0f;

    /** Belly point (jet space) bombs are released from. */
    public static final Vector3f BOMB_BAY = new Vector3f(0f, -0.7f, 0.2f);

    /** Wing/tip hardpoints (jet space) rockets launch from, fired in turn. */
    public static final List<Vector3f> HARDPOINTS = List.of(
            new Vector3f(-4.6f, 0.05f, 0.9f),
            new Vector3f(4.6f, 0.05f, 0.9f),
            new Vector3f(-2.2f, -0.4f, 1.3f),
            new Vector3f(2.2f, -0.4f, 1.3f)
    );

    /** Engine nozzle tips (jet space) the afterburner trail streams from. */
    public static final List<Vector3f> NOZZLES = List.of(
            new Vector3f(-0.75f, -0.15f, -5.05f),
            new Vector3f(0.75f, -0.15f, -5.05f)
    );

    private static String cachedNumber;
    private static List<JetPart> cached;

    public static synchronized List<JetPart> parts(String tailNumber) {
        if (cached != null && java.util.Objects.equals(cachedNumber, tailNumber)) {
            return cached;
        }
        List<JetPart> p = new ArrayList<>();
        JetPart.Role BODY = JetPart.Role.BODY;
        JetPart.Role WING = JetPart.Role.WING;
        JetPart.Role NOSE = JetPart.Role.NOSE;
        JetPart.Role CANOPY = JetPart.Role.CANOPY;
        JetPart.Role ENGINE = JetPart.Role.ENGINE;
        JetPart.Role NOZZLE = JetPart.Role.NOZZLE;
        JetPart.Role MISSILE = JetPart.Role.MISSILE;
        JetPart.Role ACCENT = JetPart.Role.ACCENT;

        // ---- Fuselage ----
        p.add(JetPart.block(BODY, new Vector3f(0f, 0.00f, -0.5f), new Vector3f(1.50f, 1.10f, 6.5f)));
        p.add(JetPart.block(BODY, new Vector3f(0f, 0.55f, -0.5f), new Vector3f(1.10f, 0.70f, 5.5f)));
        p.add(JetPart.block(BODY, new Vector3f(0f, 0.10f, 3.6f), new Vector3f(1.10f, 0.95f, 2.2f)));
        p.add(JetPart.block(BODY, new Vector3f(0f, 0.10f, -3.6f), new Vector3f(1.60f, 1.00f, 3.0f)));
        p.add(JetPart.block(BODY, new Vector3f(0f, 0.30f, -4.6f), new Vector3f(0.70f, 0.60f, 1.6f)));

        // ---- Nose / radome ----
        p.add(JetPart.block(NOSE, new Vector3f(0f, 0.00f, 5.1f), new Vector3f(0.70f, 0.70f, 1.8f)));
        p.add(JetPart.block(NOSE, new Vector3f(0f, -0.05f, 6.2f), new Vector3f(0.42f, 0.42f, 1.2f), -4f, 0f, 0f));

        // ---- Canopy ----
        p.add(JetPart.block(CANOPY, new Vector3f(0f, 0.78f, 3.3f), new Vector3f(0.72f, 0.55f, 1.2f), -22f, 0f, 0f));
        p.add(JetPart.block(CANOPY, new Vector3f(0f, 0.86f, 2.3f), new Vector3f(0.80f, 0.60f, 1.8f)));

        // ---- LERX / leading-edge root extensions ----
        p.add(JetPart.block(WING, new Vector3f(-0.95f, 0.18f, 2.0f), new Vector3f(0.90f, 0.16f, 3.0f)));
        p.add(JetPart.block(WING, new Vector3f(0.95f, 0.18f, 2.0f), new Vector3f(0.90f, 0.16f, 3.0f)));

        // ---- Canards (foreplanes) ----
        p.add(JetPart.block(WING, new Vector3f(-1.70f, 0.35f, 3.0f), new Vector3f(1.60f, 0.14f, 0.9f), 0f, 12f, 0f));
        p.add(JetPart.block(WING, new Vector3f(1.70f, 0.35f, 3.0f), new Vector3f(1.60f, 0.14f, 0.9f), 0f, -12f, 0f));

        // ---- Main wings (swept) + wingtip rails ----
        p.add(JetPart.block(WING, new Vector3f(-3.00f, 0.00f, -0.6f), new Vector3f(4.00f, 0.22f, 2.6f), 0f, 18f, 0f));
        p.add(JetPart.block(WING, new Vector3f(3.00f, 0.00f, -0.6f), new Vector3f(4.00f, 0.22f, 2.6f), 0f, -18f, 0f));
        p.add(JetPart.block(WING, new Vector3f(-4.60f, 0.05f, -1.1f), new Vector3f(0.30f, 0.16f, 1.4f)));
        p.add(JetPart.block(WING, new Vector3f(4.60f, 0.05f, -1.1f), new Vector3f(0.30f, 0.16f, 1.4f)));

        // ---- Horizontal stabilizers ----
        p.add(JetPart.block(WING, new Vector3f(-1.90f, 0.10f, -4.2f), new Vector3f(2.20f, 0.18f, 1.5f), 0f, 20f, 0f));
        p.add(JetPart.block(WING, new Vector3f(1.90f, 0.10f, -4.2f), new Vector3f(2.20f, 0.18f, 1.5f), 0f, -20f, 0f));

        // ---- Twin vertical tails (canted outward) ----
        p.add(JetPart.block(WING, new Vector3f(-1.00f, 1.10f, -3.8f), new Vector3f(0.20f, 1.90f, 1.7f), 0f, 6f, 18f));
        p.add(JetPart.block(WING, new Vector3f(1.00f, 1.10f, -3.8f), new Vector3f(0.20f, 1.90f, 1.7f), 0f, -6f, -18f));

        // ---- Engine nacelles + nozzles ----
        p.add(JetPart.block(ENGINE, new Vector3f(-0.75f, -0.15f, -2.2f), new Vector3f(0.95f, 0.95f, 4.2f)));
        p.add(JetPart.block(ENGINE, new Vector3f(0.75f, -0.15f, -2.2f), new Vector3f(0.95f, 0.95f, 4.2f)));
        p.add(JetPart.block(NOZZLE, new Vector3f(-0.75f, -0.15f, -4.6f), new Vector3f(0.80f, 0.80f, 0.7f)));
        p.add(JetPart.block(NOZZLE, new Vector3f(0.75f, -0.15f, -4.6f), new Vector3f(0.80f, 0.80f, 0.7f)));

        // ---- Intakes (under the LERX) ----
        p.add(JetPart.block(ENGINE, new Vector3f(-0.80f, -0.55f, 1.6f), new Vector3f(0.90f, 0.85f, 2.6f)));
        p.add(JetPart.block(ENGINE, new Vector3f(0.80f, -0.55f, 1.6f), new Vector3f(0.90f, 0.85f, 2.6f)));

        // ---- Underwing & wingtip missiles ----
        p.add(JetPart.block(MISSILE, new Vector3f(-2.20f, -0.40f, 0.4f), new Vector3f(0.28f, 0.28f, 1.8f)));
        p.add(JetPart.block(MISSILE, new Vector3f(2.20f, -0.40f, 0.4f), new Vector3f(0.28f, 0.28f, 1.8f)));
        p.add(JetPart.block(MISSILE, new Vector3f(-3.40f, -0.30f, -0.4f), new Vector3f(0.26f, 0.26f, 1.6f)));
        p.add(JetPart.block(MISSILE, new Vector3f(3.40f, -0.30f, -0.4f), new Vector3f(0.26f, 0.26f, 1.6f)));
        p.add(JetPart.block(MISSILE, new Vector3f(-4.60f, 0.05f, 0.1f), new Vector3f(0.24f, 0.24f, 1.8f)));
        p.add(JetPart.block(MISSILE, new Vector3f(4.60f, 0.05f, 0.1f), new Vector3f(0.24f, 0.24f, 1.8f)));

        // ---- Red star accents (wings + tails) ----
        p.add(JetPart.block(ACCENT, new Vector3f(-2.60f, 0.16f, -0.6f), new Vector3f(0.70f, 0.12f, 0.7f)));
        p.add(JetPart.block(ACCENT, new Vector3f(2.60f, 0.16f, -0.6f), new Vector3f(0.70f, 0.12f, 0.7f)));
        p.add(JetPart.block(ACCENT, new Vector3f(-1.05f, 1.55f, -3.9f), new Vector3f(0.12f, 0.50f, 0.5f), 0f, 6f, 18f));
        p.add(JetPart.block(ACCENT, new Vector3f(1.05f, 1.55f, -3.9f), new Vector3f(0.12f, 0.50f, 0.5f), 0f, -6f, -18f));

        // ---- Board number (always present; empty string => invisible) so the
        //      part count never depends on the number, keeping rehydration stable.
        String number = (tailNumber == null) ? "" : tailNumber;
        p.add(new JetPart(NOSE, new Vector3f(0.80f, 0.40f, 4.0f), new Vector3f(1f, 1f, 1f), 0f, -90f, 0f, number));
        p.add(new JetPart(NOSE, new Vector3f(-0.80f, 0.40f, 4.0f), new Vector3f(1f, 1f, 1f), 0f, 90f, 0f, number));

        // ---- Visual upgrade pack (append-only: old part indexes stay stable) ----
        // Nose probe + cheek sensors: gives the front a sharper, meaner profile.
        p.add(JetPart.block(NOSE, new Vector3f(0f, -0.03f, 6.95f), new Vector3f(0.16f, 0.16f, 1.05f), -4f, 0f, 0f));
        p.add(JetPart.block(ACCENT, new Vector3f(-0.52f, 0.24f, 4.75f), new Vector3f(0.16f, 0.20f, 0.55f), -8f, 0f, 0f));
        p.add(JetPart.block(ACCENT, new Vector3f(0.52f, 0.24f, 4.75f), new Vector3f(0.16f, 0.20f, 0.55f), -8f, 0f, 0f));

        // Canopy frame and dorsal spine: breaks up the plain glass/body slab.
        p.add(JetPart.block(CANOPY, new Vector3f(-0.44f, 1.05f, 2.75f), new Vector3f(0.08f, 0.10f, 1.85f), -18f, 0f, 0f));
        p.add(JetPart.block(CANOPY, new Vector3f(0.44f, 1.05f, 2.75f), new Vector3f(0.08f, 0.10f, 1.85f), -18f, 0f, 0f));
        p.add(JetPart.block(CANOPY, new Vector3f(0f, 1.17f, 2.85f), new Vector3f(0.88f, 0.08f, 0.16f), -18f, 0f, 0f));
        p.add(JetPart.block(BODY, new Vector3f(0f, 1.02f, -1.45f), new Vector3f(0.42f, 0.26f, 3.65f)));
        p.add(JetPart.block(ACCENT, new Vector3f(0f, 1.20f, -1.25f), new Vector3f(0.36f, 0.08f, 1.15f)));

        // Leading-edge paint, wing fences and chunky wingtip pods.
        p.add(JetPart.block(ACCENT, new Vector3f(-3.10f, 0.17f, 0.80f), new Vector3f(3.15f, 0.08f, 0.22f), 0f, 18f, 0f));
        p.add(JetPart.block(ACCENT, new Vector3f(3.10f, 0.17f, 0.80f), new Vector3f(3.15f, 0.08f, 0.22f), 0f, -18f, 0f));
        p.add(JetPart.block(WING, new Vector3f(-4.95f, 0.16f, -0.15f), new Vector3f(0.18f, 0.42f, 1.05f), 0f, 8f, 0f));
        p.add(JetPart.block(WING, new Vector3f(4.95f, 0.16f, -0.15f), new Vector3f(0.18f, 0.42f, 1.05f), 0f, -8f, 0f));
        p.add(JetPart.block(ACCENT, new Vector3f(-5.00f, 0.13f, 0.58f), new Vector3f(0.24f, 0.18f, 0.42f)));
        p.add(JetPart.block(ACCENT, new Vector3f(5.00f, 0.13f, 0.58f), new Vector3f(0.24f, 0.18f, 0.42f)));

        // Underside pylons and centreline tank: makes the jet read as armed even
        // from below or from a distance.
        p.add(JetPart.block(ENGINE, new Vector3f(-2.20f, -0.24f, -0.55f), new Vector3f(0.18f, 0.45f, 1.05f)));
        p.add(JetPart.block(ENGINE, new Vector3f(2.20f, -0.24f, -0.55f), new Vector3f(0.18f, 0.45f, 1.05f)));
        p.add(JetPart.block(ENGINE, new Vector3f(-3.40f, -0.20f, -1.15f), new Vector3f(0.16f, 0.38f, 0.95f)));
        p.add(JetPart.block(ENGINE, new Vector3f(3.40f, -0.20f, -1.15f), new Vector3f(0.16f, 0.38f, 0.95f)));
        p.add(JetPart.block(MISSILE, new Vector3f(0f, -0.86f, -0.65f), new Vector3f(0.52f, 0.52f, 2.25f)));
        p.add(JetPart.block(NOZZLE, new Vector3f(0f, -0.86f, 0.65f), new Vector3f(0.58f, 0.58f, 0.22f)));

        // Reserved empty slots where the old thin landing gear used to be.
        // Keep these indexes stable so already-placed jets rehydrate cleanly.
        p.add(hiddenPart());
        p.add(hiddenPart());
        p.add(hiddenPart());
        p.add(hiddenPart());
        p.add(hiddenPart());
        p.add(hiddenPart());

        // Tail stinger, exhaust cores and extra fin markings for a stronger rear view.
        p.add(JetPart.block(BODY, new Vector3f(0f, 0.15f, -5.55f), new Vector3f(0.42f, 0.36f, 1.35f)));
        p.add(JetPart.block(NOZZLE, new Vector3f(-0.75f, -0.15f, -5.12f), new Vector3f(0.46f, 0.46f, 0.22f)));
        p.add(JetPart.block(NOZZLE, new Vector3f(0.75f, -0.15f, -5.12f), new Vector3f(0.46f, 0.46f, 0.22f)));
        p.add(JetPart.block(ACCENT, new Vector3f(-1.20f, 1.35f, -4.15f), new Vector3f(0.10f, 0.70f, 0.18f), 0f, 6f, 18f));
        p.add(JetPart.block(ACCENT, new Vector3f(1.20f, 1.35f, -4.15f), new Vector3f(0.10f, 0.70f, 0.18f), 0f, -6f, -18f));

        cached = java.util.Collections.unmodifiableList(p);
        cachedNumber = tailNumber;
        return cached;
    }

    private static JetPart hiddenPart() {
        return JetPart.block(JetPart.Role.BODY, new Vector3f(), new Vector3f());
    }
}
