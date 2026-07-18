package me.bibo.militarycraft.weapons.antiair.listeners;

import me.bibo.militarycraft.weapons.antiair.AntiAirRuntime;
import me.bibo.militarycraft.weapons.antiair.fuel.FuelTable;
import me.bibo.militarycraft.weapons.antiair.gui.TurretMenu;
import me.bibo.militarycraft.weapons.antiair.turret.Mode;
import me.bibo.militarycraft.weapons.antiair.turret.Turret;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Drives the turret control panel: mode buttons, the "remove" button, and a fully
 * manual fuel slot. The turret's fuel reserve is the single source of truth; the
 * slot just renders it, so items can never be dropped or duplicated by the GUI.
 */
public final class GuiListener implements Listener {

    private final AntiAirRuntime plugin;

    public GuiListener(AntiAirRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TurretMenu)) {
            return;
        }
        int top = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < top) {
                event.setCancelled(true); // dragging into the panel isn't supported
                return;
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory topInv = event.getView().getTopInventory();
        InventoryHolder holder = topInv.getHolder();
        if (!(holder instanceof TurretMenu menu)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Turret turret = plugin.turrets().byId(menu.turretId());
        if (turret == null || !turret.isActive()) {
            event.setCancelled(true);
            player.closeInventory();
            player.sendActionBar(Component.text("Anti-Air turret is unavailable.", NamedTextColor.RED));
            return;
        }

        int raw = event.getRawSlot();
        boolean clickedTop = raw < topInv.getSize();

        if (!clickedTop) {
            // Player-inventory side: only intercept shift-clicking a fuel item in.
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack cur = event.getCurrentItem();
                if (FuelTable.isFuel(cur)) {
                    depositFromStack(turret, cur);
                    if (cur.getAmount() <= 0) {
                        event.setCurrentItem(null);
                    }
                    menu.render(turret);
                    click(player);
                }
            }
            return;
        }

        // Top inventory.
        if (TurretMenu.isButton(raw)) {
            event.setCancelled(true);
            handleButton(turret, player, raw, menu);
            return;
        }
        if (raw == TurretMenu.FUEL_SLOT) {
            event.setCancelled(true);
            handleFuelClick(turret, event, menu);
            return;
        }
        event.setCancelled(true); // fillers / labels / status
    }

    // ----------------------------------------------------------------- buttons

    private void handleButton(Turret turret, Player player, int raw, TurretMenu menu) {
        if (raw == TurretMenu.REMOVE_SLOT) {
            boolean owner = turret.owner() != null && turret.owner().equals(player.getUniqueId());
            if (!owner && !player.hasPermission("antiaircraft.admin")) {
                player.sendActionBar(Component.text("Only the owner can remove it.", NamedTextColor.RED));
                return;
            }
            player.closeInventory();
            plugin.turrets().remove(turret, false);
            player.getInventory().addItem(me.bibo.militarycraft.weapons.antiair.items.TurretItem.create(plugin));
            player.sendActionBar(Component.text("Anti-Air turret removed.", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7f, 1.2f);
            return;
        }
        Mode mode = switch (raw) {
            case TurretMenu.MODE_PHANTOM -> Mode.NORMIES_PHANTOMS;
            case TurretMenu.MODE_HOSTILE -> Mode.NORMIES_HOSTILES;
            case TurretMenu.MODE_SVO -> Mode.SVO;
            default -> turret.mode();
        };
        if (mode != turret.mode()) {
            turret.setMode(mode);
            player.sendActionBar(Component.text("Mode: " + mode.title(), NamedTextColor.AQUA));
        }
        menu.render(turret);
        click(player);
    }

    // ----------------------------------------------------------------- fuel slot

    private void handleFuelClick(Turret turret, InventoryClickEvent event, TurretMenu menu) {
        Player player = (Player) event.getWhoClicked();
        ItemStack cursor = event.getCursor();
        ClickType ct = event.getClick();
        Material t = turret.fuelType();
        int n = turret.fuelCount();
        boolean cursorEmpty = cursor == null || cursor.getType().isAir() || cursor.getAmount() <= 0;

        if (ct.isShiftClick()) {
            // Move the whole reserve back into the player's inventory.
            if (n > 0 && t != null) {
                Map<Integer, ItemStack> left = player.getInventory().addItem(new ItemStack(t, n));
                int remaining = left.values().stream().mapToInt(ItemStack::getAmount).sum();
                turret.setFuel(remaining > 0 ? t : null, remaining);
            }
            menu.render(turret);
            click(player);
            return;
        }

        if (ct == ClickType.LEFT || ct == ClickType.DOUBLE_CLICK) {
            if (cursorEmpty) {
                if (n > 0) {
                    event.getWhoClicked().setItemOnCursor(new ItemStack(t, n));
                    turret.setFuel(null, 0);
                }
            } else if (FuelTable.isFuel(cursor)) {
                Material c = cursor.getType();
                int amt = cursor.getAmount();
                if (n == 0) {
                    int move = Math.min(amt, cap(c));
                    turret.setFuel(c, move);
                    setCursorAmount(event, cursor, amt - move);
                } else if (c == t) {
                    int move = Math.min(cap(t) - n, amt);
                    turret.setFuel(t, n + move);
                    setCursorAmount(event, cursor, amt - move);
                } else if (amt <= cap(c)) {
                    event.getWhoClicked().setItemOnCursor(new ItemStack(t, n)); // swap stacks
                    turret.setFuel(c, amt);
                }
            }
        } else if (ct == ClickType.RIGHT) {
            if (cursorEmpty) {
                if (n > 0) {
                    int half = (n + 1) / 2;
                    event.getWhoClicked().setItemOnCursor(new ItemStack(t, half));
                    turret.setFuel(n - half > 0 ? t : null, n - half);
                }
            } else if (FuelTable.isFuel(cursor)) {
                Material c = cursor.getType();
                int amt = cursor.getAmount();
                if (n == 0) {
                    turret.setFuel(c, 1);
                    setCursorAmount(event, cursor, amt - 1);
                } else if (c == t && n < cap(t)) {
                    turret.setFuel(t, n + 1);
                    setCursorAmount(event, cursor, amt - 1);
                }
            }
        }
        // other click types (number key, middle, etc.) are ignored (already cancelled)

        menu.render(turret);
        click(player);
    }

    /** Deposit a fuel stack from the player inventory into the reserve (in place). */
    private void depositFromStack(Turret turret, ItemStack stack) {
        Material c = stack.getType();
        int amt = stack.getAmount();
        Material t = turret.fuelType();
        int n = turret.fuelCount();
        if (n == 0) {
            int move = Math.min(amt, cap(c));
            turret.setFuel(c, move);
            stack.setAmount(amt - move);
        } else if (c == t) {
            int move = Math.min(cap(t) - n, amt);
            if (move > 0) {
                turret.setFuel(t, n + move);
                stack.setAmount(amt - move);
            }
        }
        // different fuel already loaded -> leave the player's stack alone
    }

    /** Per-material reserve cap, like a furnace fuel slot (1 lava bucket, 64 coal). */
    private static int cap(Material m) {
        return Math.min(TurretMenu.MAX_FUEL, m.getMaxStackSize());
    }

    private static void setCursorAmount(InventoryClickEvent event, ItemStack cursor, int amount) {
        if (amount <= 0) {
            event.getWhoClicked().setItemOnCursor(null);
        } else {
            cursor.setAmount(amount);
            event.getWhoClicked().setItemOnCursor(cursor);
        }
    }

    private static void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);
    }
}
