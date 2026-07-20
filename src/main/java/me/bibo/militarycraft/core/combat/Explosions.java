package me.bibo.militarycraft.core.combat;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared explosion plumbing: the internal-explosion guard flag (dedupes every
 * source plugin's own {@code internalExplosion} boolean - TankCraft/KamazCraft/
 * JetCraft each had one) plus the standard impact fx.
 */
public final class Explosions {

    // Bukkit explosions are synchronous on the server thread. A depth counter also
    // keeps the guard correct if another guarded explosion is created from an event.
    private static int internalDepth;

    private Explosions() {
    }

    public static boolean isInternal() {
        return internalDepth > 0;
    }

    /** Fires a real explosion guarded so shared {@code ExplosionSink}s know it's our own and don't double-route it. */
    public static void createExplosion(World world, Location loc, float power, boolean setFire, boolean breakBlocks) {
        createExplosion(world, loc, power, setFire, breakBlocks, null);
    }

    /** Same guarded explosion while preserving the Bukkit damage source. */
    public static void createExplosion(World world, Location loc, float power, boolean setFire, boolean breakBlocks,
                                       Entity source) {
        if (world == null || loc == null || !Float.isFinite(power) || power <= 0.0f) {
            return;
        }
        internalDepth++;
        try {
            world.createExplosion(loc, Math.min(power, 100.0f), setFire, breakBlocks, source);
        } finally {
            internalDepth--;
        }
    }

    /** The standard blast look/sound (dedupe of Tank/Kamaz/Jet's near-identical fx calls). */
    public static void impactFx(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1, 0.45, 0.35, 0.45, 0);
        world.spawnParticle(Particle.FLASH, loc, 1, 0.0, 0.0, 0.0, 0);
        world.spawnParticle(Particle.FLAME, loc, 24, 0.9, 0.55, 0.9, 0.055);
        world.spawnParticle(Particle.LAVA, loc, 8, 0.45, 0.22, 0.45, 0);
        world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 16, 0.95, 0.55, 0.95, 0.075);
        world.spawnParticle(Particle.CRIT, loc, 20, 1.1, 0.55, 1.1, 0.12);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.85f);
    }

    /**
     * Short post-impact sparkle burst. It intentionally replaces the old lingering grey smoke:
     * terrain damage, vehicle damage, craters and display-debris animations are handled elsewhere.
     */
    public static BukkitTask impactAfterglow(Plugin plugin, Location loc, int configuredDurationTicks,
                                             double configuredRadius) {
        if (plugin == null || loc == null || loc.getWorld() == null
                || configuredDurationTicks <= 0 || configuredRadius <= 0.0) {
            return null;
        }
        Location base = loc.clone();
        World world = base.getWorld();
        double radius = Math.min(6.0, Math.max(0.45, configuredRadius));
        int pulses = Math.max(1, Math.min(5, (configuredDurationTicks + 23) / 24));
        return new BukkitRunnable() {
            private int left = pulses;

            @Override
            public void run() {
                if (left <= 0 || !world.isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) {
                    cancel();
                    return;
                }
                ThreadLocalRandom rng = ThreadLocalRandom.current();
                int samples = Math.max(4, Math.min(8, (int) Math.round(radius * 1.7)));
                for (int i = 0; i < samples; i++) {
                    double dx = (rng.nextDouble() * 2.0 - 1.0) * radius;
                    double dz = (rng.nextDouble() * 2.0 - 1.0) * radius;
                    double dy = 0.05 + rng.nextDouble() * 0.95;
                    Location p = base.clone().add(dx, dy, dz);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, p, 1, 0.08, 0.06, 0.08, 0.04);
                    world.spawnParticle(Particle.CRIT, p, 1, 0.12, 0.08, 0.12, 0.06);
                    if (left >= pulses - 1) {
                        world.spawnParticle(Particle.FLAME, p, 1, 0.06, 0.04, 0.06, 0.01);
                    }
                }
                if (left == pulses) {
                    world.spawnParticle(Particle.LAVA, base, 4,
                            Math.min(0.8, radius * 0.25), 0.12, Math.min(0.8, radius * 0.25), 0);
                }
                left--;
            }
        }.runTaskTimer(plugin, 1L, 2L);
    }

    /**
     * Heuristic blast "power" for a vanilla explosion source we didn't cause
     * ourselves (used by {@link VehicleCombatServiceImpl}'s {@code ExplosionSink}),
     * generalised from TankCraft's {@code DamageListener.powerFor}.
     */
    public static double powerFor(Entity source) {
        if (source instanceof Creeper creeper) {
            return creeper.isPowered() ? 6.0 : 3.0;
        }
        if (source instanceof TNTPrimed) {
            return 4.0;
        }
        if (source instanceof EnderCrystal) {
            return 6.0;
        }
        if (source instanceof Fireball) {
            return 1.0;
        }
        return 4.0;
    }
}
