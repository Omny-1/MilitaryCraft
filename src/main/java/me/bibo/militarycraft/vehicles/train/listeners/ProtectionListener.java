package me.bibo.militarycraft.vehicles.train.listeners;

import me.bibo.militarycraft.vehicles.train.TrainRuntime;
import me.bibo.militarycraft.vehicles.train.util.Keys;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Riders don't suffocate inside the model and never take fall damage when
 * hopping off a moving train.
 */
public final class ProtectionListener implements Listener {

    private final TrainRuntime plugin;

    public ProtectionListener(TrainRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.FALL
                && plugin.trains().isFallProtected(player)) {
            event.setCancelled(true);
            return;
        }
        Entity vehicle = player.getVehicle();
        if (vehicle != null && vehicle.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)
                && (cause == EntityDamageEvent.DamageCause.SUFFOCATION
                || cause == EntityDamageEvent.DamageCause.FALL
                || cause == EntityDamageEvent.DamageCause.CONTACT
                || cause == EntityDamageEvent.DamageCause.FLY_INTO_WALL)) {
            event.setCancelled(true);
        }
    }
}
