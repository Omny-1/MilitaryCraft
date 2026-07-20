package me.bibo.militarycraft.vehicles.train.listeners;

import me.bibo.militarycraft.vehicles.train.TrainRuntime;
import me.bibo.militarycraft.vehicles.train.util.Keys;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * Housekeeping: train entities are transient (never saved), but if any
 * tagged stray ever loads with a chunk - e.g. after a crash mid-save - it is
 * swept away so no ghost carriages litter the world.
 */
public final class WorldListener implements Listener {

    private final TrainRuntime plugin;

    public WorldListener(TrainRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if (!e.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
                continue;
            }
            if (plugin.trains().byEntity(e) == null) {
                e.remove();
            }
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        plugin.trains().removeInWorld(event.getWorld());
    }
}
