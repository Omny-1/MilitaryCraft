package me.bibo.militarycraft.vehicles.pickup.listeners;

import me.bibo.militarycraft.vehicles.pickup.PickupRuntime;
import me.bibo.militarycraft.vehicles.pickup.combat.GunManager;
import me.bibo.militarycraft.vehicles.pickup.util.Keys;
import me.bibo.militarycraft.vehicles.pickup.vehicle.Pickup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Entity;
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
import org.bukkit.persistence.PersistentDataType;

/**
 * Boarding, leaving and using the pickup: which seat a right-click means, who is allowed in it, and
 * what happens when a rider dismounts, swings, or disconnects while aboard.
 */
public final class InteractionListener
implements Listener {
    private final PickupRuntime plugin;

    public InteractionListener(PickupRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClickPickup(PlayerInteractEntityEvent event) {
        int zone;
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Pickup pickup = this.plugin.pickups().byEntity(event.getRightClicked());
        if (pickup == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (this.plugin.pickups().isCrew(player.getUniqueId())) {
            return;
        }
        Integer partIndex = (Integer)event.getRightClicked().getPersistentDataContainer().get(Keys.PART_INDEX, PersistentDataType.INTEGER);
        int n = zone = partIndex != null ? partIndex : 0;
        if (zone == 1) {
            if (!player.hasPermission("pickupcraft.gun")) {
                player.sendActionBar(Component.text("You do not have permission to use the machine gun", NamedTextColor.RED));
                return;
            }
            if (!this.plugin.pickups().enterGunner(pickup, player)) {
                player.sendActionBar(Component.text("The gunner seat is occupied", NamedTextColor.RED));
            }
            return;
        }
        if (zone == 2 && pickup.isDriverSeatOccupied()) {
            if (!player.hasPermission("pickupcraft.passenger")) {
                player.sendActionBar(Component.text("You do not have permission to ride as a passenger", NamedTextColor.RED));
                return;
            }
            if (!this.plugin.pickups().enterPassenger(pickup, player)) {
                player.sendActionBar(Component.text("The passenger seat is occupied", NamedTextColor.RED));
            }
            return;
        }
        if (!player.hasPermission("pickupcraft.drive")) {
            player.sendActionBar(Component.text("You do not have permission to drive the pickup", NamedTextColor.RED));
            return;
        }
        if (!this.plugin.pickups().enterDriver(pickup, player)) {
            player.sendActionBar(Component.text("The driver's seat is occupied", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            Player player = (Player)entity;
            this.plugin.pickups().handleDismount(player);
        }
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        Pickup gunnerPickup = this.plugin.pickups().byGunner(player.getUniqueId());
        if (gunnerPickup != null) {
            GunManager.fire(this.plugin, gunnerPickup, player);
            return;
        }
        if (this.plugin.pickups().byDriver(player.getUniqueId()) != null) {
            return;
        }
        this.plugin.pickups().meleeFromPlayer(player);
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void onCrewInteract(PlayerInteractEvent event) {
        if (this.plugin.pickups().isCrew(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.plugin.pickups().handleDismount(event.getPlayer());
    }
}
