package me.bibo.militarycraft.core.util;

/**
 * NaN/Infinity-safe numeric guards for config values. Plain {@code Math.max/min} does NOT
 * sanitize {@code NaN} ({@code Math.max(1, NaN) == NaN}), so a typo'd or corrupt config
 * value can slip a {@code NaN} into an entity scan range, a spawn count or a coordinate and
 * take the whole server down. Route amplifying config reads (scan ranges, spawn/particle
 * counts, radii) through here.
 */
public final class Bounds {

    private Bounds() {
    }

    /** @return {@code v} clamped to {@code [min, max]}, or {@code fallback} if {@code v} is not finite. */
    public static double ranged(double v, double min, double max, double fallback) {
        if (!Double.isFinite(v)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, v));
    }

    /** @return {@code v} clamped to {@code [min, max]}. */
    public static int ranged(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
