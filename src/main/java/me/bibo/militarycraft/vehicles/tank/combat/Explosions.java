package me.bibo.militarycraft.vehicles.tank.combat;

import me.bibo.militarycraft.vehicles.tank.TankRuntime;
import me.bibo.militarycraft.vehicles.tank.config.TankConfig;
import me.bibo.militarycraft.vehicles.tank.tank.Tank;
import org.bukkit.Location;
import org.bukkit.World;

/** Shared impact effects: the blast itself, area damage to tanks, and short impact afterglow. */
public final class Explosions {

    private Explosions() {
    }

    public static void detonate(TankRuntime plugin, Location loc, java.util.UUID ownerTankToSkip) {
        TankConfig cfg = plugin.config();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        // The real explosion: damages entities and (optionally) breaks terrain.
        // Guarded so our own damage listener ignores any event it emits.
        plugin.tanks().setInternalExplosion(true);
        try {
            world.createExplosion(loc, cfg.explosionPower, cfg.setFire, cfg.breakBlocks);
        } finally {
            plugin.tanks().setInternalExplosion(false);
        }

        me.bibo.militarycraft.core.combat.Explosions.impactFx(loc);

        // Direct damage to any tanks caught in the blast - but never the tank
        // that fired this shell (no self-damage from your own gun).
        for (Tank tank : new java.util.ArrayList<>(plugin.tanks().all())) {
            if (ownerTankToSkip != null && tank.id().equals(ownerTankToSkip)) {
                continue;
            }
            applyBlastTo(tank, loc, cfg.explosionPower, cfg);
        }

        impactAfterglow(plugin, loc.clone(), cfg);
    }

    /** Apply explosion damage (scaled by distance/power) to a single tank. */
    public static void applyBlastTo(Tank tank, Location loc, double power, TankConfig cfg) {
        if (!tank.isActive() || tank.world() != loc.getWorld()) {
            return;
        }
        Location centre = tank.anchor().clone().add(0, 1.0, 0);
        double dist = centre.distance(loc);
        double contact = 2.0;                  // within this range it is a "contact" hit
        double radius = power * 2.0 + contact; // beyond this it does nothing
        if (dist > radius) {
            return;
        }
        // Full damage up close (so two point-blank creepers == max HP), tapering off with range.
        double falloff = dist <= contact ? 1.0 : Math.max(0.0, 1.0 - (dist - contact) / (radius - contact));
        double dmg = cfg.creeperDamage * (power / 3.0) * falloff;
        if (dmg > 0) {
            tank.damage(dmg);
        }
    }

    private static void impactAfterglow(TankRuntime plugin, Location loc, TankConfig cfg) {
        me.bibo.militarycraft.core.combat.Explosions.impactAfterglow(
                plugin.bukkitPlugin(), loc, cfg.impactSmokeDuration, cfg.impactSmokeRadius);
    }
}
