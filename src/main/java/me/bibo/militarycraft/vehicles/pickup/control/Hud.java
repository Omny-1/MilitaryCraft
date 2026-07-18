/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  org.bukkit.entity.Player
 */
package me.bibo.militarycraft.vehicles.pickup.control;

import me.bibo.militarycraft.vehicles.pickup.vehicle.Pickup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

public final class Hud {
    private Hud() {
    }

    public static void send(Pickup pickup, Player viewer) {
        if (pickup.isOverheated() && viewer.getUniqueId().equals(pickup.gunner())) {
            double seconds = (double)pickup.overheatTicks() / 20.0;
            Component hud = Component.text((String)"\ud83d\udd25 MACHINE GUN OVERHEATED ", (TextColor)NamedTextColor.RED).append((Component)Component.text((String)String.format("%.1fs", seconds), (TextColor)NamedTextColor.GOLD));
            viewer.sendActionBar(hud);
            return;
        }
        int hpPct = (int)Math.round(100.0 * pickup.health() / pickup.maxHealth());
        int kmh = (int)Math.round(Math.abs(pickup.speed()) * 20.0 * 3.6);
        NamedTextColor hpColor = hpPct > 50 ? NamedTextColor.GREEN : (hpPct > 25 ? NamedTextColor.YELLOW : NamedTextColor.RED);
        Component hud = ((TextComponent)Component.text((String)"HP ", (TextColor)NamedTextColor.GRAY).append((Component)Component.text((String)(hpPct + "%"), (TextColor)hpColor))).append((Component)Component.text((String)("   \u2699 " + kmh + " km/h"), (TextColor)NamedTextColor.AQUA));
        viewer.sendActionBar(hud);
    }

    public static void sendSubmergedWarning(Player viewer) {
        viewer.sendActionBar((Component)Component.text((String)"\u26a0 Engine flooded - the pickup is sinking!", (TextColor)NamedTextColor.RED));
    }
}
