package me.bibo.militarycraft.weapons.tckbus;

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

/** Spawns a TckBusRig + its workers when a player right-clicks the ground with a summons. */
public final class TckBusPlacementListener implements Listener {

    private final TckBusRuntime plugin;

    public TckBusPlacementListener(TckBusRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = false)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!TckBusItem.isBusItem(item)) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.hasPermission("tckbus.place")) {
            player.sendActionBar(Component.text("You do not have permission to place TCK Buses", NamedTextColor.RED));
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        if (!clicked.getRelative(0, 1, 0).isPassable()) {
            player.sendActionBar(Component.text("Not enough space above the block", NamedTextColor.RED));
            return;
        }
        if (overLimit(player)) {
            return;
        }

        double yaw = player.getLocation().getYaw();
        if (plugin.config().yawSnap) {
            yaw = Math.round(yaw / 90.0) * 90.0;
        }
        Location at = new Location(clicked.getWorld(),
                clicked.getX() + 0.5, clicked.getY() + 1.0, clicked.getZ() + 0.5);
        TckBusSettings.Skin skin = plugin.config().skin(TckBusItem.skinId(item));
        plugin.buses().create(at, yaw, player.getUniqueId(), skin.id);

        player.getWorld().playSound(at, Sound.BLOCK_NETHERITE_BLOCK_PLACE, 1.0f, 0.8f);
        player.getWorld().playSound(at, Sound.ENTITY_VILLAGER_AMBIENT, 1.0f, 0.7f);
        player.sendActionBar(Component.text(skin.busName + " has arrived. Passers-by, beware!", NamedTextColor.GREEN));

        if (plugin.config().consumeItem && player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    private boolean overLimit(Player player) {
        int maxPer = plugin.config().maxPerPlayer;
        if (maxPer > 0 && !player.hasPermission("tckbus.admin")
                && plugin.buses().countByOwner(player.getUniqueId()) >= maxPer) {
            player.sendActionBar(Component.text("TCK Bus limit per player: " + maxPer, NamedTextColor.RED));
            return true;
        }
        int maxTotal = plugin.config().maxLoaded;
        if (maxTotal > 0 && plugin.buses().count() >= maxTotal) {
            player.sendActionBar(Component.text("Global TCK Bus limit reached", NamedTextColor.RED));
            return true;
        }
        return false;
    }
}


