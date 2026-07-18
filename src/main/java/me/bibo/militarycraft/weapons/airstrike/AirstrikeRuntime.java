package me.bibo.militarycraft.weapons.airstrike;

import me.bibo.militarycraft.MilitaryCraftPlugin;
import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.weapons.airstrike.manager.AirstrikeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Logger;

public final class AirstrikeRuntime {

    public static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private static final String MODULE_ID = "airstrike";

    private final Core core;
    private ConfigurationSection config;
    private final AirstrikeManager airstrikeManager;

    AirstrikeRuntime(Core core) {
        this.core = core;
        this.config = section(core);
        this.airstrikeManager = new AirstrikeManager(this);
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

    public ConfigurationSection getConfig() {
        return config;
    }

    public AirstrikeManager getAirstrikeManager() {
        return airstrikeManager;
    }

    public void reloadConfig() {
        this.config = section(core);
    }

    public void cleanup() {
        airstrikeManager.cleanup();
    }

    public Component message(String key, Object... replacements) {
        String raw = getConfig().getString("messages." + key, "&cMessage not found: " + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace("{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
        }
        return LEGACY.deserialize(raw);
    }

    public static Component text(String raw) {
        return LEGACY.deserialize(raw);
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
                && section.contains("tnt-count")
                && section.contains("jet-altitude")
                && section.contains("max-target-distance")
                && section.contains("messages.no-permission");
    }

    private static File moduleDataFolder(Core core) {
        File parent = core.plugin().getDataFolder().getParentFile();
        if (parent == null) {
            return new File(core.plugin().getDataFolder(), "AirstrikePlugin");
        }
        return new File(parent, "AirstrikePlugin");
    }
}
