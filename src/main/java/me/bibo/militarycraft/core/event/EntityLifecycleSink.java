package me.bibo.militarycraft.core.event;

import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

/** Consumes chunk entity load/unload events routed through {@link EventBus} (adopt/forget). */
public interface EntityLifecycleSink {

    void onEntitiesLoad(EntitiesLoadEvent event);

    void onEntitiesUnload(EntitiesUnloadEvent event);
}
