package me.bibo.militarycraft.vehicles.drone.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Central place for all {@link NamespacedKey}s and tags used to mark DroneCraft
 * entities and items so they can be recognised, rebuilt and cleaned up.
 */
public final class Keys {

    /** Stored on every entity that belongs to a drone: the drone's UUID as a string. */
    public static NamespacedKey DRONE_ID;
    /** Stored on every drone entity: which role it plays ("core", "hitbox", "part"). */
    public static NamespacedKey DRONE_PART;
    /** Stored on each model display: its index into the model parts list (for rehydration). */
    public static NamespacedKey PART_INDEX;
    /** Marks the placer item. */
    public static NamespacedKey ITEM_TAG;
    /** On a debris chunk: the world full-time tick after which it must be swept. */
    public static NamespacedKey DEBRIS_EXPIRE;

    // Persisted attitude/health, written on the core so a drone can be rebuilt
    // after a chunk reload or restart without a separate save file.
    public static NamespacedKey STATE_YAW;
    public static NamespacedKey STATE_PITCH;
    public static NamespacedKey STATE_ROLL;
    public static NamespacedKey STATE_SPEED;
    public static NamespacedKey STATE_HEALTH;
    public static NamespacedKey STATE_BATTERY;
    public static NamespacedKey STATE_ROCKETS;

    // Stored on the operator while they pilot in spectator mode, so their previous
    // game mode and spot can be restored even after a crash / unexpected reconnect.
    public static NamespacedKey CTRL_PREV_GM;
    public static NamespacedKey CTRL_RETURN;

    /** Scoreboard tag put on every drone entity, used for a cheap world sweep. */
    public static final String SCOREBOARD_TAG = "dronecraft_entity";
    /** Scoreboard tag put on every transient debris chunk, so they can be swept. */
    public static final String DEBRIS_TAG = "dronecraft_debris";

    private Keys() {
    }

    public static void init(Plugin plugin) {
        DRONE_ID = key("drone_id");
        DRONE_PART = key("drone_part");
        PART_INDEX = key("part_index");
        ITEM_TAG = key("drone_item");
        DEBRIS_EXPIRE = key("debris_expire");
        STATE_YAW = key("state_yaw");
        STATE_PITCH = key("state_pitch");
        STATE_ROLL = key("state_roll");
        STATE_SPEED = key("state_speed");
        STATE_HEALTH = key("state_health");
        STATE_BATTERY = key("state_battery");
        STATE_ROCKETS = key("state_rockets");
        CTRL_PREV_GM = key("ctrl_prev_gm");
        CTRL_RETURN = key("ctrl_return");
    }

    private static NamespacedKey key(String key) {
        return new NamespacedKey("dronecraft", key);
    }
}
