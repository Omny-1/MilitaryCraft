package me.bibo.militarycraft.camera;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.module.MilitaryModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.List;
import java.util.Locale;

public final class CameraModule implements MilitaryModule, Listener, TabExecutor {

    private final CameraServiceImpl service;
    private Core core;
    private BukkitTask task;

    public CameraModule(CameraServiceImpl service) {
        this.service = service;
    }

    @Override
    public String id() {
        return "camera";
    }

    @Override
    public void enable(Core core) {
        this.core = core;
        core.registerListener(this);
        service.loadConfigValues(section(core), core.logger());
        registerDirectCommand(core);
        startTask(core);
    }

    @Override
    public void disable() {
        try {
            if (task != null) {
                task.cancel();
                task = null;
            }
            if (core != null) {
                PluginCommand direct = core.plugin().getCommand("vehiclecamera");
                if (direct != null) {
                    direct.setExecutor(null);
                    direct.setTabCompleter(null);
                }
            }
            service.clearAll();
            HandlerList.unregisterAll(this);
        } finally {
            core = null;
        }
    }

    @Override
    public void reload(Core core) {
        if (task != null) {
            task.cancel();
            task = null;
        }
        service.loadConfigValues(section(core), core.logger());
        startTask(core);
    }

    private void registerDirectCommand(Core core) {
        PluginCommand direct = core.plugin().getCommand("vehiclecamera");
        if (direct != null) {
            direct.setExecutor(this);
            direct.setTabCompleter(this);
        }
    }

    private void startTask(Core core) {
        ConfigurationSection config = section(core);
        long interval = Math.max(1L, config.getLong("reconcile-interval",
                config.getLong("reconcile-interval-ticks", 5L)));
        task = core.scheduler().runTaskTimer(core.plugin(), service::reconcileAll, interval, interval);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.clearPlayer(event.getPlayer());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("vehiclecamera.admin")) {
                sender.sendMessage("§8[§bCamera§8] §cYou do not have permission.");
                return true;
            }
            core.plugin().reloadAll();
            sender.sendMessage("§8[§bCamera§8] §aConfig reloaded.");
            return true;
        }
        sender.sendMessage("§8[§bCamera§8] §e/vehiclecamera reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("vehiclecamera.admin")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return "reload".startsWith(prefix) ? List.of("reload") : List.of();
        }
        return List.of();
    }

    private static ConfigurationSection section(Core core) {
        ConfigurationSection current = core.plugin().getConfig().getConfigurationSection("camera");
        if (current != null && current.isConfigurationSection("vehicles")) {
            return current;
        }
        File legacy = new File(moduleDataFolder(core), "config.yml");
        if (legacy.isFile()) {
            return YamlConfiguration.loadConfiguration(legacy);
        }
        return current != null ? current : new YamlConfiguration();
    }

    private static File moduleDataFolder(Core core) {
        File parent = core.plugin().getDataFolder().getParentFile();
        if (parent == null) {
            return new File(core.plugin().getDataFolder(), "VehicleCamera");
        }
        return new File(parent, "VehicleCamera");
    }
}
