package me.bibo.militarycraft.weapons.artillery;

import me.bibo.militarycraft.core.config.ModuleConfig;
import org.bukkit.Material;

/** Immutable artillery settings snapshot, rebuilt on module reload. */
public final class ArtillerySettings {

    public final int maxAmmo;
    public final long cooldownMillis;
    public final boolean durabilityEnabled;
    public final int maxHits;
    public final boolean consumeItem;

    public final double cameraHeight;
    public final double maxRange;
    public final int minFlightTicks;
    public final int maxFlightTicks;
    public final double minSpread;
    public final double maxSpread;
    public final double accuracyReferenceRange;
    public final double accuracyExponent;

    public final float explosionPower;
    public final boolean breakBlocks;
    public final boolean setFire;
    public final boolean particles;
    public final boolean sounds;
    public final int impactSmokeDurationTicks;
    public final double impactSmokeRadius;

    public final Material modelCamo;
    public final Material modelBarrel;
    public final Material modelMetal;
    public final Material modelWheel;
    public final Material shellMaterial;
    public final boolean shellGlow;

    public ArtillerySettings(ModuleConfig config) {
        boolean original = config.has("artillery.max-ammo") || config.has("config-version");
        String artillery = original ? "artillery." : "";
        String model = original ? "artillery.model." : "model.";

        maxAmmo = config.getInt(artillery + "max-ammo", 3, 1, 64);
        cooldownMillis = config.getInt(artillery + "cooldown-seconds", 120, 0, 86_400) * 1000L;
        durabilityEnabled = config.getBoolean("durability.enabled", true);
        maxHits = config.getInt("durability.max-hits", 6, 1, 1000);
        consumeItem = config.getBoolean(artillery + "consume-item",
                config.getBoolean("consume-item", true));

        cameraHeight = config.getDouble("targeting.camera-height", 120.0, 8.0, 1024.0);
        maxRange = config.getDouble("ballistics.max-range", 1200.0, 1.0, 30_000_000.0);
        minFlightTicks = config.getInt("ballistics.min-flight-ticks", 20, 1, 12_000);
        maxFlightTicks = Math.max(minFlightTicks,
                config.getInt("ballistics.max-flight-ticks", 200, 1, 12_000));
        double originalMinSpread = config.getDouble("ballistics.player-hit-min-radius", 6.0, 0.0, 10_000.0);
        double originalMaxSpread = config.getDouble("ballistics.player-hit-max-radius", 13.0, 0.0, 10_000.0);
        minSpread = config.getDouble("ballistics.min-spread", originalMinSpread, 0.0, 10_000.0);
        maxSpread = Math.max(minSpread,
                config.getDouble("ballistics.max-spread", originalMaxSpread, 0.0, 10_000.0));
        accuracyReferenceRange = config.getDouble(
                "ballistics.accuracy-reference-range", maxRange, 1.0, 30_000_000.0);
        accuracyExponent = config.getDouble("ballistics.accuracy-exponent", 1.0, 0.01, 16.0);

        explosionPower = (float) config.getDouble("impact.explosion-power", 12.0, 0.0, 100.0);
        breakBlocks = config.getBoolean("impact.break-blocks", true);
        setFire = config.getBoolean("impact.set-fire", false);
        particles = config.getBoolean("effects.particles", true);
        sounds = config.getBoolean("effects.sounds", true);
        impactSmokeDurationTicks = config.getInt("effects.impact-smoke-duration-ticks", 200, 0, 12_000);
        impactSmokeRadius = config.getDouble("effects.impact-smoke-radius", 3.5, 0.0, 64.0);

        modelCamo = config.block(model + "camo-material", Material.SMOOTH_SANDSTONE);
        modelBarrel = config.block(model + "barrel-material", Material.SMOOTH_SANDSTONE);
        modelMetal = config.block(model + "metal-material", Material.POLISHED_DEEPSLATE);
        modelWheel = config.block(model + "wheel-material", Material.BLACK_CONCRETE);
        shellMaterial = config.block("effects.shell.material", Material.MAGMA_BLOCK);
        shellGlow = config.getBoolean("effects.shell.glow", true);
    }
}
