package me.bibo.militarycraft.vehicles.moto.items;

import me.bibo.militarycraft.vehicles.moto.MotoRuntime;
import me.bibo.militarycraft.vehicles.moto.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Factory and strict PDC-based recognition for the motorcycle placer item. */
public final class MotorcycleItem {

    public static final Material MATERIAL = Material.NETHERITE_SCRAP;
    private static final int MODEL_DATA = 7351;

    private MotorcycleItem() {
    }

    public static ItemStack create(MotoRuntime plugin) {
        return create(plugin, true);
    }

    public static ItemStack create(MotoRuntime plugin, boolean withSidecar) {
        ItemStack item = new ItemStack(MATERIAL);
        ItemMeta meta = item.getItemMeta();
        String seats = withSidecar
                ? "Seats: driver, rear passenger and sidecar passenger"
                : "Seats: driver and rear passenger";
        meta.displayName(plain(Component.text(
                withSidecar ? "🏍 Motorcycle with Sidecar" : "🏍 Motorcycle", NamedTextColor.RED)));
        meta.lore(List.of(
                plain(Component.text("Right-click ground - place", NamedTextColor.GRAY)),
                plain(Component.text("Right-click motorcycle - enter, Shift - exit", NamedTextColor.DARK_GRAY)),
                plain(Component.text("W/S - throttle and brake, steer with camera", NamedTextColor.DARK_GRAY)),
                plain(Component.text(seats, NamedTextColor.DARK_GRAY))));
        meta.setCustomModelData(MODEL_DATA);
        meta.getPersistentDataContainer().set(Keys.ITEM_TAG, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(Keys.ITEM_SIDECAR, PersistentDataType.BYTE,
                (byte) (withSidecar ? 1 : 0));
        item.setItemMeta(meta);
        return item;
    }

    /** Reads the variant off a placer item; legacy items (no tag) mean with sidecar. */
    public static boolean hasSidecar(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return true;
        }
        Byte tag = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.ITEM_SIDECAR, PersistentDataType.BYTE);
        return tag == null || tag != (byte) 0;
    }

    private static Component plain(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    public static boolean isMotorcycleItem(ItemStack item) {
        if (item == null || item.getType() != MATERIAL || !item.hasItemMeta()) {
            return false;
        }
        Byte tag = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.ITEM_TAG, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }
}
