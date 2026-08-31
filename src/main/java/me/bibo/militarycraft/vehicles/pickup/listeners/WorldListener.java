package me.bibo.militarycraft.vehicles.pickup.listeners;

import me.bibo.militarycraft.vehicles.pickup.PickupRuntime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

/**
 * Follows pickups in and out of loaded chunks: rebuild one from its entities when its chunk comes
 * back, and forget it when the chunk goes away. The vehicle itself lives in the world, not in a
 * save file, so this is the whole of its persistence.
 */
public final class WorldListener
implements Listener {
    private final PickupRuntime plugin;

    public WorldListener(PickupRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        this.plugin.pickups().onEntitiesLoad(event.getEntities());
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        this.plugin.pickups().onEntitiesUnload(event.getEntities());
    }
}

