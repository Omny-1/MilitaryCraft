package me.bibo.militarycraft.vehicles.kamaz;

import me.bibo.militarycraft.MilitaryCraftPlugin;
import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.vehicles.kamaz.config.KamazConfig;
import me.bibo.militarycraft.vehicles.kamaz.truck.TruckManager;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Logger;

public final class KamazRuntime {

    private static final String MODULE_ID = "kamaz";

    private final Core core;
    private KamazConfig config;
    private final TruckManager truckManager;

    KamazRuntime(Core core) {
        this.core = core;
        this.config = new KamazConfig(core.plugin(), section(core));
        this.truckManager = new TruckManager(this);
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

    public KamazConfig config() {
        return config;
    }

    public TruckManager trucks() {
        return truckManager;
    }

    public void reloadAll() {
        this.config = new KamazConfig(core.plugin(), section(core));
        truckManager.repaintAll();
    }

    double cameraScale() {
        ConfigurationSection current = core.plugin().getConfig().getConfigurationSection(MODULE_ID);
        return clamp(current != null ? current.getDouble("camera-scale", 2.5) : 2.5, 0.0625, 16.0);
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
                && section.isConfigurationSection("run-over")
                && section.isConfigurationSection("model")
                && section.contains("run-over.fling-speed-fraction")
                && section.contains("placement.max-trucks-per-player")
                && section.contains("model.passenger-seat-height")
                && section.contains("performance.run-over-scan-interval-ticks");
    }

    private static File moduleDataFolder(Core core) {
        File parent = core.plugin().getDataFolder().getParentFile();
        return parent != null ? new File(parent, "KamazCraft") : new File(core.plugin().getDataFolder(), "KamazCraft");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
