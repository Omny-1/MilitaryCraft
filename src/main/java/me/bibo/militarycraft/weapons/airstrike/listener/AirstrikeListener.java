package me.bibo.militarycraft.weapons.airstrike.listener;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import me.bibo.militarycraft.weapons.airstrike.AirstrikeRuntime;
import me.bibo.militarycraft.weapons.airstrike.command.AirstrikeCommand;

public class AirstrikeListener implements Listener {

    private final AirstrikeRuntime plugin;

    public AirstrikeListener(AirstrikeRuntime plugin) {
        this.plugin = plugin;
    }

    // Not ignoreCancelled: the beacon should work everywhere, even where a
    // protection plugin would cancel the raw block interaction.
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        // The interact event fires once per hand; only react to the hand holding the beacon.
        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }
        ItemStack item = event.getItem();
        if (!AirstrikeCommand.isAirstrikeItem(plugin, item)) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        // Enforce the declared airstrike.use permission (default op). The beacon is an
        // admin-distributed tool, so a non-op who picks one up must not be able to fire it
        // - the /airstrike command is already gated by this same node in plugin.yml.
        if (!player.hasPermission("airstrike.use")) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    "You don't have permission to call an airstrike.",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        int maxDist = plugin.getConfig().getInt("max-target-distance", 150);
        Location target = AirstrikeCommand.resolveTarget(player, maxDist);
        boolean started = plugin.getAirstrikeManager().callAirstrike(player, target);

        // Consume one beacon from the exact hand that triggered the strike.
        if (started && player.getGameMode() != GameMode.CREATIVE) {
            consumeOne(player.getInventory(), hand);
        }
    }

    private void consumeOne(PlayerInventory inv, EquipmentSlot hand) {
        ItemStack stack = hand == EquipmentSlot.OFF_HAND ? inv.getItemInOffHand() : inv.getItemInMainHand();
        int amount = stack.getAmount();
        if (amount > 1) {
            stack.setAmount(amount - 1);
        } else if (hand == EquipmentSlot.OFF_HAND) {
            inv.setItemInOffHand(null);
        } else {
            inv.setItemInMainHand(null);
        }
    }
}
