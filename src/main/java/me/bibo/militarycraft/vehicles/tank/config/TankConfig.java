package me.bibo.militarycraft.vehicles.tank.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Typed, validated snapshot of config.yml. Re-created on every reload.
 */
public final class TankConfig {

    // movement
    public final double maxForwardSpeed;
    public final double maxReverseSpeed;
    public final double acceleration;
    public final double braking;
    public final double friction;
    public final double turnSpeed;
    public final double maxStepUp;
    public final double climbRate;
    public final double groundSnapDistance;
    public final double gravity;
    public final double maxFallSpeed;
    public final double fallDamage;
    public final double fallDamageThreshold;

    // turret
    public final double maxElevation;
    public final double maxDepression;
    public final double pitchSpeed;

    // weapon
    public final int mountGraceTicks;
    public final int reloadTicks;
    public final double shellSpeed;
    public final double shellGravity;
    public final int shellSubsteps;
    public final int shellLifetimeTicks;
    public final float explosionPower;
    public final boolean breakBlocks;
    public final boolean setFire;
    public final double shellTankDamage;
    public final double recoil;
    public final double spreadDegrees;
    public final double shellMaxRange;
    public final int maxActiveShells;
    public final double overheatDamagePercent;
    public final int overheatSpamThreshold;

    // combat
    public final boolean rammingEnabled;
    public final double rammingDamage;
    public final double rammingKnockback;
    public final double rammingMinSpeed;
    public final int rammingCooldownMs;
    public final double rammingConeDegrees;
    public final int projectileSweepIntervalTicks;
    // weapon damage dealt to the vehicle itself, as a percent of its max HP per hit
    public final double weaponMeleePercent;
    public final double weaponArrowPercent;
    public final double weaponFireballPercent;
    public final int weaponMeleeCooldownMs;

    // hud
    public final int hudInterval;

    // durability
    public final double creeperDamage;
    public final double creepersToDestroy;
    public final double maxHealth;

    // water
    public final boolean drownEnabled;
    public final double drownDamagePercent;

    // effects
    public final int impactSmokeDuration;
    public final double impactSmokeRadius;
    public final boolean trackDust;
    public final boolean engineSound;
    public final boolean damageSmoke;

    // placement / destruction
    public final boolean consumeItem;
    public final boolean dropItemOnDestroy;
    public final boolean debris;

    // model materials
    public final Material hullBlock;
    public final Material turretBlock;
    public final Material detailBlock;
    public final Material trackBlock;
    public final Material wheelBlock;
    public final Material barrelBlock;
    public final String turretNumber;
    /** How high above the tank's ground anchor the driver's seat (camera) sits. */
    public final double seatHeight;

    public final boolean debug;

    public TankConfig(Plugin plugin, ConfigurationSection section) {
        ConfigurationSection c = section != null ? section : new YamlConfiguration();

        maxForwardSpeed = Math.max(0.0, c.getDouble("movement.max-forward-speed", 0.25));
        maxReverseSpeed = Math.max(0.0, c.getDouble("movement.max-reverse-speed", 0.12));
        acceleration = Math.max(0.0, c.getDouble("movement.acceleration", 0.025));
        braking = Math.max(0.0, c.getDouble("movement.braking", 0.045));
        friction = Math.max(0.0, c.getDouble("movement.friction", 0.02));
        turnSpeed = Math.max(0.0, c.getDouble("movement.turn-speed", 6.0));
        maxStepUp = Math.max(0.0, c.getDouble("movement.max-step-up", 1.2));
        climbRate = Math.max(0.05, c.getDouble("movement.climb-rate", 0.5));
        groundSnapDistance = Math.max(0.25, c.getDouble("movement.ground-snap-distance", 2.0));
        gravity = Math.max(0.0, c.getDouble("movement.gravity", 0.08));
        double fallDefault = Math.max(0.05, groundSnapDistance * 0.9);
        maxFallSpeed = Math.min(Math.max(0.05, c.getDouble("movement.max-fall-speed", fallDefault)),
                Math.max(0.05, groundSnapDistance * 0.95));
        fallDamage = Math.max(0.0, c.getDouble("movement.fall-damage", 16.0));
        fallDamageThreshold = Math.max(0.0, c.getDouble("movement.fall-damage-threshold", 0.9));

        maxElevation = Math.max(0.0, c.getDouble("turret.max-elevation", 30.0));
        maxDepression = Math.max(0.0, c.getDouble("turret.max-depression", 12.0));
        pitchSpeed = Math.max(0.0, c.getDouble("turret.pitch-speed", 8.0));

        mountGraceTicks = Math.max(0, c.getInt("weapon.mount-grace-ticks", 40));
        reloadTicks = Math.max(0, c.getInt("weapon.reload-ticks", 50));
        shellSpeed = Math.max(0.05, c.getDouble("weapon.shell-speed", 2.6));
        shellGravity = Math.max(0.0, c.getDouble("weapon.shell-gravity", 0.035));
        shellSubsteps = Math.max(1, c.getInt("weapon.shell-substeps", 4));
        shellLifetimeTicks = Math.max(1, c.getInt("weapon.shell-lifetime-ticks", 120));
        explosionPower = (float) Math.max(0.0, c.getDouble("weapon.explosion-power", 3.0));
        breakBlocks = c.getBoolean("weapon.break-blocks", true);
        setFire = c.getBoolean("weapon.set-fire", false);
        shellTankDamage = Math.max(0.0, c.getDouble("weapon.shell-tank-damage", 55.0));
        recoil = Math.max(0.0, c.getDouble("weapon.recoil", 0.12));
        spreadDegrees = Math.max(0.0, c.getDouble("weapon.spread-degrees", 1.5));
        shellMaxRange = Math.max(1.0, c.getDouble("weapon.shell-max-range", 160.0));
        maxActiveShells = Math.max(1, c.getInt("weapon.max-active-shells", 80));
        overheatDamagePercent = Math.max(0.0, c.getDouble("weapon.overheat-damage-percent", 2.0));
        overheatSpamThreshold = Math.max(1, c.getInt("weapon.overheat-spam-threshold", 10));

        rammingEnabled = c.getBoolean("combat.ramming-enabled", true);
        rammingDamage = Math.max(0.0, c.getDouble("combat.ramming-damage", 4.0));
        rammingKnockback = Math.max(0.0, c.getDouble("combat.ramming-knockback", 0.9));
        rammingMinSpeed = Math.max(0.0, c.getDouble("combat.ramming-min-speed", 0.12));
        rammingCooldownMs = Math.max(50, c.getInt("combat.ramming-cooldown-ms", 600));
        rammingConeDegrees = Math.max(10.0, Math.min(180.0, c.getDouble("combat.ramming-cone-degrees", 85.0)));
        projectileSweepIntervalTicks = Math.max(1, c.getInt("combat.projectile-sweep-interval-ticks", 2));
        weaponMeleePercent = Math.max(0.0, c.getDouble("combat.weapon-melee-percent", 4.0));
        weaponArrowPercent = Math.max(0.0, c.getDouble("combat.weapon-arrow-percent", 6.0));
        weaponFireballPercent = Math.max(0.0, c.getDouble("combat.weapon-fireball-percent", 12.0));
        weaponMeleeCooldownMs = Math.max(50, c.getInt("combat.weapon-melee-cooldown-ms", 250));

        hudInterval = Math.max(1, c.getInt("hud.update-interval-ticks", 4));

        creeperDamage = Math.max(0.0, c.getDouble("durability.creeper-damage", 50.0));
        creepersToDestroy = Math.max(1.0, c.getDouble("durability.creepers-to-destroy", 4.0));
        maxHealth = Math.max(1.0, creeperDamage * creepersToDestroy);

        drownEnabled = c.getBoolean("water.drown-enabled", true);
        drownDamagePercent = Math.max(0.0, c.getDouble("water.drown-damage-percent", 2.5));

        impactSmokeDuration = Math.max(0, c.getInt("effects.impact-smoke-duration", 25));
        impactSmokeRadius = Math.max(0.0, c.getDouble("effects.impact-smoke-radius", 3.5));
        trackDust = c.getBoolean("effects.track-dust", true);
        engineSound = c.getBoolean("effects.engine-sound", true);
        damageSmoke = c.getBoolean("effects.damage-smoke", true);

        consumeItem = c.getBoolean("placement.consume-item", true);
        dropItemOnDestroy = c.getBoolean("destruction.drop-item-on-destroy", false);
        debris = c.getBoolean("destruction.debris", true);

        hullBlock = block(plugin, c.getString("model.hull-block"), Material.GREEN_CONCRETE);
        turretBlock = block(plugin, c.getString("model.turret-block"), Material.GREEN_CONCRETE);
        detailBlock = block(plugin, c.getString("model.detail-block"), Material.MOSS_BLOCK);
        trackBlock = block(plugin, c.getString("model.track-block"), Material.BLACK_CONCRETE);
        wheelBlock = block(plugin, c.getString("model.wheel-block"), Material.POLISHED_BLACKSTONE);
        barrelBlock = block(plugin, c.getString("model.barrel-block"), Material.POLISHED_BLACKSTONE);
        turretNumber = c.getString("model.turret-number", "1-81");
        seatHeight = Math.max(0.1, c.getDouble("model.seat-height", 1.6));

        debug = c.getBoolean("debug", false);
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
