package me.bibo.militarycraft.vehicles.drone.listeners;

import me.bibo.militarycraft.vehicles.drone.DroneRuntime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

/**
 * Rebuilds drone wrappers when their entities load, and drops them when they
 * unload. Uses the entity-load events (not chunk-load) because in 1.17+ a
 * chunk can load before its entities do.
 */
public final class WorldListener implements Listener {

    private final DroneRuntime plugin;

    public WorldListener(DroneRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        plugin.drones().onEntitiesLoad(event.getEntities());
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        plugin.drones().onEntitiesUnload(event.getEntities());
    }
}
