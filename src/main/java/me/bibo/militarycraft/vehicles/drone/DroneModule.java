package me.bibo.militarycraft.vehicles.drone;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.module.MilitaryModule;
import me.bibo.militarycraft.core.vehicle.ManagedVehicleProvider;
import me.bibo.militarycraft.core.vehicle.VehicleProvider;
import me.bibo.militarycraft.vehicles.drone.commands.DroneCommand;
import me.bibo.militarycraft.vehicles.drone.drone.DroneManager;
import me.bibo.militarycraft.vehicles.drone.listeners.DamageListener;
import me.bibo.militarycraft.vehicles.drone.listeners.InteractionListener;
import me.bibo.militarycraft.vehicles.drone.listeners.PlacementListener;
import me.bibo.militarycraft.vehicles.drone.listeners.WorldListener;
import me.bibo.militarycraft.vehicles.drone.util.Keys;
import me.bibo.militarycraft.vehicles.drone.util.PlayerScale;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.List;

public final class DroneModule implements MilitaryModule {

    private Core core;
    private DroneRuntime runtime;
    private DroneManager manager;
    private VehicleProvider provider;
    private DroneCommand commands;
    private List<Listener> listeners = List.of();

    @Override
    public String id() {
        return "drone";
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        Keys.init(core.plugin());
        PlayerScale.init(core.plugin());
        runtime = new DroneRuntime(core);
        manager = runtime.drones();

        listeners = List.of(
                new PlacementListener(runtime),
                new InteractionListener(runtime),
                new DamageListener(runtime),
                new WorldListener(runtime));
        for (Listener listener : listeners) {
            core.registerListener(listener);
        }

        commands = new DroneCommand(runtime);
        core.commands().register(id(), commands.all());
        core.commands().access().registerContextAction("drone", "fire",
                (player, args) -> manager.byDriver(player.getUniqueId()) != null);
        core.commands().access().registerContextAction("drone", "exit",
                (player, args) -> manager.byDriver(player.getUniqueId()) != null);
        PluginCommand direct = core.plugin().getCommand("drone");
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
        core.logger().info("DroneCraft enabled. Drone HP = " + runtime.config().maxHealth
                + " (" + runtime.config().creepersToDestroy + " creeper blasts).");
    }

    @Override
    public void disable() {
        try {
            if (core != null) {
                core.commands().unregister(id());
                core.commands().access().unregisterContextAction("drone", "fire");
                core.commands().access().unregisterContextAction("drone", "exit");
            }
            if (commands != null && core != null) {
                PluginCommand direct = core.plugin().getCommand("drone");
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
