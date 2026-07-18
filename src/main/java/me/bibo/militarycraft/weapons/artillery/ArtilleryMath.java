package me.bibo.militarycraft.weapons.artillery;

/** Pure artillery trajectory and dispersion math. */
public final class ArtilleryMath {

    private ArtilleryMath() {
    }

    /** Distance-dependent maximum error radius from the artillery specification. */
    public static double spreadRadius(double horizontalDistance, double minSpread, double maxSpread,
                                      double accuracyReferenceRange, double accuracyExponent) {
        double ratio = clamp(horizontalDistance / accuracyReferenceRange, 0.0, 1.0);
        return minSpread + (maxSpread - minSpread) * Math.pow(ratio, accuracyExponent);
    }

    /** Deterministic uniform-disc sampling; callers provide independent samples in [0, 1). */
    public static Offset sampleUniformDisc(double radius, double radialSample, double angularSample) {
        double r = radius * Math.sqrt(clamp(radialSample, 0.0, 1.0));
        double angle = Math.PI * 2.0 * clamp(angularSample, 0.0, 1.0);
        return new Offset(Math.cos(angle) * r, Math.sin(angle) * r);
    }

    public static double horizontalDistance(double originX, double originZ, double targetX, double targetZ) {
        return Math.hypot(targetX - originX, targetZ - originZ);
    }

    public static int flightTicks(double distance, double maxRange, int minTicks, int maxTicks) {
        double fraction = clamp(distance / maxRange, 0.0, 1.0);
        return Math.max(1, (int) Math.round(lerp(minTicks, maxTicks, fraction)));
    }

    public static double lerp(double from, double to, double fraction) {
        return from + (to - from) * fraction;
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Offset(double x, double z) {
    }
}
