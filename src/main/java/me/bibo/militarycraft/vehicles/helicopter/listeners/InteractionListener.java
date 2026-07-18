package me.bibo.militarycraft.vehicles.helicopter.listeners;

import me.bibo.militarycraft.vehicles.helicopter.HelicopterRuntime;
import me.bibo.militarycraft.vehicles.helicopter.helicopter.Helicopter;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Enter (right-click the cabin), exit (sneak/dismount), fire rockets
 * (left-click / arm-swing), and drop bombs (right-click while flying).
 */
public final class InteractionListener implements Listener {

    private final HelicopterRuntime plugin;

    public InteractionListener(HelicopterRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClickShip(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Helicopter heli = plugin.helicopters().byEntity(event.getRightClicked());
        if (heli == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        // The pilot sits inside this helicopter's hitbox, so their own right-click
        // lands here too. That isn't re-entry — it's a bomb release.
        if (plugin.helicopters().byDriver(player.getUniqueId()) == heli) {
            plugin.helicopters().weapons().dropBomb(heli);
            return;
        }
        // A passenger (non-pilot) right-clicking: ignore — they don't re-board
        // and they don't control the weapons.
        if (heli.hasRider(player.getUniqueId())) {
            return;
        }
        if (!player.hasPermission("helicraft.use")) {
            player.sendActionBar(Component.text("You do not have permission to operate helicopters", NamedTextColor.RED));
            return;
        }
        if (!plugin.helicopters().enter(heli, player)) {
            player.sendActionBar(Component.text("Helicopter is occupied (no seats)", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.helicopters().handleDismount(player);
        }
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        Helicopter heli = plugin.helicopters().byDriver(player.getUniqueId());
        if (heli != null) {
            plugin.helicopters().weapons().fireRocket(heli);
        } else {
            plugin.helicopters().meleeFromPlayer(player);
        }
    }

    /**
     * While flying, the mouse steers the helicopter, so block every world
     * interaction (no item use / placing) and repurpose right-click to drop a
     * bomb. Left-click fires a rocket via {@link PlayerAnimationEvent}, a
     * separate event we don't cancel.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRiderInteract(PlayerInteractEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        Helicopter heli = plugin.helicopters().byRider(uuid);
        if (heli == null) {
            return;
        }
        // Block all world interaction for anyone aboard (no item use / placing
        // while riding); the pilot's right-click is repurposed to drop a bomb.
        event.setCancelled(true);
        if (heli.isDriver(uuid)) {
            switch (event.getAction()) {
                case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> plugin.helicopters().weapons().dropBomb(heli);
                default -> {
                }
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin.bukkitPlugin(),
                () -> plugin.helicopters().refreshCloakFor(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.helicopters().handleDismount(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.helicopters().handleDismount(event.getPlayer());
    }
}
