package me.bibo.militarycraft.weapons.antiair.listeners;

import me.bibo.militarycraft.weapons.antiair.AntiAirRuntime;
import me.bibo.militarycraft.weapons.antiair.items.TurretItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Places a turret when a player right-clicks the ground with the placer item. */
public final class PlacementListener implements Listener {

    private final AntiAirRuntime plugin;

    public PlacementListener(AntiAirRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = false)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!TurretItem.isTurretItem(item)) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.hasPermission("antiaircraft.place")) {
            player.sendActionBar(Component.text("You do not have permission to place Anti-Air turrets", NamedTextColor.RED));
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

        Location at = new Location(clicked.getWorld(),
                clicked.getX() + 0.5, clicked.getY() + 1.0, clicked.getZ() + 0.5);
        plugin.turrets().create(at, player.getLocation().getYaw(), player.getUniqueId());
        player.getWorld().playSound(at, Sound.BLOCK_ANVIL_PLACE, 1.0f, 0.7f);
        player.getWorld().playSound(at, Sound.BLOCK_NETHERITE_BLOCK_PLACE, 1.0f, 0.9f);
        player.sendActionBar(Component.text("Anti-Air placed. Right-click the turret to open its panel.", NamedTextColor.GREEN));

        if (plugin.config().consumeItem && player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    private boolean overLimit(Player player) {
        int maxPer = plugin.config().maxPerPlayer;
        if (maxPer > 0 && !player.hasPermission("antiaircraft.admin")
                && plugin.turrets().countByOwner(player.getUniqueId()) >= maxPer) {
            player.sendActionBar(Component.text("Anti-Air limit per player: " + maxPer, NamedTextColor.RED));
            return true;
        }
        int maxTotal = plugin.config().maxLoaded;
        if (maxTotal > 0 && plugin.turrets().count() >= maxTotal) {
            player.sendActionBar(Component.text("Global Anti-Air limit reached", NamedTextColor.RED));
            return true;
        }
        return false;
    }
}
