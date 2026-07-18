package me.bibo.militarycraft.vehicles.kamaz.listeners;

import me.bibo.militarycraft.vehicles.kamaz.KamazRuntime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

/**
 * Rebuilds truck wrappers when their entities load, and drops them when they
 * unload. Uses the entity-load events (not chunk-load) because in 1.17+ a chunk
 * can load before its entities do.
 */
public final class WorldListener implements Listener {

    private final KamazRuntime plugin;

    public WorldListener(KamazRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        plugin.trucks().onEntitiesLoad(event.getEntities());
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        plugin.trucks().onEntitiesUnload(event.getEntities());
    }
}
