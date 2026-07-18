package me.bibo.militarycraft.vehicles.helicopter;

import me.bibo.militarycraft.MilitaryCraftPlugin;
import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.vehicles.helicopter.config.HelicopterConfig;
import me.bibo.militarycraft.vehicles.helicopter.helicopter.HelicopterManager;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Logger;

public final class HelicopterRuntime {

    private static final String MODULE_ID = "helicopter";

    private final Core core;
    private HelicopterConfig config;
    private final HelicopterManager helicopterManager;

    HelicopterRuntime(Core core) {
        this.core = core;
        this.config = new HelicopterConfig(core.plugin(), section(core));
        this.helicopterManager = new HelicopterManager(this);
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

    public HelicopterConfig config() {
        return config;
    }

    public HelicopterManager helicopters() {
        return helicopterManager;
    }

    public void reloadAll() {
        this.config = new HelicopterConfig(core.plugin(), section(core));
    }

    double cameraScale() {
        ConfigurationSection current = core.plugin().getConfig().getConfigurationSection(MODULE_ID);
        return clamp(current != null ? current.getDouble("camera-scale", 4.5) : 4.5, 0.0625, 16.0);
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
                && section.isConfigurationSection("flight")
                && section.isConfigurationSection("weapons.rockets")
                && section.isConfigurationSection("weapons.bombs")
                && section.contains("weapons.rockets.heli-damage")
                && section.contains("weapons.bombs.heli-damage")
                && section.contains("model.teleport-duration")
                && section.contains("seats.capacity");
    }

    private static File moduleDataFolder(Core core) {
        File parent = core.plugin().getDataFolder().getParentFile();
        return parent != null ? new File(parent, "HeliCraft") : new File(core.plugin().getDataFolder(), "HeliCraft");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
