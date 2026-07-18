package me.bibo.militarycraft.vehicles.helicopter.combat;

import me.bibo.militarycraft.vehicles.helicopter.HelicopterRuntime;
import me.bibo.militarycraft.vehicles.helicopter.config.HelicopterConfig;
import me.bibo.militarycraft.vehicles.helicopter.helicopter.Helicopter;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/** Shared impact effects: the area blast, area damage to helicopters, and short impact afterglow. */
public final class Explosions {

    private Explosions() {
    }

    public static void detonate(HelicopterRuntime plugin, Location loc, UUID ownerShipToSkip,
                                float power, boolean breakBlocks, boolean setFire) {
        detonate(plugin, loc, ownerShipToSkip, null, power, breakBlocks, setFire);
    }

    public static void detonate(HelicopterRuntime plugin, Location loc, UUID ownerShipToSkip,
                                UUID directHitShipToSkip, float power, boolean breakBlocks, boolean setFire) {
        HelicopterConfig cfg = plugin.config();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        // The real explosion: damages entities and (optionally) carves terrain.
        // Guarded so our own damage listener ignores any event it emits, and the
        // pilot who fired/dropped it is made immune to their own blast for its duration.
        UUID immune = null;
        if (ownerShipToSkip != null) {
            Helicopter owner = plugin.helicopters().byId(ownerShipToSkip);
            if (owner != null) {
                immune = owner.driver();
            }
        }
        plugin.helicopters().setInternalExplosion(true);
        plugin.helicopters().setMunitionImmunePilot(immune);
        try {
            world.createExplosion(loc, power, setFire, breakBlocks);
        } finally {
            plugin.helicopters().setInternalExplosion(false);
            plugin.helicopters().setMunitionImmunePilot(null);
        }

        me.bibo.militarycraft.core.combat.Explosions.impactFx(loc);

        // Direct area damage to any helicopters caught in the blast — but never
        // the helicopter that fired this munition (no self-damage from your own weapons).
        for (Helicopter heli : new java.util.ArrayList<>(plugin.helicopters().all())) {
            if (ownerShipToSkip != null && heli.id().equals(ownerShipToSkip)) {
                continue;
            }
            if (directHitShipToSkip != null && heli.id().equals(directHitShipToSkip)) {
                continue;
            }
            applyBlastTo(heli, loc, power, cfg);
        }

        impactAfterglow(plugin, loc.clone(), cfg);
    }

    /** Apply explosion damage (scaled by distance/power) to a single helicopter. */
    public static void applyBlastTo(Helicopter heli, Location loc, double power, HelicopterConfig cfg) {
        if (!heli.isActive() || heli.world() != loc.getWorld()) {
            return;
        }
        double dist = heli.minBlastDistance(loc);
        double contact = cfg.contactRadius;       // within this range it is a "contact" hit
        double radius = power * 2.0 + contact;     // beyond this it does nothing
        if (dist > radius) {
            return;
        }
        // Full damage up close (so two point-blank creepers == max HP), tapering off.
        double falloff = dist <= contact ? 1.0 : Math.max(0.0, 1.0 - (dist - contact) / (radius - contact));
        double dmg = cfg.creeperDamage * (power / 3.0) * falloff;
        if (dmg > 0) {
            heli.damage(dmg);
        }
    }

    private static void impactAfterglow(HelicopterRuntime plugin, Location loc, HelicopterConfig cfg) {
        me.bibo.militarycraft.core.combat.Explosions.impactAfterglow(
                plugin.bukkitPlugin(), loc, cfg.impactSmokeDuration, cfg.impactSmokeRadius);
    }
}
