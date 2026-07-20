package me.bibo.militarycraft.core.config;

import me.bibo.militarycraft.core.util.MathUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Typed, clamped view over a {@link ConfigurationSection}. {@code core.config()} returns
 * the root wrapper over the whole config.yml; call {@link #section(String)} to get a
 * module's own section, e.g. {@code core.config().section("tank")}.
 */
public final class ModuleConfig {

    private final ConfigurationSection section;

    public ModuleConfig(ConfigurationSection section) {
        this.section = section;
    }

    /** Child section wrapper; safe even if the path is missing (getters then just fall back). */
    public ModuleConfig section(String path) {
        return new ModuleConfig(section != null ? section.getConfigurationSection(path) : null);
    }

    public boolean has(String path) {
        return section != null && section.isSet(path);
    }

    public double getDouble(String path, double fallback) {
        double safeFallback = Double.isFinite(fallback) ? fallback : 0.0;
        double value = section != null ? section.getDouble(path, safeFallback) : safeFallback;
        return Double.isFinite(value) ? value : safeFallback;
    }

    public double getDouble(String path, double fallback, double min, double max) {
        return MathUtil.clamp(getDouble(path, fallback), min, max);
    }

    public int getInt(String path, int fallback) {
        return section != null ? section.getInt(path, fallback) : fallback;
    }

    public int getInt(String path, int fallback, int min, int max) {
        return (int) MathUtil.clamp(getInt(path, fallback), min, max);
    }

    public boolean getBoolean(String path, boolean fallback) {
        return section != null ? section.getBoolean(path, fallback) : fallback;
    }

    public String getString(String path, String fallback) {
        return section != null ? section.getString(path, fallback) : fallback;
    }

    public Material material(String path, Material fallback) {
        return ConfigSupport.material(section, path, fallback);
    }

    public Material block(String path, Material fallback) {
        return ConfigSupport.block(section, path, fallback);
    }
}
