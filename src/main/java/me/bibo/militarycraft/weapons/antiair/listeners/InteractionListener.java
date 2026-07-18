package me.bibo.militarycraft.weapons.antiair.listeners;

import me.bibo.militarycraft.weapons.antiair.AntiAirRuntime;
import me.bibo.militarycraft.weapons.antiair.gui.TurretMenu;
import me.bibo.militarycraft.weapons.antiair.turret.Turret;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Right-click a turret to open its control panel. */
public final class InteractionListener implements Listener {

    private final AntiAirRuntime plugin;

    public InteractionListener(AntiAirRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Turret turret = plugin.turrets().byEntity(event.getRightClicked());
        if (turret == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("antiaircraft.use")) {
            player.sendActionBar(Component.text("You do not have permission to operate Anti-Air turrets", NamedTextColor.RED));
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.8f, 1.1f);
        TurretMenu.open(plugin, player, turret);
    }
}
