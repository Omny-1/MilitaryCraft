package me.bibo.militarycraft.core.module;

import me.bibo.militarycraft.core.Core;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

/**
 * Holds the hardcoded module list (no reflection/classpath scan — §2), enables/disables/
 * reloads them, and gates each by its {@code modules.<id>.enabled} config toggle (default true).
 */
public final class ModuleManager {

    private final Plugin plugin;
    private final List<MilitaryModule> modules;
    private final Set<String> active = new HashSet<>();

    public ModuleManager(Plugin plugin, List<MilitaryModule> modules) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.modules = List.copyOf(modules);
        Set<String> ids = new HashSet<>();
        for (MilitaryModule module : this.modules) {
            String id = Objects.requireNonNull(module, "module").id();
            if (id == null || id.isBlank() || !ids.add(id)) {
                throw new IllegalArgumentException("Module ids must be non-blank and unique: " + id);
            }
        }
    }

    public void enableAll(Core core) {
        for (MilitaryModule module : modules) {
            if (!isEnabled(module.id())) {
                continue;
            }
            enable(module, core);
        }
    }

    public void disableAll() {
        for (int i = modules.size() - 1; i >= 0; i--) {
            MilitaryModule module = modules.get(i);
            if (active.contains(module.id())) {
                if (disable(module)) {
                    active.remove(module.id());
                }
            }
        }
    }

    public void reloadAll(Core core) {
        for (MilitaryModule module : modules) {
            boolean configured = isEnabled(module.id());
            boolean running = active.contains(module.id());
            if (configured && !running) {
                enable(module, core);
            } else if (!configured && running) {
                if (disable(module)) {
                    active.remove(module.id());
                }
            } else if (running) {
                try {
                    module.reload(core);
                } catch (RuntimeException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Could not reload module: " + module.id(), ex);
                }
            }
        }
    }

    private void enable(MilitaryModule module, Core core) {
        try {
            module.enable(core);
            active.add(module.id());
            plugin.getLogger().info("Module enabled: " + module.id());
        } catch (RuntimeException ex) {
            try {
                module.disable();
            } catch (RuntimeException cleanupFailure) {
                ex.addSuppressed(cleanupFailure);
            }
            plugin.getLogger().log(Level.SEVERE, "Could not enable module: " + module.id(), ex);
        }
    }

    private boolean disable(MilitaryModule module) {
        try {
            module.disable();
            plugin.getLogger().info("Module disabled: " + module.id());
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not disable module: " + module.id(), ex);
            return false;
        }
    }

    public boolean isEnabled(String id) {
        return plugin.getConfig().getBoolean("modules." + id + ".enabled", true);
    }

    public boolean isActive(String id) {
        return active.contains(id);
    }

    public List<MilitaryModule> modules() {
        return modules;
    }
}
