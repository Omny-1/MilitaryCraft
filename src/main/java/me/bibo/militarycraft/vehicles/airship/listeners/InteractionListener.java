package me.bibo.militarycraft.vehicles.airship.listeners;

import me.bibo.militarycraft.vehicles.airship.AirshipRuntime;
import me.bibo.militarycraft.vehicles.airship.airship.Airship;
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
 * Enter (right-click the gondola), exit (sneak/dismount), and drop bombs
 * (left-click swing or right-click while flying).
 */
public final class InteractionListener implements Listener {

    private final AirshipRuntime plugin;

    public InteractionListener(AirshipRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClickShip(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Airship ship = plugin.airships().byEntity(event.getRightClicked());
        if (ship == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        // The pilot sits inside this airship's hitbox, so their own right-click
        // lands here too. That isn't re-entry - it's a bomb release.
        if (plugin.airships().byDriver(player.getUniqueId()) == ship) {
            plugin.airships().weapons().dropBomb(ship);
            return;
        }
        // A passenger (non-pilot) right-clicking: ignore - they don't re-board
        // and they don't control the weapons.
        if (ship.hasRider(player.getUniqueId())) {
            return;
        }
        if (!player.hasPermission("airshipcraft.use")) {
            player.sendActionBar(Component.text("You do not have permission to operate airships", NamedTextColor.RED));
            return;
        }
        if (!plugin.airships().enter(ship, player)) {
            player.sendActionBar(Component.text("Airship is occupied (no seats)", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.airships().handleDismount(player);
        }
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        Airship ship = plugin.airships().byDriver(player.getUniqueId());
        if (ship != null) {
            plugin.airships().weapons().dropBomb(ship);
        } else {
            plugin.airships().meleeFromPlayer(player);
        }
    }

    /**
     * While flying, the mouse steers the airship, so block every world interaction
     * (no item use / placing) and repurpose right-click to drop a bomb. Left-click
     * fires via {@link PlayerAnimationEvent}, a separate event we don't cancel.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRiderInteract(PlayerInteractEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        Airship ship = plugin.airships().byRider(uuid);
        if (ship == null) {
            return;
        }
        // Block all world interaction for anyone aboard (no item use / placing
        // while riding); the pilot's right-click is repurposed to drop a bomb.
        event.setCancelled(true);
        if (ship.isDriver(uuid)) {
            switch (event.getAction()) {
                case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> plugin.airships().weapons().dropBomb(ship);
                default -> {
                }
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin.bukkitPlugin(),
                () -> plugin.airships().refreshCloakFor(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.airships().handleDismount(event.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.airships().handleDismount(event.getPlayer());
    }
}
