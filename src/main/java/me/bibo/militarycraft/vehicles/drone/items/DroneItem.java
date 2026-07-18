package me.bibo.militarycraft.vehicles.drone.items;

import me.bibo.militarycraft.vehicles.drone.DroneRuntime;
import me.bibo.militarycraft.vehicles.drone.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** The placer item: right-click the ground with it to deploy a UAV. */
public final class DroneItem {

    // An inert item: no vanilla right-click action on blocks/entities, so it
    // can't be accidentally consumed (e.g. equipped onto a mob).
    public static final Material MATERIAL = Material.ECHO_SHARD;
    private static final int MODEL_DATA = 7351;

    private DroneItem() {
    }

    public static ItemStack create(DroneRuntime plugin) {
        ItemStack item = new ItemStack(MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("🛩 UAV \"Geran-K\"", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Right-click ground - launch the UAV", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click UAV - take control (spectator mode)", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Direct spectator-style flight - steer with camera", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Ram targets/ground to detonate; left-click detonates manually", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Double Shift - exit", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.setCustomModelData(MODEL_DATA);
        meta.getPersistentDataContainer().set(Keys.ITEM_TAG, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isDroneItem(ItemStack item) {
        if (item == null || item.getType() != MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        Byte tag = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.ITEM_TAG, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }
}
