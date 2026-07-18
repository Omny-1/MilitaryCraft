package me.bibo.militarycraft.vehicles.kamaz;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.module.MilitaryModule;
import me.bibo.militarycraft.core.vehicle.ManagedVehicleProvider;
import me.bibo.militarycraft.core.vehicle.VehicleProvider;
import me.bibo.militarycraft.vehicles.kamaz.commands.KamazCommand;
import me.bibo.militarycraft.vehicles.kamaz.listeners.DamageListener;
import me.bibo.militarycraft.vehicles.kamaz.listeners.InteractionListener;
import me.bibo.militarycraft.vehicles.kamaz.listeners.PlacementListener;
import me.bibo.militarycraft.vehicles.kamaz.listeners.WorldListener;
import me.bibo.militarycraft.vehicles.kamaz.truck.TruckManager;
import me.bibo.militarycraft.vehicles.kamaz.util.Keys;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.List;

public final class KamazModule implements MilitaryModule {

    private Core core;
    private KamazRuntime runtime;
    private TruckManager manager;
    private VehicleProvider provider;
    private KamazCommand commands;
    private List<Listener> listeners = List.of();

    @Override
    public String id() {
        return "kamaz";
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        Keys.init(core.plugin());
        runtime = new KamazRuntime(core);
        manager = runtime.trucks();

        listeners = List.of(
                new PlacementListener(runtime),
                new InteractionListener(runtime),
                new DamageListener(runtime),
                new WorldListener(runtime));
        for (Listener listener : listeners) {
            core.registerListener(listener);
        }

        commands = new KamazCommand(runtime);
        core.commands().register(id(), commands.all());
        PluginCommand direct = core.plugin().getCommand("kamaz");
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
        core.logger().info("KamazCraft enabled. Max truck HP = " + runtime.config().maxHealth
                + " (" + runtime.config().creepersToDestroy + " creeper blasts).");
    }

    @Override
    public void disable() {
        try {
            if (core != null) {
                core.commands().unregister(id());
            }
            if (commands != null && core != null) {
                PluginCommand direct = core.plugin().getCommand("kamaz");
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
