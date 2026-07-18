package me.bibo.militarycraft.weapons.antiair;

import me.bibo.militarycraft.MilitaryCraftPlugin;
import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.weapons.antiair.config.AntiAirConfig;
import me.bibo.militarycraft.weapons.antiair.turret.TurretManager;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Logger;

public final class AntiAirRuntime {

    private static final String MODULE_ID = "antiair";

    private final Core core;
    private AntiAirConfig config;
    private final TurretManager turretManager;

    AntiAirRuntime(Core core) {
        this.core = core;
        this.config = new AntiAirConfig(core.plugin(), section(core));
        this.turretManager = new TurretManager(this);
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

    public AntiAirConfig config() {
        return config;
    }

    public TurretManager turrets() {
        return turretManager;
    }

    public void reloadAll() {
        this.config = new AntiAirConfig(core.plugin(), section(core));
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
                && section.contains("targeting.scan-interval-ticks")
                && section.contains("weapons.fire-interval-ticks")
                && section.contains("svo.vehicle-tags")
                && section.contains("fuel.consume-per-shot")
                && section.contains("model.base-block");
    }

    private static File moduleDataFolder(Core core) {
        File parent = core.plugin().getDataFolder().getParentFile();
        if (parent == null) {
            return new File(core.plugin().getDataFolder(), "AntiAirCraft");
        }
        return new File(parent, "AntiAirCraft");
    }
}
