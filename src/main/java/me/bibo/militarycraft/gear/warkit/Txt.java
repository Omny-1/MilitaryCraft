package me.bibo.militarycraft.gear.warkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Short helpers for non-italic Adventure components. */
public final class Txt {

    private Txt() {}

    public static Component t(String s, TextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }

    public static Component gray(String s) {
        return t(s, NamedTextColor.GRAY);
    }

    /** mm:ss from seconds. */
    public static String mmss(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }
}
