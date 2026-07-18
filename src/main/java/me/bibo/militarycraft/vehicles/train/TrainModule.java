package me.bibo.militarycraft.vehicles.train;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.module.MilitaryModule;
import me.bibo.militarycraft.core.vehicle.ManagedVehicleProvider;
import me.bibo.militarycraft.core.vehicle.VehicleProvider;
import me.bibo.militarycraft.vehicles.train.commands.TrainCommand;
import me.bibo.militarycraft.vehicles.train.listeners.InteractionListener;
import me.bibo.militarycraft.vehicles.train.listeners.PlacementListener;
import me.bibo.militarycraft.vehicles.train.listeners.ProtectionListener;
import me.bibo.militarycraft.vehicles.train.listeners.WorldListener;
import me.bibo.militarycraft.vehicles.train.train.TrainManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.List;

public final class TrainModule implements MilitaryModule {

    private Core core;
    private TrainRuntime runtime;
    private TrainManager manager;
    private VehicleProvider provider;
    private TrainCommand commands;
    private List<Listener> listeners = List.of();

    @Override
    public String id() {
        return "train";
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        runtime = new TrainRuntime(core);
        manager = runtime.trains();
        manager.start();
        provider = ManagedVehicleProvider.trackedOnly(
                id(), manager::byEntity, manager::all, manager::removeAll);
        core.vehicles().registerProvider(provider);

        listeners = List.of(
                new PlacementListener(runtime),
                new InteractionListener(runtime),
                new ProtectionListener(runtime),
                new WorldListener(runtime));
        for (Listener listener : listeners) {
            core.registerListener(listener);
        }

        commands = new TrainCommand(runtime);
        core.commands().register(id(), commands.all());
        PluginCommand direct = core.plugin().getCommand("train");
        if (direct != null) {
            direct.setExecutor(commands);
            direct.setTabCompleter(commands);
        }

        core.camera().registerScale(id(), runtime.cameraScale());
        core.logger().info("TrainCraft is on steam: /train give and go.");
    }

    @Override
    public void disable() {
        try {
            if (core != null) {
                core.commands().unregister(id());
            }
            if (commands != null && core != null) {
                PluginCommand direct = core.plugin().getCommand("train");
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
            runtime.reloadCfg();
            core.camera().registerScale(id(), runtime.cameraScale());
        }
    }
}
