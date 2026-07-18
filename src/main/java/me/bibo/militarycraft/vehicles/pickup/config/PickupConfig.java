/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.plugin.Plugin
 */
package me.bibo.militarycraft.vehicles.pickup.config;

import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

public final class PickupConfig {
    public final double maxForwardSpeed;
    public final double maxReverseSpeed;
    public final double acceleration;
    public final double accelerationMax;
    public final int accelRampTicks;
    public final double braking;
    public final double friction;
    public final double turnSpeed;
    public final double frontWheelSteerAngle;
    public final double maxStepUp;
    public final double climbRate;
    public final double groundSnapDistance;
    public final double gravity;
    public final double maxFallSpeed;
    public final double fallDamage;
    public final double fallDamageThreshold;
    public final double gunMaxElevation;
    public final double gunMaxDepression;
    public final double gunPitchSpeed;
    public final int mountGraceTicks;
    public final int fireCooldownTicks;
    public final double bulletDamage;
    public final double pickupDamage;
    public final double bulletRange;
    public final double spreadDegrees;
    public final boolean tracerEffects;
    public final int overheatShotLimit;
    public final int overheatWindowTicks;
    public final int overheatDurationTicks;
    public final double creeperDamage;
    public final double creepersToDestroy;
    public final double maxHealth;
    public final boolean rammingEnabled;
    public final double rammingDamage;
    public final double rammingKnockback;
    public final double rammingMinSpeed;
    public final int rammingCooldownMs;
    public final double rammingConeDegrees;
    public final int projectileSweepIntervalTicks;
    public final double weaponMeleePercent;
    public final double weaponArrowPercent;
    public final double weaponFireballPercent;
    public final int weaponMeleeCooldownMs;
    public final int hudInterval;
    public final boolean drownEnabled;
    public final double drownDamagePercent;
    public final int impactSmokeDuration;
    public final double impactSmokeRadius;
    public final boolean dustTrail;
    public final boolean engineSound;
    public final boolean damageSmoke;
    public final boolean consumeItem;
    public final boolean dropItemOnDestroy;
    public final boolean debris;
    public final float explosionPower;
    public final boolean breakBlocks;
    public final boolean setFire;
    public final Material hullBlock;
    public final Material frameBlock;
    public final Material detailBlock;
    public final Material seatBlock;
    public final Material wheelBlock;
    public final Material lightBlock;
    public final Material mountBlock;
    public final Material barrelBlock;
    public final double driverSeatHeight;
    public final double gunnerSeatHeight;
    public final double seatScale;
    public final boolean debug;

    public PickupConfig(Plugin plugin, ConfigurationSection c) {
        this.maxForwardSpeed = Math.max(0.0, c.getDouble("movement.max-forward-speed", 0.6));
        this.maxReverseSpeed = Math.max(0.0, c.getDouble("movement.max-reverse-speed", 0.28));
        this.acceleration = Math.max(0.0, c.getDouble("movement.acceleration", 0.02));
        this.accelerationMax = Math.max(this.acceleration, c.getDouble("movement.acceleration-max", 0.055));
        this.accelRampTicks = Math.max(1, c.getInt("movement.accel-ramp-ticks", 40));
        this.braking = Math.max(0.0, c.getDouble("movement.braking", 0.09));
        this.friction = Math.max(0.0, c.getDouble("movement.friction", 0.025));
        this.turnSpeed = Math.max(0.0, c.getDouble("movement.turn-speed", 8.0));
        this.frontWheelSteerAngle = Math.max(0.0, Math.min(45.0, c.getDouble("movement.front-wheel-steer-angle", 24.0)));
        this.maxStepUp = Math.max(0.0, c.getDouble("movement.max-step-up", 1.2));
        this.climbRate = Math.max(0.05, c.getDouble("movement.climb-rate", 0.5));
        this.groundSnapDistance = Math.max(0.25, c.getDouble("movement.ground-snap-distance", 2.0));
        this.gravity = Math.max(0.0, c.getDouble("movement.gravity", 0.08));
        double fallDefault = Math.max(0.05, this.groundSnapDistance * 0.9);
        this.maxFallSpeed = Math.min(Math.max(0.05, c.getDouble("movement.max-fall-speed", fallDefault)), Math.max(0.05, this.groundSnapDistance * 0.95));
        this.fallDamage = Math.max(0.0, c.getDouble("movement.fall-damage", 10.0));
        this.fallDamageThreshold = Math.max(0.0, c.getDouble("movement.fall-damage-threshold", 0.8));
        this.gunMaxElevation = Math.max(0.0, c.getDouble("gunner.max-elevation", 25.0));
        this.gunMaxDepression = Math.max(0.0, c.getDouble("gunner.max-depression", 35.0));
        this.gunPitchSpeed = Math.max(0.0, c.getDouble("gunner.pitch-speed", 12.0));
        this.mountGraceTicks = Math.max(0, c.getInt("weapon.mount-grace-ticks", 6));
        this.fireCooldownTicks = Math.max(1, c.getInt("weapon.fire-cooldown-ticks", 3));
        this.bulletDamage = Math.max(0.0, c.getDouble("weapon.bullet-damage", 3.0));
        this.pickupDamage = Math.max(0.0, c.getDouble("weapon.pickup-damage", 3.0));
        this.bulletRange = Math.max(1.0, c.getDouble("weapon.bullet-range", 55.0));
        this.spreadDegrees = Math.max(0.0, c.getDouble("weapon.spread-degrees", 1.2));
        this.tracerEffects = c.getBoolean("weapon.tracer-effects", true);
        this.overheatShotLimit = Math.max(1, c.getInt("weapon.overheat-shot-limit", 15));
        this.overheatWindowTicks = Math.max(1, c.getInt("weapon.overheat-window-ticks", 200));
        this.overheatDurationTicks = Math.max(1, c.getInt("weapon.overheat-duration-ticks", 60));
        this.creeperDamage = Math.max(0.0, c.getDouble("durability.creeper-damage", 40.0));
        this.creepersToDestroy = Math.max(1.0, c.getDouble("durability.creepers-to-destroy", 1.5));
        this.maxHealth = Math.max(1.0, this.creeperDamage * this.creepersToDestroy);
        this.rammingEnabled = c.getBoolean("combat.ramming-enabled", true);
        this.rammingDamage = Math.max(0.0, c.getDouble("combat.ramming-damage", 3.0));
        this.rammingKnockback = Math.max(0.0, c.getDouble("combat.ramming-knockback", 0.8));
        this.rammingMinSpeed = Math.max(0.0, c.getDouble("combat.ramming-min-speed", 0.15));
        this.rammingCooldownMs = Math.max(50, c.getInt("combat.ramming-cooldown-ms", 600));
        this.rammingConeDegrees = Math.max(10.0, Math.min(180.0, c.getDouble("combat.ramming-cone-degrees", 80.0)));
        this.projectileSweepIntervalTicks = Math.max(1, c.getInt("combat.projectile-sweep-interval-ticks", 2));
        this.weaponMeleePercent = Math.max(0.0, c.getDouble("combat.weapon-melee-percent", 8.0));
        this.weaponArrowPercent = Math.max(0.0, c.getDouble("combat.weapon-arrow-percent", 10.0));
        this.weaponFireballPercent = Math.max(0.0, c.getDouble("combat.weapon-fireball-percent", 25.0));
        this.weaponMeleeCooldownMs = Math.max(50, c.getInt("combat.weapon-melee-cooldown-ms", 250));
        this.hudInterval = Math.max(1, c.getInt("hud.update-interval-ticks", 4));
        this.drownEnabled = c.getBoolean("water.drown-enabled", true);
        this.drownDamagePercent = Math.max(0.0, c.getDouble("water.drown-damage-percent", 4.0));
        this.impactSmokeDuration = Math.max(0, c.getInt("effects.impact-smoke-duration", 15));
        this.impactSmokeRadius = Math.max(0.0, c.getDouble("effects.impact-smoke-radius", 2.0));
        this.dustTrail = c.getBoolean("effects.dust-trail", true);
        this.engineSound = c.getBoolean("effects.engine-sound", false);
        this.damageSmoke = c.getBoolean("effects.damage-smoke", true);
        this.consumeItem = c.getBoolean("placement.consume-item", true);
        this.dropItemOnDestroy = c.getBoolean("destruction.drop-item-on-destroy", false);
        this.debris = c.getBoolean("destruction.debris", true);
        this.explosionPower = (float)Math.max(0.0, c.getDouble("destruction.explosion-power", 1.6));
        this.breakBlocks = c.getBoolean("destruction.break-blocks", false);
        this.setFire = c.getBoolean("destruction.set-fire", false);
        this.hullBlock = PickupConfig.block(plugin, c.getString("model.hull-block"), Material.WHITE_CONCRETE);
        this.frameBlock = PickupConfig.block(plugin, c.getString("model.frame-block"), Material.POLISHED_BLACKSTONE);
        this.detailBlock = PickupConfig.block(plugin, c.getString("model.detail-block"), Material.MOSS_BLOCK);
        this.seatBlock = PickupConfig.block(plugin, c.getString("model.seat-block"), Material.BLACK_WOOL);
        this.wheelBlock = PickupConfig.block(plugin, c.getString("model.wheel-block"), Material.BLACK_CONCRETE);
        this.lightBlock = PickupConfig.block(plugin, c.getString("model.light-block"), Material.SHROOMLIGHT);
        this.mountBlock = PickupConfig.block(plugin, c.getString("model.mount-block"), Material.POLISHED_BLACKSTONE);
        this.barrelBlock = PickupConfig.block(plugin, c.getString("model.barrel-block"), Material.POLISHED_BLACKSTONE);
        this.driverSeatHeight = Math.max(-3.0, c.getDouble("model.driver-seat-height", 1.0));
        this.gunnerSeatHeight = Math.max(-3.0, c.getDouble("model.gunner-seat-height", 1.8));
        this.seatScale = Math.max(0.0625, Math.min(1.0, c.getDouble("model.seat-scale", 0.5)));
        this.debug = c.getBoolean("debug", false);
    }

    private static Material block(Plugin plugin, String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material m = Material.matchMaterial((String)name.trim());
        if (m == null || !m.isBlock()) {
            plugin.getLogger().log(Level.WARNING, "Invalid block material '" + name + "', using " + String.valueOf(fallback));
            return fallback;
        }
        return m;
    }
}
