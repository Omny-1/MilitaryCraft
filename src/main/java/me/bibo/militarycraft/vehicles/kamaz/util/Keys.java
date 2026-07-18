package me.bibo.militarycraft.vehicles.kamaz.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Central place for all {@link NamespacedKey}s and tags used to mark
 * KamazCraft entities and items so they can be recognised, rebuilt and cleaned up.
 *
 * <p>Every key/tag is namespaced under {@code kamazcraft} (distinct from TankCraft
 * and the other vehicle plugins) so the Kamaz coexists with them without clashes.
 */
public final class Keys {

    /** Stored on every entity that belongs to a truck: the truck's UUID as a string. */
    public static NamespacedKey TRUCK_ID;
    /** Stored on every truck entity: which role it plays ("seat", "hitbox", "part"). */
    public static NamespacedKey TRUCK_PART;
    /** Stored on the persistent seat: the player UUID that created the truck, if known. */
    public static NamespacedKey TRUCK_OWNER;
    /** Stored on each model display: its index into the model parts list (for rehydration). */
    public static NamespacedKey PART_INDEX;
    /** Marks the placer item. */
    public static NamespacedKey ITEM_TAG;

    // Persisted articulation/health, written on the seat so a truck can be rebuilt
    // after a chunk reload or server restart without a separate save file.
    public static NamespacedKey STATE_HULL_YAW;
    public static NamespacedKey STATE_HEALTH;

    /** Scoreboard tag put on every truck entity, used for a cheap world sweep. */
    public static final String SCOREBOARD_TAG = "kamazcraft_entity";

    private Keys() {
    }

    public static void init(Plugin plugin) {
        TRUCK_ID = key("truck_id");
        TRUCK_PART = key("truck_part");
        TRUCK_OWNER = key("truck_owner");
        PART_INDEX = key("part_index");
        ITEM_TAG = key("kamaz_item");
        STATE_HULL_YAW = key("state_hull_yaw");
        STATE_HEALTH = key("state_health");
    }

    @SuppressWarnings("deprecation")
    private static NamespacedKey key(String key) {
        return new NamespacedKey("kamazcraft", key);
    }
}
