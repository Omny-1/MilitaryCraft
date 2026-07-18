package me.bibo.militarycraft.vehicles.helicopter.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Typed, validated snapshot of config.yml. Re-created on every reload.
 */
public final class HelicopterConfig {

    // flight
    public final double maxSpeed;
    public final double reverseSpeed;
    public final double acceleration;
    public final double braking;
    public final double turnRate;
    public final double bankFactor;
    public final double maxBank;
    public final double rollReturnSpeed;

    // vertical / hover
    public final double trimClimbRate;
    public final double trimFullPitch;
    public final double verticalAccel;
    public final double neutralDrift;
    public final double boostClimb;
    public final double maxSink;
    public final boolean groundRest;

    // crash
    public final double crashSpeed;
    public final float crashExplosionPower;
    public final boolean crashBreakBlocks;
    public final boolean crashSetFire;

    // seats
    public final int seatsCapacity;
    public final int mountGraceTicks;

    // engine (collective boost) heat
    public final double burnerHeatPerTick;
    public final double burnerCoolPerTick;
    public final double burnerResumeThreshold;

    // bombs
    public final int bombCooldownTicks;
    public final int bombLoad;
    public final int bombRegenTicks;
    public final double bombGravity;
    public final int bombSubsteps;
    public final int bombLifetimeTicks;
    public final double bombInheritVelocity;
    public final float bombExplosionPower;
    public final boolean bombBreakBlocks;
    public final boolean bombSetFire;
    public final double bombHeliDamage;

    // rockets
    public final int rocketReloadTicks;
    public final int rocketMagazine;
    public final int rocketRegenTicks;
    public final double rocketSpeed;
    public final double rocketGravity;
    public final int rocketSubsteps;
    public final int rocketLifetimeTicks;
    public final double rocketMaxRange;
    public final float rocketExplosionPower;
    public final boolean rocketBreakBlocks;
    public final boolean rocketSetFire;
    public final double rocketSpread;
    public final double rocketHeliDamage;

    public final int maxActiveMunitions;

    // durability
    public final double creeperDamage;
    public final double creepersToDestroy;
    public final double maxHealth;
    public final double contactRadius;
    // weapon damage dealt to the helicopter itself, as a percent of its max HP per hit
    public final double weaponMeleePercent;
    public final double weaponArrowPercent;
    public final double weaponFireballPercent;
    public final int weaponMeleeCooldownMs;

    // effects
    public final boolean engineTrail;
    public final boolean burnerSteam;
    public final int impactSmokeDuration;
    public final double impactSmokeRadius;
    public final int bombTrailDensity;
    public final int ejectSlowFallTicks;

    // hud
    public final int hudInterval;

    // limits
    public final int maxPerPlayer;
    public final int maxLoaded;

    // placement / destruction
    public final boolean consumeItem;
    public final double spawnHeight;
    public final boolean dropItemOnDestroy;
    public final boolean debris;

    // performance / render smoothing
    public final float viewRange;
    public final int modelTeleportDuration;
    public final int modelInterpolationDuration;

    // model materials
    public final Material bodyBlock;
    public final Material camoBlock;
    public final Material glassBlock;
    public final Material rotorBlock;
    public final Material hubBlock;
    public final Material gearBlock;
    public final Material engineBlock;
    public final Material accentBlock;
    public final String tailNumber;

    // altitude ceiling
    public final boolean altitudeEnabled;
    public final double altitudeMaxY;
    public final double altitudeDamage;
    public final double altitudeDamagePer10;
    public final int altitudeIntervalTicks;
    public final String altitudeMessage;

    public final boolean debug;

    public HelicopterConfig(Plugin plugin, ConfigurationSection section) {
        ConfigurationSection c = section != null ? section : new YamlConfiguration();

        maxSpeed = c.getDouble("flight.max-speed", 0.62);
        reverseSpeed = c.getDouble("flight.reverse-speed", 0.28);
        acceleration = c.getDouble("flight.acceleration", 0.03);
        braking = c.getDouble("flight.braking", 0.04);
        turnRate = c.getDouble("flight.turn-rate", 3.6);
        bankFactor = c.getDouble("flight.bank-factor", 1.2);
        maxBank = c.getDouble("flight.max-bank", 18.0);
        rollReturnSpeed = c.getDouble("flight.roll-return-speed", 2.5);

        trimClimbRate = c.getDouble("flight.trim-climb-rate", 0.24);
        trimFullPitch = Math.max(5.0, c.getDouble("flight.trim-full-pitch", 55.0));
        verticalAccel = c.getDouble("flight.vertical-accel", 0.03);
        neutralDrift = c.getDouble("flight.neutral-drift", 0.0);
        boostClimb = c.getDouble("flight.boost-climb", 0.45);
        maxSink = c.getDouble("flight.max-sink", 0.5);
        groundRest = c.getBoolean("flight.ground-rest", true);

        crashSpeed = c.getDouble("crash.speed", 0.55);
        crashExplosionPower = (float) c.getDouble("crash.explosion-power", 3.5);
        crashBreakBlocks = c.getBoolean("crash.break-blocks", true);
        crashSetFire = c.getBoolean("crash.set-fire", false);

        // 1 pilot + HelicopterModel.SEAT_OFFSETS.size() passengers is the hard max.
        int maxCap = 1 + me.bibo.militarycraft.vehicles.helicopter.model.HelicopterModel.SEAT_OFFSETS.size();
        seatsCapacity = Math.max(1, Math.min(maxCap, c.getInt("seats.capacity", 4)));
        mountGraceTicks = Math.max(0, c.getInt("weapons.mount-grace-ticks", 40));
        burnerHeatPerTick = c.getDouble("engine.heat-per-tick", 0.018);
        burnerCoolPerTick = c.getDouble("engine.cool-per-tick", 0.011);
        burnerResumeThreshold = clamp01(c.getDouble("engine.resume-threshold", 0.30));

        bombCooldownTicks = Math.max(1, c.getInt("weapons.bombs.cooldown-ticks", 24));
        bombLoad = Math.max(1, c.getInt("weapons.bombs.load", 12));
        bombRegenTicks = Math.max(1, c.getInt("weapons.bombs.regen-ticks", 90));
        bombGravity = c.getDouble("weapons.bombs.gravity", 0.045);
        bombSubsteps = Math.max(1, c.getInt("weapons.bombs.substeps", 4));
        bombLifetimeTicks = c.getInt("weapons.bombs.lifetime-ticks", 240);
        bombInheritVelocity = c.getDouble("weapons.bombs.inherit-velocity", 1.0);
        bombExplosionPower = (float) c.getDouble("weapons.bombs.explosion-power", 4.5);
        bombBreakBlocks = c.getBoolean("weapons.bombs.break-blocks", true);
        bombSetFire = c.getBoolean("weapons.bombs.set-fire", false);
        bombHeliDamage = c.getDouble("weapons.bombs.heli-damage", 60.0);

        rocketReloadTicks = Math.max(1, c.getInt("weapons.rockets.reload-ticks", 5));
        rocketMagazine = Math.max(1, c.getInt("weapons.rockets.magazine", 18));
        rocketRegenTicks = Math.max(1, c.getInt("weapons.rockets.regen-ticks", 45));
        rocketSpeed = c.getDouble("weapons.rockets.speed", 3.4);
        rocketGravity = c.getDouble("weapons.rockets.gravity", 0.0);
        rocketSubsteps = Math.max(1, c.getInt("weapons.rockets.substeps", 4));
        rocketLifetimeTicks = c.getInt("weapons.rockets.lifetime-ticks", 80);
        rocketMaxRange = c.getDouble("weapons.rockets.max-range", 200.0);
        rocketExplosionPower = (float) c.getDouble("weapons.rockets.explosion-power", 2.0);
        rocketBreakBlocks = c.getBoolean("weapons.rockets.break-blocks", true);
        rocketSetFire = c.getBoolean("weapons.rockets.set-fire", false);
        rocketSpread = c.getDouble("weapons.rockets.spread-degrees", 1.5);
        rocketHeliDamage = c.getDouble("weapons.rockets.heli-damage", 35.0);

        maxActiveMunitions = Math.max(1, c.getInt("weapons.max-active-munitions", 96));

        creeperDamage = c.getDouble("durability.creeper-damage", 50.0);
        creepersToDestroy = Math.max(0.1, c.getDouble("durability.creepers-to-destroy", 2.0));
        maxHealth = creeperDamage * creepersToDestroy;
        contactRadius = c.getDouble("durability.contact-radius", 3.0);
        weaponMeleePercent = c.getDouble("combat.weapon-melee-percent", 6.0);
        weaponArrowPercent = c.getDouble("combat.weapon-arrow-percent", 10.0);
        weaponFireballPercent = c.getDouble("combat.weapon-fireball-percent", 20.0);
        weaponMeleeCooldownMs = c.getInt("combat.weapon-melee-cooldown-ms", 250);

        engineTrail = c.getBoolean("effects.engine-trail", true);
        burnerSteam = c.getBoolean("effects.burner-steam", true);
        impactSmokeDuration = c.getInt("effects.impact-smoke-duration", 160);
        impactSmokeRadius = c.getDouble("effects.impact-smoke-radius", 4.0);
        bombTrailDensity = Math.max(1, c.getInt("effects.bomb-trail-density", 1));
        ejectSlowFallTicks = c.getInt("effects.eject-slowfall-ticks", 160);

        hudInterval = Math.max(1, c.getInt("hud.update-interval-ticks", 4));

        maxPerPlayer = c.getInt("limits.max-per-player", 3);
        maxLoaded = c.getInt("limits.max-loaded", 40);

        consumeItem = c.getBoolean("placement.consume-item", true);
        spawnHeight = c.getDouble("placement.spawn-height", 2.5);
        dropItemOnDestroy = c.getBoolean("destruction.drop-item-on-destroy", false);
        debris = c.getBoolean("destruction.debris", true);

        viewRange = (float) c.getDouble("performance.view-range", 4.0);
        modelTeleportDuration = Math.max(0, c.getInt("model.teleport-duration", 3));
        modelInterpolationDuration = Math.max(0, c.getInt("model.interpolation-duration", 3));

        bodyBlock = block(plugin, c.getString("model.body-block"), Material.GREEN_TERRACOTTA);
        camoBlock = block(plugin, c.getString("model.camo-block"), Material.GREEN_CONCRETE_POWDER);
        glassBlock = block(plugin, c.getString("model.glass-block"), Material.BLACK_CONCRETE);
        rotorBlock = block(plugin, c.getString("model.rotor-block"), Material.POLISHED_BLACKSTONE);
        hubBlock = block(plugin, c.getString("model.hub-block"), Material.IRON_BLOCK);
        gearBlock = block(plugin, c.getString("model.gear-block"), Material.BLACK_CONCRETE);
        engineBlock = block(plugin, c.getString("model.engine-block"), Material.GRAY_CONCRETE);
        accentBlock = block(plugin, c.getString("model.accent-block"), Material.RED_CONCRETE);
        tailNumber = c.getString("model.tail-number", "24");

        altitudeEnabled = c.getBoolean("altitude.enabled", true);
        altitudeMaxY = c.getDouble("altitude.max-y", 240.0);
        altitudeDamage = Math.max(0.0, c.getDouble("altitude.damage", 2.0));
        altitudeDamagePer10 = Math.max(0.0, c.getDouble("altitude.damage-per-10-blocks", 1.0));
        altitudeIntervalTicks = Math.max(1, c.getInt("altitude.interval-ticks", 20));
        altitudeMessage = c.getString("altitude.message", "Atmospheric pressure is crushing you - altitude too high!");

        debug = c.getBoolean("debug", false);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
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
