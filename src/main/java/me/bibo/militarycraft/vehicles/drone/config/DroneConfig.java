package me.bibo.militarycraft.vehicles.drone.config;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * Typed, validated snapshot of config.yml. Re-created on every reload.
 */
public final class DroneConfig {

    // flight (auto-forward; the camera only steers)
    public final double speed;
    public final double turnRate;
    public final double maxPitch;
    public final double maxBank;
    public final double autoBankFactor;
    public final double rollReturnSpeed;
    public final int unmannedLifetimeTicks;

    // control
    public final int exitDoubleTapMs;
    public final double operatorScale;

    // kamikaze
    public final double armSpeed;
    public final double proximityRadius;
    public final boolean detonateOnEntityContact;
    public final int armDelayTicks;
    public final float explosionPower;
    public final double directDamage;
    public final boolean breakBlocks;
    public final boolean setFire;

    // rockets (4 one-shot, RMB)
    public final int rocketCount;
    public final int rocketReloadTicks;
    public final double rocketSpeed;
    public final int rocketSubsteps;
    public final int rocketLifetimeTicks;
    public final double rocketMaxRange;
    public final float rocketExplosionPower;
    public final double rocketDirectDamage;
    public final boolean rocketBreakBlocks;
    public final boolean rocketSetFire;
    public final double rocketSpread;

    // battery
    public final boolean batteryEnabled;
    public final int batteryFlightTicks;
    public final int batteryLowPercent;

    // durability
    public final double creeperDamage;
    public final double creepersToDestroy;
    public final double maxHealth;
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

    // effects
    public final Sound motorSound;
    public final int motorInterval;
    public final float motorVolume;
    public final float motorPitch;
    public final double propSpinPerTick;
    public final boolean propWash;
    public final int interpolationTicks;
    public final int ejectSlowFallTicks;
    public final boolean debris;

    // hud
    public final int hudInterval;

    // riding grace
    public final int mountGraceTicks;

    // placement / destruction
    public final boolean consumeItem;
    public final boolean dropItemOnDestroy;

    // model materials
    public final Material frameBlock;
    public final Material armBlock;
    public final Material motorBlock;
    public final Material propBlock;
    public final Material cameraBlock;
    public final Material warheadBlock;
    public final Material accentBlock;

    public final boolean debug;

    public DroneConfig(Plugin plugin, ConfigurationSection c) {

        speed = c.getDouble("flight.speed", 1.7);
        turnRate = c.getDouble("flight.turn-rate", 16.0);
        maxPitch = c.getDouble("flight.max-pitch", 88.0);
        maxBank = c.getDouble("flight.max-bank", 32.0);
        autoBankFactor = c.getDouble("flight.auto-bank-factor", 3.0);
        rollReturnSpeed = c.getDouble("flight.roll-return-speed", 8.0);
        unmannedLifetimeTicks = Math.max(20, c.getInt("flight.unmanned-lifetime-ticks", 600));

        exitDoubleTapMs = Math.max(120, c.getInt("control.exit-double-tap-ms", 500));
        operatorScale = Math.max(0.0625, Math.min(1.0, c.getDouble("control.operator-scale", 0.45)));

        armSpeed = c.getDouble("kamikaze.arm-speed", 0.10);
        proximityRadius = c.getDouble("kamikaze.proximity-radius", 1.2);
        detonateOnEntityContact = c.getBoolean("kamikaze.detonate-on-entity-contact", false);
        armDelayTicks = Math.max(0, c.getInt("kamikaze.arm-delay-ticks", 20));
        explosionPower = (float) c.getDouble("kamikaze.explosion-power", 6.0);
        directDamage = c.getDouble("kamikaze.direct-damage", 40.0);
        breakBlocks = c.getBoolean("kamikaze.break-blocks", true);
        setFire = c.getBoolean("kamikaze.set-fire", false);

        rocketCount = Math.max(0, c.getInt("rockets.count", 4));
        rocketReloadTicks = Math.max(1, c.getInt("rockets.reload-ticks", 6));
        rocketSpeed = c.getDouble("rockets.speed", 2.6);
        rocketSubsteps = Math.max(1, c.getInt("rockets.substeps", 6));
        rocketLifetimeTicks = c.getInt("rockets.lifetime-ticks", 60);
        rocketMaxRange = c.getDouble("rockets.max-range", 120.0);
        rocketExplosionPower = (float) c.getDouble("rockets.explosion-power", 1.6);
        rocketDirectDamage = c.getDouble("rockets.direct-damage", 16.0);
        rocketBreakBlocks = c.getBoolean("rockets.break-blocks", false);
        rocketSetFire = c.getBoolean("rockets.set-fire", false);
        rocketSpread = c.getDouble("rockets.spread-degrees", 1.5);

        batteryEnabled = c.getBoolean("battery.enabled", true);
        batteryFlightTicks = Math.max(1, c.getInt("battery.flight-ticks", 4800));
        batteryLowPercent = c.getInt("battery.low-percent", 20);

        creeperDamage = c.getDouble("durability.creeper-damage", 50.0);
        creepersToDestroy = c.getDouble("durability.creepers-to-destroy", 1.0);
        maxHealth = creeperDamage * creepersToDestroy;
        weaponMeleePercent = c.getDouble("combat.weapon-melee-percent", 20.0);
        weaponArrowPercent = c.getDouble("combat.weapon-arrow-percent", 25.0);
        weaponFireballPercent = c.getDouble("combat.weapon-fireball-percent", 50.0);
        weaponMeleeCooldownMs = c.getInt("combat.weapon-melee-cooldown-ms", 250);

        altitudeEnabled = c.getBoolean("altitude.enabled", true);
        altitudeMaxY = c.getDouble("altitude.max-y", 300.0);
        altitudeDamage = Math.max(0.0, c.getDouble("altitude.damage", 2.0));
        altitudeDamagePer10 = Math.max(0.0, c.getDouble("altitude.damage-per-10-blocks", 1.0));
        altitudeIntervalTicks = Math.max(1, c.getInt("altitude.interval-ticks", 20));
        altitudeMessage = c.getString("altitude.message", "Signal is weakening - altitude too high!");

        motorSound = sound(plugin, c.getString("effects.motor-sound"), Sound.ENTITY_BEE_LOOP);
        motorInterval = Math.max(1, c.getInt("effects.motor-interval-ticks", 3));
        motorVolume = (float) c.getDouble("effects.motor-volume", 0.6);
        motorPitch = (float) c.getDouble("effects.motor-pitch", 1.5);
        propSpinPerTick = c.getDouble("effects.prop-spin-per-tick", 90.0);
        propWash = c.getBoolean("effects.prop-wash", true);
        interpolationTicks = Math.max(1, Math.min(10, c.getInt("effects.interpolation-ticks", 2)));
        ejectSlowFallTicks = c.getInt("effects.eject-slowfall-ticks", 120);
        debris = c.getBoolean("effects.debris", true);

        hudInterval = Math.max(1, c.getInt("hud.update-interval-ticks", 3));
        mountGraceTicks = Math.max(0, c.getInt("mount-grace-ticks", 20));

        consumeItem = c.getBoolean("placement.consume-item", true);
        dropItemOnDestroy = c.getBoolean("destruction.drop-item-on-destroy", false);

        frameBlock = block(plugin, c.getString("model.frame-block"), Material.WHITE_CONCRETE);
        armBlock = block(plugin, c.getString("model.arm-block"), Material.BLACK_CONCRETE);
        motorBlock = block(plugin, c.getString("model.motor-block"), Material.POLISHED_BLACKSTONE);
        propBlock = block(plugin, c.getString("model.prop-block"), Material.LIGHT_GRAY_STAINED_GLASS);
        cameraBlock = block(plugin, c.getString("model.camera-block"), Material.BLACK_CONCRETE);
        warheadBlock = block(plugin, c.getString("model.warhead-block"), Material.LIGHT_GRAY_CONCRETE);
        accentBlock = block(plugin, c.getString("model.accent-block"), Material.RED_CONCRETE);

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

    private static Sound sound(Plugin plugin, String name, Sound fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        String raw = name.trim();
        org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(
                raw.toLowerCase(java.util.Locale.ROOT));
        Sound sound = key == null ? null : org.bukkit.Registry.SOUND_EVENT.get(key);
        if (sound == null) {
            String legacyName = raw.toUpperCase(java.util.Locale.ROOT);
            sound = org.bukkit.Registry.SOUND_EVENT.stream()
                    .filter(candidate -> {
                        org.bukkit.NamespacedKey candidateKey =
                                org.bukkit.Registry.SOUND_EVENT.getKeyOrThrow(candidate);
                        return candidateKey.getNamespace().equals("minecraft")
                                && candidateKey.getKey().replace('.', '_')
                                .toUpperCase(java.util.Locale.ROOT).equals(legacyName);
                    })
                    .findFirst()
                    .orElse(null);
        }
        if (sound != null) {
            return sound;
        }
        plugin.getLogger().log(Level.WARNING,
                "Invalid sound '" + name + "', using " + fallback);
        return fallback;
    }
}
