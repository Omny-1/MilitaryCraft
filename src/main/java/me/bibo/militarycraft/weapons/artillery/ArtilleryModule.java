package me.bibo.militarycraft.weapons.artillery;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.module.MilitaryModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;

/** Stationary coordinate-targeted artillery module. */
public final class ArtilleryModule implements MilitaryModule {

    private Core core;
    private ArtilleryManager manager;
    private ArtilleryCommands commands;
    private ArtilleryListener listener;

    @Override
    public String id() {
        return "artillery";
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        ArtillerySettings settings = new ArtillerySettings(core.config().section("artillery"));
        manager = new ArtilleryManager(core, settings);
        manager.start();

        listener = new ArtilleryListener(core, manager);
        core.events().register(listener);
        core.registerListener(listener);
        commands = new ArtilleryCommands(manager);
        core.commands().register("artillery", commands.all());
        core.commands().access().registerContextAction("artillery", "enter",
                (player, args) -> manager.selectedOrNearest(player, 8.0) != null);
        core.commands().access().registerContextAction("artillery", "fire",
                (player, args) -> manager.sessions().active(player));
        core.commands().access().registerContextAction("artillery", "exit",
                (player, args) -> manager.sessions().active(player) || manager.sessions().hasPending(player));
        bindDirect("shoot", commands);
        bindDirect("artillery", commands);
    }

    @Override
    public void disable() {
        try {
            if (core != null) {
                core.commands().unregister("artillery");
                core.commands().access().unregisterContextAction("artillery", "enter");
                core.commands().access().unregisterContextAction("artillery", "fire");
                core.commands().access().unregisterContextAction("artillery", "exit");
                unbindDirect("shoot");
                unbindDirect("artillery");
            }
            if (listener != null && core != null) {
                core.events().unregister(listener);
                HandlerList.unregisterAll(listener);
            }
            if (manager != null) {
                manager.shutdown();
            }
        } finally {
            manager = null;
            commands = null;
            listener = null;
            core = null;
        }
    }

    @Override
    public void reload(Core core) {
        if (manager == null) {
            return;
        }
        manager.setSettings(new ArtillerySettings(core.config().section("artillery")));
    }

    private void bindDirect(String name, ArtilleryCommands commands) {
        PluginCommand direct = core.plugin().getCommand(name);
        if (direct != null) {
            direct.setExecutor(commands);
            direct.setTabCompleter(commands);
        }
    }

    private void unbindDirect(String name) {
        PluginCommand direct = core.plugin().getCommand(name);
        if (direct != null) {
            direct.setExecutor(null);
            direct.setTabCompleter(null);
        }
    }
}
