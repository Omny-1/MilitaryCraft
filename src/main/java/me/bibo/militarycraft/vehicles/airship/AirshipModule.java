package me.bibo.militarycraft.vehicles.airship;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.module.MilitaryModule;
import me.bibo.militarycraft.core.vehicle.ManagedVehicleProvider;
import me.bibo.militarycraft.core.vehicle.VehicleProvider;
import me.bibo.militarycraft.vehicles.airship.airship.AirshipManager;
import me.bibo.militarycraft.vehicles.airship.commands.AirshipCommand;
import me.bibo.militarycraft.vehicles.airship.listeners.DamageListener;
import me.bibo.militarycraft.vehicles.airship.listeners.InteractionListener;
import me.bibo.militarycraft.vehicles.airship.listeners.PlacementListener;
import me.bibo.militarycraft.vehicles.airship.listeners.WorldListener;
import me.bibo.militarycraft.vehicles.airship.util.Keys;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.List;

public final class AirshipModule implements MilitaryModule {

    private Core core;
    private AirshipRuntime runtime;
    private AirshipManager manager;
    private VehicleProvider provider;
    private AirshipCommand commands;
    private List<Listener> listeners = List.of();

    @Override
    public String id() {
        return "airship";
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        Keys.init(core.plugin());
        runtime = new AirshipRuntime(core);
        manager = runtime.airships();

        listeners = List.of(
                new PlacementListener(runtime),
                new InteractionListener(runtime),
                new DamageListener(runtime),
                new WorldListener(runtime));
        for (Listener listener : listeners) {
            core.registerListener(listener);
        }

        commands = new AirshipCommand(runtime);
        core.commands().register(id(), commands.all());
        PluginCommand direct = core.plugin().getCommand("airship");
        if (direct != null) {
            direct.setExecutor(commands);
            direct.setTabCompleter(commands);
        }

        manager.adoptExisting();
        manager.start();
        provider = ManagedVehicleProvider.withStraySweep(
                id(), manager::byEntity, manager::all, manager::cleanupAll);
        core.vehicles().registerProvider(provider);
        core.camera().registerScale(id(), runtime.cameraScale());
        core.logger().info("AirshipCraft enabled. Max airship HP = " + runtime.config().maxHealth
                + " (" + runtime.config().creepersToDestroy + " creeper blast(s)).");
    }

    @Override
    public void disable() {
        try {
            if (core != null) {
                core.commands().unregister(id());
            }
            if (commands != null && core != null) {
                PluginCommand direct = core.plugin().getCommand("airship");
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
