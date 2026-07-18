package me.bibo.militarycraft.weapons.antiair.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Central place for all {@link NamespacedKey}s and tags used to mark AntiAirCraft
 * entities and items so they can be recognised, rebuilt and cleaned up after a
 * chunk reload, restart or crash.
 */
public final class Keys {

    /** Stored on every entity that belongs to a turret: the turret's UUID. */
    public static NamespacedKey TURRET_ID;
    /** Role of the entity within the turret ("core", "hitbox", "part"). */
    public static NamespacedKey TURRET_PART;
    /** Index of a model display into the parts list (for rehydration). */
    public static NamespacedKey PART_INDEX;
    /** Marks the placer item. */
    public static NamespacedKey ITEM_TAG;
    /** Stored on the core: UUID of the player who placed the turret. */
    public static NamespacedKey OWNER;

    // Persisted state, written on the core so a turret can be rebuilt after a
    // chunk reload or restart without a separate save file.
    public static NamespacedKey STATE_MODE;
    public static NamespacedKey STATE_HEALTH;
    public static NamespacedKey STATE_YAW;
    public static NamespacedKey STATE_PITCH;
    public static NamespacedKey STATE_BURN;        // burn time remaining (ticks)
    public static NamespacedKey STATE_BURN_TOTAL;  // burn time of the current fuel
    public static NamespacedKey STATE_FUEL_TYPE;   // reserve fuel material name
    public static NamespacedKey STATE_FUEL_COUNT;  // reserve fuel count

    /** Scoreboard tag put on every turret entity, used for a cheap world sweep. */
    public static final String SCOREBOARD_TAG = "antiaircraft_entity";

    private Keys() {
    }

    public static void init(Plugin plugin) {
        TURRET_ID = new NamespacedKey("antiaircraft", "turret_id");
        TURRET_PART = new NamespacedKey("antiaircraft", "turret_part");
        PART_INDEX = new NamespacedKey("antiaircraft", "part_index");
        ITEM_TAG = new NamespacedKey("antiaircraft", "antiair_item");
        OWNER = new NamespacedKey("antiaircraft", "owner");
        STATE_MODE = new NamespacedKey("antiaircraft", "state_mode");
        STATE_HEALTH = new NamespacedKey("antiaircraft", "state_health");
        STATE_YAW = new NamespacedKey("antiaircraft", "state_yaw");
        STATE_PITCH = new NamespacedKey("antiaircraft", "state_pitch");
        STATE_BURN = new NamespacedKey("antiaircraft", "state_burn");
        STATE_BURN_TOTAL = new NamespacedKey("antiaircraft", "state_burn_total");
        STATE_FUEL_TYPE = new NamespacedKey("antiaircraft", "state_fuel_type");
        STATE_FUEL_COUNT = new NamespacedKey("antiaircraft", "state_fuel_count");
    }
}
