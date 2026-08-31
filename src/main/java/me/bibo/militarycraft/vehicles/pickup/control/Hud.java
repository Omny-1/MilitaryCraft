package me.bibo.militarycraft.vehicles.pickup.control;

import me.bibo.militarycraft.vehicles.pickup.vehicle.Pickup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

/** The crew's action-bar readout: hull, speed, and the two warnings worth interrupting for. */
public final class Hud {
    private Hud() {
    }

    public static void send(Pickup pickup, Player viewer) {
        if (pickup.isOverheated() && viewer.getUniqueId().equals(pickup.gunner())) {
            double seconds = (double)pickup.overheatTicks() / 20.0;
            Component hud = Component.text("\ud83d\udd25 MACHINE GUN OVERHEATED ", NamedTextColor.RED).append(Component.text(String.format("%.1fs", seconds), NamedTextColor.GOLD));
            viewer.sendActionBar(hud);
            return;
        }
        int hpPct = (int)Math.round(100.0 * pickup.health() / pickup.maxHealth());
        int kmh = (int)Math.round(Math.abs(pickup.speed()) * 20.0 * 3.6);
        NamedTextColor hpColor = hpPct > 50 ? NamedTextColor.GREEN : (hpPct > 25 ? NamedTextColor.YELLOW : NamedTextColor.RED);
        Component hud = (Component.text("HP ", NamedTextColor.GRAY).append(Component.text((String)(hpPct + "%"), hpColor))).append(Component.text((String)("   \u2699 " + kmh + " km/h"), NamedTextColor.AQUA));
        viewer.sendActionBar(hud);
    }

    public static void sendSubmergedWarning(Player viewer) {
        viewer.sendActionBar(Component.text("\u26a0 Engine flooded - the pickup is sinking!", NamedTextColor.RED));
    }
}
