/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  org.bukkit.GameMode
 *  org.bukkit.Location
 *  org.bukkit.Sound
 *  org.bukkit.block.Block
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.inventory.EquipmentSlot
 *  org.bukkit.inventory.ItemStack
 */
package me.bibo.militarycraft.vehicles.pickup.listeners;

import me.bibo.militarycraft.vehicles.pickup.PickupRuntime;
import me.bibo.militarycraft.vehicles.pickup.items.PickupItem;
import me.bibo.militarycraft.vehicles.pickup.vehicle.PickupCollision;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class PlacementListener
implements Listener {
    private final PickupRuntime plugin;

    public PlacementListener(PickupRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority=EventPriority.NORMAL, ignoreCancelled=true)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!PickupItem.isPickupItem(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (this.plugin.pickups().isCrew(player.getUniqueId())) {
            return;
        }
        if (!player.hasPermission("pickupcraft.place")) {
            player.sendActionBar((Component)Component.text((String)"You do not have permission to place a pickup", (TextColor)NamedTextColor.RED));
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        double yaw = player.getLocation().getYaw();
        Location at = PickupCollision.anchorOnTop(clicked);
        PickupCollision.PlacementResult result = PickupCollision.validatePlacement(this.plugin.pickups().all(), null, at, yaw);
        if (!result.ok()) {
            player.sendActionBar((Component)Component.text((String)result.message(), (TextColor)NamedTextColor.RED));
            return;
        }
        this.plugin.pickups().create(at, yaw);
        player.getWorld().playSound(at, Sound.BLOCK_ANVIL_PLACE, 1.0f, 0.9f);
        if (this.plugin.config().consumeItem && player.getGameMode() != GameMode.CREATIVE) {
            item.subtract(1);
        }
    }
}
