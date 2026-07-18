package me.bibo.militarycraft.core.event;

import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** Consumes explosion events routed through {@link EventBus} (vehicles, placeables). */
public interface ExplosionSink {

    void onEntityExplode(EntityExplodeEvent event);

    void onBlockExplode(BlockExplodeEvent event);
}
