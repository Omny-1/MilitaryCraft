package me.bibo.militarycraft.core.vehicle;

import org.bukkit.entity.Entity;

import java.util.Collection;

/**
 * Adapter-facing registry contract for restored vehicle managers.
 *
 * <p>The original managers remain responsible for lifecycle, persistence and
 * gameplay. Core only receives a lookup/all/purge view of their live objects.</p>
 */
public interface VehicleProvider {

    String type();

    VehicleHandle vehicleOf(Entity anyPart);

    Collection<? extends VehicleHandle> all();

    VehicleService.PurgeResult purge();
}
