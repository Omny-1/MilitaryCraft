package me.bibo.militarycraft.vehicles.train.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Snapshot of config.yml. Speeds are converted to per-tick units once here so
 * the hot tick loop never divides. Reloaded in place by /train reload; live
 * trains read these fields every tick, so changes apply immediately.
 */
public final class TrainConfig {

    // movement (per-tick units)
    public double cruisePerTick;
    public double decelPerTick;
    public double carGap;

    // animation
    public double wheelSpinMultiplier;

    // collision
    public boolean collisionEnabled;
    public double collisionDamage;
    public double widthMargin;
    public double minHitSpeedPerTick;
    public double knockback;
    public double knockbackUp;
    public long hitCooldownMs;
    public boolean affectMobs;

    // seats
    public int seatsLocomotive;
    public int seatsWagon;
    public double seatHeightLocomotive;
    public double seatHeightWagon;

    // effects
    public boolean smoke;
    public boolean sounds;
    public boolean whistle;

    // placement
    public boolean consumeItem;
    public int maxTrains;

    // misc
    public float viewRange;
    public boolean keepChunksLoaded;
    public boolean debug;

    public void load(ConfigurationSection c) {
        cruisePerTick = Math.max(0.02, c.getDouble("movement.speed", 8.0)) / 20.0;
        decelPerTick = Math.max(0.5, c.getDouble("movement.deceleration", 10.0)) / 400.0;
        carGap = Math.max(0.2, c.getDouble("movement.car-gap", 1.0));
        wheelSpinMultiplier = Math.max(0.05, c.getDouble("animation.wheel-spin-multiplier", 0.55));

        collisionEnabled = c.getBoolean("collision.enabled", true);
        collisionDamage = c.getDouble("collision.damage", 8.0);
        widthMargin = c.getDouble("collision.width-margin", 0.35);
        minHitSpeedPerTick = c.getDouble("collision.min-speed", 2.0) / 20.0;
        knockback = c.getDouble("collision.knockback", 1.7);
        knockbackUp = c.getDouble("collision.knockback-up", 0.55);
        hitCooldownMs = c.getLong("collision.hit-cooldown-ms", 800);
        affectMobs = c.getBoolean("collision.affect-mobs", true);

        seatsLocomotive = Math.max(1, c.getInt("seats.locomotive", 2));
        seatsWagon = Math.max(1, c.getInt("seats.wagon", 4));
        seatHeightLocomotive = c.getDouble("seats.seat-height-locomotive", 0.85);
        seatHeightWagon = c.getDouble("seats.seat-height-wagon", 0.3);

        smoke = c.getBoolean("effects.smoke", true);
        sounds = c.getBoolean("effects.sounds", true);
        whistle = c.getBoolean("effects.whistle", true);

        consumeItem = c.getBoolean("placement.consume-item", true);
        maxTrains = Math.max(1, c.getInt("placement.max-trains", 6));

        viewRange = (float) c.getDouble("misc.view-range", 2.2);
        keepChunksLoaded = c.getBoolean("misc.keep-chunks-loaded", true);
        debug = c.getBoolean("debug", false);
    }
}
