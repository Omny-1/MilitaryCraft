package me.bibo.militarycraft.vehicles.aircraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AircraftSafetyTest {

    @Test
    void clampsNonFiniteAndExtremeValues() {
        assertEquals(0.0, AircraftSafety.clamp(Double.NaN, 0.0, 10.0));
        assertEquals(0.0, AircraftSafety.clamp(Double.POSITIVE_INFINITY, 0.0, 10.0));
        assertEquals(10.0, AircraftSafety.clamp(100.0, 0.0, 10.0));
        assertEquals(-2.0, AircraftSafety.clamp(-5.0, -2.0, 2.0));
        assertEquals(4, AircraftSafety.clamp(99, 1, 4));
    }

    @Test
    void validatesFiniteCoordinates() {
        assertTrue(AircraftSafety.coordinatesFinite(1.0, -20.0, 3.0));
        assertFalse(AircraftSafety.coordinatesFinite(Double.NaN, 0.0, 0.0));
        assertFalse(AircraftSafety.coordinatesFinite(0.0, Double.NEGATIVE_INFINITY, 0.0));
    }

    @Test
    void limitsDisplayTextAndRemovesControlCharacters() {
        assertEquals("Callsign", AircraftSafety.limitText("Call\nsign", "fallback", 20));
        assertEquals("Long", AircraftSafety.limitText("Long Callsign", "fallback", 4));
        assertEquals("fallback", AircraftSafety.limitText(null, "fallback", 20));
    }

    @Test
    void munitionSpecEnforcesHardRuntimeLimits() {
        AirMunitionSpec spec = new AirMunitionSpec(
                -1.0, Integer.MAX_VALUE, -5, -20.0, Float.POSITIVE_INFINITY,
                true, true, -10.0, Double.POSITIVE_INFINITY, null, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Double.NaN);

        assertEquals(0.0, spec.gravity());
        assertEquals(AircraftSafety.MAX_SUBSTEPS, spec.substeps());
        assertEquals(1, spec.lifetimeTicks());
        assertEquals(0.0, spec.maxRange());
        assertEquals(0.0f, spec.explosionPower());
        assertEquals(0.0, spec.directVehicleDamage());
        assertEquals(0.0, spec.directLivingDamage());
        assertEquals(AircraftSafety.MAX_TRAIL_PARTICLES, spec.trailCount());
        assertEquals(AircraftSafety.MAX_EFFECT_DURATION_TICKS, spec.impactSmokeDuration());
        assertEquals(0.0, spec.impactSmokeRadius());
    }
}
