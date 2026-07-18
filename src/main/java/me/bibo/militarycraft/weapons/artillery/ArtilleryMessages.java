package me.bibo.militarycraft.weapons.artillery;

import me.bibo.militarycraft.core.text.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** English artillery feedback with the preserved proper-name prefix. */
final class ArtilleryMessages {

    static final String NAME = "Artillery \"Belochka\"";
    private static final String PREFIX = "&8[&6Belochka&8]&r ";

    private ArtilleryMessages() {
    }

    static void send(CommandSender sender, String message) {
        sender.sendMessage(Text.of(PREFIX + message));
    }

    static void action(Player player, String message) {
        player.sendActionBar(Text.of(message));
    }
}
