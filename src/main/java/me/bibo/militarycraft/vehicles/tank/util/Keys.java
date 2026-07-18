package me.bibo.militarycraft.vehicles.tank.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Central place for all {@link NamespacedKey}s and tags used to mark
 * TankCraft entities and items so they can be recognised, rebuilt and cleaned up.
 */
public final class Keys {

    /** Stored on every entity that belongs to a tank: the tank's UUID as a string. */
    public static NamespacedKey TANK_ID;
    /** Stored on every tank entity: which role it plays ("seat", "hitbox", "part"). */
    public static NamespacedKey TANK_PART;
    /** Stored on each model display: its index into the model parts list (for rehydration). */
    public static NamespacedKey PART_INDEX;
    /** Marks the placer item. */
    public static NamespacedKey ITEM_TAG;

    // Persisted articulation/health, written on the seat so a tank can be rebuilt
    // after a chunk reload or server restart without a separate save file.
    public static NamespacedKey STATE_HULL_YAW;
    public static NamespacedKey STATE_TURRET_YAW;
    public static NamespacedKey STATE_BARREL_PITCH;
    public static NamespacedKey STATE_HEALTH;

    /** Scoreboard tag put on every tank entity, used for a cheap world sweep. */
    public static final String SCOREBOARD_TAG = "tankcraft_entity";

    private Keys() {
    }

    public static void init(Plugin plugin) {
        TANK_ID = key("tank_id");
        TANK_PART = key("tank_part");
        PART_INDEX = key("part_index");
        ITEM_TAG = key("tank_item");
        STATE_HULL_YAW = key("state_hull_yaw");
        STATE_TURRET_YAW = key("state_turret_yaw");
        STATE_BARREL_PITCH = key("state_barrel_pitch");
        STATE_HEALTH = key("state_health");
    }

    @SuppressWarnings("deprecation")
    private static NamespacedKey key(String key) {
        return new NamespacedKey("tankcraft", key);
    }
}
