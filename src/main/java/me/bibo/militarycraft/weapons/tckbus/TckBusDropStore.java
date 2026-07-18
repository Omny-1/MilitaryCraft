package me.bibo.militarycraft.weapons.tckbus;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Persists the admin-configured custom TckBusRig loot in its own {@code drop.yml}, so
 * setting it never rewrites (and strips the comments from) the main config.yml.
 * Holds a LIST of items (the admin's hotbar snapshot); Bukkit serialises each
 * {@link ItemStack} into YAML natively, so enchanted / renamed / NBT-tagged items
 * round-trip correctly.
 */
public final class TckBusDropStore {

    private static final String KEY = "custom-drops";
    private static final String LEGACY_KEY = "custom-drop"; // old single-item format

    private final TckBusRuntime plugin;
    private final File file;
    private List<ItemStack> drops = new ArrayList<>();

    public TckBusDropStore(TckBusRuntime plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "drop.yml");
        load();
    }

    public void load() {
        drops = new ArrayList<>();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<?> raw = yaml.getList(KEY);
        if (raw != null) {
            for (Object o : raw) {
                if (o instanceof ItemStack is && !is.getType().isAir()) {
                    drops.add(is);
                }
            }
        }
        // Migrate the old single-item format so a previously set drop isn't lost.
        if (drops.isEmpty()) {
            ItemStack legacy = yaml.getItemStack(LEGACY_KEY, null);
            if (legacy != null && !legacy.getType().isAir()) {
                drops.add(legacy);
            }
        }
    }

    /** @return fresh clones of every configured loot item (may be empty). */
    public List<ItemStack> get() {
        List<ItemStack> out = new ArrayList<>(drops.size());
        for (ItemStack is : drops) {
            out.add(is.clone());
        }
        return out;
    }

    public boolean has() {
        return !drops.isEmpty();
    }

    public int size() {
        return drops.size();
    }

    /** Replace the loot with a snapshot of the given items (air entries skipped). */
    public void set(List<ItemStack> items) {
        drops = new ArrayList<>();
        if (items != null) {
            for (ItemStack is : items) {
                if (is != null && !is.getType().isAir()) {
                    drops.add(is.clone());
                }
            }
        }
        save();
    }

    public void clear() {
        set(null);
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        if (!drops.isEmpty()) {
            yaml.set(KEY, drops);
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save drop.yml", ex);
        }
    }
}


