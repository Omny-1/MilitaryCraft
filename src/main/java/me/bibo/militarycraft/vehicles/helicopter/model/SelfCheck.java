package me.bibo.militarycraft.vehicles.helicopter.model;

import org.joml.Vector3f;

import java.util.List;

/**
 * Tiny standalone self-check for the two bits of new, non-trivial logic added
 * for HeliCraft's rotor model: that {@link Part#rotorY} / {@link Part#rotorX}
 * report the correct {@link Part.Spin} axis (and only those are "rotors"),
 * and that cycling {@link HelicopterModel#HARDPOINTS} the way
 * {@code Helicopter.nextHardpoint()} does alternates left/right.
 *
 * <p>No test framework: run directly, e.g.
 * {@code java -cp target/classes;<paper-api.jar> me.bibo.militarycraft.vehicles.helicopter.model.SelfCheck}
 * It exits with a non-zero status and a message on the first failure.
 */
public final class SelfCheck {

    private SelfCheck() {
    }

    public static void main(String[] args) {
        checkRotorSpinAxes();
        checkHardpointCycling();
        System.out.println("SelfCheck OK: rotor spin axes + hardpoint L/R cycling both correct.");
    }

    private static void checkRotorSpinAxes() {
        // Main rotor now uses radial hub-pivot blades: spin about Y AND radial.
        Part mainBlade = Part.blade(Part.Role.ROTOR, new Vector3f(0, 3.25f, 0.1f),
                6.4f, 0.13f, 0.52f, 72f);
        require(mainBlade.spin == Part.Spin.Y, "blade() must report Spin.Y");
        require(mainBlade.isRotor(), "blade() part must be isRotor() == true");
        require(mainBlade.radial, "blade() must be radial (pivots at the hub)");

        // Tail rotor now also uses radial hub-pivot blades: spin about X AND radial.
        Part tailBlade = Part.bladeX(Part.Role.ROTOR, new Vector3f(0.72f, 2.5f, -10.35f),
                1.8f, 0.14f, 0.42f, 90f);
        require(tailBlade.spin == Part.Spin.X, "bladeX() must report Spin.X");
        require(tailBlade.isRotor(), "bladeX() part must be isRotor() == true");
        require(tailBlade.radial, "bladeX() (tail rotor) must be radial (hub-pivot)");

        Part body = Part.block(Part.Role.BODY, new Vector3f(0, 0, 0), new Vector3f(1, 1, 1));
        require(body.spin == Part.Spin.NONE, "a plain block() part must report Spin.NONE");
        require(!body.isRotor(), "a plain block() part must be isRotor() == false");
        require(!body.radial, "a plain block() part must not be radial");
    }

    private static void checkHardpointCycling() {
        List<Vector3f> hp = HelicopterModel.HARDPOINTS;
        require(hp.size() == 2, "expected exactly 2 rocket hardpoints (L/R), found " + hp.size());
        require(hp.get(0).x < 0, "hardpoint 0 should be the LEFT pod (negative x)");
        require(hp.get(1).x > 0, "hardpoint 1 should be the RIGHT pod (positive x)");

        // Reproduces Helicopter.nextHardpoint()'s cycling formula in isolation.
        int hardpointIndex = 0;
        boolean[] expectLeft = {true, false, true, false};
        for (boolean wantLeft : expectLeft) {
            Vector3f p = hp.get(hardpointIndex % hp.size());
            hardpointIndex = (hardpointIndex + 1) % hp.size();
            boolean isLeft = p.x < 0;
            require(isLeft == wantLeft,
                    "hardpoint ripple should alternate L/R; expected left=" + wantLeft + " got x=" + p.x);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("SelfCheck FAILED: " + message);
        }
    }
}
