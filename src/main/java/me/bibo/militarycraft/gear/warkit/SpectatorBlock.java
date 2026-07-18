package me.bibo.militarycraft.gear.warkit;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/** Shared guard: spectators must not operate WarKit combat items or deployables. */
public final class SpectatorBlock {

    private SpectatorBlock() {}

    public static boolean isSpectator(Player p) {
        return p.getGameMode() == GameMode.SPECTATOR;
    }

    public static boolean deny(Player p) {
        if (!isSpectator(p)) return false;
        p.sendActionBar(Txt.t("WarKit is unavailable in spectator mode", NamedTextColor.YELLOW));
        return true;
    }
}
