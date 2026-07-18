package me.bibo.militarycraft.weapons.nuke;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.module.MilitaryModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

public final class NukeModule implements MilitaryModule {

    private Core core;
    private NukeManager manager;
    private NukeListener listener;
    private NukeCommands commands;

    @Override
    public String id() {
        return NukeManager.MODULE_ID;
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        manager = new NukeManager(core, new NukeSettings(core.config().section(id())));
        listener = new NukeListener(manager);
        commands = new NukeCommands(manager);

        core.registerListener(listener);
        core.commands().register(id(), commands.all());

        PluginCommand direct = core.plugin().getCommand("nuke");
        if (direct != null) {
            direct.setExecutor(commands);
            direct.setTabCompleter(commands);
        } else {
            core.logger().severe("Command 'nuke' is missing from plugin.yml; direct NukeStrike command disabled.");
        }

        core.logger().info("NukeStrike parity module enabled - nuclear airstrike armed.");
    }

    @Override
    public void disable() {
        try {
            if (core != null) {
                core.commands().unregister(id());
                PluginCommand direct = core.plugin().getCommand("nuke");
                if (direct != null) {
                    direct.setExecutor(null);
                    direct.setTabCompleter(null);
                }
            }
            if (listener != null) {
                HandlerList.unregisterAll(listener);
            }
            if (manager != null) {
                manager.shutdown();
            }
        } finally {
            manager = null;
            listener = null;
            commands = null;
            core = null;
        }
    }

    @Override
    public void reload(Core core) {
        if (manager != null) {
            manager.setSettings(new NukeSettings(core.config().section(id())));
        }
    }
}
