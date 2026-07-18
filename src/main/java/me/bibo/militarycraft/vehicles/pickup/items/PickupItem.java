/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  net.kyori.adventure.text.format.TextDecoration
 *  org.bukkit.Material
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataType
 */
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

public final class PickupItem {
    public static final Material MATERIAL = Material.NETHERITE_SCRAP;
    private static final int MODEL_DATA = 7342;

    private PickupItem() {
    }

    public static ItemStack create(PickupRuntime plugin) {
        ItemStack item = new ItemStack(MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text((String)"\ud83d\ude99 Pickup", (TextColor)NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of((TextComponent)Component.text((String)"Right-click the ground - place the pickup", (TextColor)NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), (TextComponent)Component.text((String)"Right-click the hood - take the driver's seat", (TextColor)NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false), (TextComponent)Component.text((String)"Right-click the adjacent seat - ride as passenger", (TextColor)NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false), (TextComponent)Component.text((String)"Right-click the machine gun - take the gunner seat", (TextColor)NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false), (TextComponent)Component.text((String)"Shift - exit", (TextColor)NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false), (TextComponent)Component.text((String)"Gunner: left-click or Space - fire a burst", (TextColor)NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
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
