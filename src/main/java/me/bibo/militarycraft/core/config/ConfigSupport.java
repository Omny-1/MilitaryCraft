package me.bibo.militarycraft.core.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Static config-parsing helpers shared by every module's settings snapshot.
 *
 * <p>Invalid input is not logged here, which keeps these plain three-argument helpers.
 * A caller that wants a warning can compare the result against its own fallback and log
 * that itself.
 */
public final class ConfigSupport {

    private ConfigSupport() {
    }

    /** Any material by name, falling back silently if missing/invalid. */
    public static Material material(ConfigurationSection section, String path, Material fallback) {
        if (section == null) {
            return fallback;
        }
        String name = section.getString(path);
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material m = Material.matchMaterial(name.trim());
        return m != null ? m : fallback;
    }

    /** Like {@link #material}, but rejects non-block materials (falls back too). */
    public static Material block(ConfigurationSection section, String path, Material fallback) {
        Material m = material(section, path, fallback);
        return m.isBlock() ? m : fallback;
    }
}
