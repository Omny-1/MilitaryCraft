package me.bibo.militarycraft.vehicles.moto.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Immutable, validated snapshot of {@code config.yml}. A new instance is built
 * on every reload, so a drive tick always observes one internally consistent set
 * of values.
 */
public final class MotoConfig {

    // Movement.
    public final double maxForwardSpeed;
    public final double maxReverseSpeed;
    public final double acceleration;
    public final double braking;
    public final double friction;
    public final double maxTurnSpeed;
    public final double highSpeedTurnFactor;
    public final double handlebarAngle;
    public final double steeringReturnSpeed;
    public final double maxStepUp;
    public final double climbRate;
    public final double groundSnapDistance;
    public final double gravity;
    public final double fallDamage;
    public final double fallDamageThreshold;

    // Collision with living entities.
    public final boolean impactEnabled;
    public final double impactMinSpeed;
    public final double impactReach;
    public final int impactCooldownMs;
    public final double highSpeedFraction;
    public final double highSpeedKnockback;
    public final double highSpeedVertical;
    public final double highSpeedDamage;
    public final double lowSpeedDamage;
    public final double lowSpeedKnockback;
    public final double lowSpeedMinHealth;

    // Durability and weapons.
    public final double creeperDamage;
    public final double creepersToDestroy;
    public final double maxHealth;
    public final double weaponMeleePercent;
    public final double weaponArrowPercent;
    public final double weaponFireballPercent;
    public final int weaponMeleeCooldownMs;

    // Water, HUD and placement.
    public final boolean drownEnabled;
    public final double drownDamagePercent;
    public final int hudInterval;
    public final boolean consumeItem;
    public final int maxMotorcyclesTotal;
    public final int maxMotorcyclesPerPlayer;
    public final int maxMotorcyclesPerChunk;
    public final int spawnCooldownSeconds;
    public final double minMotorcycleSpacing;
    public final boolean dropItemOnDestroy;
    public final boolean debris;

    // Background work cadence.
    public final int projectileSweepInterval;
    public final int waterCheckInterval;
    public final int transientRepairInterval;
    public final String antiAirScoreboardTag;

    // Model palette.
    public final Material bodyBlock;
    public final Material frameBlock;
    public final Material engineBlock;
    public final Material wheelBlock;
    public final Material hubBlock;
    public final Material glassBlock;
    public final Material seatBlock;
    public final Material detailBlock;
    public final Material lightBlock;
    public final Material indicatorBlock;
    public final Material exhaustBlock;

    // Seat anchors in motorcycle-local coordinates.
    public final double driverSeatHeight;
    public final double driverSeatZ;
    public final double pillionSeatHeight;
    public final double sidecarSeatHeight;

    public final boolean debug;

    public MotoConfig(Plugin plugin, ConfigurationSection c) {

        maxForwardSpeed = number(plugin, c, "movement.max-forward-speed", 0.9167, 0.02, 2.0);
        maxReverseSpeed = number(plugin, c, "movement.max-reverse-speed", 0.10, 0.0, maxForwardSpeed);
        acceleration = number(plugin, c, "movement.acceleration", 0.012, 0.0001, 1.0);
        braking = number(plugin, c, "movement.braking", 0.035, 0.0001, 1.0);
        friction = number(plugin, c, "movement.friction", 0.008, 0.0, 1.0);
        maxTurnSpeed = number(plugin, c, "movement.max-turn-speed", 4.8, 0.0, 30.0);
        highSpeedTurnFactor = number(plugin, c, "movement.high-speed-turn-factor", 0.48, 0.05, 1.0);
        handlebarAngle = number(plugin, c, "movement.handlebar-angle", 32.0, 0.0, 60.0);
        steeringReturnSpeed = number(plugin, c, "movement.steering-return-speed", 8.0, 0.1, 90.0);
        maxStepUp = number(plugin, c, "movement.max-step-up", 1.1, 0.0, 2.0);
        climbRate = number(plugin, c, "movement.climb-rate", 0.5, 0.01, 2.0);
        groundSnapDistance = number(plugin, c, "movement.ground-snap-distance", 1.6, 0.1, 16.0);
        gravity = number(plugin, c, "movement.gravity", 0.08, 0.0, 1.0);
        fallDamage = number(plugin, c, "movement.fall-damage", 10.0, 0.0, 1000.0);
        fallDamageThreshold = number(plugin, c, "movement.fall-damage-threshold", 0.75, 0.0, 10.0);

        impactEnabled = c.getBoolean("impact.enabled", true);
        impactMinSpeed = number(plugin, c, "impact.min-speed", 0.08, 0.0, maxForwardSpeed);
        impactReach = number(plugin, c, "impact.reach", 0.25, 0.0, 4.0);
        impactCooldownMs = integer(plugin, c, "impact.hit-cooldown-ms", 750, 0, 60_000);
        highSpeedFraction = number(plugin, c, "impact.high-speed-fraction", 0.75, 0.05, 1.0);
        highSpeedKnockback = number(plugin, c, "impact.high-speed-knockback", 1.55, 0.0, 10.0);
        highSpeedVertical = number(plugin, c, "impact.high-speed-vertical", 0.48, 0.0, 5.0);
        highSpeedDamage = number(plugin, c, "impact.high-speed-damage", 5.0, 0.0, 1000.0);
        lowSpeedDamage = number(plugin, c, "impact.low-speed-damage", 2.0, 0.0, 1000.0);
        lowSpeedKnockback = number(plugin, c, "impact.low-speed-knockback", 0.38, 0.0, 10.0);
        lowSpeedMinHealth = number(plugin, c, "impact.low-speed-min-health", 1.0, 0.0, 2048.0);

        creeperDamage = number(plugin, c, "durability.creeper-damage", 50.0, 0.01, 1_000_000.0);
        creepersToDestroy = number(plugin, c, "durability.creepers-to-destroy", 1.5, 0.01, 10_000.0);
        maxHealth = creeperDamage * creepersToDestroy;
        weaponMeleePercent = number(plugin, c, "durability.weapon-melee-percent", 8.0, 0.0, 1000.0);
        weaponArrowPercent = number(plugin, c, "durability.weapon-arrow-percent", 12.0, 0.0, 1000.0);
        weaponFireballPercent = number(plugin, c, "durability.weapon-fireball-percent", 25.0, 0.0, 1000.0);
        weaponMeleeCooldownMs = integer(plugin, c, "durability.weapon-melee-cooldown-ms", 300, 0, 60_000);

        drownEnabled = c.getBoolean("water.drown-enabled", true);
        drownDamagePercent = number(plugin, c, "water.drown-damage-percent", 5.0, 0.0, 1000.0);
        hudInterval = integer(plugin, c, "hud.update-interval-ticks", 4, 1, 1200);

        consumeItem = c.getBoolean("placement.consume-item", true);
        maxMotorcyclesTotal = integer(plugin, c, "placement.max-motorcycles-total", 20, 0, 100_000);
        maxMotorcyclesPerPlayer = integer(plugin, c, "placement.max-motorcycles-per-player", 2, 0, 10_000);
        maxMotorcyclesPerChunk = integer(plugin, c, "placement.max-motorcycles-per-chunk", 4, 0, 10_000);
        spawnCooldownSeconds = integer(plugin, c, "placement.spawn-cooldown-seconds", 10, 0, 86_400);
        minMotorcycleSpacing = number(plugin, c, "placement.min-motorcycle-spacing", 3.5, 0.0, 1000.0);
        dropItemOnDestroy = c.getBoolean("destruction.drop-item-on-destroy", false);
        debris = c.getBoolean("destruction.debris", true);

        projectileSweepInterval = integer(plugin, c, "performance.projectile-sweep-interval-ticks", 1, 1, 3);
        waterCheckInterval = integer(plugin, c, "performance.water-check-interval-ticks", 5, 1, 1200);
        transientRepairInterval = integer(plugin, c, "performance.transient-repair-interval-ticks", 20, 1, 1200);
        antiAirScoreboardTag = text(c, "compatibility.anti-air-scoreboard-tag",
                "antiaircraft_entity", 64);

        bodyBlock = block(plugin, c.getString("model.body-block"), Material.RED_CONCRETE);
        frameBlock = block(plugin, c.getString("model.frame-block"), Material.POLISHED_BLACKSTONE);
        engineBlock = block(plugin, c.getString("model.engine-block"), Material.IRON_BLOCK);
        wheelBlock = block(plugin, c.getString("model.wheel-block"), Material.BLACK_CONCRETE);
        hubBlock = block(plugin, c.getString("model.hub-block"), Material.LIGHT_GRAY_CONCRETE);
        glassBlock = block(plugin, c.getString("model.glass-block"), Material.TINTED_GLASS);
        seatBlock = block(plugin, c.getString("model.seat-block"), Material.BLACK_WOOL);
        detailBlock = block(plugin, c.getString("model.detail-block"), Material.GRAY_CONCRETE);
        lightBlock = block(plugin, c.getString("model.light-block"), Material.SEA_LANTERN);
        indicatorBlock = block(plugin, c.getString("model.indicator-block"), Material.ORANGE_CONCRETE);
        exhaustBlock = block(plugin, c.getString("model.exhaust-block"), Material.SMOOTH_STONE);

        driverSeatHeight = number(plugin, c, "model.driver-seat-height", 0.35, -2.0, 8.0);
        driverSeatZ = number(plugin, c, "model.driver-seat-z", -0.32, -8.0, 8.0);
        pillionSeatHeight = number(plugin, c, "model.pillion-seat-height", 0.4, -2.0, 8.0);
        sidecarSeatHeight = number(plugin, c, "model.sidecar-seat-height", 0.48, -2.0, 8.0);

        debug = c.getBoolean("debug", false);
    }

    private static double number(Plugin plugin, ConfigurationSection c, String path,
                                 double fallback, double min, double max) {
        double value = c.getDouble(path, fallback);
        if (!Double.isFinite(value)) {
            warn(plugin, path, "must be finite", fallback);
            return fallback;
        }
        if (value < min || value > max) {
            double clamped = Math.max(min, Math.min(max, value));
            warn(plugin, path, "must be in [" + min + ", " + max + "]", clamped);
            return clamped;
        }
        return value;
    }

    private static int integer(Plugin plugin, ConfigurationSection c, String path,
                               int fallback, int min, int max) {
        int value = c.getInt(path, fallback);
        if (value < min || value > max) {
            int clamped = Math.max(min, Math.min(max, value));
            warn(plugin, path, "must be in [" + min + ", " + max + "]", clamped);
            return clamped;
        }
        return value;
    }

    private static Material block(Plugin plugin, String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim());
        if (material == null || !material.isBlock()) {
            plugin.getLogger().log(Level.WARNING,
                    "Invalid block material '" + name + "', using " + fallback);
            return fallback;
        }
        return material;
    }

    private static String text(ConfigurationSection c, String path, String fallback, int maxLength) {
        String value = c.getString(path, fallback);
        if (value == null) {
            return fallback;
        }
        value = value.trim();
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static void warn(Plugin plugin, String path, String requirement, Object replacement) {
        plugin.getLogger().warning("Invalid config value at '" + path + "': "
                + requirement + "; using " + replacement);
    }
}
