package me.bibo.militarycraft.vehicles.aircraft;

public final class AircraftSafety {

    public static final double MAX_FLIGHT_SPEED = 10.0;
    public static final double MAX_MUNITION_SPEED = 32.0;
    public static final double MAX_GRAVITY = 4.0;
    public static final double MAX_DAMAGE = 1_000_000.0;
    public static final double MAX_HEALTH = 1_000_000.0;
    public static final double MAX_MUNITION_RANGE = 4096.0;
    public static final double MAX_EFFECT_RADIUS = 32.0;
    public static final float MAX_EXPLOSION_POWER = 16.0f;
    public static final int MAX_SUBSTEPS = 32;
    public static final int MAX_MUNITION_LIFETIME_TICKS = 6000;
    public static final int MAX_ACTIVE_MUNITIONS = 128;
    public static final int MAX_EFFECT_TASKS = 256;
    public static final int MAX_AMMO = 512;
    public static final int MAX_TIMER_TICKS = 12000;
    public static final int MAX_BATTERY_TICKS = 1_728_000;
    public static final int MAX_INTERVAL_TICKS = 1200;
    public static final int MAX_EFFECT_DURATION_TICKS = 1200;
    public static final int MAX_TRAIL_PARTICLES = 32;
    public static final int MAX_MELEE_COOLDOWN_MS = 60_000;

    private AircraftSafety() {
    }

    public static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    public static boolean coordinatesFinite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    public static String limitText(String value, String fallback, int maxLength) {
        String source = value != null ? value : (fallback != null ? fallback : "");
        int limit = Math.max(0, maxLength);
        StringBuilder clean = new StringBuilder(Math.min(source.length(), limit));
        for (int i = 0; i < source.length() && clean.length() < limit; i++) {
            char ch = source.charAt(i);
            if (!Character.isISOControl(ch)) {
                clean.append(ch);
            }
        }
        return clean.toString();
    }
}
