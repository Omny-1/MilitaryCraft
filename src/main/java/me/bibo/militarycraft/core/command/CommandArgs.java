package me.bibo.militarycraft.core.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Shared argument-parsing helpers for every module's {@link SubCommand}. */
public final class CommandArgs {

    private CommandArgs() {
    }

    /** {@code sender} as a Player, or null if it's console/command-block. */
    public static Player player(CommandSender sender) {
        return sender instanceof Player p ? p : null;
    }

    public static Player resolvePlayer(String name) {
        return Bukkit.getPlayerExact(name);
    }

    /** Absolute number, or {@code ~}/{@code ~offset} relative to base (players only). Null on parse error. */
    public static Double coord(String token, double base, boolean allowRelative) {
        if (token == null || !Double.isFinite(base)) {
            return null;
        }
        try {
            double value;
            if (token.startsWith("~")) {
                if (!allowRelative) {
                    return null;
                }
                String rest = token.substring(1);
                value = base + (rest.isEmpty() ? 0.0 : Double.parseDouble(rest));
            } else {
                value = Double.parseDouble(token);
            }
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Gives {@code stack} to {@code player}, dropping any inventory overflow at their feet. */
    public static boolean giveItem(Player player, ItemStack stack) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (leftover.isEmpty()) {
            return false;
        }
        leftover.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        return true;
    }
}
