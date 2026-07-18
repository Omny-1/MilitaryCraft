package me.bibo.militarycraft.weapons.artillery;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArtilleryStateTest {

    @Test
    void oneAmmoUnitAlwaysRepresentsOneThreeShellSalvo() {
        Artillery artillery = new Artillery(UUID.randomUUID(), UUID.randomUUID(), "world",
                0, 64, 0, 0.0f, 3, 0L, 6);

        assertEquals(3, ArtilleryBallistics.SALVO_SIZE);
        artillery.consumeSalvo(1234L);
        assertEquals(2, artillery.ammo());
        assertEquals(1234L, artillery.lastShotMillis());
    }

    @Test
    void yawRejectsNonFiniteStateAndNormalizesAngles() {
        Artillery artillery = new Artillery(UUID.randomUUID(), UUID.randomUUID(), "world",
                0, 64, 0, 0.0f, 3, 0L, 6);

        artillery.setYaw(Float.NaN);
        assertEquals(0.0f, artillery.yaw());
        artillery.setYaw(540.0f);
        assertEquals(180.0f, artillery.yaw());
    }
}
