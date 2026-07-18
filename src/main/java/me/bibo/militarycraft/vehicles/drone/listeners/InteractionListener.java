package me.bibo.militarycraft.vehicles.drone.listeners;

import me.bibo.militarycraft.vehicles.drone.DroneRuntime;
import me.bibo.militarycraft.vehicles.drone.drone.Drone;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Take control (right-click the UAV), fire rockets (right-click while flying),
 * detonate (left-click), and exit (double-tap Shift — the UAV then flies on).
 */
public final class InteractionListener implements Listener {

    private final DroneRuntime plugin;
    private final Map<UUID, Long> lastShift = new HashMap<>();

    public InteractionListener(DroneRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClickDrone(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Drone drone = plugin.drones().byEntity(event.getRightClicked());
        if (drone == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        // The operator sits inside this UAV's hitbox, so their own right-click lands
        // here too — that's a rocket shot, not a re-entry.
        if (plugin.drones().byDriver(player.getUniqueId()) == drone) {
            plugin.drones().fireRocket(drone);
            return;
        }
        if (!player.hasPermission("dronecraft.use")) {
            player.sendActionBar(Component.text("You do not have permission to operate UAVs", NamedTextColor.RED));
            return;
        }
        if (!plugin.drones().enter(drone, player)) {
            player.sendActionBar(Component.text("UAV is occupied", NamedTextColor.RED));
        }
    }

    /**
     * While flying: right-click fires a rocket, left-click detonates. We use the
     * interact action (not the arm-swing animation) because the client also swings
     * on right-click, which used to make right-click blow the UAV up like left-click.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onDriverInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Drone drone = plugin.drones().byDriver(player.getUniqueId());
        if (drone == null) {
            return;
        }
        event.setCancelled(true);
        switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> plugin.drones().fireRocket(drone);
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> {
                if (drone.isArmed()) {
                    plugin.drones().detonate(drone, drone.nose());
                } else {
                    player.sendActionBar(Component.text("Warhead armed...", NamedTextColor.YELLOW));
                }
            }
            default -> {
            }
        }
    }

    /** Non-drivers swinging at a nearby UAV melee it (operators' swings do nothing). */
    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.drones().byDriver(player.getUniqueId()) == null) {
            plugin.drones().meleeFromPlayer(player);
        }
    }

    /**
     * Keep the operator glued to the UAV. We cancel every dismount we didn't
     * ourselves initiate — that covers both Shift and the spurious client dismounts
     * that happen flying fast across chunk borders (which used to drop the player
     * mid-flight). A deliberate Shift (the player is sneaking) counts toward the
     * double-tap exit; the spurious ones don't.
     */
    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Drone drone = plugin.drones().byDriver(player.getUniqueId());
        if (drone == null || drone.isDismountAllowed()) {
            return; // not our rider, or an eject we triggered → let it proceed
        }
        event.setCancelled(true); // never let the game drop the operator
        if (!player.isSneaking()) {
            return; // spurious dismount (chunk transition), not a real Shift press
        }
        long now = System.currentTimeMillis();
        Long last = lastShift.get(player.getUniqueId());
        if (last != null && now - last <= plugin.config().exitDoubleTapMs) {
            lastShift.remove(player.getUniqueId());
            plugin.drones().exitControl(drone); // eject → teleport to stand; UAV flies on
        } else {
            lastShift.put(player.getUniqueId(), now);
            player.sendActionBar(Component.text("Press Shift again to exit the UAV", NamedTextColor.YELLOW));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.drones().byDriver(player.getUniqueId()) != null) {
            plugin.drones().handleDismount(player);
        }
        lastShift.remove(player.getUniqueId());
    }

    /** Safety: a player who logged out mid-flight could come back invisible / shrunk. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.drones().byDriver(player.getUniqueId()) == null) {
            if (player.isInvisible()) {
                player.setInvisible(false);
            }
            me.bibo.militarycraft.vehicles.drone.util.PlayerScale.clear(player);
        }
    }
}
