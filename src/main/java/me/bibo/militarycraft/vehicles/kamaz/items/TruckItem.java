package me.bibo.militarycraft.vehicles.kamaz.items;

import me.bibo.militarycraft.vehicles.kamaz.KamazRuntime;
import me.bibo.militarycraft.vehicles.kamaz.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** The placer item: right-click the ground with it to spawn a Kamaz "Pushinka". */
public final class TruckItem {

    // An inert item: no vanilla right-click action on blocks/entities, so it
    // can't be accidentally consumed (e.g. equipped onto a horse).
    public static final Material MATERIAL = Material.NETHERITE_SCRAP;
    private static final int MODEL_DATA = 7342;

    private TruckItem() {
    }

    public static ItemStack create(KamazRuntime plugin) {
        ItemStack item = new ItemStack(MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("🚛 Kamaz \"Pushinka\"", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Right-click ground - place the Kamaz", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click the truck - enter, Shift - exit", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("First rider drives, others ride as passengers (up to 6)", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("At full speed it throws targets away,", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("slower impacts crush without killing", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.setCustomModelData(MODEL_DATA);
        meta.getPersistentDataContainer().set(Keys.ITEM_TAG, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isTruckItem(ItemStack item) {
        if (item == null || item.getType() != MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        Byte tag = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.ITEM_TAG, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }
}
