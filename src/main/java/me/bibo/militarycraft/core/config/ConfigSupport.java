package me.bibo.militarycraft.core.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Static config-parsing helpers shared by every module's settings snapshot (generalised
 * from TankConfig's private material parser). No logging on invalid input here — deviates
 * from TankConfig's own warning log to keep this a plain 3-arg helper per spec; callers
 * that want a warning can check the result against their own fallback and log it themselves.
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
