package me.bibo.militarycraft.vehicles.tank.listeners;

import me.bibo.militarycraft.vehicles.tank.TankRuntime;
import me.bibo.militarycraft.vehicles.tank.tank.Tank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Enter (right-click), exit (sneak/dismount), and fire (left-click). */
public final class InteractionListener implements Listener {

    private final TankRuntime plugin;

    public InteractionListener(TankRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClickTank(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Tank tank = plugin.tanks().byEntity(event.getRightClicked());
        if (tank == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (plugin.tanks().byDriver(player.getUniqueId()) != null) {
            return;
        }
        if (!player.hasPermission("tankcraft.drive")) {
            player.sendActionBar(Component.text("You do not have permission to operate tanks", NamedTextColor.RED));
            return;
        }
        if (!plugin.tanks().enter(tank, player)) {
            player.sendActionBar(Component.text("Tank is occupied", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.tanks().handleDismount(player);
        }
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        Tank tank = plugin.tanks().byDriver(player.getUniqueId());
        if (tank != null) {
            plugin.tanks().shells().fire(tank);
        } else {
            plugin.tanks().meleeFromPlayer(player);
        }
    }

    /**
     * While driving, the mouse controls the tank: block every world interaction so
     * right-click does nothing (no item use, no placing). Left-click still fires the
     * gun via {@link PlayerAnimationEvent}, which is a separate event we don't touch.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDriverInteract(PlayerInteractEvent event) {
        if (plugin.tanks().byDriver(event.getPlayer().getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.tanks().handleDismount(event.getPlayer());
    }
}
