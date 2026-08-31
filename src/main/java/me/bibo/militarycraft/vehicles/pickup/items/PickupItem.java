package me.bibo.militarycraft.vehicles.pickup.items;

import java.util.List;
import me.bibo.militarycraft.vehicles.pickup.PickupRuntime;
import me.bibo.militarycraft.vehicles.pickup.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The placeable pickup item. Identified by a tag in its persistent data rather than by name or
 * material, so a renamed item still works and a look-alike does not.
 */
public final class PickupItem {
    public static final Material MATERIAL = Material.NETHERITE_SCRAP;
    private static final int MODEL_DATA = 7342;

    private PickupItem() {
    }

    public static ItemStack create(PickupRuntime plugin) {
        ItemStack item = new ItemStack(MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("\ud83d\ude99 Pickup", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Right-click the ground - place the pickup", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), Component.text("Right-click the hood - take the driver's seat", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false), Component.text("Right-click the adjacent seat - ride as passenger", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false), Component.text("Right-click the machine gun - take the gunner seat", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false), Component.text("Shift - exit", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false), Component.text("Gunner: left-click or Space - fire a burst", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.setCustomModelData(Integer.valueOf(7342));
        meta.getPersistentDataContainer().set(Keys.ITEM_TAG, PersistentDataType.BYTE, (byte)1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isPickupItem(ItemStack item) {
        if (item == null || item.getType() != MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        Byte tag = (Byte)item.getItemMeta().getPersistentDataContainer().get(Keys.ITEM_TAG, PersistentDataType.BYTE);
        return tag != null && tag == 1;
    }
}
