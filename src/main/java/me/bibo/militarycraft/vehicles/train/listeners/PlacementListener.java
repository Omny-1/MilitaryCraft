package me.bibo.militarycraft.vehicles.train.listeners;

import me.bibo.militarycraft.vehicles.train.TrainRuntime;
import me.bibo.militarycraft.vehicles.train.items.TrainItem;
import me.bibo.militarycraft.vehicles.train.rail.RailTracer;
import me.bibo.militarycraft.vehicles.train.train.Train;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Right-click a rail with the train item: a whole express appears, already moving. */
public final class PlacementListener implements Listener {

    private final TrainRuntime plugin;

    public PlacementListener(TrainRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (!TrainItem.is(item)) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // Never let the furnace-minecart place a real minecart.
        event.setCancelled(true);

        Block block = event.getClickedBlock();
        if (block == null || !RailTracer.isRail(block)) {
            player.sendActionBar(Component.text("Train can only be placed on rails", NamedTextColor.RED));
            return;
        }
        if (!player.hasPermission("traincraft.place")) {
            player.sendActionBar(Component.text("You do not have permission to place trains", NamedTextColor.RED));
            return;
        }

        Train train = plugin.trains().spawn(block, player);
        if (train == null) {
            return; // limit message already sent
        }
        if (plugin.cfg().consumeItem && player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
        player.sendActionBar(Component.text("🚂 Desert Express is departing!", NamedTextColor.GOLD));
    }
}
