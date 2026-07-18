package me.bibo.militarycraft.gear.warkit;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.module.MilitaryModule;
import me.bibo.militarycraft.gear.warkit.listener.CombatListener;
import me.bibo.militarycraft.gear.warkit.listener.EffectListener;
import me.bibo.militarycraft.gear.warkit.listener.StateListener;
import me.bibo.militarycraft.gear.warkit.listener.UseListener;
import me.bibo.militarycraft.gear.warkit.weapon.DeployableListener;
import me.bibo.militarycraft.gear.warkit.weapon.SpecialListener;
import me.bibo.militarycraft.gear.warkit.weapon.WeaponListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.List;

public final class WarKitModule implements MilitaryModule {

    public static final String ID = "warkit";

    private Core core;
    private WarKitRuntime runtime;
    private WarKitCommand commands;
    private List<Listener> listeners = List.of();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        runtime = new WarKitRuntime(core);
        commands = new WarKitCommand(runtime);

        listeners = List.of(
                new UseListener(runtime),
                new CombatListener(runtime),
                new EffectListener(runtime),
                new StateListener(runtime),
                new WeaponListener(runtime),
                new DeployableListener(runtime),
                new SpecialListener(runtime),
                runtime.grenades(),
                runtime.spray());
        for (Listener listener : listeners) {
            core.registerListener(listener);
        }

        core.commands().register(id(), commands.all());
        PluginCommand direct = core.plugin().getCommand("warkit");
        if (direct != null) {
            direct.setExecutor(commands);
            direct.setTabCompleter(commands);
        }

        runtime.startTicker();
        core.logger().info("WarKit ready: " + runtime.items().ids().size() + " equipment items.");
    }

    @Override
    public void disable() {
        try {
            if (core != null) {
                core.commands().unregister(id());
            }
            if (commands != null && core != null) {
                PluginCommand direct = core.plugin().getCommand("warkit");
                if (direct != null) {
                    direct.setExecutor(null);
                    direct.setTabCompleter(null);
                }
            }
            for (Listener listener : listeners) {
                HandlerList.unregisterAll(listener);
            }
            if (runtime != null) {
                runtime.shutdown();
            }
        } finally {
            listeners = List.of();
            commands = null;
            runtime = null;
            core = null;
        }
    }

    @Override
    public void reload(Core core) {
        if (runtime != null) {
            runtime.reloadSettings();
        }
    }
}
