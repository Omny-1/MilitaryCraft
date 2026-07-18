package me.bibo.militarycraft.vehicles.tank.listeners;

import me.bibo.militarycraft.vehicles.tank.TankRuntime;
import me.bibo.militarycraft.vehicles.tank.items.TankItem;
import me.bibo.militarycraft.vehicles.tank.tank.TankCollision;
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

/** Spawns a tank when a player right-clicks the ground with the placer item. */
public final class PlacementListener implements Listener {

    private final TankRuntime plugin;

    public PlacementListener(TankRuntime plugin) {
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
        if (!TankItem.isTankItem(item)) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (plugin.tanks().byDriver(player.getUniqueId()) != null) {
            return; // no placing tanks while driving one
        }
        if (!player.hasPermission("tankcraft.place")) {
            player.sendActionBar(Component.text("You do not have permission to place tanks", NamedTextColor.RED));
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        double yaw = player.getLocation().getYaw();
        Location at = TankCollision.anchorOnTop(clicked);

        plugin.tanks().create(at, yaw);
        player.getWorld().playSound(at, org.bukkit.Sound.BLOCK_ANVIL_PLACE, 1.0f, 0.8f);

        if (plugin.config().consumeItem && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }
}
