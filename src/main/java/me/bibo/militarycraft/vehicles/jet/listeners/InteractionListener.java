package me.bibo.militarycraft.vehicles.jet.listeners;

import me.bibo.militarycraft.vehicles.jet.JetRuntime;
import me.bibo.militarycraft.vehicles.jet.jet.Jet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Enter (right-click the jet), exit (sneak/dismount), fire rockets (left-click),
 * and drop bombs (right-click in the air while flying).
 */
public final class InteractionListener implements Listener {

    private final JetRuntime plugin;

    public InteractionListener(JetRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClickJet(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Jet jet = plugin.jets().byEntity(event.getRightClicked());
        if (jet == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        // The pilot sits inside this jet's hitbox, so their own right-click lands
        // here too. That isn't a re-entry attempt - it's a bomb release.
        if (plugin.jets().byDriver(player.getUniqueId()) == jet) {
            plugin.jets().weapons().dropBomb(jet);
            return;
        }
        if (!player.hasPermission("jetcraft.use")) {
            player.sendActionBar(Component.text("You do not have permission to operate fighters", NamedTextColor.RED));
            return;
        }
        if (!plugin.jets().enter(jet, player)) {
            player.sendActionBar(Component.text("Fighter is occupied", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.jets().handleDismount(player);
        }
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        Jet jet = plugin.jets().byDriver(player.getUniqueId());
        if (jet != null) {
            plugin.jets().weapons().fireRocket(jet);
        } else {
            plugin.jets().meleeFromPlayer(player);
        }
    }

    /**
     * While flying, the mouse controls the jet, so block every world interaction
     * (no item use / placing). A right-click is repurposed to drop a bomb. Left-click
     * fires rockets via {@link PlayerAnimationEvent}, a separate event we don't touch.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDriverInteract(PlayerInteractEvent event) {
        Jet jet = plugin.jets().byDriver(event.getPlayer().getUniqueId());
        if (jet == null) {
            return;
        }
        event.setCancelled(true);
        switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> plugin.jets().weapons().dropBomb(jet);
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.jets().handleDriverLoss(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.jets().handleDriverLoss(event.getEntity());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.jets().hideCloakedDriversFrom(event.getPlayer());
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        plugin.jets().refreshDriverCloak(event.getPlayer());
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        plugin.jets().refreshDriverCloak(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            org.bukkit.Bukkit.getScheduler().runTask(plugin.bukkitPlugin(), () -> plugin.jets().refreshDriverCloak(player));
        }
    }
}
