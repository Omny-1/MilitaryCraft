package me.bibo.militarycraft.weapons.tckbus;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps a stunned/captured player inert and cleans up their state on quit, death
 * or world-change so nobody is ever left frozen. A held player is invulnerable
 * (the TckBusRig runs a scripted death instead), cannot act, and cannot fight back.
 */
public final class TckBusPlayerListener implements Listener {

    private final TckBusRuntime plugin;

    public TckBusPlayerListener(TckBusRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHeldDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && plugin.snatch().isHeld(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHeldAttacks(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && plugin.snatch().isHeld(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (plugin.snatch().isHeld(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.snatch().isHeld(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlaceBlock(BlockPlaceEvent event) {
        if (plugin.snatch().isHeld(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwitchSlot(PlayerItemHeldEvent event) {
        if (plugin.snatch().isHeld(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.snatch().isHeld(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.snatch().forceRelease(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        plugin.snatch().forceRelease(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        Component msg = plugin.snatch().takeDeathMessage(p.getUniqueId());
        if (msg != null) {
            event.deathMessage(msg);
        }
        plugin.snatch().forceRelease(p.getUniqueId());
    }
}


