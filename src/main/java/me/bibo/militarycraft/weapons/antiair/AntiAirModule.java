package me.bibo.militarycraft.weapons.antiair;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.module.MilitaryModule;
import me.bibo.militarycraft.weapons.antiair.commands.AntiAirCommand;
import me.bibo.militarycraft.weapons.antiair.listeners.DamageListener;
import me.bibo.militarycraft.weapons.antiair.listeners.GuiListener;
import me.bibo.militarycraft.weapons.antiair.listeners.InteractionListener;
import me.bibo.militarycraft.weapons.antiair.listeners.PlacementListener;
import me.bibo.militarycraft.weapons.antiair.listeners.WorldListener;
import me.bibo.militarycraft.weapons.antiair.turret.TurretManager;
import me.bibo.militarycraft.weapons.antiair.util.Keys;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.List;

public final class AntiAirModule implements MilitaryModule {

    private Core core;
    private AntiAirRuntime runtime;
    private TurretManager manager;
    private AntiAirCommand commands;
    private List<Listener> listeners = List.of();

    @Override
    public String id() {
        return "antiair";
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        Keys.init(core.plugin());
        runtime = new AntiAirRuntime(core);
        manager = runtime.turrets();

        listeners = List.of(
                new PlacementListener(runtime),
                new InteractionListener(runtime),
                new DamageListener(runtime),
                new WorldListener(runtime),
                new GuiListener(runtime));
        for (Listener listener : listeners) {
            core.registerListener(listener);
        }

        commands = new AntiAirCommand(runtime);
        core.commands().register(id(), commands.all());
        PluginCommand direct = core.plugin().getCommand("pvo");
        if (direct != null) {
            direct.setExecutor(commands);
            direct.setTabCompleter(commands);
        }

        manager.adoptExisting();
        manager.start();
        core.logger().info("AntiAirCraft enabled. Max turret HP = " + runtime.config().maxHealth
                + " (" + runtime.config().creepersToDestroy + " creeper blast(s)).");
    }

    @Override
    public void disable() {
        try {
            if (core != null) {
                core.commands().unregister(id());
            }
            if (commands != null && core != null) {
                PluginCommand direct = core.plugin().getCommand("pvo");
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
