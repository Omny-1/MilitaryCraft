package me.bibo.militarycraft.vehicles.airship.listeners;

import me.bibo.militarycraft.vehicles.airship.AirshipRuntime;
import me.bibo.militarycraft.vehicles.airship.items.AirshipItem;
import me.bibo.militarycraft.vehicles.airship.model.AirshipModel;
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

/** Spawns an airship when a player right-clicks the ground with the placer item. */
public final class PlacementListener implements Listener {

    private final AirshipRuntime plugin;

    public PlacementListener(AirshipRuntime plugin) {
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
        if (!AirshipItem.isAirshipItem(item)) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (plugin.airships().byRider(player.getUniqueId()) != null) {
            return; // no placing while aboard one
        }
        if (!player.hasPermission("airshipcraft.place")) {
            player.sendActionBar(Component.text("You do not have permission to place airships", NamedTextColor.RED));
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

        if (overLimit(player)) {
            return;
        }

        double h = plugin.config().spawnHeight;
        Location at = new Location(clicked.getWorld(),
                clicked.getX() + 0.5, clicked.getY() + 1 + h, clicked.getZ() + 0.5);
        double yaw = player.getLocation().getYaw();
        plugin.airships().create(at, yaw, player.getUniqueId());
        player.getWorld().playSound(at, org.bukkit.Sound.BLOCK_CHAIN_PLACE, 1.2f, 0.7f);
        player.getWorld().playSound(at, org.bukkit.Sound.BLOCK_ANVIL_PLACE, 0.8f, 0.6f);

        if (plugin.config().consumeItem && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    /** Enforce per-player and global airship caps; messages the player if blocked. */
    private boolean overLimit(Player player) {
        int maxPer = plugin.config().maxPerPlayer;
        if (maxPer > 0 && !player.hasPermission("airshipcraft.admin")
                && plugin.airships().countByOwner(player.getUniqueId()) >= maxPer) {
            player.sendActionBar(Component.text("Airship limit per player: " + maxPer, NamedTextColor.RED));
            return true;
        }
        int maxTotal = plugin.config().maxLoaded;
        if (maxTotal > 0 && plugin.airships().count() >= maxTotal) {
            player.sendActionBar(Component.text("Global airship limit reached", NamedTextColor.RED));
            return true;
        }
        return false;
    }
}
