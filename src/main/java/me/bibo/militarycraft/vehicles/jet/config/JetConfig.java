package me.bibo.militarycraft.vehicles.jet.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Typed, validated snapshot of config.yml. Re-created on every reload.
 */
public final class JetConfig {

    // flight
    public final double maxSpeed;
    public final double boostSpeed;
    public final double acceleration;
    public final double braking;
    public final double coastDeceleration;
    public final double turnRate;
    public final double pitchRate;
    public final double maxPitch;
    public final double rollSpeed;
    public final double rollReturnSpeed;
    public final double autoBankFactor;
    public final double maxBank;
    public final double gravity;
    public final double liftSpeed;
    public final double crashSpeed;
    public final double stallSpeed;
    public final double stallSink;
    public final boolean groundRest;

    // anti-camp: lose altitude when too slow (only when airborne)
    public final double stallFallKmh;
    public final double stallFastKmh;
    public final double stallCriticalKmh;
    public final double stallSinkMild;
    public final double stallSinkFast;
    public final double stallSinkCritical;
    public final int stallClearance;

    // kamikaze crash
    public final float crashExplosionPower;
    public final boolean crashBreakBlocks;
    public final boolean crashSetFire;

    // afterburner
    public final double boostHeatPerTick;
    public final double boostCoolPerTick;
    public final double boostResumeThreshold;

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
    public final double rocketJetDamage;
    public final double rocketSpread;

    // bombs
    public final int bombReloadTicks;
    public final int bombLoad;
    public final int bombRegenTicks;
    public final double bombGravity;
    public final int bombSubsteps;
    public final int bombLifetimeTicks;
    public final float bombExplosionPower;
    public final boolean bombBreakBlocks;
    public final boolean bombSetFire;
    public final double bombJetDamage;
    public final double bombInheritVelocity;
    public final int maxActiveMunitions;

    // durability
    public final double creeperDamage;
    public final double creepersToDestroy;
    public final double maxHealth;
    // weapon damage dealt to the vehicle itself, as a percent of its max HP per hit
    public final double weaponMeleePercent;
    public final double weaponArrowPercent;
    public final double weaponFireballPercent;
    public final int weaponMeleeCooldownMs;

    // altitude ceiling
    public final boolean altitudeEnabled;
    public final double altitudeMaxY;
    public final double altitudeDamage;
    public final double altitudeDamagePer10;
    public final int altitudeIntervalTicks;
    public final String altitudeMessage;

    // riding
    public final int mountGraceTicks;

    // effects
    public final boolean engineTrail;
    public final int impactSmokeDuration;
    public final double impactSmokeRadius;
    public final int ejectSlowFallTicks;

    // hud
    public final int hudInterval;

    // placement / destruction
    public final boolean consumeItem;
    public final boolean dropItemOnDestroy;
    public final boolean debris;

    // model materials
    public final Material bodyBlock;
    public final Material wingBlock;
    public final Material noseBlock;
    public final Material canopyBlock;
    public final Material engineBlock;
    public final Material nozzleBlock;
    public final Material missileBlock;
    public final Material accentBlock;
    public final String tailNumber;

    // model render smoothing (ticks). teleport-duration governs how the parts
    // follow the moving jet; 0 = snap in lockstep with the (snapping) ridden
    // camera so the model doesn't appear to jerk fore/aft against it.
    public final int modelTeleportDuration;
    public final int modelInterpolationDuration;

    public final boolean debug;

    public JetConfig(Plugin plugin, ConfigurationSection section) {
        ConfigurationSection c = section != null ? section : new YamlConfiguration();

        maxSpeed = c.getDouble("flight.max-speed", 1.6);
        boostSpeed = c.getDouble("flight.boost-speed", 2.6);
        acceleration = c.getDouble("flight.acceleration", 0.06);
        braking = c.getDouble("flight.braking", 0.12);
        coastDeceleration = c.getDouble("flight.coast-deceleration", 0.025);
        turnRate = c.getDouble("flight.turn-rate", 6.0);
        pitchRate = c.getDouble("flight.pitch-rate", 6.0);
        maxPitch = c.getDouble("flight.max-pitch", 85.0);
        rollSpeed = c.getDouble("flight.roll-speed", 16.0);
        rollReturnSpeed = c.getDouble("flight.roll-return-speed", 9.0);
        autoBankFactor = c.getDouble("flight.auto-bank-factor", 4.0);
        maxBank = c.getDouble("flight.max-bank", 35.0);
        gravity = c.getDouble("flight.gravity", 0.10);
        liftSpeed = c.getDouble("flight.lift-speed", 0.70);
        crashSpeed = c.getDouble("flight.crash-speed", 0.65);
        stallSpeed = c.getDouble("flight.stall-speed", 0.14);
        stallSink = c.getDouble("flight.stall-sink", 0.7);
        groundRest = c.getBoolean("flight.ground-rest", true);

        stallFallKmh = c.getDouble("flight.stall.fall-kmh", 90.0);
        stallFastKmh = c.getDouble("flight.stall.fast-kmh", 60.0);
        stallCriticalKmh = c.getDouble("flight.stall.critical-kmh", 30.0);
        stallSinkMild = c.getDouble("flight.stall.sink-mild", 0.22);
        stallSinkFast = c.getDouble("flight.stall.sink-fast", 0.5);
        stallSinkCritical = c.getDouble("flight.stall.sink-critical", 0.9);
        stallClearance = Math.max(1, c.getInt("flight.stall.clearance-blocks", 5));

        crashExplosionPower = (float) c.getDouble("crash.explosion-power", 4.0);
        crashBreakBlocks = c.getBoolean("crash.break-blocks", true);
        crashSetFire = c.getBoolean("crash.set-fire", true);

        boostHeatPerTick = c.getDouble("afterburner.heat-per-tick", 0.02);
        boostCoolPerTick = c.getDouble("afterburner.cool-per-tick", 0.012);
        boostResumeThreshold = clamp01(c.getDouble("afterburner.resume-threshold", 0.35));

        rocketReloadTicks = Math.max(1, c.getInt("weapons.rockets.reload-ticks", 6));
        rocketMagazine = Math.max(1, c.getInt("weapons.rockets.magazine", 12));
        rocketRegenTicks = Math.max(1, c.getInt("weapons.rockets.regen-ticks", 40));
        rocketSpeed = c.getDouble("weapons.rockets.speed", 3.4);
        rocketGravity = c.getDouble("weapons.rockets.gravity", 0.0);
        rocketSubsteps = Math.max(1, c.getInt("weapons.rockets.substeps", 4));
        rocketLifetimeTicks = c.getInt("weapons.rockets.lifetime-ticks", 80);
        rocketMaxRange = c.getDouble("weapons.rockets.max-range", 220.0);
        rocketExplosionPower = (float) c.getDouble("weapons.rockets.explosion-power", 2.0);
        rocketBreakBlocks = c.getBoolean("weapons.rockets.break-blocks", true);
        rocketSetFire = c.getBoolean("weapons.rockets.set-fire", false);
        rocketJetDamage = c.getDouble("weapons.rockets.jet-damage", 35.0);
        rocketSpread = c.getDouble("weapons.rockets.spread-degrees", 1.2);

        bombReloadTicks = Math.max(1, c.getInt("weapons.bombs.reload-ticks", 30));
        bombLoad = Math.max(1, c.getInt("weapons.bombs.load", 6));
        bombRegenTicks = Math.max(1, c.getInt("weapons.bombs.regen-ticks", 110));
        bombGravity = c.getDouble("weapons.bombs.gravity", 0.06);
        bombSubsteps = Math.max(1, c.getInt("weapons.bombs.substeps", 4));
        bombLifetimeTicks = c.getInt("weapons.bombs.lifetime-ticks", 200);
        bombExplosionPower = (float) c.getDouble("weapons.bombs.explosion-power", 5.0);
        bombBreakBlocks = c.getBoolean("weapons.bombs.break-blocks", true);
        bombSetFire = c.getBoolean("weapons.bombs.set-fire", true);
        bombJetDamage = c.getDouble("weapons.bombs.jet-damage", 70.0);
        bombInheritVelocity = c.getDouble("weapons.bombs.inherit-velocity", 1.0);
        maxActiveMunitions = Math.max(1, c.getInt("weapons.max-active-munitions", 96));

        creeperDamage = c.getDouble("durability.creeper-damage", 50.0);
        creepersToDestroy = c.getDouble("durability.creepers-to-destroy", 2.0);
        maxHealth = creeperDamage * creepersToDestroy;
        weaponMeleePercent = c.getDouble("combat.weapon-melee-percent", 5.0);
        weaponArrowPercent = c.getDouble("combat.weapon-arrow-percent", 8.0);
        weaponFireballPercent = c.getDouble("combat.weapon-fireball-percent", 16.0);
        weaponMeleeCooldownMs = c.getInt("combat.weapon-melee-cooldown-ms", 250);

        altitudeEnabled = c.getBoolean("altitude.enabled", true);
        altitudeMaxY = c.getDouble("altitude.max-y", 240.0);
        altitudeDamage = Math.max(0.0, c.getDouble("altitude.damage", 2.0));
        altitudeDamagePer10 = Math.max(0.0, c.getDouble("altitude.damage-per-10-blocks", 1.0));
        altitudeIntervalTicks = Math.max(1, c.getInt("altitude.interval-ticks", 20));
        altitudeMessage = c.getString("altitude.message", "Atmospheric pressure is crushing you - altitude too high!");

        mountGraceTicks = Math.max(0, c.getInt("mount-grace-ticks", 40));

        engineTrail = c.getBoolean("effects.engine-trail", true);
        impactSmokeDuration = c.getInt("effects.impact-smoke-duration", 120);
        impactSmokeRadius = c.getDouble("effects.impact-smoke-radius", 3.5);
        ejectSlowFallTicks = c.getInt("effects.eject-slowfall-ticks", 120);

        hudInterval = Math.max(1, c.getInt("hud.update-interval-ticks", 4));

        consumeItem = c.getBoolean("placement.consume-item", true);
        dropItemOnDestroy = c.getBoolean("destruction.drop-item-on-destroy", false);
        debris = c.getBoolean("destruction.debris", true);

        bodyBlock = block(plugin, c.getString("model.body-block"), Material.LIGHT_BLUE_TERRACOTTA);
        wingBlock = block(plugin, c.getString("model.wing-block"), Material.LIGHT_BLUE_TERRACOTTA);
        noseBlock = block(plugin, c.getString("model.nose-block"), Material.LIGHT_GRAY_CONCRETE);
        canopyBlock = block(plugin, c.getString("model.canopy-block"), Material.BLACK_STAINED_GLASS);
        engineBlock = block(plugin, c.getString("model.engine-block"), Material.GRAY_CONCRETE);
        nozzleBlock = block(plugin, c.getString("model.nozzle-block"), Material.POLISHED_BLACKSTONE);
        missileBlock = block(plugin, c.getString("model.missile-block"), Material.WHITE_CONCRETE);
        accentBlock = block(plugin, c.getString("model.accent-block"), Material.RED_CONCRETE);
        tailNumber = c.getString("model.tail-number", "36");

        modelTeleportDuration = Math.max(0, c.getInt("model.teleport-duration", 1));
        modelInterpolationDuration = Math.max(0, c.getInt("model.interpolation-duration", 2));

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
