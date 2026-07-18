package me.bibo.militarycraft.vehicles.kamaz.util;

public final class MathUtil {

    private MathUtil() {
    }

    /** Wrap an angle in degrees to the range (-180, 180]. */
    public static double wrapDegrees(double deg) {
        double d = deg % 360.0;
        if (d <= -180.0) {
            d += 360.0;
        } else if (d > 180.0) {
            d -= 360.0;
        }
        return d;
    }

    /** Move {@code current} toward {@code target} by at most {@code maxStep}. */
    public static double approach(double current, double target, double maxStep) {
        double diff = target - current;
        if (Math.abs(diff) <= maxStep) {
            return target;
        }
        return current + Math.signum(diff) * maxStep;
    }

    /** Like {@link #approach} but on the angular domain (handles wrap-around). */
    public static double approachAngle(double current, double target, double maxStep) {
        double diff = wrapDegrees(target - current);
        if (Math.abs(diff) <= maxStep) {
            return wrapDegrees(target);
        }
        return wrapDegrees(current + Math.signum(diff) * maxStep);
    }

    public static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
