package me.bibo.militarycraft.vehicles.airship.combat;

import me.bibo.militarycraft.vehicles.airship.AirshipRuntime;
import me.bibo.militarycraft.vehicles.airship.airship.Airship;
import me.bibo.militarycraft.vehicles.airship.config.AirshipConfig;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/** Shared impact effects: the area blast, area damage to airships, and short impact afterglow. */
public final class Explosions {

    private Explosions() {
    }

    public static void detonate(AirshipRuntime plugin, Location loc, UUID ownerShipToSkip,
                                float power, boolean breakBlocks, boolean setFire) {
        detonate(plugin, loc, ownerShipToSkip, null, power, breakBlocks, setFire);
    }

    public static void detonate(AirshipRuntime plugin, Location loc, UUID ownerShipToSkip,
                                UUID directHitShipToSkip, float power, boolean breakBlocks, boolean setFire) {
        AirshipConfig cfg = plugin.config();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        // The real explosion: damages entities and (optionally) carves terrain.
        // Guarded so our own damage listener ignores any event it emits, and the
        // pilot who dropped it is made immune to their own blast for its duration.
        UUID immune = null;
        if (ownerShipToSkip != null) {
            Airship owner = plugin.airships().byId(ownerShipToSkip);
            if (owner != null) {
                immune = owner.driver();
            }
        }
        plugin.airships().setInternalExplosion(true);
        plugin.airships().setMunitionImmunePilot(immune);
        try {
            world.createExplosion(loc, power, setFire, breakBlocks);
        } finally {
            plugin.airships().setInternalExplosion(false);
            plugin.airships().setMunitionImmunePilot(null);
        }

        me.bibo.militarycraft.core.combat.Explosions.impactFx(loc);

        // Direct area damage to any airships caught in the blast - but never the
        // airship that dropped this bomb (no self-damage from your own weapons).
        for (Airship ship : new java.util.ArrayList<>(plugin.airships().all())) {
            if (ownerShipToSkip != null && ship.id().equals(ownerShipToSkip)) {
                continue;
            }
            if (directHitShipToSkip != null && ship.id().equals(directHitShipToSkip)) {
                continue;
            }
            applyBlastTo(ship, loc, power, cfg);
        }

        impactAfterglow(plugin, loc.clone(), cfg);
    }

    /** Apply explosion damage (scaled by distance/power) to a single airship. */
    public static void applyBlastTo(Airship ship, Location loc, double power, AirshipConfig cfg) {
        if (!ship.isActive() || ship.world() != loc.getWorld()) {
            return;
        }
        double dist = ship.minBlastDistance(loc);
        double contact = cfg.contactRadius;       // within this range it is a "contact" hit
        double radius = power * 2.0 + contact;     // beyond this it does nothing
        if (dist > radius) {
            return;
        }
        // Full damage up close (so one point-blank creeper == max HP), tapering off.
        double falloff = dist <= contact ? 1.0 : Math.max(0.0, 1.0 - (dist - contact) / (radius - contact));
        double dmg = cfg.creeperDamage * (power / 3.0) * falloff;
        if (dmg > 0) {
            ship.damage(dmg);
        }
    }

    private static void impactAfterglow(AirshipRuntime plugin, Location loc, AirshipConfig cfg) {
        me.bibo.militarycraft.core.combat.Explosions.impactAfterglow(
                plugin.bukkitPlugin(), loc, cfg.impactSmokeDuration, cfg.impactSmokeRadius);
    }
}
