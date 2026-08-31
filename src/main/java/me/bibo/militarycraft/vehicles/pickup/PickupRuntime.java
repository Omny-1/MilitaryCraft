package me.bibo.militarycraft.vehicles.pickup;

import me.bibo.militarycraft.MilitaryCraftPlugin;
import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.vehicles.pickup.config.PickupConfig;
import me.bibo.militarycraft.vehicles.pickup.vehicle.PickupManager;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Logger;

/**
 * What the rest of the pickup code holds instead of the plugin itself - config, the manager and the
 * few server handles it needs. Keeping the module behind this makes it obvious how little of the
 * plugin a pickup is allowed to reach into.
 */
public final class PickupRuntime {

    private static final String MODULE_ID = "pickup";

    private final Core core;
    private PickupConfig config;
    private final PickupManager pickupManager;

    PickupRuntime(Core core) {
        this.core = core;
        this.config = new PickupConfig(core.plugin(), section(core));
        this.pickupManager = new PickupManager(this);
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

    public PickupConfig config() {
        return config;
    }

    public PickupManager pickups() {
        return pickupManager;
    }

    public void reloadAll() {
        this.config = new PickupConfig(core.plugin(), section(core));
        pickupManager.repaintAll();
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
                && section.contains("gunner.max-elevation")
                && section.contains("weapon.fire-cooldown-ticks")
                && section.contains("combat.projectile-sweep-interval-ticks")
                && section.contains("model.hull-block");
    }

    private static File moduleDataFolder(Core core) {
        File parent = core.plugin().getDataFolder().getParentFile();
        if (parent == null) {
            return new File(core.plugin().getDataFolder(), "JeepCraft");
        }
        File jeep = new File(parent, "JeepCraft");
        return jeep.exists() ? jeep : new File(parent, "PickupCraft");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
