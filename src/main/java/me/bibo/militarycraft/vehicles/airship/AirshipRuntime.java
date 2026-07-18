package me.bibo.militarycraft.vehicles.airship;

import me.bibo.militarycraft.MilitaryCraftPlugin;
import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.vehicles.airship.airship.Airship;
import me.bibo.militarycraft.vehicles.airship.airship.AirshipManager;
import me.bibo.militarycraft.vehicles.airship.config.AirshipConfig;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Logger;

public final class AirshipRuntime {

    private static final String MODULE_ID = "airship";

    private final Core core;
    private AirshipConfig config;
    private final AirshipManager airshipManager;

    AirshipRuntime(Core core) {
        this.core = core;
        this.config = new AirshipConfig(core.plugin(), section(core));
        this.airshipManager = new AirshipManager(this);
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

    public AirshipConfig config() {
        return config;
    }

    public AirshipManager airships() {
        return airshipManager;
    }

    public void reloadAll() {
        this.config = new AirshipConfig(core.plugin(), section(core));
        for (Airship ship : airshipManager.all()) {
            ship.refreshModel();
        }
    }

    double cameraScale() {
        ConfigurationSection current = core.plugin().getConfig().getConfigurationSection(MODULE_ID);
        return clamp(current != null ? current.getDouble("camera-scale", 5.0) : 5.0, 0.0625, 16.0);
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
                && section.contains("flight.max-speed")
                && section.contains("weapons.bombs.airship-damage")
                && section.contains("performance.rounded-envelope")
                && section.contains("model.envelope-block")
                && section.isConfigurationSection("burner");
    }

    private static File moduleDataFolder(Core core) {
        File parent = core.plugin().getDataFolder().getParentFile();
        return parent != null ? new File(parent, "AirshipCraft") : new File(core.plugin().getDataFolder(), "AirshipCraft");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
