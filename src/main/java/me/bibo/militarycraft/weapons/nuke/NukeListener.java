package me.bibo.militarycraft.weapons.nuke;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class NukeListener implements Listener {

    private final NukeManager manager;

    NukeListener(NukeManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }
        ItemStack item = event.getItem();
        if (!NukeItem.isItem(manager.core().plugin(), item)) {
            return;
        }

        event.setCancelled(true);

        int maxDist = manager.settings().getInt("max-target-distance", 160);
        Location target = NukeTargeting.resolveTarget(event.getPlayer(), maxDist);
        boolean started = manager.callNuke(event.getPlayer(), target);

        if (started && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            consumeOne(event.getPlayer().getInventory(), hand);
        }
    }

    private void consumeOne(PlayerInventory inventory, EquipmentSlot hand) {
        ItemStack stack = hand == EquipmentSlot.OFF_HAND ? inventory.getItemInOffHand() : inventory.getItemInMainHand();
        int amount = stack.getAmount();
        if (amount > 1) {
            stack.setAmount(amount - 1);
        } else if (hand == EquipmentSlot.OFF_HAND) {
            inventory.setItemInOffHand(null);
        } else {
            inventory.setItemInMainHand(null);
        }
    }
}
