package me.bibo.militarycraft.vehicles.tank;

import me.bibo.militarycraft.MilitaryCraftPlugin;
import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.vehicles.tank.config.TankConfig;
import me.bibo.militarycraft.vehicles.tank.tank.TankManager;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Logger;

public final class TankRuntime {

    private static final String MODULE_ID = "tank";

    private final Core core;
    private TankConfig config;
    private final TankManager tankManager;

    TankRuntime(Core core) {
        this.core = core;
        this.config = new TankConfig(core.plugin(), section(core));
        this.tankManager = new TankManager(this);
    }

    public MilitaryCraftPlugin bukkitPlugin() {
        return core.plugin();
    }

    public Server getServer() {
        return core.plugin().getServer();
    }

    public Logger getLogger() {
        return core.logger();
    }

    public File getDataFolder() {
        return moduleDataFolder(core);
    }

    public TankConfig config() {
        return config;
    }

    public TankManager tanks() {
        return tankManager;
    }

    public void reloadAll() {
        this.config = new TankConfig(core.plugin(), section(core));
        tankManager.repaintAll();
    }

    double cameraScale() {
        ConfigurationSection current = core.plugin().getConfig().getConfigurationSection(MODULE_ID);
        return clamp(current != null ? current.getDouble("camera-scale", 3.0) : 3.0, 0.0625, 16.0);
    }

    private static ConfigurationSection section(Core core) {
        ConfigurationSection current = core.plugin().getConfig().getConfigurationSection(MODULE_ID);
        if (isOriginalShape(current)) {
            return current;
        }
        File legacy = new File(moduleDataFolder(core), "config.yml");
        if (legacy.isFile()) {
            return YamlConfiguration.loadConfiguration(legacy);
        }
        return current != null ? current : new YamlConfiguration();
    }

    private static boolean isOriginalShape(ConfigurationSection section) {
        return section != null
                && section.isConfigurationSection("movement")
                && section.isConfigurationSection("weapon")
                && section.isConfigurationSection("model")
                && section.contains("movement.ground-snap-distance")
                && section.contains("weapon.shell-tank-damage")
                && section.contains("combat.projectile-sweep-interval-ticks")
                && section.contains("model.seat-height");
    }

    private static File moduleDataFolder(Core core) {
        File parent = core.plugin().getDataFolder().getParentFile();
        return parent != null ? new File(parent, "TankCraft") : new File(core.plugin().getDataFolder(), "TankCraft");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
