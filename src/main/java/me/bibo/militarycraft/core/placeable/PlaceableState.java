package me.bibo.militarycraft.core.placeable;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Pure validation and normalization shared by placeable persistence and model specs. */
final class PlaceableState {

    static final int FIRST_SCHEMA_VERSION = 1;
    static final double MAX_HORIZONTAL_COORDINATE = 30_000_000.0;

    private static final Pattern STABLE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    private PlaceableState() {
    }

    static String requireStableId(String value, String name) {
        if (value == null || !STABLE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must match " + STABLE_ID.pattern());
        }
        return value;
    }

    static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    static float requirePositiveFinite(float value, float maximum, String name) {
        if (!Float.isFinite(value) || value <= 0.0f || value > maximum) {
            throw new IllegalArgumentException(name + " must be finite and in (0, " + maximum + "]");
        }
        return value;
    }

    static double normalizeYaw(double yaw) {
        requireFinite(yaw, "yaw");
        double normalized = yaw % 360.0;
        if (normalized <= -180.0) {
            normalized += 360.0;
        } else if (normalized > 180.0) {
            normalized -= 360.0;
        }
        return normalized == -0.0d ? 0.0d : normalized;
    }

    static double clampHealth(double value, double maximum) {
        requireFinite(maximum, "maximum health");
        if (maximum <= 0.0) {
            throw new IllegalArgumentException("maximum health must be positive");
        }
        if (!Double.isFinite(value)) {
            return maximum;
        }
        return Math.max(0.0, Math.min(maximum, value));
    }

    static int normalizeSchemaVersion(int version) {
        return Math.max(FIRST_SCHEMA_VERSION, version);
    }

    static Optional<UUID> parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    static boolean isUsableHorizontalCoordinate(double value) {
        return Double.isFinite(value) && Math.abs(value) <= MAX_HORIZONTAL_COORDINATE;
    }
}
