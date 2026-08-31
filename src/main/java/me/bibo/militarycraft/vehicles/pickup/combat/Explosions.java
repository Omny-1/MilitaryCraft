package me.bibo.militarycraft.vehicles.pickup.combat;

import me.bibo.militarycraft.vehicles.pickup.PickupRuntime;
import me.bibo.militarycraft.vehicles.pickup.config.PickupConfig;
import me.bibo.militarycraft.vehicles.pickup.vehicle.Pickup;
import org.bukkit.Location;

/**
 * How a pickup takes explosive damage, and what an impact looks like afterwards.
 *
 * <p>Blast damage falls off with distance from the charge, so a grenade at the wheel is not the same
 * hit as one under the engine.
 */
public final class Explosions {
    private Explosions() {
    }

    public static void applyBlastTo(Pickup pickup, Location loc, double power, PickupConfig cfg) {
        double contact;
        double radius;
        if (!pickup.isActive() || pickup.world() != loc.getWorld()) {
            return;
        }
        Location centre = pickup.anchor().clone().add(0.0, 0.8, 0.0);
        double dist = centre.distance(loc);
        if (dist > (radius = power * 2.0 + (contact = 2.0))) {
            return;
        }
        double falloff = dist <= contact ? 1.0 : Math.max(0.0, 1.0 - (dist - contact) / (radius - contact));
        double dmg = cfg.creeperDamage * (power / 3.0) * falloff;
        if (dmg > 0.0) {
            pickup.damage(dmg);
        }
    }

    public static void impactAfterglow(PickupRuntime plugin, final Location loc, final PickupConfig cfg) {
        me.bibo.militarycraft.core.combat.Explosions.impactAfterglow(
                plugin.bukkitPlugin(), loc, cfg.impactSmokeDuration, cfg.impactSmokeRadius);
    }
}
