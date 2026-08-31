package me.bibo.militarycraft.vehicles.pickup.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** The pickup's NamespacedKeys: what marks its entities and what its saved state is stored under. */
public final class Keys {
    public static NamespacedKey PICKUP_ID;
    public static NamespacedKey PICKUP_PART;
    public static NamespacedKey PART_INDEX;
    public static NamespacedKey ITEM_TAG;
    public static NamespacedKey STATE_HULL_YAW;
    public static NamespacedKey STATE_GUN_YAW;
    public static NamespacedKey STATE_GUN_PITCH;
    public static NamespacedKey STATE_HEALTH;
    public static NamespacedKey STATE_ANCHOR_X;
    public static NamespacedKey STATE_ANCHOR_Y;
    public static NamespacedKey STATE_ANCHOR_Z;
    public static final String SCOREBOARD_TAG = "pickupcraft_entity";
    public static final String LEGACY_NAMESPACE = "jeepcraft";
    public static final String LEGACY_SCOREBOARD_TAG = "jeepcraft_entity";
    public static NamespacedKey LEGACY_PICKUP_ID;
    public static NamespacedKey LEGACY_PICKUP_PART;
    public static NamespacedKey LEGACY_STATE_HULL_YAW;
    public static NamespacedKey LEGACY_STATE_ANCHOR_X;
    public static NamespacedKey LEGACY_STATE_ANCHOR_Y;
    public static NamespacedKey LEGACY_STATE_ANCHOR_Z;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        PICKUP_ID = key("pickup_id");
        PICKUP_PART = key("pickup_part");
        PART_INDEX = key("part_index");
        ITEM_TAG = key("pickup_item");
        STATE_HULL_YAW = key("state_hull_yaw");
        STATE_GUN_YAW = key("state_gun_yaw");
        STATE_GUN_PITCH = key("state_gun_pitch");
        STATE_HEALTH = key("state_health");
        STATE_ANCHOR_X = key("state_anchor_x");
        STATE_ANCHOR_Y = key("state_anchor_y");
        STATE_ANCHOR_Z = key("state_anchor_z");
        LEGACY_PICKUP_ID = new NamespacedKey(LEGACY_NAMESPACE, "jeep_id");
        LEGACY_PICKUP_PART = new NamespacedKey(LEGACY_NAMESPACE, "jeep_part");
        LEGACY_STATE_HULL_YAW = new NamespacedKey(LEGACY_NAMESPACE, "state_hull_yaw");
        LEGACY_STATE_ANCHOR_X = new NamespacedKey(LEGACY_NAMESPACE, "state_anchor_x");
        LEGACY_STATE_ANCHOR_Y = new NamespacedKey(LEGACY_NAMESPACE, "state_anchor_y");
        LEGACY_STATE_ANCHOR_Z = new NamespacedKey(LEGACY_NAMESPACE, "state_anchor_z");
    }

    private static NamespacedKey key(String key) {
        return new NamespacedKey("pickupcraft", key);
    }
}
