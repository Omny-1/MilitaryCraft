package me.bibo.militarycraft.core.vehicle;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The unified read/act surface other modules use instead of scoreboard-tag scanning
 * (§4.3). {@link DisplayVehicle} implements this directly — every concrete vehicle
 * (CP3) is a {@code VehicleHandle} for free.
 */
public interface VehicleHandle {

    UUID id();

    /** Owning module id, e.g. "tank", "kamaz". */
    String type();

    /** The entity a driver actually rides (the seat/core ArmorStand). */
    Entity coreEntity();

    Location location();

    boolean isActive();

    double health();

    double maxHealth();

    /** @return true if this hit destroyed the vehicle. */
    boolean damage(double amount);

    /** @return health actually restored. */
    double repair(double amount);

    /** Flat 1-creeper hit, no knockback, no block break (§6) — the AntiAir contract. */
    void applyAntiAirHit();

    /** Distance-falloff blast damage from a real explosion nearby. */
    void applyExplosion(Location loc, double power);

    /**
     * Physical entities that define this vehicle's original hit geometry.
     * Restored plugins expose their existing Interaction entities here, so shared
     * weapons use the exact same hitboxes as the source implementation.
     */
    default Collection<? extends Entity> collisionEntities() {
        Entity core = coreEntity();
        return core == null ? List.of() : List.of(core);
    }

    /**
     * True when the owning legacy module already routes Bukkit explosion events.
     * Explicit weapon damage still calls {@link #applyExplosion}; this flag only
     * prevents the same vanilla event from being routed a second time by core.
     */
    default boolean handlesBukkitExplosionEvents() {
        return false;
    }
}
