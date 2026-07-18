package me.bibo.militarycraft.vehicles.helicopter.listeners;

import me.bibo.militarycraft.vehicles.helicopter.HelicopterRuntime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

/**
 * Rebuilds helicopter wrappers when their entities load, and drops them when
 * they unload. Uses the entity-load events (not chunk-load) because in 1.17+
 * a chunk can load before its entities do.
 */
public final class WorldListener implements Listener {

    private final HelicopterRuntime plugin;

    public WorldListener(HelicopterRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        plugin.helicopters().onEntitiesLoad(event.getEntities());
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        plugin.helicopters().onEntitiesUnload(event.getEntities());
    }
}
