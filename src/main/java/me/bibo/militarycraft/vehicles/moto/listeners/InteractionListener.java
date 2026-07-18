package me.bibo.militarycraft.vehicles.moto.listeners;

import me.bibo.militarycraft.vehicles.moto.MotoRuntime;
import me.bibo.militarycraft.vehicles.moto.motorcycle.Motorcycle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Mounting, dismounting and deliberate melee interaction with a motorcycle. */
public final class InteractionListener implements Listener {

    private final MotoRuntime plugin;

    public InteractionListener(MotoRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRightClickMotorcycle(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Motorcycle motorcycle = plugin.motorcycles().byEntity(event.getRightClicked());
        if (motorcycle == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("motocraft.use")) {
            player.sendActionBar(Component.text("You do not have motocraft.use.", NamedTextColor.RED));
            return;
        }
        if (!plugin.motorcycles().enter(motorcycle, player)) {
            player.sendActionBar(Component.text("All three seats are occupied or boarding is impossible.",
                    NamedTextColor.RED));
            return;
        }
        if (plugin.motorcycles().byDriver(player.getUniqueId()) == motorcycle) {
            player.sendActionBar(Component.text("W/S - throttle and brake, A/D - steer, Shift - exit.",
                    NamedTextColor.GREEN));
        } else {
            player.sendActionBar(Component.text("You are riding as a passenger. Shift - exit.",
                    NamedTextColor.GREEN));
        }
    }

    /**
     * Dismount is cancellable. Reconcile on the next tick, after every plugin has
     * had a chance to cancel it and Bukkit has applied the final vehicle state.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!plugin.bukkitPlugin().isEnabled()) {
            plugin.motorcycles().handleDismount(player);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin.bukkitPlugin(), () -> {
            if (player.getVehicle() == null) {
                plugin.motorcycles().handleDismount(player);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.motorcycles().byDriver(player.getUniqueId()) == null
                && plugin.motorcycles().byPassenger(player.getUniqueId()) == null) {
            plugin.motorcycles().meleeFromPlayer(player);
        }
    }

    /** Prevent drivers from placing/using items accidentally while steering. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDriverInteract(PlayerInteractEvent event) {
        if (plugin.motorcycles().byDriver(event.getPlayer().getUniqueId()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.motorcycles().handleDismount(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.motorcycles().handleDismount(event.getEntity());
    }
}
