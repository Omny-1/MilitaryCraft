package me.bibo.militarycraft.vehicles.train.util;

import org.bukkit.NamespacedKey;

/** Persistent-data keys and the scoreboard tag every train entity carries. */
public final class Keys {

    public static final String SCOREBOARD_TAG = "traincraft_entity";

    public static final NamespacedKey TRAIN_ID = new NamespacedKey("traincraft", "train_id");
    public static final NamespacedKey CAR_INDEX = new NamespacedKey("traincraft", "car_index");
    public static final NamespacedKey ROLE = new NamespacedKey("traincraft", "role");
    public static final NamespacedKey TRAIN_ITEM = new NamespacedKey("traincraft", "train_item");

    private Keys() {
    }
}
