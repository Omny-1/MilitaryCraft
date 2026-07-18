package me.bibo.militarycraft.weapons.tckbus;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Central registry of every {@link NamespacedKey} and tag used to mark TCKBus
 * entities and items, so a TckBusRig (and its workers) can be recognised, rebuilt and
 * cleaned up after a chunk reload, restart or crash.
 */
public final class TckBusKeys {

    /** Stored on every entity that belongs to a TckBusRig: the TckBusRig's UUID. */
    public static NamespacedKey BUS_ID;
    /** Role of the entity within the TckBusRig ("core", "hitbox", "part", "text", "worker"). */
    public static NamespacedKey ROLE;
    /** Index of a model display into the parts list (for rehydration). */
    public static NamespacedKey PART_INDEX;
    /** Skin id stored on placer items and TckBusRig entities. */
    public static NamespacedKey SKIN;
    /** Marks the placer item. */
    public static NamespacedKey ITEM_TAG;
    /** Stored on the core: UUID of the player who placed the TckBusRig. */
    public static NamespacedKey OWNER;

    // Persisted state, written on the core so a TckBusRig can be rebuilt after a
    // chunk reload or restart without a separate save file.
    public static NamespacedKey STATE_YAW;
    public static NamespacedKey STATE_HEALTH;
    /** Intended worker count for this TckBusRig (so vanished workers can be re-spawned). */
    public static NamespacedKey STATE_WORKERS;
    /** How many workers a PLAYER has killed (these stay dead; the mechanic gate). */
    public static NamespacedKey STATE_DEFEATED;

    /** Scoreboard tag put on every TCKBus entity, used for a cheap world sweep. */
    public static final String SCOREBOARD_TAG = "tckbus_entity";

    private TckBusKeys() {
    }

    public static void init(Plugin plugin) {
        BUS_ID = key("bus_id");
        ROLE = key("role");
        PART_INDEX = key("part_index");
        SKIN = key("skin");
        ITEM_TAG = key("tck_item");
        OWNER = key("owner");
        STATE_YAW = key("state_yaw");
        STATE_HEALTH = key("state_health");
        STATE_WORKERS = key("state_workers");
        STATE_DEFEATED = key("state_defeated");
    }

    private static NamespacedKey key(String key) {
        return new NamespacedKey("tckbus", key);
    }
}


