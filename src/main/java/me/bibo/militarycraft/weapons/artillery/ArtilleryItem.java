package me.bibo.militarycraft.weapons.artillery;

import me.bibo.militarycraft.core.item.ItemFactory;
import me.bibo.militarycraft.core.key.Keys;
import me.bibo.militarycraft.core.text.Text;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Tagged placer/operator item for Artillery "Belochka". */
final class ArtilleryItem {

    private static final Material MATERIAL = Material.BARRIER;
    private static final Material LEGACY_UNIFIED_MATERIAL = Material.NETHERITE_SCRAP;

    private ArtilleryItem() {
    }

    static ItemStack create(ItemFactory items) {
        ItemStack item = items.build(MATERIAL, ArtilleryMessages.NAME, NamedTextColor.GOLD,
                Text.lore(
                        "Stationary SVO artillery installation.",
                        "Right-click a block to place it; right-click the artillery to operate it.",
                        "Use /shoot <x> <z> or /mc artillery fire <x> <z> while operating.",
                        "Each ammo unit launches a three-shell salvo; closer targets are more accurate."),
                null, originalTagKey());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(unifiedTagKey(), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    static boolean isItem(ItemFactory items, ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        Material type = stack.getType();
        if (type != MATERIAL && type != LEGACY_UNIFIED_MATERIAL) {
            return false;
        }
        return items.isTagged(stack, originalTagKey()) || items.isTagged(stack, unifiedTagKey());
    }

    private static NamespacedKey originalTagKey() {
        NamespacedKey key = NamespacedKey.fromString("svoartillery:artillery_item");
        if (key == null) {
            throw new IllegalStateException("Invalid SvoArtillery item key");
        }
        return key;
    }

    private static NamespacedKey unifiedTagKey() {
        return Keys.of("artillery", "item");
    }
}
