package me.bibo.militarycraft.vehicles.jet.items;

import me.bibo.militarycraft.vehicles.jet.JetRuntime;
import me.bibo.militarycraft.vehicles.jet.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** The placer item: right-click the ground with it to spawn a jet. */
public final class JetItem {

    // An inert item: no vanilla right-click action on blocks/entities, so it
    // can't be accidentally consumed (e.g. equipped onto a mob).
    public static final Material MATERIAL = Material.NETHERITE_SCRAP;
    private static final int MODEL_DATA = 7342;

    private JetItem() {
    }

    public static ItemStack create(JetRuntime plugin) {
        ItemStack item = new ItemStack(MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("✈ Su-30 Fighter", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Right-click ground - place the fighter", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click aircraft - enter, Shift - eject", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("W/S - throttle, A/D - roll, Space - afterburner", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Left-click - rockets, right-click - drop bomb", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.setCustomModelData(MODEL_DATA);
        meta.getPersistentDataContainer().set(Keys.ITEM_TAG, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isJetItem(ItemStack item) {
        if (item == null || item.getType() != MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        Byte tag = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.ITEM_TAG, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }
}
