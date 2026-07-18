package me.bibo.militarycraft.core.placeable;

import me.bibo.militarycraft.core.model.DisplayConfig;
import me.bibo.militarycraft.core.model.Part;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable shape contract for a stationary rig. Stable string ids are persisted;
 * list positions are retained only as a migration/debug fallback.
 */
public record PlaceableModel(List<PartSpec> parts, List<HitboxSpec> hitboxes,
                             double height, DisplayConfig displayConfig) {

    public PlaceableModel {
        parts = List.copyOf(Objects.requireNonNull(parts, "parts"));
        hitboxes = List.copyOf(Objects.requireNonNull(hitboxes, "hitboxes"));
        PlaceableState.requireFinite(height, "model height");
        if (height <= 0.0) {
            throw new IllegalArgumentException("model height must be positive");
        }
        validateDisplayConfig(Objects.requireNonNull(displayConfig, "displayConfig"));
        requireUniquePartIds(parts);
        requireUniqueHitboxIds(hitboxes);
    }

    public PlaceableModel(List<PartSpec> parts, List<HitboxSpec> hitboxes, double height) {
        this(parts, hitboxes, height, DisplayConfig.STANDARD);
    }

    private static void requireUniquePartIds(List<PartSpec> parts) {
        Set<String> ids = new HashSet<>();
        for (PartSpec spec : parts) {
            Objects.requireNonNull(spec, "part spec");
            if (!ids.add(spec.id())) {
                throw new IllegalArgumentException("duplicate part id: " + spec.id());
            }
        }
    }

    private static void requireUniqueHitboxIds(List<HitboxSpec> hitboxes) {
        Set<String> ids = new HashSet<>();
        for (HitboxSpec spec : hitboxes) {
            Objects.requireNonNull(spec, "hitbox spec");
            if (!ids.add(spec.id())) {
                throw new IllegalArgumentException("duplicate hitbox id: " + spec.id());
            }
        }
    }

    private static void validateDisplayConfig(DisplayConfig config) {
        if (!Float.isFinite(config.viewRange()) || config.viewRange() <= 0.0f) {
            throw new IllegalArgumentException("display view range must be positive and finite");
        }
        if (config.teleportDuration() < 0 || config.teleportDuration() > 59) {
            throw new IllegalArgumentException("display teleport duration must be in [0, 59]");
        }
        if (config.interpolationDuration() < 0) {
            throw new IllegalArgumentException("display interpolation duration cannot be negative");
        }
    }

    public record PartSpec(String id, Part part) {

        public PartSpec {
            id = PlaceableState.requireStableId(id, "part id");
            part = Objects.requireNonNull(part, "part");
            validateVector(part.offset, false, "part offset");
            validateVector(part.scale, true, "part scale");
            if (!Float.isFinite(part.pitch) || !Float.isFinite(part.yaw) || !Float.isFinite(part.roll)) {
                throw new IllegalArgumentException("part rotation must be finite");
            }
            if (!part.isText() && part.material == null) {
                throw new IllegalArgumentException("block part material cannot be null");
            }
        }

        private static void validateVector(Vector3f vector, boolean positive, String name) {
            Objects.requireNonNull(vector, name);
            if (!Float.isFinite(vector.x) || !Float.isFinite(vector.y) || !Float.isFinite(vector.z)) {
                throw new IllegalArgumentException(name + " must be finite");
            }
            if (positive && (vector.x <= 0.0f || vector.y <= 0.0f || vector.z <= 0.0f)) {
                throw new IllegalArgumentException(name + " components must be positive");
            }
        }
    }

    /** Offset is the local-space base location expected by an Interaction entity. */
    public record HitboxSpec(String id, Vector3f offset, float width, float height) {

        public HitboxSpec {
            id = PlaceableState.requireStableId(id, "hitbox id");
            offset = new Vector3f(Objects.requireNonNull(offset, "offset"));
            if (!Float.isFinite(offset.x) || !Float.isFinite(offset.y) || !Float.isFinite(offset.z)) {
                throw new IllegalArgumentException("hitbox offset must be finite");
            }
            width = PlaceableState.requirePositiveFinite(width, 64.0f, "hitbox width");
            height = PlaceableState.requirePositiveFinite(height, 64.0f, "hitbox height");
        }

        @Override
        public Vector3f offset() {
            return new Vector3f(offset);
        }
    }
}
