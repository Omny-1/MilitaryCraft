package me.bibo.militarycraft.weapons.antiair.listeners;

import me.bibo.militarycraft.weapons.antiair.AntiAirRuntime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

/**
 * Rebuilds turret wrappers when their entities load, and drops them when they
 * unload. Uses the entity-load events (not chunk-load) because in 1.17+ a chunk
 * can load before its entities do.
 */
public final class WorldListener implements Listener {

    private final AntiAirRuntime plugin;

    public WorldListener(AntiAirRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        plugin.turrets().onEntitiesLoad(event.getEntities());
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        plugin.turrets().onEntitiesUnload(event.getEntities());
    }
}
