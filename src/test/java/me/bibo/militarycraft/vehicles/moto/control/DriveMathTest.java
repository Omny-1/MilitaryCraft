package me.bibo.militarycraft.vehicles.moto.control;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriveMathTest {

    private static final double EPS = 1.0e-9;

    @Test
    void opposingInputsCancel() {
        assertAll(
                () -> assertEquals(1, DriveMath.signedInput(true, false)),
                () -> assertEquals(-1, DriveMath.signedInput(false, true)),
                () -> assertEquals(0, DriveMath.signedInput(true, true)),
                () -> assertEquals(0, DriveMath.signedInput(false, false))
        );
    }

    @Test
    void throttleTargetHandlesForwardReverseNeutralAndStall() {
        assertAll(
                () -> assertEquals(0.52, DriveMath.throttleTarget(true, false, false, 0.52, 0.10), EPS),
                () -> assertEquals(-0.10, DriveMath.throttleTarget(false, true, false, 0.52, 0.10), EPS),
                () -> assertEquals(0.0, DriveMath.throttleTarget(true, true, false, 0.52, 0.10), EPS),
                () -> assertEquals(0.0, DriveMath.throttleTarget(true, false, true, 0.52, 0.10), EPS),
                () -> assertEquals(0.0, DriveMath.throttleTarget(true, false, false, Double.NaN, 0.10), EPS),
                () -> assertEquals(0.0, DriveMath.throttleTarget(false, true, false, 0.52, -1.0), EPS)
        );
    }

    @Test
    void nextSpeedUsesAccelerationBrakeAndFriction() {
        assertAll(
                () -> assertEquals(0.012, DriveMath.nextSpeed(0.0, 0.52, 0.012, 0.035, 0.008), EPS),
                () -> assertEquals(0.165, DriveMath.nextSpeed(0.20, -0.10, 0.012, 0.035, 0.008), EPS),
                () -> assertEquals(-0.065, DriveMath.nextSpeed(-0.10, 0.52, 0.012, 0.035, 0.008), EPS),
                () -> assertEquals(0.0, DriveMath.nextSpeed(0.02, -0.10, 0.012, 0.035, 0.008), EPS),
                () -> assertEquals(0.092, DriveMath.nextSpeed(0.10, 0.0, 0.012, 0.035, 0.008), EPS),
                () -> assertEquals(0.0, DriveMath.nextSpeed(0.004, 0.0, 0.012, 0.035, 0.008), EPS)
        );
    }

    @Test
    void nextSpeedSanitisesInvalidAndZeroRates() {
        assertAll(
                () -> assertEquals(0.012, DriveMath.nextSpeed(Double.NaN, 0.52, 0.012, 0.035, 0.008), EPS),
                () -> assertEquals(0.10, DriveMath.nextSpeed(0.10, Double.NaN, 0.012, 0.035, 0.0), EPS),
                () -> assertEquals(0.10, DriveMath.nextSpeed(0.10, 0.52, -1.0, 0.035, 0.008), EPS),
                () -> assertEquals(0.10, DriveMath.nextSpeed(0.10, 0.52, Double.POSITIVE_INFINITY, 0.035, 0.008), EPS)
        );
    }

    @Test
    void handlebarMovesAndReturnsAtConfiguredRate() {
        assertAll(
                () -> assertEquals(8.0, DriveMath.nextHandlebar(0.0, 1, 32.0, 8.0), EPS),
                () -> assertEquals(-8.0, DriveMath.nextHandlebar(0.0, -1, 32.0, 8.0), EPS),
                () -> assertEquals(12.0, DriveMath.nextHandlebar(20.0, 0, 32.0, 8.0), EPS),
                () -> assertEquals(32.0, DriveMath.nextHandlebar(31.0, 1, 32.0, 8.0), EPS),
                () -> assertEquals(0.0, DriveMath.nextHandlebar(12.0, 1, 0.0, 8.0), EPS)
        );
    }

    @Test
    void handlebarSanitisesInvalidValuesAndInputRange() {
        assertAll(
                () -> assertEquals(8.0, DriveMath.nextHandlebar(Double.NaN, 4, 32.0, 8.0), EPS),
                () -> assertEquals(0.0, DriveMath.nextHandlebar(12.0, 0, Double.NaN, 8.0), EPS),
                () -> assertEquals(12.0, DriveMath.nextHandlebar(12.0, 1, 32.0, -1.0), EPS)
        );
    }

    @Test
    void yawIsZeroAtRestOrWithUnavailableSteering() {
        assertAll(
                () -> assertEquals(0.0, DriveMath.yawDeltaDegrees(0.0, 32.0, 32.0, 0.52, 4.8, 0.48), EPS),
                () -> assertEquals(0.0, DriveMath.yawDeltaDegrees(0.52, 0.0, 32.0, 0.52, 4.8, 0.48), EPS),
                () -> assertEquals(0.0, DriveMath.yawDeltaDegrees(0.52, 32.0, 0.0, 0.52, 4.8, 0.48), EPS),
                () -> assertEquals(0.0, DriveMath.yawDeltaDegrees(0.52, 32.0, 32.0, 0.0, 4.8, 0.48), EPS),
                () -> assertEquals(0.0, DriveMath.yawDeltaDegrees(0.52, 32.0, 32.0, 0.52, 0.0, 0.48), EPS)
        );
    }

    @Test
    void yawUsesSmoothHighSpeedReduction() {
        // ratio=.5, smoothstep=.5, factor=1 + (.48-1)*.5 = .74
        assertAll(
                () -> assertEquals(1.776,
                        DriveMath.yawDeltaDegrees(0.26, 32.0, 32.0, 0.52, 4.8, 0.48), EPS),
                () -> assertEquals(2.304,
                        DriveMath.yawDeltaDegrees(0.52, 32.0, 32.0, 0.52, 4.8, 0.48), EPS),
                () -> assertEquals(2.304,
                        DriveMath.yawDeltaDegrees(5.0, 100.0, 32.0, 0.52, 4.8, 0.48), EPS)
        );
    }

    @Test
    void reverseSpeedInvertsHullYawButNotHandlebarDirection() {
        double forward = DriveMath.yawDeltaDegrees(0.10, 16.0, 32.0, 0.52, 4.8, 0.48);
        double reverse = DriveMath.yawDeltaDegrees(-0.10, 16.0, 32.0, 0.52, 4.8, 0.48);
        assertTrue(forward > 0.0);
        assertEquals(-forward, reverse, EPS);
    }

    @Test
    void yawRejectsInvalidInputsAndDefaultsInvalidHighSpeedFactorSafely() {
        assertAll(
                () -> assertEquals(0.0,
                        DriveMath.yawDeltaDegrees(Double.NaN, 32.0, 32.0, 0.52, 4.8, 0.48), EPS),
                () -> assertEquals(0.0,
                        DriveMath.yawDeltaDegrees(0.52, Double.POSITIVE_INFINITY, 32.0, 0.52, 4.8, 0.48), EPS),
                () -> assertEquals(4.8,
                        DriveMath.yawDeltaDegrees(0.52, 32.0, 32.0, 0.52, 4.8, Double.NaN), EPS)
        );
    }

    @Test
    void substepsCoverTranslationAndRotationAndHaveSafeBounds() {
        assertAll(
                () -> assertEquals(3, DriveMath.substepCount(0.52, 0.0, 0.18, 2.0)),
                () -> assertEquals(3, DriveMath.substepCount(0.0, 4.8, 0.18, 2.0)),
                () -> assertEquals(4, DriveMath.substepCount(0.52, 7.1, 0.18, 2.0)),
                () -> assertEquals(1, DriveMath.substepCount(0.0, 0.0, 0.18, 2.0)),
                () -> assertEquals(1, DriveMath.substepCount(Double.NaN, Double.NaN, 0.18, 2.0)),
                () -> assertEquals(1, DriveMath.substepCount(1.0, 1.0, 0.0, Double.NaN)),
                () -> assertEquals(64, DriveMath.substepCount(1_000.0, 1_000.0, 0.01, 0.01))
        );
    }

    @Test
    void angularInterpolationUsesShortestWrappedPath() {
        assertAll(
                () -> assertEquals(180.0, DriveMath.interpolateAngle(170.0, -170.0, 0.5), EPS),
                () -> assertEquals(170.0, DriveMath.interpolateAngle(170.0, -170.0, -1.0), EPS),
                () -> assertEquals(-170.0, DriveMath.interpolateAngle(170.0, -170.0, 2.0), EPS),
                () -> assertEquals(0.0, DriveMath.interpolateAngle(Double.NaN, 20.0, 0.5), EPS)
        );
    }

    @Test
    void minecraftYawForwardVectorsAreCorrect() {
        assertAll(
                () -> assertEquals(0.0, DriveMath.forwardX(0.0), EPS),
                () -> assertEquals(1.0, DriveMath.forwardZ(0.0), EPS),
                () -> assertEquals(-1.0, DriveMath.forwardX(90.0), EPS),
                () -> assertEquals(0.0, DriveMath.forwardZ(90.0), EPS),
                () -> assertEquals(1.0, DriveMath.forwardX(-90.0), EPS),
                () -> assertEquals(0.0, DriveMath.forwardX(Double.NaN), EPS),
                () -> assertEquals(1.0, DriveMath.forwardZ(Double.NaN), EPS)
        );
    }

    @Test
    void localPointsRotateAroundAnchor() {
        assertAll(
                () -> assertEquals(10.0, DriveMath.localToWorldX(10.0, 0.0, 0.0, 2.0), EPS),
                () -> assertEquals(22.0, DriveMath.localToWorldZ(20.0, 0.0, 0.0, 2.0), EPS),
                () -> assertEquals(8.0, DriveMath.localToWorldX(10.0, 90.0, 0.0, 2.0), EPS),
                () -> assertEquals(20.0, DriveMath.localToWorldZ(20.0, 90.0, 0.0, 2.0), EPS),
                () -> assertTrue(Double.isNaN(DriveMath.localToWorldX(Double.NaN, 0.0, 0.0, 0.0)))
        );
    }

    @Test
    void satDetectsOverlapContainmentRotationAndTouching() {
        assertAll(
                () -> assertTrue(DriveMath.obbIntersectsAabb(
                        0, 0, 0, 1, 2, -0.5, -0.5, 0.5, 0.5)),
                () -> assertTrue(DriveMath.obbIntersectsAabb(
                        0, 0, 45, 0.25, 3, -0.2, -0.2, 0.2, 0.2)),
                () -> assertTrue(DriveMath.obbIntersectsAabb(
                        0, 0, 90, 0.25, 2, -2, -0.1, 2, 0.1)),
                () -> assertTrue(DriveMath.obbIntersectsAabb(
                        0, 0, 0, 1, 2, 1, -0.2, 2, 0.2)),
                () -> assertTrue(DriveMath.obbIntersectsAabb(
                        0, 0, 0, 1, 2, 0.5, 0.5, -0.5, -0.5))
        );
    }

    @Test
    void satRejectsSeparatedAndInvalidShapes() {
        assertAll(
                () -> assertFalse(DriveMath.obbIntersectsAabb(
                        0, 0, 0, 1, 2, 2.01, -0.5, 3.0, 0.5)),
                () -> assertFalse(DriveMath.obbIntersectsAabb(
                        0, 0, 45, 0.25, 1, 3, 3, 4, 4)),
                () -> assertFalse(DriveMath.obbIntersectsAabb(
                        Double.NaN, 0, 0, 1, 2, -1, -1, 1, 1)),
                () -> assertFalse(DriveMath.obbIntersectsAabb(
                        0, 0, 0, -1, 2, -1, -1, 1, 1))
        );
    }

    @Test
    void verticalVelocityAppliesGravityAndTerminalCap() {
        assertAll(
                () -> assertEquals(-0.08, DriveMath.nextVerticalVelocity(0.0, 0.08, 1.5), EPS),
                () -> assertEquals(-1.5, DriveMath.nextVerticalVelocity(-1.49, 0.08, 1.5), EPS),
                () -> assertEquals(0.92, DriveMath.nextVerticalVelocity(1.0, 0.08, 1.5), EPS),
                () -> assertEquals(-0.08, DriveMath.nextVerticalVelocity(Double.NaN, 0.08, 1.5), EPS),
                () -> assertEquals(0.0, DriveMath.nextVerticalVelocity(0.0, Double.NaN, 1.5), EPS),
                () -> assertEquals(0.0, DriveMath.nextVerticalVelocity(0.0, 0.08, 0.0), EPS),
                () -> assertEquals(0.92, DriveMath.nextVerticalVelocity(1.0, 0.08, 0.0), EPS)
        );
    }

    @Test
    void lowSpeedDamageNeverCrossesHealthFloor() {
        assertAll(
                () -> assertEquals(2.0, DriveMath.cappedNonLethalDamage(10.0, 2.0, 1.0), EPS),
                () -> assertEquals(0.5, DriveMath.cappedNonLethalDamage(1.5, 2.0, 1.0), EPS),
                () -> assertEquals(0.0, DriveMath.cappedNonLethalDamage(1.0, 2.0, 1.0), EPS),
                () -> assertEquals(1.0, DriveMath.cappedNonLethalDamage(1.0, 2.0, -5.0), EPS),
                () -> assertEquals(0.0, DriveMath.cappedNonLethalDamage(Double.NaN, 2.0, 1.0), EPS),
                () -> assertEquals(0.0, DriveMath.cappedNonLethalDamage(10.0, Double.POSITIVE_INFINITY, 1.0), EPS),
                () -> assertEquals(0.0, DriveMath.cappedNonLethalDamage(10.0, -1.0, 1.0), EPS)
        );
    }

    @Test
    void approachAndWrapHandleBoundaryValues() {
        assertAll(
                () -> assertEquals(2.0, DriveMath.approach(1.0, 3.0, 1.0), EPS),
                () -> assertEquals(1.0, DriveMath.approach(1.0, 3.0, 0.0), EPS),
                () -> assertEquals(0.0, DriveMath.approach(Double.NaN, Double.NaN, 1.0), EPS),
                () -> assertEquals(180.0, DriveMath.wrapDegrees(-180.0), EPS),
                () -> assertEquals(-179.0, DriveMath.wrapDegrees(181.0), EPS),
                () -> assertEquals(0.0, DriveMath.wrapDegrees(Double.POSITIVE_INFINITY), EPS)
        );
    }
}
