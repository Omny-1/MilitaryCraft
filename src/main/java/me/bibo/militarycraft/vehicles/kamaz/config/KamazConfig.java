package me.bibo.militarycraft.vehicles.kamaz.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Typed, validated snapshot of config.yml. Re-created on every reload.
 */
public final class KamazConfig {

    // movement
    public final double maxForwardSpeed;
    public final double maxReverseSpeed;
    public final double acceleration;
    public final double braking;
    public final double friction;
    public final double turnSpeed;
    public final double frontWheelSteerAngle;
    public final double maxStepUp;
    public final double climbRate;
    public final double groundSnapDistance;
    public final double gravity;
    public final double fallDamage;
    public final double fallDamageThreshold;

    // run-over (the signature mechanic)
    public final boolean runOverEnabled;
    public final double runOverMinSpeed;
    public final double runOverReach;
    public final int hitCooldownMs;
    /** Fraction of top speed at/above which the truck FLINGS instead of crushing. */
    public final double flingSpeedFraction;
    public final double flingKnockback;
    public final double flingVertical;
    public final double flingDamage;
    public final double crushDamage;
    public final double crushKnockback;
    /** The crush never reduces a victim below this much health (so it can't kill). */
    public final double crushMinHealth;

    // durability
    public final double creeperDamage;
    public final double creepersToDestroy;
    public final double maxHealth;
    // weapon damage dealt TO THE TRUCK itself, as a percent of its max HP per hit
    public final double weaponMeleePercent;
    public final double weaponArrowPercent;
    public final double weaponFireballPercent;
    public final int weaponMeleeCooldownMs;

    // water
    public final boolean drownEnabled;
    public final double drownDamagePercent;

    // hud
    public final int hudInterval;

    // placement / destruction
    public final boolean consumeItem;
    public final int maxTrucksTotal;
    public final int maxTrucksPerPlayer;
    public final int spawnCooldownSeconds;
    public final double minTruckSpacing;
    public final boolean dropItemOnDestroy;
    public final boolean debris;

    // performance
    public final int projectileSweepInterval;
    public final int waterCheckInterval;
    public final int runOverScanInterval;

    // model materials
    public final Material hullBlock;
    public final Material cabBlock;
    public final Material chassisBlock;
    public final Material wheelBlock;
    public final Material hubBlock;
    public final Material glassBlock;
    public final Material detailBlock;
    public final Material lightBlock;
    public final Material bumperBlock;
    /** How high above the truck's ground anchor the driver's seat (camera) sits. */
    public final double seatHeight;
    /** How far the driver's seat is pushed toward the rear (so F5 clears the cab). */
    public final double seatBackOffset;
    /** How high passengers sit inside the troop compartment. */
    public final double passengerSeatHeight;

    public final boolean debug;

    public KamazConfig(Plugin plugin, ConfigurationSection section) {
        ConfigurationSection c = section != null ? section : new YamlConfiguration();

        // Faster top speed (0.65 ≈ 47 km/h) with a heavy 6-second spool-up:
        // 0.65 / 0.0054167 = ~120 ticks = ~6 s to reach top speed.
        maxForwardSpeed = c.getDouble("movement.max-forward-speed", 0.65);
        maxReverseSpeed = c.getDouble("movement.max-reverse-speed", 0.18);
        acceleration = c.getDouble("movement.acceleration", 0.0054167);
        braking = c.getDouble("movement.braking", 0.03);
        friction = c.getDouble("movement.friction", 0.015);
        turnSpeed = c.getDouble("movement.turn-speed", 5.0);
        frontWheelSteerAngle = MathClamp(c.getDouble("movement.front-wheel-steer-angle", 28.0), 0.0, 45.0);
        maxStepUp = c.getDouble("movement.max-step-up", 1.2);
        climbRate = Math.max(0.05, c.getDouble("movement.climb-rate", 0.5));
        groundSnapDistance = c.getDouble("movement.ground-snap-distance", 2.0);
        gravity = c.getDouble("movement.gravity", 0.08);
        fallDamage = c.getDouble("movement.fall-damage", 16.0);
        fallDamageThreshold = c.getDouble("movement.fall-damage-threshold", 0.9);

        runOverEnabled = c.getBoolean("run-over.enabled", true);
        runOverMinSpeed = c.getDouble("run-over.min-speed", 0.05);
        runOverReach = c.getDouble("run-over.reach", 0.6);
        hitCooldownMs = Math.max(0, c.getInt("run-over.hit-cooldown-ms", 600));
        flingSpeedFraction = MathClamp(c.getDouble("run-over.fling-speed-fraction", 0.92), 0.1, 1.0);
        flingKnockback = c.getDouble("run-over.fling-knockback", 2.4);
        flingVertical = c.getDouble("run-over.fling-vertical", 0.85);
        flingDamage = c.getDouble("run-over.fling-damage", 6.0);
        crushDamage = c.getDouble("run-over.crush-damage", 9.0);
        crushKnockback = c.getDouble("run-over.crush-knockback", 0.6);
        crushMinHealth = Math.max(0.0, c.getDouble("run-over.crush-min-health", 4.0));

        creeperDamage = c.getDouble("durability.creeper-damage", 50.0);
        creepersToDestroy = c.getDouble("durability.creepers-to-destroy", 3.0);
        maxHealth = creeperDamage * creepersToDestroy;
        weaponMeleePercent = c.getDouble("durability.weapon-melee-percent", 4.0);
        weaponArrowPercent = c.getDouble("durability.weapon-arrow-percent", 6.0);
        weaponFireballPercent = c.getDouble("durability.weapon-fireball-percent", 12.0);
        weaponMeleeCooldownMs = c.getInt("durability.weapon-melee-cooldown-ms", 250);

        drownEnabled = c.getBoolean("water.drown-enabled", true);
        drownDamagePercent = c.getDouble("water.drown-damage-percent", 2.5);

        hudInterval = Math.max(1, c.getInt("hud.update-interval-ticks", 4));

        consumeItem = c.getBoolean("placement.consume-item", true);
        maxTrucksTotal = Math.max(0, c.getInt("placement.max-trucks-total", 30));
        maxTrucksPerPlayer = Math.max(0, c.getInt("placement.max-trucks-per-player", 1));
        spawnCooldownSeconds = Math.max(0, c.getInt("placement.spawn-cooldown-seconds", 30));
        minTruckSpacing = Math.max(0.0, c.getDouble("placement.min-truck-spacing", 5.5));
        dropItemOnDestroy = c.getBoolean("destruction.drop-item-on-destroy", false);
        debris = c.getBoolean("destruction.debris", true);

        projectileSweepInterval = Math.max(1, c.getInt("performance.projectile-sweep-interval-ticks", 3));
        waterCheckInterval = Math.max(1, c.getInt("performance.water-check-interval-ticks", 5));
        runOverScanInterval = Math.max(1, c.getInt("performance.run-over-scan-interval-ticks", 2));

        hullBlock = block(plugin, c.getString("model.hull-block"), Material.GREEN_CONCRETE);
        cabBlock = block(plugin, c.getString("model.cab-block"), Material.GREEN_CONCRETE);
        chassisBlock = block(plugin, c.getString("model.chassis-block"), Material.BLACK_CONCRETE);
        wheelBlock = block(plugin, c.getString("model.wheel-block"), Material.BLACK_CONCRETE);
        hubBlock = block(plugin, c.getString("model.hub-block"), Material.GRAY_CONCRETE);
        glassBlock = block(plugin, c.getString("model.glass-block"), Material.TINTED_GLASS);
        detailBlock = block(plugin, c.getString("model.detail-block"), Material.MOSS_BLOCK);
        lightBlock = block(plugin, c.getString("model.light-block"), Material.SEA_LANTERN);
        bumperBlock = block(plugin, c.getString("model.bumper-block"), Material.POLISHED_BLACKSTONE);
        seatHeight = c.getDouble("model.seat-height", 3.2);
        seatBackOffset = c.getDouble("model.seat-back-offset", 1.5);
        passengerSeatHeight = c.getDouble("model.passenger-seat-height", 1.4);

        debug = c.getBoolean("debug", false);
    }

    private static double MathClamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static Material block(Plugin plugin, String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material m = Material.matchMaterial(name.trim());
        if (m == null || !m.isBlock()) {
            plugin.getLogger().log(Level.WARNING,
                    "Invalid block material '" + name + "', using " + fallback);
            return fallback;
        }
        return m;
    }
}
