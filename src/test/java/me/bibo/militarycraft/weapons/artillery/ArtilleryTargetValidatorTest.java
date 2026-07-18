package me.bibo.militarycraft.weapons.artillery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ArtilleryTargetValidatorTest {

    @Test
    void parserAcceptsOnlyFiniteAbsoluteRealNumbers() {
        assertEquals(12.5, ArtilleryTargetValidator.parseFinite("12.5"));
        assertEquals(-1000.0, ArtilleryTargetValidator.parseFinite("-1e3"));
        assertNull(ArtilleryTargetValidator.parseFinite("NaN"));
        assertNull(ArtilleryTargetValidator.parseFinite("Infinity"));
        assertNull(ArtilleryTargetValidator.parseFinite("~10"));
        assertNull(ArtilleryTargetValidator.parseFinite("B7"));
    }

    @Test
    void validatesRangeAndFullDispersionArea() {
        ArtilleryTargetValidator.Validation valid = ArtilleryTargetValidator.validate(
                0.0, 0.0, 100.0, 100.0, 500.0,
                0.0, 0.0, 1000.0, 20.0);
        assertEquals(ArtilleryTargetValidator.Error.NONE, valid.error());

        ArtilleryTargetValidator.Validation range = ArtilleryTargetValidator.validate(
                0.0, 0.0, 501.0, 0.0, 500.0,
                0.0, 0.0, 2000.0, 0.0);
        assertEquals(ArtilleryTargetValidator.Error.OUT_OF_RANGE, range.error());

        ArtilleryTargetValidator.Validation border = ArtilleryTargetValidator.validate(
                0.0, 0.0, 490.0, 0.0, 1000.0,
                0.0, 0.0, 1000.0, 20.0);
        assertEquals(ArtilleryTargetValidator.Error.OUTSIDE_WORLD_BORDER, border.error());

        ArtilleryTargetValidator.Validation worldLimit = ArtilleryTargetValidator.validate(
                29_999_900.0, 0.0, 29_999_980.0, 0.0, 500.0,
                0.0, 0.0, 60_000_000.0, 10.0);
        assertEquals(ArtilleryTargetValidator.Error.OUTSIDE_WORLD_LIMIT, worldLimit.error());

        ArtilleryTargetValidator.Validation notFinite = ArtilleryTargetValidator.validate(
                0.0, 0.0, Double.NaN, 0.0, 500.0,
                0.0, 0.0, 1000.0, 0.0);
        assertEquals(ArtilleryTargetValidator.Error.NOT_FINITE, notFinite.error());
    }
}
