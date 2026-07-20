package me.bibo.militarycraft.vehicles.moto.model;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotorcycleModelTest {

    private static final float EPS = 1.0e-5f;

    @Test
    void fixedPartListIsStableAndImmutable() {
        List<MotorcyclePart> parts = MotorcycleModel.parts();

        assertEquals(67, parts.size());
        assertTrue(parts == MotorcycleModel.parts(), "parts() must retain stable index identity");
        assertThrows(UnsupportedOperationException.class, () -> parts.add(parts.get(0)));
        assertEquals(1, parts.stream().filter(MotorcyclePart::isText).count());
        assertEquals(0, parts.stream()
                .filter(part -> part.role == MotorcyclePart.Role.INDICATOR).count());
    }

    @Test
    void modelHasThreeRollingWheelsAndUnifiedSteeringAssembly() {
        List<MotorcyclePart> parts = MotorcycleModel.parts();

        assertEquals(9, parts.stream().filter(part -> part.rollsWithWheel).count());
        assertEquals(23, parts.stream().filter(part -> part.steersWithWheel).count());
        assertEquals(3, parts.stream()
                .filter(part -> part.rollsWithWheel && part.steersWithWheel).count());
        assertEquals(20, parts.stream()
                .filter(part -> part.steersWithWheel && !part.rollsWithWheel).count());

        assertEquals(3, rollingPartsAt(parts, MotorcycleModel.frontWheelCenter()));
        assertEquals(3, rollingPartsAt(parts, MotorcycleModel.rearWheelCenter()));
        assertEquals(3, rollingPartsAt(parts, MotorcycleModel.sidecarWheelCenter()));
    }

    @Test
    void crossedWheelTilesRespectDeclaredRadiusInsteadOfGrowingBySqrtTwo() {
        List<MotorcyclePart> parts = MotorcycleModel.parts();
        float expectedTileSide = (float) (Math.sqrt(2.0) * MotorcycleModel.WHEEL_RADIUS);

        for (int wheelStart : new int[]{0, 3, 6}) {
            MotorcyclePart square = parts.get(wheelStart);
            MotorcyclePart diamond = parts.get(wheelStart + 1);
            assertEquals(MotorcyclePart.Role.WHEEL, square.role);
            assertEquals(MotorcyclePart.Role.WHEEL, diamond.role);
            assertEquals(expectedTileSide, square.scale.y, EPS);
            assertEquals(expectedTileSide, square.scale.z, EPS);
            assertEquals(expectedTileSide, diamond.scale.y, EPS);
            assertEquals(expectedTileSide, diamond.scale.z, EPS);

            float squareExtent = rotatedSquareAxisExtent(square.scale.y, square.pitch);
            float diamondExtent = rotatedSquareAxisExtent(diamond.scale.y, diamond.pitch);
            assertTrue(squareExtent <= MotorcycleModel.WHEEL_RADIUS + EPS);
            assertEquals(MotorcycleModel.WHEEL_RADIUS, diamondExtent, EPS);
            assertEquals(0f, diamond.offset.y - diamondExtent, EPS,
                    "the wheel must touch, not penetrate, the ground plane");
        }
    }

    @Test
    void entireFrontAssemblySharesOneSteeringPivotSoItTurnsRigidly() {
        List<MotorcyclePart> parts = MotorcycleModel.parts();
        Vector3f pivot = MotorcycleModel.steeringHeadPivot();

        // Front wheel (0..2), front fender (29..31) and the fork/handlebar blocks
        // (35..44, 60..66) must all steer about the SAME axis, or they detach when turned.
        for (int i = 0; i <= 2; i++) {
            assertTrue(parts.get(i).steersWithWheel);
            assertVector(pivot, parts.get(i).steeringPivot);
        }
        for (int i = 29; i <= 31; i++) {
            assertTrue(parts.get(i).steersWithWheel);
            assertFalse(parts.get(i).rollsWithWheel);
            assertVector(pivot, parts.get(i).steeringPivot);
        }
        for (int i = 35; i <= 44; i++) {
            assertTrue(parts.get(i).steersWithWheel);
            assertVector(pivot, parts.get(i).steeringPivot);
        }
        for (int i = 60; i <= 66; i++) {
            assertTrue(parts.get(i).steersWithWheel);
            assertVector(pivot, parts.get(i).steeringPivot);
        }

        // The front wheel sits ahead of the axis, so steering must ORBIT it about the
        // shared pivot (not spin it in place) - that is what keeps it under the fork.
        MotorcyclePart frontWheel = parts.get(0);
        Vector3f steered = Transforms.articulatedLocalOffset(frontWheel, 32.0);
        assertNotEquals(frontWheel.offset.x, steered.x, EPS,
                "front wheel must orbit the shared steering axis, not spin in place");
        assertEquals(new Vector3f(frontWheel.offset).sub(pivot).length(),
                new Vector3f(steered).sub(pivot).length(), EPS);

        // A fork part orbits the very same pivot at its own radius: same axis = rigid.
        MotorcyclePart fork = parts.get(35);
        Vector3f forkSteered = Transforms.articulatedLocalOffset(fork, 32.0);
        assertEquals(new Vector3f(fork.offset).sub(pivot).length(),
                new Vector3f(forkSteered).sub(pivot).length(), EPS);
    }

    @Test
    void steerOnlyPartsIgnoreWheelSpinButRollingPartsDoNot() {
        MotorcyclePart fork = MotorcycleModel.parts().get(35);
        Transformation forkAtZero = Transforms.forPart(fork, 17.0, 0.0, 20.0);
        Transformation forkAtSpin = Transforms.forPart(fork, 17.0, 123.0, 20.0);
        assertQuaternion(forkAtZero.getLeftRotation(), forkAtSpin.getLeftRotation());

        MotorcyclePart frontTyre = MotorcycleModel.parts().get(0);
        Transformation tyreAtZero = Transforms.forPart(frontTyre, 17.0, 0.0, 20.0);
        Transformation tyreAtSpin = Transforms.forPart(frontTyre, 17.0, 123.0, 20.0);
        assertFalse(quaternionEquals(tyreAtZero.getLeftRotation(), tyreAtSpin.getLeftRotation()));
    }

    @Test
    void blockTransformKeepsDeclaredPartCentreForYawAndBaseRotation() {
        MotorcyclePart part = MotorcycleModel.parts().get(24);
        double hullYaw = 73.0;
        Transformation transform = Transforms.forPart(part, hullYaw, 0.0, 0.0);

        Vector3f renderedCentre = new Vector3f(transform.getScale()).mul(0.5f);
        transform.getLeftRotation().transform(renderedCentre);
        renderedCentre.add(transform.getTranslation());
        Vector3f expectedCentre = Transforms.localPointToWorld(part.offset, hullYaw);
        assertVector(expectedCentre, renderedCentre);
    }

    @Test
    void coordinateConventionAndSeatAnchorsRemainSidecarSpecific() {
        assertEquals(0.625f, MotorcycleModel.WHEEL_RADIUS, EPS);
        assertEquals(2.85f, MotorcycleModel.WIDTH, EPS);
        assertEquals(3.45f, MotorcycleModel.LENGTH, EPS);
        assertEquals(2.15f, MotorcycleModel.HEIGHT, EPS);
        assertTrue(MotorcycleModel.sidecarWheelCenter().x < 0f);

        Vector3f driver = MotorcycleModel.driverMountOffset();
        Vector3f pillion = MotorcycleModel.pillionMountOffset();
        Vector3f sidecar = MotorcycleModel.sidecarMountOffset();
        assertEquals(0f, driver.x, EPS);
        assertEquals(0f, pillion.x, EPS);
        assertTrue(pillion.z < driver.z);
        assertTrue(sidecar.x < 0f);
    }

    @Test
    void partConstructorDefensivelyCopiesInputVectors() {
        Vector3f offset = new Vector3f(1f, 2f, 3f);
        Vector3f scale = new Vector3f(0.5f);
        Vector3f pivot = new Vector3f(0f, 1f, 0f);
        MotorcyclePart part = MotorcyclePart.steerOnly(MotorcyclePart.Role.FRAME,
                offset, scale, 0f, 0f, 0f, pivot);

        offset.set(99f);
        scale.set(99f);
        pivot.set(99f);
        assertVector(new Vector3f(1f, 2f, 3f), part.offset);
        assertVector(new Vector3f(0.5f), part.scale);
        assertVector(new Vector3f(0f, 1f, 0f), part.steeringPivot);
    }

    @Test
    void soloVariantDropsEverySidecarPart() {
        List<MotorcyclePart> full = MotorcycleModel.parts(true);
        List<MotorcyclePart> solo = MotorcycleModel.parts(false);

        assertTrue(full == MotorcycleModel.parts());
        assertEquals(67, full.size());
        assertEquals(53, solo.size()); // 14 sidecar parts removed (wheel, fender, tub, lamp)

        Vector3f sidecarWheel = MotorcycleModel.sidecarWheelCenter();
        assertEquals(3, full.stream()
                .filter(part -> part.offset.distance(sidecarWheel) < EPS).count());
        assertEquals(0, solo.stream()
                .filter(part -> part.offset.distance(sidecarWheel) < EPS).count());

        // Front + rear wheels (2 × 3 rolling parts) survive; the sidecar wheel is gone.
        assertEquals(9, full.stream().filter(part -> part.rollsWithWheel).count());
        assertEquals(6, solo.stream().filter(part -> part.rollsWithWheel).count());
    }

    private static long rollingPartsAt(List<MotorcyclePart> parts, Vector3f centre) {
        return parts.stream()
                .filter(part -> part.rollsWithWheel)
                .filter(part -> part.offset.distance(centre) < EPS)
                .count();
    }

    private static float rotatedSquareAxisExtent(float side, float pitchDegrees) {
        double angle = Math.toRadians(pitchDegrees);
        return (float) (side * 0.5 * (Math.abs(Math.cos(angle)) + Math.abs(Math.sin(angle))));
    }

    private static boolean quaternionEquals(Quaternionf expected, Quaternionf actual) {
        return Math.abs(expected.x - actual.x) < EPS
                && Math.abs(expected.y - actual.y) < EPS
                && Math.abs(expected.z - actual.z) < EPS
                && Math.abs(expected.w - actual.w) < EPS;
    }

    private static void assertQuaternion(Quaternionf expected, Quaternionf actual) {
        assertTrue(quaternionEquals(expected, actual),
                () -> "expected " + expected + " but got " + actual);
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, EPS);
        assertEquals(expected.y, actual.y, EPS);
        assertEquals(expected.z, actual.z, EPS);
    }
}
