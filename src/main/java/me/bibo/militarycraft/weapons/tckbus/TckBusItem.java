package me.bibo.militarycraft.weapons.tckbus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The placer item - a draft summons. Right-click the ground with it
 * to spawn a TCK Bus rig and its workers. PAPER is inert on a block right-click, so it
 * can never be accidentally consumed by a vanilla interaction.
 */
public final class TckBusItem {

    public static final Material MATERIAL = Material.PAPER;
    private static final int MODEL_DATA = 7421;

    private TckBusItem() {
    }

    public static ItemStack create(TckBusSettings.Skin skin) {
        ItemStack item = new ItemStack(MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(skin.itemName)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Right-click ground - call " + skin.busName, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Spawns the bus and 2 " + skin.workerPlural, NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Kill the " + skin.workerPlural + ", then break the bus", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("to make it drop loot.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.setCustomModelData(MODEL_DATA);
        meta.getPersistentDataContainer().set(TckBusKeys.ITEM_TAG, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(TckBusKeys.SKIN, PersistentDataType.STRING, skin.id);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isBusItem(ItemStack item) {
        if (item == null || item.getType() != MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        Byte tag = item.getItemMeta().getPersistentDataContainer()
                .get(TckBusKeys.ITEM_TAG, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }

    public static String skinId(ItemStack item) {
        if (!isBusItem(item)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(TckBusKeys.SKIN, PersistentDataType.STRING);
    }
}


