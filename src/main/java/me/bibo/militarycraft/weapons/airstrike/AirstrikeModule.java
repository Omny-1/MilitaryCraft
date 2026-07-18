package me.bibo.militarycraft.weapons.airstrike;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.module.MilitaryModule;
import me.bibo.militarycraft.weapons.airstrike.command.AirstrikeCommand;
import me.bibo.militarycraft.weapons.airstrike.listener.AirstrikeListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class AirstrikeModule implements MilitaryModule {

    private Core core;
    private AirstrikeRuntime runtime;
    private AirstrikeCommand commands;
    private AirstrikeListener listener;

    @Override
    public String id() {
        return "airstrike";
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        runtime = new AirstrikeRuntime(core);

        commands = new AirstrikeCommand(runtime);
        core.commands().register(id(), commands.all());
        PluginCommand direct = core.plugin().getCommand("airstrike");
        if (direct != null) {
            direct.setExecutor(commands);
            direct.setTabCompleter(commands);
        }

        listener = new AirstrikeListener(runtime);
        core.registerListener(listener);

        core.logger().info("AirstrikePlugin enabled - Su-57 airstrike ready.");
    }

    @Override
    public void disable() {
        try {
            if (core != null) {
                core.commands().unregister(id());
            }
            if (commands != null && core != null) {
                PluginCommand direct = core.plugin().getCommand("airstrike");
                if (direct != null) {
                    direct.setExecutor(null);
                    direct.setTabCompleter(null);
                }
            }
            if (listener != null) {
                HandlerList.unregisterAll(listener);
            }
            if (runtime != null) {
                runtime.cleanup();
            }
        } finally {
            listener = null;
            commands = null;
            runtime = null;
            core = null;
        }
    }

    @Override
    public void reload(Core core) {
        if (runtime != null) {
            runtime.reloadConfig();
        }
    }
}
