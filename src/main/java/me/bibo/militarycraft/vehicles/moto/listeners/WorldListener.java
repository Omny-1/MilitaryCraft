package me.bibo.militarycraft.vehicles.moto.listeners;

import me.bibo.militarycraft.vehicles.moto.MotoRuntime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

/**
 * Rebuilds motorcycle wrappers when their entities load, and drops them when they
 * unload. Uses the entity-load events (not chunk-load) because in 1.17+ a chunk
 * can load before its entities do.
 */
public final class WorldListener implements Listener {

    private final MotoRuntime plugin;

    public WorldListener(MotoRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        plugin.motorcycles().onEntitiesLoad(event.getEntities());
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        plugin.motorcycles().onEntitiesUnload(event.getEntities());
    }
}
