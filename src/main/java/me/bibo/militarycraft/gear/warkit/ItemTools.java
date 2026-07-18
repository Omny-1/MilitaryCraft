package me.bibo.militarycraft.gear.warkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Shared static helpers for item names and non-italic lore. */
public final class ItemTools {

    private ItemTools() {}

    public static void name(ItemMeta m, String text, TextColor color) {
        m.displayName(Txt.t(text, color));
    }

    /** Gray lore lines. */
    public static void lore(ItemMeta m, String... lines) {
        List<Component> lore = new ArrayList<>(lines.length);
        for (String line : lines) lore.add(Txt.gray(line));
        m.lore(lore);
    }

    /** Lore with a colored accent first line and gray remaining lines. */
    public static void loreAccent(ItemMeta m, String accent, TextColor accentColor, String... gray) {
        List<Component> lore = new ArrayList<>(gray.length + 1);
        lore.add(Txt.t(accent, accentColor));
        for (String line : gray) lore.add(Txt.gray(line));
        m.lore(lore);
    }

    public static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** Damage in half-hearts -> an "N hearts" line. */
    public static String hearts(double dmg) {
        double h = dmg / 2.0;
        if (h != Math.floor(h)) return fmt(h) + " hearts";
        long whole = (long) h;
        return fmt(h) + (whole == 1 ? " heart" : " hearts");
    }
}
