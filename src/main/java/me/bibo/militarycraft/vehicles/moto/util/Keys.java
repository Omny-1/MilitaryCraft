package me.bibo.militarycraft.vehicles.moto.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** PDC keys and the scoreboard tag owned exclusively by MotoCraft. */
public final class Keys {

    public static NamespacedKey MOTORCYCLE_ID;
    public static NamespacedKey ENTITY_ROLE;
    public static NamespacedKey MOTORCYCLE_OWNER;
    public static NamespacedKey PART_INDEX;
    public static NamespacedKey ITEM_TAG;
    public static NamespacedKey ITEM_SIDECAR;
    public static NamespacedKey MODEL_VERSION;
    public static NamespacedKey STATE_YAW;
    public static NamespacedKey STATE_HEALTH;
    public static NamespacedKey STATE_SIDECAR;

    public static final String SCOREBOARD_TAG = "motocraft_entity";

    private Keys() {
    }

    public static void init(Plugin plugin) {
        MOTORCYCLE_ID = key("motorcycle_id");
        ENTITY_ROLE = key("entity_role");
        MOTORCYCLE_OWNER = key("motorcycle_owner");
        PART_INDEX = key("part_index");
        ITEM_TAG = key("motorcycle_item");
        ITEM_SIDECAR = key("item_sidecar");
        MODEL_VERSION = key("model_version");
        STATE_YAW = key("state_yaw");
        STATE_HEALTH = key("state_health");
        STATE_SIDECAR = key("state_sidecar");
    }

    private static NamespacedKey key(String key) {
        return new NamespacedKey("motocraft", key);
    }
}
