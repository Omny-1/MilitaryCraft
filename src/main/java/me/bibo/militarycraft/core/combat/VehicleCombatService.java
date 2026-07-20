package me.bibo.militarycraft.core.combat;

import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

/**
 * The AntiAir-to-vehicle contract as a direct call, replacing the older
 * tagged-throwaway-ArmorStand approach. Behaviour must match the old semantics exactly:
 * a flat 1-creeper hit within roughly 8 blocks, no knockback, no block break.
 */
public interface VehicleCombatService {

    /** @return true if {@code vehiclePart} belongs to a vehicle (and it was hit). */
    boolean antiAirHit(Entity vehiclePart);

    /** @return true if {@code vehiclePart} belongs to a vehicle and damage was applied. */
    boolean directDamage(Entity vehiclePart, double amount);

    boolean directDamage(VehicleHandle vehicle, double amount);

    double repair(VehicleHandle vehicle, double amount);

    VehicleHit rayTrace(Location origin, Vector direction, double range, double pad, UUID excludedVehicle);

    VehicleHit vehicleNear(Location center, double radius, UUID excludedVehicle);

    List<VehicleHit> vehiclesNear(Location center, double radius, UUID excludedVehicle);

    int radiusDamage(Location center, double radius, double maxDamage, UUID excludedVehicle, UUID excludedDirectHit);

    /** Routes a blast to every nearby vehicle (and, later, placeable). */
    void explosionDamage(Location loc, double power);

    void explosionDamage(Location loc, double power, UUID excludedVehicle);
}
