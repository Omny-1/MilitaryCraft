package me.bibo.militarycraft.core;

import me.bibo.militarycraft.MilitaryCraftPlugin;
import me.bibo.militarycraft.camera.CameraService;
import me.bibo.militarycraft.core.combat.VehicleCombatService;
import me.bibo.militarycraft.core.command.RootCommand;
import me.bibo.militarycraft.core.config.ModuleConfig;
import me.bibo.militarycraft.core.event.EventBus;
import me.bibo.militarycraft.core.item.ItemFactory;
import me.bibo.militarycraft.core.vehicle.VehicleService;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Logger;

/**
 * The facade handed to every module on enable/reload (§4.1).
 *
 * <p>Deviation from the literal §4.1 accessor list: no {@code keys()}/{@code text()} getters.
 * {@link me.bibo.militarycraft.core.key.Keys} and {@link me.bibo.militarycraft.core.text.Text}
 * are pure static utilities (per their own spec, e.g. {@code Keys.of(...)}, {@code Text.of(...)}
 * are always called directly, never through {@code core}) — an accessor returning an object
 * for them would be dead indirection with no caller.
 */
public final class Core {

    private final MilitaryCraftPlugin plugin;
    private final EventBus events;
    private final RootCommand commands;
    private final ItemFactory items;
    private final VehicleService vehicles;
    private final VehicleCombatService combat;
    private final CameraService camera;
    private ModuleConfig config;

    public Core(MilitaryCraftPlugin plugin, EventBus events, RootCommand commands, ItemFactory items,
                VehicleService vehicles, VehicleCombatService combat, CameraService camera) {
        this.plugin = plugin;
        this.events = events;
        this.commands = commands;
        this.items = items;
        this.vehicles = vehicles;
        this.combat = combat;
        this.camera = camera;
        this.config = new ModuleConfig(plugin.getConfig());
    }

    /** Rebuilds the root config snapshot; call after {@code plugin.reloadConfig()}. */
    public void refreshConfig() {
        this.config = new ModuleConfig(plugin.getConfig());
    }

    public MilitaryCraftPlugin plugin() {
        return plugin;
    }

    public ModuleConfig config() {
        return config;
    }

    public EventBus events() {
        return events;
    }

    public RootCommand commands() {
        return commands;
    }

    public ItemFactory items() {
        return items;
    }

    public VehicleService vehicles() {
        return vehicles;
    }

    public VehicleCombatService combat() {
        return combat;
    }

    public CameraService camera() {
        return camera;
    }

    public Logger logger() {
        return plugin.getLogger();
    }

    public void registerListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    public BukkitTask runSync(Runnable task) {
        return plugin.getServer().getScheduler().runTask(plugin, task);
    }

    public BukkitTask runAsync(Runnable task) {
        return plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    public BukkitScheduler scheduler() {
        return plugin.getServer().getScheduler();
    }
}
