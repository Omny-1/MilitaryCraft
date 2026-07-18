package me.bibo.militarycraft.core.key;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/** Typed get/set helpers over {@link PersistentDataContainer}, every getter takes an explicit fallback. */
public final class Pdc {

    private Pdc() {
    }

    public static void setString(PersistentDataContainer c, NamespacedKey key, String value) {
        c.set(key, PersistentDataType.STRING, value);
    }

    public static String getString(PersistentDataContainer c, NamespacedKey key, String fallback) {
        String v = c.get(key, PersistentDataType.STRING);
        return v != null ? v : fallback;
    }

    public static void setUuid(PersistentDataContainer c, NamespacedKey key, UUID value) {
        c.set(key, PersistentDataType.STRING, value.toString());
    }

    public static UUID getUuid(PersistentDataContainer c, NamespacedKey key, UUID fallback) {
        String v = c.get(key, PersistentDataType.STRING);
        if (v == null) {
            return fallback;
        }
        try {
            return UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public static void setDouble(PersistentDataContainer c, NamespacedKey key, double value) {
        c.set(key, PersistentDataType.DOUBLE, value);
    }

    public static double getDouble(PersistentDataContainer c, NamespacedKey key, double fallback) {
        Double v = c.get(key, PersistentDataType.DOUBLE);
        return v != null ? v : fallback;
    }

    public static void setInt(PersistentDataContainer c, NamespacedKey key, int value) {
        c.set(key, PersistentDataType.INTEGER, value);
    }

    public static int getInt(PersistentDataContainer c, NamespacedKey key, int fallback) {
        Integer v = c.get(key, PersistentDataType.INTEGER);
        return v != null ? v : fallback;
    }

    public static void setByte(PersistentDataContainer c, NamespacedKey key, byte value) {
        c.set(key, PersistentDataType.BYTE, value);
    }

    public static byte getByte(PersistentDataContainer c, NamespacedKey key, byte fallback) {
        Byte v = c.get(key, PersistentDataType.BYTE);
        return v != null ? v : fallback;
    }
}
