package me.bibo.militarycraft.weapons.nuke;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class NukeItem {

    private NukeItem() {
    }

    static NamespacedKey briefcaseKey(Plugin plugin) {
        return new NamespacedKey("nukestrike", "nuke_briefcase");
    }

    public static ItemStack create(Plugin plugin) {
        ItemStack item = new ItemStack(Material.HEAVY_CORE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Nuclear Briefcase", NamedTextColor.DARK_GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .decorate(TextDecoration.BOLD));
        meta.lore(List.of(
                Component.text("Right-click a surface - call a nuclear strike.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("A bomber will drop one bomb on the target.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("God can't save you.", NamedTextColor.DARK_RED)
                        .decoration(TextDecoration.ITALIC, true)));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(briefcaseKey(plugin), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isItem(Plugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.HEAVY_CORE || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(briefcaseKey(plugin), PersistentDataType.BYTE);
    }
}
