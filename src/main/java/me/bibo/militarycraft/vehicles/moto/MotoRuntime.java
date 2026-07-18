package me.bibo.militarycraft.vehicles.moto;

import me.bibo.militarycraft.MilitaryCraftPlugin;
import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.vehicles.moto.config.MotoConfig;
import me.bibo.militarycraft.vehicles.moto.motorcycle.MotorcycleManager;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Logger;

public final class MotoRuntime {

    private static final String MODULE_ID = "moto";

    private final Core core;
    private MotoConfig config;
    private final MotorcycleManager motorcycleManager;

    MotoRuntime(Core core) {
        this.core = core;
        this.config = new MotoConfig(core.plugin(), section(core));
        this.motorcycleManager = new MotorcycleManager(this);
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

    public MotoConfig config() {
        return config;
    }

    public MotorcycleManager motorcycles() {
        return motorcycleManager;
    }

    public void reloadAll() {
        this.config = new MotoConfig(core.plugin(), section(core));
        motorcycleManager.onConfigReload();
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
                && section.contains("movement.max-forward-speed")
                && section.contains("impact.enabled")
                && section.contains("durability.creepers-to-destroy")
                && section.contains("placement.max-motorcycles-total")
                && section.contains("model.body-block");
    }

    private static File moduleDataFolder(Core core) {
        File parent = core.plugin().getDataFolder().getParentFile();
        return parent != null ? new File(parent, "MotoCraft") : new File(core.plugin().getDataFolder(), "MotoCraft");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
