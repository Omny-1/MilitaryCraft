package me.bibo.militarycraft.weapons.nuke;

import me.bibo.militarycraft.core.config.ModuleConfig;

final class NukeSettings {

    private final ModuleConfig config;

    NukeSettings(ModuleConfig config) {
        this.config = config;
    }

    double getDouble(String path, double fallback) {
        return config.getDouble(path, fallback);
    }

    int getInt(String path, int fallback) {
        return config.getInt(path, fallback);
    }

    boolean getBoolean(String path, boolean fallback) {
        return config.getBoolean(path, fallback);
    }

    String getString(String path, String fallback) {
        return config.getString(path, fallback);
    }

    String message(String key) {
        return config.getString("messages." + key, "&cMessage not found: " + key);
    }
}
