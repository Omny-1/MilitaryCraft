package me.bibo.militarycraft.vehicles.train.model;

import org.bukkit.Material;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static me.bibo.militarycraft.vehicles.train.model.TrainPart.p;
import static me.bibo.militarycraft.vehicles.train.model.TrainPart.rot;

/**
 * The Wild-West steam train, after the concept art: a rust-and-copper
 * locomotive with a fat octagonal boiler, flared chimney, cowcatcher and big
 * spoked drivers, pulling three slightly different wooden passenger cars.
 *
 * <p>Car space: origin at the centre of the car on the rail surface, +Z is
 * the direction of travel. Octagonal "cylinders" are built from a pair of
 * overlapping boxes, one rolled 45° - see {@link #octX} / {@link #octZ}.</p>
 */
public final class TrainModel {

    public static final float LOCO_LENGTH = 9.0f;
    public static final float WAGON_LENGTH = 7.5f;
    public static final float WIDTH = 2.8f;

    /** Wheel radii (blocks) - must match the octagon face sizes below, since
     *  a wheel's spin rate is distance/radius (rolling without slipping). */
    public static final float DRIVER_WHEEL_RADIUS = 0.75f;
    public static final float LEADING_WHEEL_RADIUS = 0.5f;
    public static final float BOGIE_WHEEL_RADIUS = 0.42f;
    /** Crank throw for the coupling/piston rods (blocks). */
    private static final float CRANK_RADIUS = 0.35f;

    /** Where chimney smoke comes out, in locomotive space. */
    public static final Vector3f CHIMNEY = new Vector3f(0f, 4.35f, 3.55f);
    /** Steam-cylinder vents (whistle bursts), in locomotive space. */
    public static final Vector3f CYLINDER_L = new Vector3f(1.12f, 0.95f, 4.0f);
    public static final Vector3f CYLINDER_R = new Vector3f(-1.12f, 0.95f, 4.0f);

    public static final float HITBOX_WIDTH = 3.4f;
    public static final float HITBOX_HEIGHT = 3.7f;
    public static final float[] LOCO_HITBOX_Z = {-3.2f, 0f, 3.2f};
    public static final float[] WAGON_HITBOX_Z = {-2.5f, 0f, 2.5f};

    /** Seat spots (x, z) in car space; capacity is clamped by config. */
    public static final float[][] LOCO_SEATS = {{0.55f, -3.3f}, {-0.55f, -3.3f}, {0f, -2.6f}};
    public static final float[][] WAGON_SEATS = {
            {0.72f, -1.6f}, {-0.72f, -1.6f}, {0.72f, 1.6f}, {-0.72f, 1.6f}, {0.72f, 0f}, {-0.72f, 0f}
    };

    // Palette
    private static final Material FRAME = Material.POLISHED_BLACKSTONE;
    private static final Material BOILER = Material.WAXED_EXPOSED_COPPER;
    private static final Material TRIM_COPPER = Material.WAXED_COPPER_BLOCK;
    private static final Material BRASS = Material.GOLD_BLOCK;
    private static final Material STEEL = Material.IRON_BLOCK;
    private static final Material ROOF_DARK = Material.POLISHED_DEEPSLATE;
    private static final Material WOOD_DARK = Material.DARK_OAK_PLANKS;
    private static final Material WOOD_WARM = Material.SPRUCE_PLANKS;
    private static final Material WOOD_RED = Material.MANGROVE_PLANKS;
    private static final Material GLASS = Material.GLASS;
    private static final Material LAMP = Material.OCHRE_FROGLIGHT;

    private TrainModel() {
    }

    /**
     * Same idea, but the round face lies in the X-Y plane (thin along Z -
     * a disc seen face-on down the length axis, like the boiler). Rolled
     * about Z ("roll"), with an anti-z-fighting inset: the second (diamond)
     * copy is nudged a hair thinner than the first so their flat faces never
     * sit on the exact same plane - perfectly coplanar same-material quads
     * z-fight and shimmer, which is exactly the "gray squares" bug this avoids.
     */
    private static void octZ(List<TrainPart> m, Material mat, double x, double y, double z,
                             double faceSize, double octFaceSize, double thickness) {
        double inset = Math.min(0.04, thickness * 0.3);
        m.add(p(mat, x, y, z, faceSize, faceSize, thickness));
        m.add(rot(mat, x, y, z, octFaceSize, octFaceSize, Math.max(0.02, thickness - inset), 0, 0, 45));
    }

    /**
     * An octagon-ish rolling wheel whose round face lies in the Y-Z plane
     * (thin along X - a disc seen edge-on from the side, like a driver wheel
     * or a bogie wheel). Both facets are tagged to spin about local X (the
     * axle) as the train moves, at {@code radius}; using any other axis here
     * would just skew the box instead of faceting AND spinning it correctly.
     * Same anti-z-fighting inset as {@link #octZ}.
     */
    private static void octXWheel(List<TrainPart> m, Material mat, double x, double y, double z,
                                  double faceSize, double octFaceSize, double thickness,
                                  TrainPart.PhaseChannel channel, float radius) {
        double inset = Math.min(0.04, thickness * 0.3);
        m.add(TrainPart.wheel(mat, x, y, z, thickness, faceSize, faceSize, 0f, channel, radius));
        m.add(TrainPart.wheel(mat, x, y, z, Math.max(0.02, thickness - inset), octFaceSize, octFaceSize,
                45f, channel, radius));
    }

    // ------------------------------------------------------------ locomotive

    public static List<TrainPart> locomotive() {
        List<TrainPart> m = new ArrayList<>(96);

        // --- frame & deck
        m.add(p(FRAME, 0, 0.62, -0.1, 2.0, 0.28, 8.6));
        m.add(p(ROOF_DARK, 0, 0.80, 0.6, 2.35, 0.10, 6.6));
        m.add(p(WOOD_DARK, 0, 0.72, 4.3, 2.6, 0.45, 0.28));   // front buffer beam
        m.add(p(WOOD_DARK, 0, 0.72, -4.4, 2.6, 0.45, 0.20));  // rear buffer beam
        m.add(p(FRAME, 0, 0.55, -4.6, 0.24, 0.24, 0.5));      // rear coupler

        // --- cowcatcher (three angled plates + lower bar)
        m.add(rot(ROOF_DARK, 0, 0.55, 4.80, 1.4, 0.08, 1.4, 50, 0, 0));
        m.add(rot(ROOF_DARK, 0.62, 0.55, 4.70, 1.1, 0.08, 1.3, 50, 28, 0));
        m.add(rot(ROOF_DARK, -0.62, 0.55, 4.70, 1.1, 0.08, 1.3, 50, -28, 0));
        m.add(p(FRAME, 0, 0.18, 5.15, 1.9, 0.14, 0.14));

        // --- driver wheels (two big octagons per side) + leading wheels
        for (int side = -1; side <= 1; side += 2) {
            double x = side * 1.08;
            // Real locos "quarter" the two sides' cranks 90° apart so one
            // side always has torque; the coupling/piston rods below share it.
            float sideQuarterDeg = side > 0 ? 0f : 90f;
            for (double z : new double[]{-0.9, -2.5}) {
                octXWheel(m, FRAME, x, 0.75, z, 1.5, 1.5, 0.16,
                        TrainPart.PhaseChannel.DRIVER, DRIVER_WHEEL_RADIUS);
                m.add(TrainPart.wheel(TRIM_COPPER, x, 0.75, z, 0.20, 0.42, 0.42, 0f,
                        TrainPart.PhaseChannel.DRIVER, DRIVER_WHEEL_RADIUS)); // hub
            }
            for (double z : new double[]{2.3, 3.5}) {
                octXWheel(m, FRAME, x, 0.5, z, 1.0, 1.0, 0.14,
                        TrainPart.PhaseChannel.LEADING, LEADING_WHEEL_RADIUS);
            }
            // connecting/side rod along the drivers: orbits with the crank,
            // it doesn't rotate itself (both driver wheels move identically).
            m.add(TrainPart.rod(TRIM_COPPER, x * 1.11, 0.72, -1.7, 0.09, 0.16, 2.4,
                    TrainPart.PhaseChannel.DRIVER, CRANK_RADIUS, sideQuarterDeg));
        }

        // --- running-board skirt: closes the gap under the boiler/firebox so
        // they don't look like they float above the deck.
        m.add(p(FRAME, 0, 1.025, 1.225, 1.6, 0.35, 6.55));

        // --- boiler (octagonal) with copper bands
        octZ(m, BOILER, 0, 2.05, 1.35, 1.8, 1.55, 5.1);
        for (double z : new double[]{0.0, 1.4, 2.8}) {
            octZ(m, TRIM_COPPER, 0, 2.05, z, 1.88, 1.62, 0.12);
        }

        // --- smokebox + riveted face
        octZ(m, FRAME, 0, 2.05, 4.15, 1.7, 1.46, 0.7);
        octZ(m, STEEL, 0, 2.05, 4.55, 1.25, 1.06, 0.10);
        m.add(p(BRASS, 0, 2.05, 4.63, 0.40, 0.40, 0.10));     // door hub

        // --- chimney with flared top
        m.add(p(FRAME, 0, 3.40, 3.55, 0.48, 0.95, 0.48));
        m.add(p(BOILER, 0, 4.00, 3.55, 0.80, 0.30, 0.80));
        m.add(p(TRIM_COPPER, 0, 4.20, 3.55, 0.90, 0.12, 0.90));

        // --- domes & bell
        m.add(p(TRIM_COPPER, 0, 3.05, 1.7, 0.75, 0.42, 0.75)); // steam dome
        m.add(p(BRASS, 0, 3.35, 1.7, 0.55, 0.22, 0.55));
        m.add(p(BOILER, 0, 3.00, 0.4, 0.65, 0.35, 0.65));      // sand dome
        m.add(p(BRASS, 0, 3.20, 2.6, 0.30, 0.35, 0.30));       // bell

        // --- headlamp
        m.add(p(TRIM_COPPER, 0, 3.15, 4.25, 0.50, 0.55, 0.45));
        m.add(p(LAMP, 0, 3.15, 4.55, 0.34, 0.34, 0.10));

        // --- steam cylinders + piston rods
        for (int side = -1; side <= 1; side += 2) {
            double x = side * 1.12;
            float sideQuarterDeg = side > 0 ? 0f : 90f;
            m.add(p(FRAME, x, 0.95, 3.3, 0.55, 0.55, 1.3));
            m.add(p(BRASS, x, 0.95, 4.0, 0.62, 0.62, 0.16));
            // Slides back and forth in the cylinder, in phase with the drivers.
            m.add(TrainPart.piston(STEEL, x, 0.85, 2.2, 0.10, 0.10, 1.2,
                    TrainPart.PhaseChannel.DRIVER, CRANK_RADIUS, sideQuarterDeg));
        }

        // --- firebox between boiler and cab
        m.add(p(FRAME, 0, 1.7, -1.55, 1.7, 1.35, 1.0));

        // --- cab
        m.add(p(WOOD_WARM, 0, 0.95, -3.15, 2.5, 0.14, 2.5));               // floor
        m.add(p(BOILER, 1.22, 1.65, -3.15, 0.14, 1.3, 2.5));               // side lower
        m.add(p(BOILER, -1.22, 1.65, -3.15, 0.14, 1.3, 2.5));
        for (int side = -1; side <= 1; side += 2) {
            double x = side * 1.22;
            // Pillars/glass now run all the way up to the roof's underside
            // (was 0.8 tall, stopping 0.32 short - the roof used to visibly
            // float above the cab walls).
            m.add(p(BOILER, x, 2.86, -2.05, 0.14, 1.12, 0.30));            // window pillars
            m.add(p(BOILER, x, 2.86, -3.15, 0.14, 1.12, 0.24));
            m.add(p(BOILER, x, 2.86, -4.25, 0.14, 1.12, 0.30));
            m.add(p(GLASS, x, 2.86, -3.15, 0.07, 1.10, 2.3));              // side glass band
        }
        m.add(p(BOILER, 0, 2.22, -1.95, 2.5, 2.44, 0.14));                 // front wall (reaches the roof)
        m.add(p(GLASS, 0.68, 2.75, -1.86, 0.50, 0.55, 0.08));              // front windows
        m.add(p(GLASS, -0.68, 2.75, -1.86, 0.50, 0.55, 0.08));
        m.add(p(BOILER, 0, 1.55, -4.35, 2.5, 1.1, 0.14));                  // rear half-wall
        m.add(p(ROOF_DARK, 0, 3.50, -3.15, 2.95, 0.16, 2.9));              // roof
        m.add(p(FRAME, 0, 3.62, -3.15, 2.3, 0.10, 2.7));                   // roof cap

        // --- boiler walkways + handrails
        m.add(p(WOOD_DARK, 1.14, 0.92, 1.1, 0.45, 0.10, 4.6));
        m.add(p(WOOD_DARK, -1.14, 0.92, 1.1, 0.45, 0.10, 4.6));
        m.add(p(BRASS, 1.30, 1.45, 1.1, 0.06, 0.06, 4.4));
        m.add(p(BRASS, -1.30, 1.45, 1.1, 0.06, 0.06, 4.4));

        return m;
    }

    // ------------------------------------------------------------ wagons

    /** Passenger car, {@code variant} 0..2 - each dressed a little differently. */
    public static List<TrainPart> wagon(int variant) {
        Material body;
        Material trim;
        Material roof;
        boolean clerestory = false;  // raised centre roof on variant 1
        boolean roofCargo = false;   // barrels strapped on top of variant 2
        switch (Math.floorMod(variant, 3)) {
            case 0 -> {
                body = WOOD_RED;
                trim = WOOD_DARK;
                roof = ROOF_DARK;
            }
            case 1 -> {
                body = WOOD_WARM;
                trim = WOOD_RED;
                roof = Material.POLISHED_BLACKSTONE;
                clerestory = true;
            }
            default -> {
                body = WOOD_DARK;
                trim = WOOD_WARM;
                roof = ROOF_DARK;
                roofCargo = true;
            }
        }

        List<TrainPart> m = new ArrayList<>(64);

        // --- frame, floor, copper skirt
        m.add(p(FRAME, 0, 0.72, 0, 2.2, 0.24, 7.3));
        m.add(p(trim, 0, 0.95, 0, 2.55, 0.12, 7.2));
        m.add(p(TRIM_COPPER, 1.25, 1.02, 0, 0.08, 0.18, 7.0));
        m.add(p(TRIM_COPPER, -1.25, 1.02, 0, 0.08, 0.18, 7.0));

        // --- sides: lower panel, long glass band, pillars, top band
        for (int side = -1; side <= 1; side += 2) {
            double x = side * 1.24;
            m.add(p(body, x, 1.55, 0, 0.12, 1.0, 7.0));
            m.add(p(GLASS, x, 2.40, 0, 0.06, 0.7, 6.5));
            for (double z : new double[]{-2.6, -1.3, 0, 1.3, 2.6}) {
                m.add(p(body, x, 2.40, z, 0.14, 0.8, 0.26));
            }
            m.add(p(body, x, 2.40, 3.38, 0.14, 0.8, 0.75));
            m.add(p(body, x, 2.40, -3.38, 0.14, 0.8, 0.75));
            m.add(p(body, x, 3.0, 0, 0.12, 0.5, 7.0));                     // top band (reaches the roof)
        }

        // --- end walls with doors, steps and couplers
        for (int end = -1; end <= 1; end += 2) {
            double z = end * 3.68;
            m.add(p(body, 0, 2.1, z, 2.45, 2.2, 0.14));
            m.add(p(trim, 0, 2.0, end * 3.76, 0.85, 1.9, 0.08));
            m.add(p(trim, 0, 0.45, end * 4.0, 0.9, 0.10, 0.30));
            m.add(p(FRAME, 0, 0.6, end * 3.95, 0.22, 0.22, 0.45));
        }

        // --- roof
        m.add(p(roof, 0, 3.28, 0, 2.75, 0.16, 7.6));
        if (clerestory) {
            m.add(p(body, 0, 3.50, 0, 1.3, 0.32, 6.4));
            m.add(p(roof, 0, 3.72, 0, 1.5, 0.10, 6.6));
        } else {
            m.add(p(roof, 0, 3.42, 0, 2.1, 0.12, 7.3));
        }
        if (roofCargo) {
            m.add(p(Material.BARREL, 0.5, 3.65, -1.0, 0.55, 0.6, 0.55));
            m.add(p(Material.BARREL, -0.45, 3.65, 0.6, 0.55, 0.6, 0.55));
        }

        // --- two bogies with octagonal wheels
        for (int end = -1; end <= 1; end += 2) {
            double bz = end * 2.55;
            m.add(p(FRAME, 0, 0.42, bz, 1.5, 0.4, 1.1));
            for (int side = -1; side <= 1; side += 2) {
                double x = side * 1.02;
                for (int a = -1; a <= 1; a += 2) {
                    double z = bz + a * 0.42;
                    octXWheel(m, FRAME, x, 0.42, z, 0.84, 0.84, 0.13,
                            TrainPart.PhaseChannel.BOGIE, BOGIE_WHEEL_RADIUS);
                }
            }
        }

        return m;
    }
}
