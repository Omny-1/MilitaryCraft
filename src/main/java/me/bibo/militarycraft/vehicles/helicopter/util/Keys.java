package me.bibo.militarycraft.vehicles.helicopter.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Central place for all {@link NamespacedKey}s and tags used to mark HeliCraft
 * entities and items so they can be recognised, rebuilt and cleaned up after a
 * chunk reload, restart or crash.
 */
public final class Keys {

    /** Stored on every entity that belongs to a helicopter: its UUID. */
    public static NamespacedKey SHIP_ID;
    /** Role of the entity within the helicopter ("core", "hitbox", "part"). */
    public static NamespacedKey SHIP_PART;
    /** Index of a model display into the parts list (for rehydration). */
    public static NamespacedKey PART_INDEX;
    /** Marks the placer item. */
    public static NamespacedKey ITEM_TAG;
    /** Stored on the core: UUID of the player who placed the helicopter (for limits). */
    public static NamespacedKey OWNER;

    // Persisted attitude/health, written on the core so a helicopter can be
    // rebuilt after a chunk reload or restart without a separate save file.
    public static NamespacedKey STATE_YAW;
    public static NamespacedKey STATE_ROLL;
    public static NamespacedKey STATE_SPEED;
    public static NamespacedKey STATE_VSPEED;
    public static NamespacedKey STATE_HEALTH;

    /** Scoreboard tag put on every helicopter entity, used for a cheap world sweep. */
    public static final String SCOREBOARD_TAG = "helicraft_entity";

    private Keys() {
    }

    public static void init(Plugin plugin) {
        SHIP_ID = key("heli_id");
        SHIP_PART = key("heli_part");
        PART_INDEX = key("part_index");
        ITEM_TAG = key("heli_item");
        OWNER = key("owner");
        STATE_YAW = key("state_yaw");
        STATE_ROLL = key("state_roll");
        STATE_SPEED = key("state_speed");
        STATE_VSPEED = key("state_vspeed");
        STATE_HEALTH = key("state_health");
    }

    @SuppressWarnings("deprecation")
    private static NamespacedKey key(String key) {
        return new NamespacedKey("helicraft", key);
    }
}
