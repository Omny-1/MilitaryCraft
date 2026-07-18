package me.bibo.militarycraft.weapons.antiair.gui;

import me.bibo.militarycraft.weapons.antiair.AntiAirRuntime;
import me.bibo.militarycraft.weapons.antiair.fuel.FuelTable;
import me.bibo.militarycraft.weapons.antiair.turret.Mode;
import me.bibo.militarycraft.weapons.antiair.turret.Turret;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The right-click control panel: a chest GUI with a furnace-style fuel slot and
 * three mode buttons (Normies: Phantoms / Normies: Hostile Mobs / SVO). The fuel
 * reserve is the turret's own state; the slot here just renders it (handled
 * manually by {@link GuiListener}, so nothing is ever dropped or duped).
 */
public final class TurretMenu implements InventoryHolder {

    public static final int SIZE = 27;
    public static final int FUEL_SLOT = 11;
    public static final int FUEL_LABEL = 10;
    public static final int STATUS_SLOT = 4;
    public static final int MODE_PHANTOM = 14;
    public static final int MODE_HOSTILE = 15;
    public static final int MODE_SVO = 16;
    public static final int REMOVE_SLOT = 22;

    /** A fuel slot can hold one stack, like a furnace. */
    public static final int MAX_FUEL = 64;

    private final AntiAirRuntime plugin;
    private final UUID turretId;
    private final Inventory inventory;

    private TurretMenu(AntiAirRuntime plugin, UUID turretId) {
        this.plugin = plugin;
        this.turretId = turretId;
        this.inventory = Bukkit.createInventory(this, SIZE,
                Component.text("Anti-Air - Control Panel", NamedTextColor.DARK_AQUA));
    }

    public static void open(AntiAirRuntime plugin, Player player, Turret turret) {
        TurretMenu menu = new TurretMenu(plugin, turret.id());
        menu.render(turret);
        player.openInventory(menu.inventory);
    }

    public UUID turretId() {
        return turretId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Repaint every slot from the turret's live state. */
    public void render(Turret turret) {
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(STATUS_SLOT, statusIcon(turret));

        inventory.setItem(FUEL_LABEL, icon(Material.ARROW, "Fuel Slot ->",
                List.of(line("Place coal, wood, lava, etc.", NamedTextColor.GRAY),
                        line("Anti-Air works like a furnace: burns fuel, then fires.", NamedTextColor.DARK_GRAY)),
                false));
        inventory.setItem(FUEL_SLOT, fuelRender(turret));

        inventory.setItem(MODE_PHANTOM, modeIcon(turret, Mode.NORMIES_PHANTOMS, Material.PHANTOM_MEMBRANE));
        inventory.setItem(MODE_HOSTILE, modeIcon(turret, Mode.NORMIES_HOSTILES, Material.ROTTEN_FLESH));
        inventory.setItem(MODE_SVO, modeIcon(turret, Mode.SVO, Material.IRON_SWORD));

        inventory.setItem(REMOVE_SLOT, icon(Material.BARRIER, "✖ Remove Turret",
                List.of(line("Owner/admin only.", NamedTextColor.GRAY),
                        line("Returns the Anti-Air item.", NamedTextColor.DARK_GRAY)), false));
    }

    /** The ItemStack shown in the fuel slot (a render of the reserve). */
    public ItemStack fuelRender(Turret turret) {
        if (turret.fuelType() != null && turret.fuelCount() > 0) {
            return new ItemStack(turret.fuelType(), turret.fuelCount());
        }
        return new ItemStack(Material.AIR);
    }

    private ItemStack statusIcon(Turret turret) {
        List<Component> lore = new ArrayList<>();
        lore.add(line("Health: " + (int) Math.ceil(turret.health()) + " / "
                + (int) Math.ceil(turret.maxHealth()), NamedTextColor.RED));
        int creepers = Math.max(1, (int) Math.round(plugin.config().creepersToDestroy));
        lore.add(line("(destroyed by " + creepers + " creeper "
                + (creepers == 1 ? "blast" : "blasts") + ")", NamedTextColor.DARK_GRAY));
        if (turret.burnTime() > 0) {
            lore.add(line("🔥 Charge: ~" + turret.litShotsLeft() + " shots", NamedTextColor.GOLD));
        } else if (turret.hasPower()) {
            lore.add(line("🔥 Fuel in reserve - ready to operate", NamedTextColor.GOLD));
        } else {
            lore.add(line("⚠ No fuel - cannot fire", NamedTextColor.YELLOW));
        }
        if (turret.fuelType() != null && turret.fuelCount() > 0) {
            lore.add(line("Reserve: " + prettyName(turret.fuelType()) + " x" + turret.fuelCount(),
                    NamedTextColor.GRAY));
        }
        if (turret.isOverheated()) {
            lore.add(line("🌡 OVERHEATED - cooling down", NamedTextColor.RED));
        } else if (turret.heat() > 0.05) {
            lore.add(line("🌡 Heat: " + (int) Math.round(turret.heat() * 100) + "%", NamedTextColor.GOLD));
        }
        lore.add(Component.empty());
        lore.add(line("Mode: " + turret.mode().title(), NamedTextColor.AQUA));
        return icon(Material.OBSERVER, "🛡 Anti-Air \"Phalanx\"", lore, false);
    }

    private ItemStack modeIcon(Turret turret, Mode mode, Material material) {
        boolean active = turret.mode() == mode;
        List<Component> lore = new ArrayList<>();
        lore.add(line(mode.description(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(active
                ? line("✔ Active", NamedTextColor.GREEN)
                : line("Click to select", NamedTextColor.DARK_GRAY));
        return icon(material, (active ? "» " : "") + mode.title(), lore, active);
    }

    // ----------------------------------------------------------------- helpers

    private static ItemStack icon(Material mat, String name, List<Component> lore, boolean glow) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        if (glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static String prettyName(Material m) {
        String s = m.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** True if the slot index is one of the clickable buttons (not the fuel slot). */
    public static boolean isButton(int rawSlot) {
        return rawSlot == MODE_PHANTOM || rawSlot == MODE_HOSTILE
                || rawSlot == MODE_SVO || rawSlot == REMOVE_SLOT;
    }
}
