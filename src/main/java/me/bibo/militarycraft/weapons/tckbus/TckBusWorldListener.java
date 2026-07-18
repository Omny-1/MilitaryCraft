package me.bibo.militarycraft.weapons.tckbus;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

/**
 * Rebuilds TckBusRig wrappers when their entities load and drops them when they unload.
 * Uses the entity-load events (not chunk-load) because in 1.17+ a chunk can load
 * before its entities do.
 */
public final class TckBusWorldListener implements Listener {

    private final TckBusRuntime plugin;

    public TckBusWorldListener(TckBusRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        plugin.buses().onEntitiesLoad(event.getEntities());
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        plugin.buses().onEntitiesUnload(event.getEntities());
    }
}


