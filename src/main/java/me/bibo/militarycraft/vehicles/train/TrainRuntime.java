package me.bibo.militarycraft.vehicles.train;

import me.bibo.militarycraft.MilitaryCraftPlugin;
import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.vehicles.train.config.TrainConfig;
import me.bibo.militarycraft.vehicles.train.train.TrainManager;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Logger;

public final class TrainRuntime {

    private static final String MODULE_ID = "train";

    private final Core core;
    private final TrainConfig config = new TrainConfig();
    private final TrainManager trains;

    TrainRuntime(Core core) {
        this.core = core;
        this.config.load(section(core));
        this.trains = new TrainManager(this);
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

    public boolean isEnabled() {
        return core.plugin().isEnabled();
    }

    public File getDataFolder() {
        return moduleDataFolder(core);
    }

    public TrainConfig cfg() {
        return config;
    }

    public TrainManager trains() {
        return trains;
    }

    public void reloadCfg() {
        config.load(section(core));
    }

    double cameraScale() {
        ConfigurationSection current = core.plugin().getConfig().getConfigurationSection(MODULE_ID);
        return clamp(current != null ? current.getDouble("camera-scale", 1.0) : 1.0, 0.0625, 16.0);
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
                && section.contains("movement.speed")
                && section.contains("movement.car-gap")
                && section.contains("collision.damage")
                && section.contains("seats.locomotive")
                && section.contains("misc.keep-chunks-loaded");
    }

    private static File moduleDataFolder(Core core) {
        File parent = core.plugin().getDataFolder().getParentFile();
        if (parent == null) {
            return new File(core.plugin().getDataFolder(), "TrainCraft");
        }
        return new File(parent, "TrainCraft");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
