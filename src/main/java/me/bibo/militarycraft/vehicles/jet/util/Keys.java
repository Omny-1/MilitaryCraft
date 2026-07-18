package me.bibo.militarycraft.vehicles.jet.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Central place for all {@link NamespacedKey}s and tags used to mark JetCraft
 * entities and items so they can be recognised, rebuilt and cleaned up.
 */
public final class Keys {

    /** Stored on every entity that belongs to a jet: the jet's UUID as a string. */
    public static NamespacedKey JET_ID;
    /** Stored on every jet entity: which role it plays ("core", "hitbox", "part"). */
    public static NamespacedKey JET_PART;
    /** Stored on each model display: its index into the model parts list (for rehydration). */
    public static NamespacedKey PART_INDEX;
    /** Marks the placer item. */
    public static NamespacedKey ITEM_TAG;
    /** On a debris chunk: the world full-time tick after which it must be swept. */
    public static NamespacedKey DEBRIS_EXPIRE;

    // Persisted attitude/health, written on the core so a jet can be rebuilt
    // after a chunk reload or restart without a separate save file.
    public static NamespacedKey STATE_YAW;
    public static NamespacedKey STATE_PITCH;
    public static NamespacedKey STATE_ROLL;
    public static NamespacedKey STATE_SPEED;
    public static NamespacedKey STATE_HEALTH;
    /** Whether the jet is flying on pilotless after a mid-air bail-out. */
    public static NamespacedKey STATE_UNMANNED;

    /** Scoreboard tag put on every jet entity, used for a cheap world sweep. */
    public static final String SCOREBOARD_TAG = "jetcraft_entity";
    /** Scoreboard tag put on every transient debris chunk, so they can be swept. */
    public static final String DEBRIS_TAG = "jetcraft_debris";

    private Keys() {
    }

    public static void init(Plugin plugin) {
        JET_ID = key("jet_id");
        JET_PART = key("jet_part");
        PART_INDEX = key("part_index");
        ITEM_TAG = key("jet_item");
        DEBRIS_EXPIRE = key("debris_expire");
        STATE_YAW = key("state_yaw");
        STATE_PITCH = key("state_pitch");
        STATE_ROLL = key("state_roll");
        STATE_SPEED = key("state_speed");
        STATE_HEALTH = key("state_health");
        STATE_UNMANNED = key("state_unmanned");
    }

    @SuppressWarnings("deprecation")
    private static NamespacedKey key(String key) {
        return new NamespacedKey("jetcraft", key);
    }
}
