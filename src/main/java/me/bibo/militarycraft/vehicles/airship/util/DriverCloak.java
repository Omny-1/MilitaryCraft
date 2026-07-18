package me.bibo.militarycraft.vehicles.airship.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Hides a seated rider's armour and held items from other players. The real
 * inventory is never touched — we only send "this entity is wearing nothing"
 * equipment packets to every other client (pure Paper API, no ProtocolLib). This
 * complements {@code setInvisible} so the floating armour a cloaked rider would
 * otherwise leave behind disappears too.
 */
public final class DriverCloak {

    private DriverCloak() {
    }

    private static final double VIEW_DISTANCE_SQUARED = 128.0 * 128.0;

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND
    };
    private static final ItemStack AIR = new ItemStack(Material.AIR);

    /** Show every other player empty equipment for this rider. */
    public static void hide(Player rider) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            hideFrom(rider, viewer);
        }
    }

    /** Show one viewer empty equipment for this rider, if they can plausibly see them. */
    public static void hideFrom(Player rider, Player viewer) {
        if (!canSeeRiderEquipment(viewer, rider)) {
            return;
        }
        for (EquipmentSlot slot : SLOTS) {
            viewer.sendEquipmentChange(rider, slot, AIR);
        }
    }

    /** Restore the rider's real equipment view for everyone. */
    public static void show(Player rider) {
        EntityEquipment eq = rider.getEquipment();
        if (eq == null) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(rider)) {
                continue;
            }
            for (EquipmentSlot slot : SLOTS) {
                viewer.sendEquipmentChange(rider, slot, eq.getItem(slot));
            }
        }
    }

    private static boolean canSeeRiderEquipment(Player viewer, Player rider) {
        return viewer != null
                && rider != null
                && viewer.isOnline()
                && rider.isOnline()
                && !viewer.equals(rider)
                && viewer.getWorld().equals(rider.getWorld())
                && viewer.getLocation().distanceSquared(rider.getLocation()) <= VIEW_DISTANCE_SQUARED;
    }
}
