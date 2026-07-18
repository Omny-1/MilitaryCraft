package me.bibo.militarycraft.gear.warkit.listener;

import org.bukkit.GameMode;
import me.bibo.militarycraft.gear.warkit.WarKitRuntime;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.projectiles.ProjectileSource;

/** Breaks camouflage, interrupts channeling on player actions, and cleans up player state. */
public final class StateListener implements Listener {

    private final WarKitRuntime plugin;

    public StateListener(WarKitRuntime plugin) {
        this.plugin = plugin;
    }

    // ---- movement (hot event, so exit as early as possible) ----

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!plugin.camo().anyDisguised()) return;
        if (!e.hasChangedBlock()) return;
        plugin.camo().breakDisguise(e.getPlayer(), "Movement broke camouflage");
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        plugin.camo().breakDisguise(e.getPlayer(), "Camouflage removed");
        plugin.channels().interrupt(e.getPlayer(), "Teleport interrupted the action");
        plugin.guns().cancelReload(e.getPlayer());
        plugin.trench().cancel(e.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        plugin.camo().breakDisguise(e.getPlayer(), "Camouflage removed");
    }

    // ---- item changes and drops ----

    @EventHandler
    public void onHeldChange(PlayerItemHeldEvent e) {
        plugin.channels().interrupt(e.getPlayer(), "Item swap interrupted the action");
        plugin.guns().cancelReload(e.getPlayer());
        plugin.trench().cancel(e.getPlayer());
        plugin.camo().breakDisguise(e.getPlayer(), "Camouflage removed");
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        plugin.channels().interrupt(e.getPlayer(), "Item swap interrupted the action");
        plugin.camo().breakDisguise(e.getPlayer(), "Camouflage removed");
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        plugin.channels().interrupt(e.getPlayer(), "Action cancelled");
        plugin.guns().cancelReload(e.getPlayer());
        plugin.trench().cancel(e.getPlayer());
        plugin.camo().breakDisguise(e.getPlayer(), "Camouflage removed");
    }

    // ---- inventory ----

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            plugin.channels().interrupt(p, "Action cancelled");
            plugin.guns().cancelReload(p);
            plugin.trench().cancel(p);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p) {
            plugin.camo().breakDisguise(p, "Camouflage removed");
        }
    }

    // ---- shooting and throws ----

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        ProjectileSource shooter = e.getEntity().getShooter();
        if (shooter instanceof Player p) {
            plugin.camo().breakDisguise(p, "Shot broke camouflage");
            plugin.channels().interrupt(p, "Action cancelled");
        }
    }

    // ---- lifecycle ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        plugin.channels().cancelSilent(p);
        plugin.guns().cancelReload(p);
        plugin.trench().cancel(p);
        plugin.camo().deactivate(p, false, null);
        plugin.painkiller().clear(p.getUniqueId());
        plugin.fallImmunity().clear(p.getUniqueId());
        plugin.marker().onDeath(p.getUniqueId());
        plugin.deployables().onPlayerGone(p.getUniqueId());
        plugin.gadgets().clear(p.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.channels().cancelSilent(p);
        plugin.guns().cancelReload(p);
        plugin.trench().cancel(p);
        plugin.camo().deactivate(p, false, null);
        plugin.painkiller().clear(p.getUniqueId());
        plugin.fallImmunity().clear(p.getUniqueId());
        plugin.deployables().onPlayerGone(p.getUniqueId());
        plugin.gadgets().clear(p.getUniqueId());
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent e) {
        if (e.getNewGameMode() != GameMode.SPECTATOR) return;
        Player p = e.getPlayer();
        plugin.channels().cancelSilent(p);
        plugin.guns().cancelReload(p);
        plugin.trench().cancel(p);
        plugin.camo().deactivate(p, false, null);
        plugin.fallImmunity().clear(p.getUniqueId());
        plugin.deployables().onPlayerGone(p.getUniqueId());
        plugin.gadgets().clear(p.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.camo().hideAllFrom(e.getPlayer());
    }
}
