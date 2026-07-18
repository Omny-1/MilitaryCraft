package me.bibo.militarycraft.vehicles.drone;

import me.bibo.militarycraft.MilitaryCraftPlugin;
import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.vehicles.drone.drone.Drone;
import me.bibo.militarycraft.vehicles.drone.drone.DroneManager;
import me.bibo.militarycraft.vehicles.drone.config.DroneConfig;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Logger;

public final class DroneRuntime {

    private static final String MODULE_ID = "drone";

    private final Core core;
    private DroneConfig config;
    private final DroneManager droneManager;

    DroneRuntime(Core core) {
        this.core = core;
        this.config = new DroneConfig(core.plugin(), section(core));
        this.droneManager = new DroneManager(this);
    }

    public MilitaryCraftPlugin bukkitPlugin() {
        return core.plugin();
    }

    public Server getServer() {
        return core.plugin().getServer();
    }

    public Core core() {
        return core;
    }

    public Logger getLogger() {
        return core.logger();
    }

    public File getDataFolder() {
        return moduleDataFolder(core);
    }

    public DroneConfig config() {
        return config;
    }

    public DroneManager drones() {
        return droneManager;
    }

    public void reloadAll() {
        this.config = new DroneConfig(core.plugin(), section(core));
        for (Drone drone : droneManager.all()) {
            drone.refreshModel();
        }
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
                && section.contains("flight.speed")
                && section.contains("control.operator-scale")
                && section.contains("kamikaze.explosion-power")
                && section.contains("rockets.count")
                && section.contains("model.frame-block");
    }

    private static File moduleDataFolder(Core core) {
        File parent = core.plugin().getDataFolder().getParentFile();
        return parent != null ? new File(parent, "DroneCraft") : new File(core.plugin().getDataFolder(), "DroneCraft");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
