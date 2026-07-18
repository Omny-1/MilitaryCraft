package me.bibo.militarycraft.core.event;

import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/** Consumes interact events routed through {@link EventBus} (placers, mounting). */
public interface InteractSink {

    void onPlayerInteract(PlayerInteractEvent event);

    void onPlayerInteractEntity(PlayerInteractEntityEvent event);
}
