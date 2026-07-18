package me.bibo.militarycraft.vehicles.airship.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Central place for all {@link NamespacedKey}s and tags used to mark AirshipCraft
 * entities and items so they can be recognised, rebuilt and cleaned up after a
 * chunk reload, restart or crash.
 */
public final class Keys {

    /** Stored on every entity that belongs to an airship: the airship's UUID. */
    public static NamespacedKey SHIP_ID;
    /** Role of the entity within the airship ("core", "hitbox", "part"). */
    public static NamespacedKey SHIP_PART;
    /** Index of a model display into the parts list (for rehydration). */
    public static NamespacedKey PART_INDEX;
    /** Marks the placer item. */
    public static NamespacedKey ITEM_TAG;
    /** Stored on the core: UUID of the player who placed the airship (for limits). */
    public static NamespacedKey OWNER;

    // Persisted attitude/health, written on the core so an airship can be rebuilt
    // after a chunk reload or restart without a separate save file.
    public static NamespacedKey STATE_YAW;
    public static NamespacedKey STATE_ROLL;
    public static NamespacedKey STATE_SPEED;
    public static NamespacedKey STATE_VSPEED;
    public static NamespacedKey STATE_HEALTH;

    /** Scoreboard tag put on every airship entity, used for a cheap world sweep. */
    public static final String SCOREBOARD_TAG = "airshipcraft_entity";

    private Keys() {
    }

    public static void init(Plugin plugin) {
        SHIP_ID = key("ship_id");
        SHIP_PART = key("ship_part");
        PART_INDEX = key("part_index");
        ITEM_TAG = key("airship_item");
        OWNER = key("owner");
        STATE_YAW = key("state_yaw");
        STATE_ROLL = key("state_roll");
        STATE_SPEED = key("state_speed");
        STATE_VSPEED = key("state_vspeed");
        STATE_HEALTH = key("state_health");
    }

    private static NamespacedKey key(String key) {
        return new NamespacedKey("airshipcraft", key);
    }
}
