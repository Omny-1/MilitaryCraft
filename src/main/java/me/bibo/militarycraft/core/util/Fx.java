package me.bibo.militarycraft.core.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.util.Vector;

/**
 * Small shared FX helpers — dedupes the tracer/particle/sound copies scattered across
 * Tank/Jet/Heli/AntiAir. Kept minimal for CP1; combat-specific FX (muzzle flash, hit
 * sparks, smoke trails) grow here as CP2+ modules need them.
 */
public final class Fx {

    private Fx() {
    }

    /** Draws a dust-particle tracer line from {@code from} to {@code to} (AntiAirCraft's algorithm). */
    public static void tracer(Location from, Location to, Color color, int density) {
        if (from == null || to == null || color == null || density <= 0
                || from.getWorld() == null || from.getWorld() != to.getWorld()) {
            return;
        }
        Vector step = to.toVector().subtract(from.toVector());
        double len = step.length();
        if (len < 1e-3) {
            return;
        }
        int n = Math.max(2, Math.min(density, (int) (len * 1.5)));
        step.multiply(1.0 / n);
        Particle.DustOptions dust = new Particle.DustOptions(color, 0.6f);
        Location p = from.clone();
        for (int i = 0; i <= n; i++) {
            from.getWorld().spawnParticle(Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, dust);
            p.add(step.getX(), step.getY(), step.getZ());
        }
    }

    /** A generic particle burst centred on {@code loc}. */
    public static void burst(Location loc, Particle particle, int count, double dx, double dy, double dz, double speed) {
        if (loc == null || loc.getWorld() == null || particle == null || count <= 0) {
            return;
        }
        loc.getWorld().spawnParticle(particle, loc, count,
                finiteNonNegative(dx), finiteNonNegative(dy), finiteNonNegative(dz), finiteNonNegative(speed));
    }

    public static void sound(Location loc, Sound sound, float volume, float pitch) {
        if (loc == null || loc.getWorld() == null || sound == null || !Float.isFinite(volume) || volume <= 0.0f) {
            return;
        }
        float safePitch = Float.isFinite(pitch) ? Math.max(0.0f, Math.min(2.0f, pitch)) : 1.0f;
        loc.getWorld().playSound(loc, sound, volume, safePitch);
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }
}
