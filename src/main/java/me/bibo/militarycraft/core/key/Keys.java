package me.bibo.militarycraft.core.key;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * The ONLY way to make a {@link NamespacedKey} in MilitaryCraft. Namespacing every key
 * by module id ({@code moduleId + "_" + name}) prevents collisions now that all 15
 * source plugins share one namespace ({@code militarycraft}).
 */
public final class Keys {

    private static Plugin plugin;

    private Keys() {
    }

    public static synchronized void init(Plugin plugin) {
        Plugin value = Objects.requireNonNull(plugin, "plugin");
        if (Keys.plugin != null && Keys.plugin != value) {
            throw new IllegalStateException("MilitaryCraft keys were already initialized by another plugin instance");
        }
        Keys.plugin = value;
    }

    public static NamespacedKey of(String moduleId, String name) {
        if (plugin == null) {
            throw new IllegalStateException("MilitaryCraft keys have not been initialized");
        }
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(name, "name");
        return new NamespacedKey(plugin, moduleId + "_" + name);
    }
}
