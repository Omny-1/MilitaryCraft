package me.bibo.militarycraft.weapons.artillery;

/** Pure parsing and validation for coordinate-targeted artillery fire. */
public final class ArtilleryTargetValidator {

    private static final double WORLD_COORDINATE_LIMIT = 29_999_984.0;

    private ArtilleryTargetValidator() {
    }

    /** Parses an absolute finite real number. Relative and non-numeric coordinates are rejected. */
    public static Double parseFinite(String token) {
        if (token == null || token.isBlank() || token.startsWith("~")) {
            return null;
        }
        try {
            double value = Double.parseDouble(token);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Validation validate(double originX, double originZ, double targetX, double targetZ,
                                      double maxRange, double borderCenterX, double borderCenterZ,
                                      double borderSize, double impactMargin) {
        if (!finite(originX, originZ, targetX, targetZ, maxRange,
                borderCenterX, borderCenterZ, borderSize, impactMargin)) {
            return new Validation(Error.NOT_FINITE, Double.NaN);
        }
        double distance = ArtilleryMath.horizontalDistance(originX, originZ, targetX, targetZ);
        double margin = Math.max(0.0, impactMargin);
        if (Math.abs(targetX) > WORLD_COORDINATE_LIMIT - margin
                || Math.abs(targetZ) > WORLD_COORDINATE_LIMIT - margin) {
            return new Validation(Error.OUTSIDE_WORLD_LIMIT, distance);
        }
        if (distance > maxRange) {
            return new Validation(Error.OUT_OF_RANGE, distance);
        }
        double usableHalfSize = borderSize * 0.5 - margin;
        if (usableHalfSize < 0.0
                || Math.abs(targetX - borderCenterX) > usableHalfSize
                || Math.abs(targetZ - borderCenterZ) > usableHalfSize) {
            return new Validation(Error.OUTSIDE_WORLD_BORDER, distance);
        }
        return new Validation(Error.NONE, distance);
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    public enum Error {
        NONE,
        NOT_FINITE,
        OUT_OF_RANGE,
        OUTSIDE_WORLD_BORDER,
        OUTSIDE_WORLD_LIMIT
    }

    public record Validation(Error error, double distance) {
        public boolean valid() {
            return error == Error.NONE;
        }
    }
}
