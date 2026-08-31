package me.bibo.militarycraft.vehicles.pickup;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.module.MilitaryModule;
import me.bibo.militarycraft.core.vehicle.ManagedVehicleProvider;
import me.bibo.militarycraft.core.vehicle.VehicleProvider;
import me.bibo.militarycraft.vehicles.pickup.commands.PickupCommand;
import me.bibo.militarycraft.vehicles.pickup.listeners.DamageListener;
import me.bibo.militarycraft.vehicles.pickup.listeners.InteractionListener;
import me.bibo.militarycraft.vehicles.pickup.listeners.PlacementListener;
import me.bibo.militarycraft.vehicles.pickup.listeners.WorldListener;
import me.bibo.militarycraft.vehicles.pickup.util.Keys;
import me.bibo.militarycraft.vehicles.pickup.vehicle.PickupManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.List;

/**
 * Wires the pickup into the plugin: config, listeners, command and the manager's tick loop.
 */
public final class PickupModule implements MilitaryModule {

    private Core core;
    private PickupRuntime runtime;
    private PickupManager manager;
    private VehicleProvider provider;
    private PickupCommand commands;
    private List<Listener> listeners = List.of();

    @Override
    public String id() {
        return "pickup";
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        Keys.init(core.plugin());
        runtime = new PickupRuntime(core);
        manager = runtime.pickups();

        listeners = List.of(
                new PlacementListener(runtime),
                new InteractionListener(runtime),
                new DamageListener(runtime),
                new WorldListener(runtime));
        for (Listener listener : listeners) {
            core.registerListener(listener);
        }

        commands = new PickupCommand(runtime);
        core.commands().register(id(), commands.all());
        PluginCommand direct = core.plugin().getCommand("pickup");
        if (direct != null) {
            direct.setExecutor(commands);
            direct.setTabCompleter(commands);
        }

        manager.adoptExisting();
        manager.start();
        provider = ManagedVehicleProvider.withStraySweep(
                id(), manager::byEntity, manager::all, manager::purgeAll);
        core.vehicles().registerProvider(provider);
        core.camera().registerScale(id(), runtime.cameraScale());
        core.logger().info("PickupCraft enabled. Max pickup HP = " + runtime.config().maxHealth
                + " (" + runtime.config().creepersToDestroy + " creeper blasts).");
    }

    @Override
    public void disable() {
        try {
            if (core != null) {
                core.commands().unregister(id());
            }
            if (commands != null && core != null) {
                PluginCommand direct = core.plugin().getCommand("pickup");
                if (direct != null) {
                    direct.setExecutor(null);
                    direct.setTabCompleter(null);
                }
            }
            for (Listener listener : listeners) {
                HandlerList.unregisterAll(listener);
            }
            if (provider != null && core != null) {
                core.vehicles().unregisterProvider(provider);
            }
            if (manager != null) {
                manager.shutdown();
            }
        } finally {
            manager = null;
            provider = null;
            runtime = null;
            commands = null;
            listeners = List.of();
            core = null;
        }
    }

    @Override
    public void reload(Core core) {
        if (runtime != null) {
            runtime.reloadAll();
            core.camera().registerScale(id(), runtime.cameraScale());
        }
    }
}
