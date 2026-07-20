package me.bibo.militarycraft.vehicles.jet.combat;

import me.bibo.militarycraft.vehicles.jet.JetRuntime;
import me.bibo.militarycraft.vehicles.jet.config.JetConfig;
import me.bibo.militarycraft.vehicles.jet.jet.Jet;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/** Shared impact effects: the blast itself, area damage to jets, and short impact afterglow. */
public final class Explosions {

    private Explosions() {
    }

    public static void detonate(JetRuntime plugin, Location loc, UUID ownerJetToSkip,
                                float power, boolean breakBlocks, boolean setFire) {
        detonate(plugin, loc, ownerJetToSkip, null, power, breakBlocks, setFire);
    }

    public static void detonate(JetRuntime plugin, Location loc, UUID ownerJetToSkip, UUID directJetToSkip,
                                float power, boolean breakBlocks, boolean setFire) {
        JetConfig cfg = plugin.config();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        // The real explosion: damages entities and (optionally) breaks terrain.
        // Guarded so our own damage listener ignores any event it emits, and the
        // pilot who fired is made immune to their own blast for its duration.
        UUID immune = null;
        if (ownerJetToSkip != null) {
            Jet owner = plugin.jets().byId(ownerJetToSkip);
            if (owner != null) {
                immune = owner.driver();
            }
        }
        plugin.jets().setInternalExplosion(true);
        plugin.jets().setMunitionImmunePilot(immune);
        try {
            world.createExplosion(loc, power, setFire, breakBlocks);
        } finally {
            plugin.jets().setInternalExplosion(false);
            plugin.jets().setMunitionImmunePilot(null);
        }

        me.bibo.militarycraft.core.combat.Explosions.impactFx(loc);

        // Direct area damage to any jets caught in the blast - but never the jet
        // that fired this munition (no self-damage from your own weapons).
        for (Jet jet : new java.util.ArrayList<>(plugin.jets().all())) {
            if (ownerJetToSkip != null && jet.id().equals(ownerJetToSkip)) {
                continue;
            }
            if (directJetToSkip != null && jet.id().equals(directJetToSkip)) {
                continue;
            }
            applyBlastTo(jet, loc, power, cfg);
        }

        impactAfterglow(plugin, loc.clone(), cfg);
    }

    /** Apply explosion damage (scaled by distance/power) to a single jet. */
    public static void applyBlastTo(Jet jet, Location loc, double power, JetConfig cfg) {
        if (!jet.isActive() || jet.world() != loc.getWorld()) {
            return;
        }
        Location centre = jet.anchor().clone().add(0, 0.4, 0);
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
            jet.damage(dmg);
        }
    }

    private static void impactAfterglow(JetRuntime plugin, Location loc, JetConfig cfg) {
        me.bibo.militarycraft.core.combat.Explosions.impactAfterglow(
                plugin.bukkitPlugin(), loc, cfg.impactSmokeDuration, cfg.impactSmokeRadius);
    }
}
