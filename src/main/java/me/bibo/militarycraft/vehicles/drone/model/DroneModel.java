package me.bibo.militarycraft.vehicles.drone.model;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Static description of the UAV model: a twin-boom pusher recon/strike drone
 * shaped after a UkrSpecSystems PD-1 — a bulbous fuselage pod with a nose sensor
 * gimbal, a high straight wing with slight dihedral and wingtips, two slim tail
 * booms running back to a twin-fin H-tail, a rear pusher propeller, fixed
 * tricycle gear and an antenna.
 *
 * <p>Coordinates are in drone space (origin at the operator's cockpit camera,
 * +X right, +Y up, +Z forward = nose). The origin is at the top-rear of the pod
 * so that, riding the UAV, the operator looks forward over the nose and wing
 * while the booms/tail/prop sit behind the camera.
 */
public final class DroneModel {

    private DroneModel() {
    }

    /** Approximate bounding box (full width / length) for the click/hit hitbox. */
    public static final float WIDTH = 5.2f;
    public static final float HEIGHT = 3.0f;
    public static final float LENGTH = 5.5f;

    /**
     * Ram point used for the kamikaze terrain check. Kept near the airframe's own
     * height (not far below) so skimming low over the ground doesn't detonate it —
     * only flying the nose into a wall / the ground does.
     */
    public static final Vector3f NOSE = new Vector3f(0f, -0.18f, 1.65f);

    /** Under-wing rocket hardpoints (drone space), fired in turn. */
    public static final List<Vector3f> HARDPOINTS = List.of(
            new Vector3f(-0.85f, -0.34f, 0.30f),
            new Vector3f(0.85f, -0.34f, 0.30f),
            new Vector3f(-1.55f, -0.30f, 0.20f),
            new Vector3f(1.55f, -0.30f, 0.20f)
    );

    /** Exhaust point (drone space) — engine smoke trails from behind the prop. */
    public static final List<Vector3f> EXHAUST = List.of(
            new Vector3f(0f, -0.40f, -1.55f)
    );

    /** Wingtip points (drone space) — faint vapour at speed. */
    public static final List<Vector3f> WINGTIPS = List.of(
            new Vector3f(-2.45f, 0.00f, 0.20f),
            new Vector3f(2.45f, 0.00f, 0.20f)
    );

    private static List<DronePart> cached;

    public static synchronized List<DronePart> parts() {
        if (cached != null) {
            return cached;
        }
        List<DronePart> p = new ArrayList<>();
        DronePart.Role FRAME = DronePart.Role.FRAME;
        DronePart.Role ARM = DronePart.Role.ARM;
        DronePart.Role MOTOR = DronePart.Role.MOTOR;
        DronePart.Role CAMERA = DronePart.Role.CAMERA;
        DronePart.Role ACCENT = DronePart.Role.ACCENT;

        // ---- Fuselage pod (rounded, built from tapering segments) ----
        p.add(DronePart.block(FRAME, new Vector3f(0f, -0.50f, 0.10f), new Vector3f(0.78f, 0.74f, 1.30f)));
        p.add(DronePart.block(FRAME, new Vector3f(0f, -0.50f, 0.95f), new Vector3f(0.66f, 0.62f, 0.55f)));
        p.add(DronePart.block(FRAME, new Vector3f(0f, -0.49f, 1.34f), new Vector3f(0.50f, 0.48f, 0.42f)));
        p.add(DronePart.block(FRAME, new Vector3f(0f, -0.47f, 1.66f), new Vector3f(0.32f, 0.32f, 0.34f)));
        p.add(DronePart.block(FRAME, new Vector3f(0f, -0.50f, -0.62f), new Vector3f(0.50f, 0.50f, 0.55f)));
        p.add(DronePart.block(FRAME, new Vector3f(0f, -0.50f, -1.00f), new Vector3f(0.34f, 0.34f, 0.40f)));

        // ---- Nose sensor gimbal (the camera ball) ----
        p.add(DronePart.block(CAMERA, new Vector3f(0f, -0.90f, 1.18f), new Vector3f(0.40f, 0.40f, 0.42f)));
        p.add(DronePart.block(CAMERA, new Vector3f(0f, -1.04f, 1.24f), new Vector3f(0.20f, 0.18f, 0.20f)));

        // ---- High straight wing (two panels, slight dihedral) + wingtips ----
        p.add(DronePart.block(FRAME, new Vector3f(-1.22f, -0.10f, 0.20f), new Vector3f(2.45f, 0.12f, 0.96f), 0f, 0f, -4f));
        p.add(DronePart.block(FRAME, new Vector3f(1.22f, -0.10f, 0.20f), new Vector3f(2.45f, 0.12f, 0.96f), 0f, 0f, 4f));
        p.add(DronePart.block(FRAME, new Vector3f(0f, -0.10f, 0.20f), new Vector3f(0.55f, 0.14f, 1.02f)));
        p.add(DronePart.block(FRAME, new Vector3f(-2.42f, -0.01f, 0.20f), new Vector3f(0.12f, 0.30f, 0.74f), 0f, 0f, -10f));
        p.add(DronePart.block(FRAME, new Vector3f(2.42f, -0.01f, 0.20f), new Vector3f(0.12f, 0.30f, 0.74f), 0f, 0f, 10f));

        // ---- Twin tail booms (slim carbon tubes) ----
        p.add(DronePart.block(ARM, new Vector3f(-0.86f, -0.12f, -1.05f), new Vector3f(0.10f, 0.10f, 2.80f)));
        p.add(DronePart.block(ARM, new Vector3f(0.86f, -0.12f, -1.05f), new Vector3f(0.10f, 0.10f, 2.80f)));

        // ---- H-tail: twin vertical fins + connecting horizontal stabiliser ----
        p.add(DronePart.block(FRAME, new Vector3f(-0.86f, 0.20f, -2.45f), new Vector3f(0.12f, 0.80f, 0.55f)));
        p.add(DronePart.block(FRAME, new Vector3f(0.86f, 0.20f, -2.45f), new Vector3f(0.12f, 0.80f, 0.55f)));
        p.add(DronePart.block(FRAME, new Vector3f(0f, 0.56f, -2.50f), new Vector3f(1.96f, 0.10f, 0.50f)));

        // ---- Rear engine hub (no spinning blade — it only jittered as a flat
        //      block-display rotates about its corner, not its axis) ----
        p.add(DronePart.block(MOTOR, new Vector3f(0f, -0.45f, -1.22f), new Vector3f(0.22f, 0.22f, 0.30f)));

        // ---- Antenna on the pod spine ----
        p.add(DronePart.block(ARM, new Vector3f(0f, 0.10f, -0.30f), new Vector3f(0.04f, 0.55f, 0.04f)));

        // ---- Fixed tricycle landing gear (legs tucked near the centreline) ----
        p.add(DronePart.block(ARM, new Vector3f(0f, -1.02f, 0.90f), new Vector3f(0.06f, 0.42f, 0.06f)));
        p.add(DronePart.block(MOTOR, new Vector3f(0f, -1.24f, 0.90f), new Vector3f(0.14f, 0.14f, 0.09f)));
        p.add(DronePart.block(ARM, new Vector3f(-0.26f, -1.00f, 0.05f), new Vector3f(0.06f, 0.40f, 0.06f)));
        p.add(DronePart.block(MOTOR, new Vector3f(-0.26f, -1.22f, 0.05f), new Vector3f(0.14f, 0.14f, 0.09f)));
        p.add(DronePart.block(ARM, new Vector3f(0.26f, -1.00f, 0.05f), new Vector3f(0.06f, 0.40f, 0.06f)));
        p.add(DronePart.block(MOTOR, new Vector3f(0.26f, -1.22f, 0.05f), new Vector3f(0.14f, 0.14f, 0.09f)));

        // ---- Markings / nav light ----
        p.add(DronePart.block(ACCENT, new Vector3f(0f, -0.47f, 1.82f), new Vector3f(0.12f, 0.12f, 0.06f)));
        p.add(DronePart.block(ACCENT, new Vector3f(-1.7f, -0.03f, 0.2f), new Vector3f(0.45f, 0.02f, 0.45f), 0f, 0f, -4f));
        p.add(DronePart.block(ACCENT, new Vector3f(1.7f, -0.03f, 0.2f), new Vector3f(0.45f, 0.02f, 0.45f), 0f, 0f, 4f));

        cached = Collections.unmodifiableList(p);
        return cached;
    }
}
