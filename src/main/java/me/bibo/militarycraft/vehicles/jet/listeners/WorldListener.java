package me.bibo.militarycraft.vehicles.jet.listeners;

import me.bibo.militarycraft.vehicles.jet.JetRuntime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

/**
 * Rebuilds jet wrappers when their entities load, and drops them when they
 * unload. Uses the entity-load events (not chunk-load) because in 1.17+ a
 * chunk can load before its entities do.
 */
public final class WorldListener implements Listener {

    private final JetRuntime plugin;

    public WorldListener(JetRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        plugin.jets().onEntitiesLoad(event.getEntities());
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        plugin.jets().onEntitiesUnload(event.getEntities());
    }
}
