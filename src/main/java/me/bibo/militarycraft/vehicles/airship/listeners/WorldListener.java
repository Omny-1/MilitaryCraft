package me.bibo.militarycraft.vehicles.airship.listeners;

import me.bibo.militarycraft.vehicles.airship.AirshipRuntime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

/**
 * Rebuilds airship wrappers when their entities load, and drops them when they
 * unload. Uses the entity-load events (not chunk-load) because in 1.17+ a chunk
 * can load before its entities do.
 */
public final class WorldListener implements Listener {

    private final AirshipRuntime plugin;

    public WorldListener(AirshipRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        plugin.airships().onEntitiesLoad(event.getEntities());
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        plugin.airships().onEntitiesUnload(event.getEntities());
    }
}
