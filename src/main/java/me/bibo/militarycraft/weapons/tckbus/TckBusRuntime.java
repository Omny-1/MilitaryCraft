package me.bibo.militarycraft.weapons.tckbus;

import me.bibo.militarycraft.MilitaryCraftPlugin;
import me.bibo.militarycraft.core.Core;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Logger;

final class TckBusRuntime {

    private final Core core;
    private TckBusSettings config;
    private final TckBusDropStore drops;
    private final TckBusManager buses;

    TckBusRuntime(Core core) {
        this.core = core;
        this.config = new TckBusSettings(core.plugin(), section(core));
        this.drops = new TckBusDropStore(this);
        this.buses = new TckBusManager(this);
    }

    MilitaryCraftPlugin plugin() {
        return core.plugin();
    }

    Server getServer() {
        return core.plugin().getServer();
    }

    Logger getLogger() {
        return core.logger();
    }

    File getDataFolder() {
        return moduleDataFolder(core);
    }

    TckBusSettings config() {
        return config;
    }

    TckBusDropStore drops() {
        return drops;
    }

    TckBusManager buses() {
        return buses;
    }

    TckBusSnatchManager snatch() {
        return buses.snatch();
    }

    void reloadAll() {
        this.config = new TckBusSettings(core.plugin(), section(core));
        this.drops.load();
    }

    private static ConfigurationSection section(Core core) {
        ConfigurationSection current = core.plugin().getConfig().getConfigurationSection(TckBusManager.MODULE_ID);
        if (current != null && current.isConfigurationSection("skins.variants")) {
            return current;
        }
        File legacy = new File(moduleDataFolder(core), "config.yml");
        if (legacy.isFile()) {
            return YamlConfiguration.loadConfiguration(legacy);
        }
        return current;
    }

    private static File moduleDataFolder(Core core) {
        File parent = core.plugin().getDataFolder().getParentFile();
        return parent != null ? new File(parent, "TCKBus") : new File(core.plugin().getDataFolder(), "TCKBus");
    }
}
