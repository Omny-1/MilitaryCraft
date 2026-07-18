/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.world.EntitiesLoadEvent
 *  org.bukkit.event.world.EntitiesUnloadEvent
 */
package me.bibo.militarycraft.vehicles.pickup.listeners;

import me.bibo.militarycraft.vehicles.pickup.PickupRuntime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

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

