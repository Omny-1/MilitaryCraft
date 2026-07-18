package me.bibo.militarycraft.vehicles.drone.listeners;

import me.bibo.militarycraft.vehicles.drone.DroneRuntime;
import me.bibo.militarycraft.vehicles.drone.items.DroneItem;
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

/** Deploys a drone when a player right-clicks the ground with the placer item. */
public final class PlacementListener implements Listener {

    private final DroneRuntime plugin;

    public PlacementListener(DroneRuntime plugin) {
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
        if (!DroneItem.isDroneItem(item)) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (plugin.drones().byDriver(player.getUniqueId()) != null) {
            return;
        }
        if (!player.hasPermission("dronecraft.place")) {
            player.sendActionBar(Component.text("You do not have permission to launch drones", NamedTextColor.RED));
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
                clicked.getX() + 0.5, clicked.getY() + 1.2, clicked.getZ() + 0.5);
        double yaw = player.getLocation().getYaw();
        plugin.drones().create(at, yaw);
        player.getWorld().playSound(at, org.bukkit.Sound.BLOCK_DISPENSER_LAUNCH, 1.0f, 1.4f);

        if (plugin.config().consumeItem && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }
}
