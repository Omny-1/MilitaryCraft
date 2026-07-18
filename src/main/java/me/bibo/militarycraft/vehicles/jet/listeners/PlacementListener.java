package me.bibo.militarycraft.vehicles.jet.listeners;

import me.bibo.militarycraft.vehicles.jet.JetRuntime;
import me.bibo.militarycraft.vehicles.jet.items.JetItem;
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

/** Spawns a jet when a player right-clicks the ground with the placer item. */
public final class PlacementListener implements Listener {

    private final JetRuntime plugin;

    public PlacementListener(JetRuntime plugin) {
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
        if (!JetItem.isJetItem(item)) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (plugin.jets().byDriver(player.getUniqueId()) != null) {
            return; // no placing jets while flying one
        }
        if (!player.hasPermission("jetcraft.place")) {
            player.sendActionBar(Component.text("You do not have permission to place fighters", NamedTextColor.RED));
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        Location at = new Location(clicked.getWorld(),
                clicked.getX() + 0.5, clicked.getY() + 1.4, clicked.getZ() + 0.5);
        double yaw = player.getLocation().getYaw();
        plugin.jets().create(at, yaw);
        player.getWorld().playSound(at, org.bukkit.Sound.BLOCK_ANVIL_PLACE, 1.0f, 0.8f);

        if (plugin.config().consumeItem && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }
}
