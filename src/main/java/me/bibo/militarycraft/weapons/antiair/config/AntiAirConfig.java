package me.bibo.militarycraft.weapons.antiair.config;

import me.bibo.militarycraft.weapons.antiair.model.Part;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.logging.Level;

/**
 * Typed, validated snapshot of config.yml. Re-created on every reload.
 */
public final class AntiAirConfig {

    // targeting
    public final int scanIntervalTicks;
    public final double slewRateDeg;
    public final double aimToleranceDeg;
    public final double restPitchDeg;
    public final boolean requireLineOfSight;

    // weapons
    public final int fireIntervalTicks;
    public final int burst;
    public final double bulletDamage;
    public final double bulletRange;
    public final double raySize;
    public final int tracerDensity;
    public final double knockback;
    public final int spinUpTicks;
    public final double heatPerShot;
    public final double heatCoolPerTick;
    public final double heatResume;

    // svo
    public final double svoRange;
    public final double svoMinSpread;
    public final double svoMaxSpread;
    public final boolean svoIgnoreOwner;
    public final boolean svoIgnoreCreative;
    public final double svoDamageMultiplier;
    public final int svoRocketIntervalTicks;
    public final double svoRocketPower;
    public final List<String> vehicleTags;

    // normies
    public final double normiesRange;
    public final double normiesSpread;

    // fuel
    public final double fuelBurnMultiplier;
    public final int fuelConsumePerShot;
    public final boolean fuelDropOnDestroy;

    // durability
    public final double creeperDamage;
    public final double creepersToDestroy;
    public final double maxHealth;
    public final double meleeDamage;
    public final double arrowDamage;
    public final double contactRadius;

    // limits
    public final int maxPerPlayer;
    public final int maxLoaded;

    // placement / destruction
    public final boolean consumeItem;
    public final boolean dropItemOnDestroy;
    public final boolean debris;

    // performance
    public final float viewRange;
    public final int interpolationTicks;
    public final boolean rounded;

    // model materials
    public final Material baseBlock;
    public final Material housingBlock;
    public final Material radomeBlock;
    public final Material gunbodyBlock;
    public final Material barrelBlock;
    public final Material muzzleBlock;
    public final Material detailBlock;

    public final boolean debug;

    public AntiAirConfig(Plugin plugin, ConfigurationSection c) {

        scanIntervalTicks = Math.max(1, c.getInt("targeting.scan-interval-ticks", 5));
        slewRateDeg = Math.max(1.0, c.getDouble("targeting.slew-rate-deg", 22.0));
        aimToleranceDeg = Math.max(0.5, c.getDouble("targeting.aim-tolerance-deg", 7.0));
        restPitchDeg = c.getDouble("targeting.rest-pitch-deg", 18.0);
        requireLineOfSight = c.getBoolean("targeting.require-line-of-sight", true);

        fireIntervalTicks = Math.max(1, c.getInt("weapons.fire-interval-ticks", 2));
        burst = Math.max(1, c.getInt("weapons.burst", 1));
        bulletDamage = Math.max(0.0, c.getDouble("weapons.bullet-damage", 3.2));
        bulletRange = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.bullet-range", 45.0), 4.0, 256.0, 45.0);
        raySize = Math.max(0.01, c.getDouble("weapons.ray-size", 0.12));
        tracerDensity = me.bibo.militarycraft.core.util.Bounds.ranged(c.getInt("weapons.tracer-density", 14), 0, 100);
        knockback = c.getDouble("weapons.knockback", 0.18);
        spinUpTicks = Math.max(0, c.getInt("weapons.spin-up-ticks", 8));
        heatPerShot = Math.max(0.0, c.getDouble("weapons.heat.per-shot", 0.06));
        heatCoolPerTick = Math.max(0.0, c.getDouble("weapons.heat.cool-per-tick", 0.012));
        heatResume = Math.max(0.0, Math.min(1.0, c.getDouble("weapons.heat.resume-threshold", 0.35)));

        svoRange = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("svo.range", 30.0), 1.0, 256.0, 30.0);
        svoMinSpread = Math.max(0.0, c.getDouble("svo.min-spread-degrees", 0.3));
        svoMaxSpread = Math.max(svoMinSpread, c.getDouble("svo.max-spread-degrees", 2.9));
        svoIgnoreOwner = c.getBoolean("svo.ignore-owner", true);
        svoIgnoreCreative = c.getBoolean("svo.ignore-creative", true);
        svoDamageMultiplier = Math.max(0.0, c.getDouble("svo.damage-multiplier", 0.75));
        svoRocketIntervalTicks = Math.max(1, c.getInt("svo.rocket-interval-ticks", 40));
        svoRocketPower = Math.max(0.0, c.getDouble("svo.rocket-power", 3.0));
        List<String> tags = c.getStringList("svo.vehicle-tags");
        vehicleTags = tags.isEmpty()
                ? List.of("tankcraft_entity", "jetcraft_entity", "airshipcraft_entity")
                : tags;

        normiesRange = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("normies.range", 40.0), 1.0, 128.0, 40.0);
        normiesSpread = Math.max(0.0, c.getDouble("normies.spread-degrees", 0.6));

        fuelBurnMultiplier = Math.max(0.05, c.getDouble("fuel.burn-time-multiplier", 1.0));
        fuelConsumePerShot = Math.max(1, c.getInt("fuel.consume-per-shot", 3));
        fuelDropOnDestroy = c.getBoolean("fuel.drop-on-destroy", true);

        creeperDamage = c.getDouble("durability.creeper-damage", 50.0);
        creepersToDestroy = Math.max(0.1, c.getDouble("durability.creepers-to-destroy", 1.0));
        maxHealth = creeperDamage * creepersToDestroy;
        meleeDamage = Math.max(0.0, c.getDouble("durability.melee-damage", 0.0));
        arrowDamage = Math.max(0.0, c.getDouble("durability.arrow-damage", 0.0));
        contactRadius = c.getDouble("durability.contact-radius", 2.5);

        maxPerPlayer = c.getInt("limits.max-per-player", 6);
        maxLoaded = c.getInt("limits.max-loaded", 60);

        consumeItem = c.getBoolean("placement.consume-item", true);
        dropItemOnDestroy = c.getBoolean("destruction.drop-item-on-destroy", true);
        debris = c.getBoolean("destruction.debris", true);

        viewRange = (float) c.getDouble("performance.view-range", 2.5);
        interpolationTicks = Math.max(0, c.getInt("performance.interpolation-ticks", 2));
        rounded = c.getBoolean("performance.rounded", true);

        baseBlock = block(plugin, c.getString("model.base-block"), Material.GRAY_CONCRETE);
        housingBlock = block(plugin, c.getString("model.housing-block"), Material.LIGHT_GRAY_CONCRETE);
        radomeBlock = block(plugin, c.getString("model.radome-block"), Material.WHITE_CONCRETE);
        gunbodyBlock = block(plugin, c.getString("model.gunbody-block"), Material.GRAY_CONCRETE);
        barrelBlock = block(plugin, c.getString("model.barrel-block"), Material.POLISHED_BLACKSTONE);
        muzzleBlock = block(plugin, c.getString("model.muzzle-block"), Material.BLACKSTONE);
        detailBlock = block(plugin, c.getString("model.detail-block"), Material.IRON_BLOCK);

        debug = c.getBoolean("debug", false);
    }

    /** Resolve the configured material for a model part role. */
    public Material materialFor(Part.Role role) {
        return switch (role) {
            case BASE -> baseBlock;
            case HOUSING -> housingBlock;
            case RADOME -> radomeBlock;
            case GUNBODY -> gunbodyBlock;
            case BARREL -> barrelBlock;
            case MUZZLE -> muzzleBlock;
            case DETAIL -> detailBlock;
        };
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
