package me.bibo.militarycraft.core.vehicle;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Hides a seated driver's armour and held items from other players (§5.3, ported
 * verbatim from TankCraft's {@code DriverCloak}). The real inventory is never
 * touched — we only send "this entity is wearing nothing" equipment packets to every
 * other client (pure Paper API, no ProtocolLib). Complements {@code setInvisible} so
 * the floating armour a cloaked driver would otherwise leave behind disappears too.
 */
public final class PilotCloak {

    private PilotCloak() {
    }

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND
    };
    private static final ItemStack AIR = new ItemStack(Material.AIR);
    private static final double VIEW_RADIUS_SQ = 96.0 * 96.0;

    /** Show every other nearby player empty equipment for this driver. */
    public static void hide(Player driver) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(driver) || viewer.getWorld() != driver.getWorld()
                    || viewer.getLocation().distanceSquared(driver.getLocation()) > VIEW_RADIUS_SQ) {
                continue;
            }
            for (EquipmentSlot slot : SLOTS) {
                viewer.sendEquipmentChange(driver, slot, AIR);
            }
        }
    }

    /** Restore the driver's real equipment view for everyone. */
    public static void show(Player driver) {
        EntityEquipment eq = driver.getEquipment();
        if (eq == null) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(driver)) {
                continue;
            }
            for (EquipmentSlot slot : SLOTS) {
                viewer.sendEquipmentChange(driver, slot, eq.getItem(slot));
            }
        }
    }
}
