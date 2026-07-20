package me.bibo.militarycraft.core.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Adventure text helpers: legacy '&amp;'-code parsing (incl. hex, e.g. {@code &#ff00aa}),
 * the chat prefix, and item name/lore builders. Replaces every plugin's own §-string /
 * {@code message()} helper and WarKit's {@code Txt}/{@code ItemTools}.
 */
public final class Text {

    /** Chat prefix for feedback messages. */
    public static final String PREFIX = "&8[&bMC&8]&r ";

    // legacyAmpersand() alone does NOT parse hex codes (verified: hexColours=false in its
    // built-in instance) - build our own with hexColors() enabled to satisfy "incl. hex".
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    /** Parses a legacy '&amp;'-coded string (colours, incl. hex) into a Component. */
    public static Component of(String legacy) {
        return LEGACY.deserialize(legacy);
    }

    /** Sends a prefixed, parsed feedback message to any sender. */
    public static void msg(CommandSender sender, String legacy) {
        sender.sendMessage(of(PREFIX + legacy));
    }

    /** Item display-name style: coloured, no italics (vanilla defaults custom names to italic). */
    public static Component item(String name, TextColor color) {
        return Component.text(name, color).decoration(TextDecoration.ITALIC, false);
    }

    /** Grey, non-italic lore lines. */
    public static List<Component> lore(String... lines) {
        List<Component> out = new ArrayList<>(lines.length);
        for (String line : lines) {
            out.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        return out;
    }
}
