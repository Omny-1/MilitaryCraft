package me.bibo.militarycraft.gear.warkit.weapon;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.bibo.militarycraft.gear.warkit.SpectatorBlock;
import me.bibo.militarycraft.gear.warkit.WarKitRuntime;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Handles deployed machine-gun interaction and gunner state cleanup. */
public final class DeployableListener implements Listener {

    private final WarKitRuntime plugin;

    public DeployableListener(WarKitRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteractMaxim(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (plugin.sentries().isSentryInteraction(e.getRightClicked())) {
            e.setCancelled(true);
            if (SpectatorBlock.deny(e.getPlayer())) return;
            plugin.sentries().interact(e.getPlayer(), e.getRightClicked());
            return;
        }
        if (!plugin.deployables().isMaximInteraction(e.getRightClicked())) return;
        e.setCancelled(true);
        if (SpectatorBlock.deny(e.getPlayer())) return;
        plugin.deployables().interactMaxim(e.getPlayer(), e.getRightClicked());
    }

    /** Left-click attack on a deployable damages and can break it. */
    @EventHandler
    public void onAttackMaxim(PrePlayerAttackEntityEvent e) {
        if (plugin.sentries().isSentryInteraction(e.getAttacked())) {
            e.setCancelled(true);
            if (SpectatorBlock.deny(e.getPlayer())) return;
            plugin.sentries().damageSentry(e.getPlayer(), e.getAttacked());
            return;
        }
        if (plugin.deployables().isMaximInteraction(e.getAttacked())) {
            e.setCancelled(true);
            if (SpectatorBlock.deny(e.getPlayer())) return;
            plugin.deployables().damageMaxim(e.getPlayer(), e.getAttacked());
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (SpectatorBlock.isSpectator(e.getPlayer()) && plugin.deployables().isManning(e.getPlayer())) {
            plugin.deployables().dismount(e.getPlayer());
            return;
        }
        if (e.isSneaking() && plugin.deployables().isManning(e.getPlayer())) {
            plugin.deployables().dismount(e.getPlayer());
        }
    }

    /** Damage dismounts the gunner so they do not get stuck in the seat. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && plugin.deployables().isManning(p)
                && e.getFinalDamage() > 0) {
            plugin.deployables().dismount(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.deployables().onPlayerGone(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        plugin.deployables().onPlayerGone(e.getEntity().getUniqueId());
    }
}
