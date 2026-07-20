package me.bibo.militarycraft.vehicles.jet.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Hides a seated pilot's armour and held items from other players. The pilot's
 * real inventory is never touched - we only send "this entity is wearing
 * nothing" equipment packets to every other client (pure Paper API, no
 * ProtocolLib). This complements {@code setInvisible} so the floating armour a
 * cloaked pilot would otherwise leave behind disappears too.
 */
public final class DriverCloak {

    private DriverCloak() {
    }

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND
    };
    private static final ItemStack AIR = new ItemStack(Material.AIR);

    /** Show every other player empty equipment for this pilot. */
    public static void hide(Player driver) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            hideFrom(driver, viewer);
        }
    }

    /** Restore the pilot's real equipment view for everyone. */
    public static void show(Player driver) {
        EntityEquipment eq = driver.getEquipment();
        if (eq == null) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            showTo(driver, viewer);
        }
    }

    public static void hideFrom(Player driver, Player viewer) {
        if (viewer.equals(driver)) {
            return;
        }
        for (EquipmentSlot slot : SLOTS) {
            viewer.sendEquipmentChange(driver, slot, AIR);
        }
    }

    public static void showTo(Player driver, Player viewer) {
        if (viewer.equals(driver)) {
            return;
        }
        EntityEquipment eq = driver.getEquipment();
        if (eq == null) {
            return;
        }
        for (EquipmentSlot slot : SLOTS) {
            viewer.sendEquipmentChange(driver, slot, eq.getItem(slot));
        }
    }
}
