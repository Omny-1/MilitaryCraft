package me.bibo.militarycraft.core.placeable;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceableStateTest {

    @Test
    void normalizesYawToStableSignedRange() {
        assertEquals(0.0, PlaceableState.normalizeYaw(720.0));
        assertEquals(180.0, PlaceableState.normalizeYaw(-180.0));
        assertEquals(-179.0, PlaceableState.normalizeYaw(181.0));
        assertEquals(90.0, PlaceableState.normalizeYaw(-630.0));
        assertThrows(IllegalArgumentException.class,
                () -> PlaceableState.normalizeYaw(Double.NaN));
    }

    @Test
    void clampsPersistedHealthAndRepairsNonFiniteValues() {
        assertEquals(100.0, PlaceableState.clampHealth(Double.NaN, 100.0));
        assertEquals(100.0, PlaceableState.clampHealth(120.0, 100.0));
        assertEquals(0.0, PlaceableState.clampHealth(-3.0, 100.0));
        assertEquals(45.5, PlaceableState.clampHealth(45.5, 100.0));
        assertThrows(IllegalArgumentException.class,
                () -> PlaceableState.clampHealth(1.0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> PlaceableState.clampHealth(1.0, 0.0));
    }

    @Test
    void parsesOnlyValidUuids() {
        UUID id = UUID.randomUUID();
        assertEquals(id, PlaceableState.parseUuid(id.toString()).orElseThrow());
        assertTrue(PlaceableState.parseUuid(null).isEmpty());
        assertTrue(PlaceableState.parseUuid("").isEmpty());
        assertTrue(PlaceableState.parseUuid("not-a-uuid").isEmpty());
    }

    @Test
    void stableIdsArePdcSafeAndSchemaNeverDropsBelowOne() {
        assertEquals("barrel.left_1", PlaceableState.requireStableId("barrel.left_1", "part id"));
        assertThrows(IllegalArgumentException.class,
                () -> PlaceableState.requireStableId("Barrel Left", "part id"));
        assertThrows(IllegalArgumentException.class,
                () -> PlaceableState.requireStableId("", "part id"));
        assertEquals(1, PlaceableState.normalizeSchemaVersion(-5));
        assertEquals(3, PlaceableState.normalizeSchemaVersion(3));
    }

    @Test
    void horizontalCoordinatesRejectInfinityAndWorldOverflow() {
        assertTrue(PlaceableState.isUsableHorizontalCoordinate(29_999_999.0));
        assertFalse(PlaceableState.isUsableHorizontalCoordinate(30_000_001.0));
        assertFalse(PlaceableState.isUsableHorizontalCoordinate(Double.NEGATIVE_INFINITY));
    }
}
