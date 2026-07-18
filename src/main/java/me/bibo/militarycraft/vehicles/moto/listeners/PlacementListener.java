package me.bibo.militarycraft.vehicles.moto.listeners;

import me.bibo.militarycraft.vehicles.moto.MotoRuntime;
import me.bibo.militarycraft.vehicles.moto.items.MotorcycleItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Level;

/** Places a motorcycle without pre-cancelling protection plugins' interact event. */
public final class PlacementListener implements Listener {

    private final MotoRuntime plugin;

    public PlacementListener(MotoRuntime plugin) {
        this.plugin = plugin;
    }

    /**
     * MONITOR only observes and queues work; it does not mutate the event. The
     * next-tick task sees the final cancellation state, including another MONITOR
     * listener registered later. Netherite scrap has no vanilla placement action.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || !MotorcycleItem.isMotorcycleItem(event.getItem())
                || event.getBlockFace() != BlockFace.UP
                || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        Location interactionPoint = event.getInteractionPoint();
        double yaw = player.getLocation().getYaw();

        plugin.getServer().getScheduler().runTask(plugin.bukkitPlugin(), () -> {
            if (event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY
                    || event.useItemInHand() == org.bukkit.event.Event.Result.DENY
                    || !player.isOnline()) {
                return;
            }
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!MotorcycleItem.isMotorcycleItem(held)) {
                return; // item was moved/consumed during the intervening tick
            }
            attemptPlacement(player, clicked, interactionPoint, yaw, held);
        });
    }

    private void attemptPlacement(Player player, Block clicked, Location interactionPoint,
                                  double yaw, ItemStack held) {
        if (!player.hasPermission("motocraft.place")) {
            error(player, "You do not have motocraft.place.");
            return;
        }
        if (plugin.motorcycles().byDriver(player.getUniqueId()) != null
                || plugin.motorcycles().byPassenger(player.getUniqueId()) != null) {
            error(player, "You cannot place vehicles while riding one.");
            return;
        }
        if (player.getWorld() != clicked.getWorld()
                || player.getLocation().distanceSquared(clicked.getLocation().add(0.5, 0.5, 0.5)) > 64.0) {
            return; // teleported or moved too far before the deferred placement
        }

        double groundY = interactionPoint == null ? Double.NaN : interactionPoint.getY();
        if (!Double.isFinite(groundY)
                || groundY < clicked.getY() - 0.01 || groundY > clicked.getY() + 1.01) {
            groundY = clicked.getBoundingBox().getMaxY();
        }
        if (!Double.isFinite(groundY) || groundY <= clicked.getY()) {
            groundY = clicked.getY() + 1.0;
        }
        Location at = new Location(clicked.getWorld(), clicked.getX() + 0.5,
                groundY, clicked.getZ() + 0.5);
        boolean withSidecar = MotorcycleItem.hasSidecar(held);
        String denial = plugin.motorcycles().validateCreate(player, at, yaw, withSidecar);
        if (denial != null) {
            error(player, denial);
            return;
        }

        try {
            plugin.motorcycles().create(at, yaw, player.getUniqueId(), withSidecar);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Motorcycle placement failed", failure);
            error(player, "Could not create motorcycle; the error was written to console.");
            return;
        }
        plugin.motorcycles().recordCreate(player);
        clicked.getWorld().playSound(at, Sound.ENTITY_MINECART_RIDING, 0.8f, 0.7f);

        if (plugin.config().consumeItem && player.getGameMode() != GameMode.CREATIVE) {
            held.subtract(1);
        }
    }

    private static void error(Player player, String message) {
        player.sendActionBar(Component.text(message, NamedTextColor.RED));
    }
}
