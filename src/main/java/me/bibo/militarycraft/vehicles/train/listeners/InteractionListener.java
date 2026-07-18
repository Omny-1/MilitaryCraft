package me.bibo.militarycraft.vehicles.train.listeners;

import me.bibo.militarycraft.vehicles.train.TrainRuntime;
import me.bibo.militarycraft.vehicles.train.items.TrainItem;
import me.bibo.militarycraft.vehicles.train.train.Train;
import me.bibo.militarycraft.vehicles.train.train.TrainCar;
import me.bibo.militarycraft.vehicles.train.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Boarding (right-click a passing car — a small test of reflexes, the train
 * won't wait), removal (sneak + right-click with the train item), and getting
 * off (sneak while riding).
 */
public final class InteractionListener implements Listener {

    private final TrainRuntime plugin;
    private final Map<UUID, Long> boardMsgCooldown = new HashMap<>();

    public InteractionListener(TrainRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity clicked = event.getRightClicked();
        Train train = plugin.trains().byEntity(clicked);
        if (train == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();

        // Sneak + the train item = pack the whole train away (refund the item).
        if (player.isSneaking() && TrainItem.is(player.getInventory().getItemInMainHand())) {
            if (!player.hasPermission("traincraft.place")) {
                player.sendActionBar(Component.text("You do not have permission to remove trains", NamedTextColor.RED));
                return;
            }
            plugin.trains().removeTrain(train);
            if (plugin.cfg().consumeItem && player.getGameMode() != GameMode.CREATIVE) {
                var leftovers = player.getInventory().addItem(TrainItem.create());
                leftovers.values().forEach(it ->
                        player.getWorld().dropItemNaturally(player.getLocation(), it));
            }
            player.sendActionBar(Component.text("Train removed", NamedTextColor.YELLOW));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);
            return;
        }

        if (!player.hasPermission("traincraft.use")) {
            player.sendActionBar(Component.text("You do not have permission to ride trains", NamedTextColor.RED));
            return;
        }
        if (player.getVehicle() != null) {
            return; // already riding something
        }

        Integer carIndex = clicked.getPersistentDataContainer()
                .get(Keys.CAR_INDEX, PersistentDataType.INTEGER);
        TrainCar car = carIndex == null ? null : train.car(carIndex);
        if (car == null) {
            return;
        }
        if (car.board(player)) {
            player.sendActionBar(Component.text(
                    carIndex == 0 ? "🚂 You hopped into the driver's cab!" : "🚃 You hopped into a carriage!",
                    NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_STEP, 1f, 0.7f);
        } else {
            long now = System.currentTimeMillis();
            Long last = boardMsgCooldown.get(player.getUniqueId());
            if (last == null || now - last > 1000) {
                boardMsgCooldown.put(player.getUniqueId(), now);
                player.sendActionBar(Component.text("This car is full - try another carriage!", NamedTextColor.RED));
            }
        }
    }

    /** The train is indestructible; a swing with the train item removes it too. */
    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        Train train = plugin.trains().byEntity(event.getEntity());
        if (train == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) {
            player.playSound(event.getEntity().getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.6f);
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Entity seat = event.getDismounted();
        if (seat.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
            plugin.trains().handleSeatDismount(player, seat);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.trains().handleQuit(event.getPlayer());
        boardMsgCooldown.remove(event.getPlayer().getUniqueId());
    }
}
