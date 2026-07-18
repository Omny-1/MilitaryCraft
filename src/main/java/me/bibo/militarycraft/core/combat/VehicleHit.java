package me.bibo.militarycraft.core.combat;

import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import org.bukkit.Location;

public record VehicleHit(VehicleHandle vehicle, Location point, double distance) {
}
