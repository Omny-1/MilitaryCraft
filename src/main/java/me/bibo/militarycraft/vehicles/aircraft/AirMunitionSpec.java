package me.bibo.militarycraft.vehicles.aircraft;

import org.bukkit.Particle;

public record AirMunitionSpec(
        double gravity,
        int substeps,
        int lifetimeTicks,
        double maxRange,
        float explosionPower,
        boolean breakBlocks,
        boolean setFire,
        double directVehicleDamage,
        double directLivingDamage,
        Particle trailParticle,
        int trailCount,
        int impactSmokeDuration,
        double impactSmokeRadius
) {
    public AirMunitionSpec {
        gravity = AircraftSafety.clamp(gravity, 0.0, AircraftSafety.MAX_GRAVITY);
        substeps = AircraftSafety.clamp(substeps, 1, AircraftSafety.MAX_SUBSTEPS);
        lifetimeTicks = AircraftSafety.clamp(lifetimeTicks, 1, AircraftSafety.MAX_MUNITION_LIFETIME_TICKS);
        maxRange = AircraftSafety.clamp(maxRange, 0.0, AircraftSafety.MAX_MUNITION_RANGE);
        explosionPower = AircraftSafety.clamp(explosionPower, 0.0f, AircraftSafety.MAX_EXPLOSION_POWER);
        directVehicleDamage = AircraftSafety.clamp(directVehicleDamage, 0.0, AircraftSafety.MAX_DAMAGE);
        directLivingDamage = AircraftSafety.clamp(directLivingDamage, 0.0, AircraftSafety.MAX_DAMAGE);
        trailCount = AircraftSafety.clamp(trailCount, 0, AircraftSafety.MAX_TRAIL_PARTICLES);
        impactSmokeDuration = AircraftSafety.clamp(impactSmokeDuration, 0,
                AircraftSafety.MAX_EFFECT_DURATION_TICKS);
        impactSmokeRadius = AircraftSafety.clamp(impactSmokeRadius, 0.0, AircraftSafety.MAX_EFFECT_RADIUS);
    }
}
