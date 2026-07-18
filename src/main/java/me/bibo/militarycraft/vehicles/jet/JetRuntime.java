package me.bibo.militarycraft.vehicles.jet;

import me.bibo.militarycraft.MilitaryCraftPlugin;
import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.vehicles.jet.config.JetConfig;
import me.bibo.militarycraft.vehicles.jet.jet.JetManager;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Logger;

public final class JetRuntime {

    private static final String MODULE_ID = "jet";

    private final Core core;
    private JetConfig config;
    private final JetManager jetManager;

    JetRuntime(Core core) {
        this.core = core;
        this.config = new JetConfig(core.plugin(), section(core));
        this.jetManager = new JetManager(this);
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

    public JetConfig config() {
        return config;
    }

    public JetManager jets() {
        return jetManager;
    }

    public void reloadAll() {
        this.config = new JetConfig(core.plugin(), section(core));
        jetManager.applyRenderSettings();
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
                && section.contains("mount-grace-ticks")
                && section.contains("weapons.rockets.jet-damage")
                && section.contains("weapons.bombs.jet-damage")
                && section.contains("model.teleport-duration")
                && section.isConfigurationSection("flight.stall");
    }

    private static File moduleDataFolder(Core core) {
        File parent = core.plugin().getDataFolder().getParentFile();
        return parent != null ? new File(parent, "JetCraft") : new File(core.plugin().getDataFolder(), "JetCraft");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
