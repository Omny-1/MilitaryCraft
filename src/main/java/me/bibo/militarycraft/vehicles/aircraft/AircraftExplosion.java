package me.bibo.militarycraft.vehicles.aircraft;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.combat.Explosions;
import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import org.bukkit.Location;

import java.util.UUID;

public final class AircraftExplosion {

    private AircraftExplosion() {
    }

    public static void detonate(Core core, Location at, float power, boolean setFire, boolean breakBlocks,
                                UUID excludedVehicle, UUID excludedDirectHit) {
        if (core == null || !AircraftPlacement.isFinite(at) || power <= 0.0f || !Float.isFinite(power)) {
            return;
        }
        float safePower = AircraftSafety.clamp(power, 0.0f, AircraftSafety.MAX_EXPLOSION_POWER);
        Explosions.createExplosion(at.getWorld(), at, safePower, setFire, breakBlocks);
        Explosions.impactFx(at);
        for (VehicleHandle handle : core.vehicles().all()) {
            UUID id = handle.id();
            if ((excludedVehicle != null && excludedVehicle.equals(id))
                    || (excludedDirectHit != null && excludedDirectHit.equals(id))) {
                continue;
            }
            handle.applyExplosion(at, safePower);
        }
    }
}
