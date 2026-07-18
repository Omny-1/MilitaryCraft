package me.bibo.militarycraft.core.item;

import me.bibo.militarycraft.core.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Builds tagged {@link ItemStack}s (display name/lore/CustomModelData/PDC marker via
 * {@link me.bibo.militarycraft.core.key.Keys}). Folds in the old WarKit ItemTools/Txt intent
 * so gear (CP6) can build items on top of this instead of its own helpers.
 */
public final class ItemFactory {

    public ItemStack build(Material material, String name, TextColor nameColor, List<Component> lore,
                            Integer customModelData, NamespacedKey tagKey) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (name != null) {
            meta.displayName(Text.item(name, nameColor));
        }
        if (lore != null) {
            meta.lore(lore);
        }
        if (customModelData != null) {
            meta.setCustomModelData(customModelData);
        }
        if (tagKey != null) {
            meta.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** Whether {@code item} carries the byte-marker at {@code tagKey} (set by {@link #build}). */
    public boolean isTagged(ItemStack item, NamespacedKey tagKey) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        Byte value = item.getItemMeta().getPersistentDataContainer().get(tagKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }
}
