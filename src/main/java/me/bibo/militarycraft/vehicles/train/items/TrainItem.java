package me.bibo.militarycraft.vehicles.train.items;

import me.bibo.militarycraft.vehicles.train.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** The placer item: right-click a rail with it and the express thunders off. */
public final class TrainItem {

    private TrainItem() {
    }

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.FURNACE_MINECART);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Desert Express", NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Right-click rails - place the train", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Shift+right-click train - remove the consist", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Starts immediately - stay clear of the tracks!", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(Keys.TRAIN_ITEM, PersistentDataType.BYTE, (byte) 1);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean is(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(Keys.TRAIN_ITEM, PersistentDataType.BYTE);
    }
}
