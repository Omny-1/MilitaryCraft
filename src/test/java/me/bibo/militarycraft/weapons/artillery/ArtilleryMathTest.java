package me.bibo.militarycraft.weapons.artillery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtilleryMathTest {

    @Test
    void spreadRadiusUsesClampedDistanceCurve() {
        assertEquals(4.0, ArtilleryMath.spreadRadius(0.0, 4.0, 20.0, 1000.0, 2.0), 1.0e-12);
        assertEquals(8.0, ArtilleryMath.spreadRadius(500.0, 4.0, 20.0, 1000.0, 2.0), 1.0e-12);
        assertEquals(20.0, ArtilleryMath.spreadRadius(5000.0, 4.0, 20.0, 1000.0, 2.0), 1.0e-12);
        assertEquals(9.5, ArtilleryMath.spreadRadius(600.0, 6.0, 13.0, 1200.0, 1.0), 1.0e-12);
    }

    @Test
    void uniformDiscSamplingUsesSquareRootRadius() {
        ArtilleryMath.Offset offset = ArtilleryMath.sampleUniformDisc(12.0, 0.25, 0.0);
        assertEquals(6.0, offset.x(), 1.0e-12);
        assertEquals(0.0, offset.z(), 1.0e-12);

        for (int radial = 0; radial <= 10; radial++) {
            for (int angular = 0; angular <= 10; angular++) {
                ArtilleryMath.Offset sample = ArtilleryMath.sampleUniformDisc(
                        12.0, radial / 10.0, angular / 10.0);
                assertTrue(Math.hypot(sample.x(), sample.z()) <= 12.0 + 1.0e-12);
            }
        }
    }

    @Test
    void flightTimeScalesWithHorizontalDistance() {
        assertEquals(20, ArtilleryMath.flightTicks(0.0, 1200.0, 20, 200));
        assertEquals(110, ArtilleryMath.flightTicks(600.0, 1200.0, 20, 200));
        assertEquals(200, ArtilleryMath.flightTicks(1200.0, 1200.0, 20, 200));
        assertEquals(200, ArtilleryMath.flightTicks(2400.0, 1200.0, 20, 200));
    }
}
