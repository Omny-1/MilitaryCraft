package me.bibo.militarycraft.vehicles.kamaz.listeners;

import me.bibo.militarycraft.vehicles.kamaz.KamazRuntime;
import me.bibo.militarycraft.vehicles.kamaz.truck.Truck;
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

/** Enter (right-click), exit (sneak/dismount), and hit-the-truck (left-click). */
public final class InteractionListener implements Listener {

    private final KamazRuntime plugin;

    public InteractionListener(KamazRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClickTruck(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Truck truck = plugin.trucks().byEntity(event.getRightClicked());
        if (truck == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("kamazcraft.use")) {
            player.sendActionBar(Component.text("You do not have permission to operate Kamaz trucks", NamedTextColor.RED));
            return;
        }
        if (!plugin.trucks().enter(truck, player)) {
            player.sendActionBar(Component.text("No seats available in the Kamaz", NamedTextColor.RED));
        } else if (plugin.trucks().byPassenger(player.getUniqueId()) != null) {
            player.sendActionBar(Component.text("🚛 Riding as a passenger in \"Pushinka\"", NamedTextColor.GREEN));
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.trucks().handleDismount(player);
        }
    }

    /** A non-driver's swing at the truck damages it; the driver's swing does nothing. */
    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.trucks().byDriver(player.getUniqueId()) == null
                && plugin.trucks().byPassenger(player.getUniqueId()) == null) {
            plugin.trucks().meleeFromPlayer(player);
        }
    }

    /**
     * While driving, the mouse controls the truck: block every world interaction so
     * right-click does nothing (no item use, no placing).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDriverInteract(PlayerInteractEvent event) {
        if (plugin.trucks().byDriver(event.getPlayer().getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.trucks().handleDismount(event.getPlayer());
    }
}
