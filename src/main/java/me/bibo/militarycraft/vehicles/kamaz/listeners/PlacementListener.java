package me.bibo.militarycraft.vehicles.kamaz.listeners;

import me.bibo.militarycraft.vehicles.kamaz.KamazRuntime;
import me.bibo.militarycraft.vehicles.kamaz.items.TruckItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Spawns a Kamaz when a player right-clicks the ground with the placer item. */
public final class PlacementListener implements Listener {

    private final KamazRuntime plugin;

    public PlacementListener(KamazRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = false)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!TruckItem.isTruckItem(item)) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (plugin.trucks().byDriver(player.getUniqueId()) != null
                || plugin.trucks().byPassenger(player.getUniqueId()) != null) {
            return; // no placing while driving one
        }
        if (!player.hasPermission("kamazcraft.place")) {
            player.sendActionBar(Component.text("You do not have permission to place Kamaz trucks", NamedTextColor.RED));
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        Block above = clicked.getRelative(0, 1, 0);
        if (!above.isPassable()) {
            player.sendActionBar(Component.text("Not enough space above the block", NamedTextColor.RED));
            return;
        }

        Location at = new Location(clicked.getWorld(),
                clicked.getX() + 0.5, clicked.getY() + 1.0, clicked.getZ() + 0.5);
        double yaw = player.getLocation().getYaw();
        String deny = plugin.trucks().validateCreate(player, at, yaw);
        if (deny != null) {
            player.sendActionBar(Component.text(deny, NamedTextColor.RED));
            return;
        }
        plugin.trucks().create(at, yaw, player.getUniqueId());
        plugin.trucks().recordCreate(player);
        player.getWorld().playSound(at, org.bukkit.Sound.BLOCK_ANVIL_PLACE, 1.0f, 0.7f);

        if (plugin.config().consumeItem && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }
}
