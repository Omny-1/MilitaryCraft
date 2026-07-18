/*
 * Decompiled with CFR 0.152.
 */
package me.bibo.militarycraft.vehicles.pickup.util;

public final class MathUtil {
    private MathUtil() {
    }

    public static double wrapDegrees(double deg) {
        double d = deg % 360.0;
        if (d <= -180.0) {
            d += 360.0;
        } else if (d > 180.0) {
            d -= 360.0;
        }
        return d;
    }

    public static double approach(double current, double target, double maxStep) {
        double diff = target - current;
        if (Math.abs(diff) <= maxStep) {
            return target;
        }
        return current + Math.signum(diff) * maxStep;
    }

    public static double approachAngle(double current, double target, double maxStep) {
        double diff = MathUtil.wrapDegrees(target - current);
        if (Math.abs(diff) <= maxStep) {
            return MathUtil.wrapDegrees(target);
        }
        return MathUtil.wrapDegrees(current + Math.signum(diff) * maxStep);
    }

    public static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}

