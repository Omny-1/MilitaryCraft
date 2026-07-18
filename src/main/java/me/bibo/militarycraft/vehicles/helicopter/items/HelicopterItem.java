package me.bibo.militarycraft.vehicles.helicopter.items;

import me.bibo.militarycraft.vehicles.helicopter.HelicopterRuntime;
import me.bibo.militarycraft.vehicles.helicopter.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** The placer item: right-click the ground with it to spawn a helicopter. */
public final class HelicopterItem {

    // An inert item: no vanilla right-click action on blocks/entities, so it
    // cannot be accidentally consumed (e.g. equipped onto a mob).
    public static final Material MATERIAL = Material.NETHERITE_SCRAP;
    private static final int MODEL_DATA = 7451;

    private HelicopterItem() {
    }

    public static ItemStack create(HelicopterRuntime plugin) {
        ItemStack item = new ItemStack(MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("🚁 Helicopter", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Right-click ground - place the helicopter", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click cockpit - take the controls", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Mouse - heading and altitude; W/S - thrust; Space - climb", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Left-click - rocket; right-click - bomb; Shift - exit", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.setCustomModelData(MODEL_DATA);
        meta.getPersistentDataContainer().set(Keys.ITEM_TAG, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isHelicopterItem(ItemStack item) {
        if (item == null || item.getType() != MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        Byte tag = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.ITEM_TAG, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }
}
