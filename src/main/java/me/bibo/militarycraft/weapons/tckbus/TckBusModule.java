package me.bibo.militarycraft.weapons.tckbus;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.module.MilitaryModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.List;

public final class TckBusModule implements MilitaryModule {

    private Core core;
    private TckBusRuntime runtime;
    private TckBusManager manager;
    private TckBusCommands commands;
    private List<Listener> listeners = List.of();

    @Override
    public String id() {
        return TckBusManager.MODULE_ID;
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        TckBusKeys.init(core.plugin());
        runtime = new TckBusRuntime(core);
        manager = runtime.buses();

        listeners = List.of(
                new TckBusPlacementListener(runtime),
                new TckBusDamageListener(runtime),
                new TckBusWorldListener(runtime),
                new TckBusPlayerListener(runtime));
        for (Listener listener : listeners) {
            core.registerListener(listener);
        }

        commands = new TckBusCommands(runtime);
        core.commands().register(id(), commands.all());
        PluginCommand direct = core.plugin().getCommand("tck");
        if (direct != null) {
            direct.setExecutor(commands);
            direct.setTabCompleter(commands);
        }

        manager.adoptExisting();
        manager.start();
        core.logger().info("TCKBus enabled. Bus HP = " + runtime.config().maxHealth
                + " (" + runtime.config().creepersToDestroy + " creeper blast(s)); workers/bus = "
                + runtime.config().workerCount + ".");
    }

    @Override
    public void disable() {
        try {
            if (core != null) {
                core.commands().unregister(id());
            }
            if (commands != null && core != null) {
                PluginCommand direct = core.plugin().getCommand("tck");
                if (direct != null) {
                    direct.setExecutor(null);
                    direct.setTabCompleter(null);
                }
            }
            for (Listener listener : listeners) {
                HandlerList.unregisterAll(listener);
            }
            if (manager != null) {
                manager.shutdown();
            }
        } finally {
            manager = null;
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
        }
    }
}

