package me.bibo.militarycraft.vehicles.tank;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.module.MilitaryModule;
import me.bibo.militarycraft.core.vehicle.ManagedVehicleProvider;
import me.bibo.militarycraft.core.vehicle.VehicleProvider;
import me.bibo.militarycraft.vehicles.tank.commands.TankCommand;
import me.bibo.militarycraft.vehicles.tank.listeners.DamageListener;
import me.bibo.militarycraft.vehicles.tank.listeners.InteractionListener;
import me.bibo.militarycraft.vehicles.tank.listeners.PlacementListener;
import me.bibo.militarycraft.vehicles.tank.listeners.WorldListener;
import me.bibo.militarycraft.vehicles.tank.tank.TankManager;
import me.bibo.militarycraft.vehicles.tank.util.Keys;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.List;

public final class TankModule implements MilitaryModule {

    private Core core;
    private TankRuntime runtime;
    private TankManager manager;
    private VehicleProvider provider;
    private TankCommand commands;
    private List<Listener> listeners = List.of();

    @Override
    public String id() {
        return "tank";
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        Keys.init(core.plugin());
        runtime = new TankRuntime(core);
        manager = runtime.tanks();

        listeners = List.of(
                new PlacementListener(runtime),
                new InteractionListener(runtime),
                new DamageListener(runtime),
                new WorldListener(runtime));
        for (Listener listener : listeners) {
            core.registerListener(listener);
        }

        commands = new TankCommand(runtime);
        core.commands().register(id(), commands.all());
        PluginCommand direct = core.plugin().getCommand("tank");
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
        core.logger().info("TankCraft enabled. Max tank HP = " + runtime.config().maxHealth
                + " (" + runtime.config().creepersToDestroy + " creeper blasts).");
    }

    @Override
    public void disable() {
        try {
            if (core != null) {
                core.commands().unregister(id());
            }
            if (commands != null && core != null) {
                PluginCommand direct = core.plugin().getCommand("tank");
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
