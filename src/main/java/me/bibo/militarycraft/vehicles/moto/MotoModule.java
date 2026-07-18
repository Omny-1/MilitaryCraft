package me.bibo.militarycraft.vehicles.moto;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.module.MilitaryModule;
import me.bibo.militarycraft.core.vehicle.ManagedVehicleProvider;
import me.bibo.militarycraft.core.vehicle.VehicleProvider;
import me.bibo.militarycraft.vehicles.moto.commands.MotoCommand;
import me.bibo.militarycraft.vehicles.moto.listeners.DamageListener;
import me.bibo.militarycraft.vehicles.moto.listeners.InteractionListener;
import me.bibo.militarycraft.vehicles.moto.listeners.PlacementListener;
import me.bibo.militarycraft.vehicles.moto.listeners.WorldListener;
import me.bibo.militarycraft.vehicles.moto.motorcycle.MotorcycleManager;
import me.bibo.militarycraft.vehicles.moto.util.Keys;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.List;

public final class MotoModule implements MilitaryModule {

    private Core core;
    private MotoRuntime runtime;
    private MotorcycleManager manager;
    private VehicleProvider provider;
    private MotoCommand commands;
    private List<Listener> listeners = List.of();

    @Override
    public String id() {
        return "moto";
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        Keys.init(core.plugin());
        runtime = new MotoRuntime(core);
        manager = runtime.motorcycles();

        listeners = List.of(
                new PlacementListener(runtime),
                new InteractionListener(runtime),
                new DamageListener(runtime),
                new WorldListener(runtime));
        for (Listener listener : listeners) {
            core.registerListener(listener);
        }

        commands = new MotoCommand(runtime);
        core.commands().register(id(), commands.all());
        PluginCommand direct = core.plugin().getCommand("moto");
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
        core.logger().info("MotoCraft enabled: " + runtime.config().maxHealth + " HP, top speed "
                + runtime.config().maxForwardSpeed + " blocks/tick, " + manager.count()
                + " loaded motorcycle(s) adopted.");
    }

    @Override
    public void disable() {
        try {
            if (core != null) {
                core.commands().unregister(id());
            }
            if (commands != null && core != null) {
                PluginCommand direct = core.plugin().getCommand("moto");
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
