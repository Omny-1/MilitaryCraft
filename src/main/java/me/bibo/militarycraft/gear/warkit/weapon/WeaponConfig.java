package me.bibo.militarycraft.gear.warkit.weapon;

import org.bukkit.configuration.ConfigurationSection;

/** Snapshot of weapon balance from config.yml (weapons.* section), rebuilt on reload. */
public final class WeaponConfig {

    // --- Rifle / Emka ---
    public final double rifleDamage;
    public final double rifleRange;
    public final int rifleMag;
    public final double rifleReloadSeconds;
    public final int rifleFireCooldownTicks;
    public final double rifleSpreadDeg;

    // --- Pistol ---
    public final double pistolDamage;
    public final double pistolRange;
    public final int pistolMag;
    public final double pistolReloadSeconds;
    public final int pistolFireCooldownTicks;
    public final double pistolSpreadDeg;

    // --- Shared firearm settings ---
    public final double headshotMultiplier;
    public final double vehicleBulletDamageMultiplier;

    // --- Grenade launcher ---
    public final double glExplosionPower;
    public final int glMag;
    public final double glReloadSeconds;
    public final int glFireCooldownTicks;
    public final double glSpeed;
    public final double glFuseSeconds;
    public final double glBounceDamping;

    // --- Patriot homing missile ---
    public final double patriotLockRange;
    public final double patriotLockConeDeg;
    public final double patriotExplosionPower;
    public final double patriotVehicleDirectDamage;
    public final double patriotSpeed;
    public final double patriotTurnRate;
    public final int patriotMagazine;
    public final int patriotCooldownSeconds;

    // --- Fragmentation grenade ---
    public final double fragFuseSeconds;
    public final double fragDamage;
    public final double fragRadius;
    public final double fragThrowSpeed;

    // --- Smoke grenade ---
    public final double smokeDurationSeconds;
    public final double smokeRadius;
    public final double smokeThrowSpeed;

    // --- Flashbang ---
    public final double flashRadius;
    public final int flashBlindSeconds;
    public final double flashThrowSpeed;

    // --- Impulse grenade ---
    public final double impulseRadius;
    public final double impulseForward;
    public final double impulseUp;
    public final int impulseNoFallSeconds;
    public final double impulseThrowSpeed;
    public final double impulseFuseSeconds;

    // --- Flamethrower ---
    public final double flameRange;
    public final double flameConeDeg;
    public final double flameTickDamage;
    public final int flameFireTicks;
    public final int flameFuel;
    public final double flameRefuelSeconds;
    public final boolean flameFireBlocks;

    // --- Chemical sprayer ---
    public final double chemRange;
    public final double chemConeDeg;
    public final int chemCloudSeconds;
    public final double chemRadius;
    public final int chemFuel;
    public final int chemCost;
    public final double chemRefuelSeconds;

    // --- Maxim machine gun deployable ---
    public final double maximDamage;
    public final double maximRange;
    public final int maximFireCooldownTicks;
    public final double maximSpreadDeg;
    public final int maximOverheatShots;
    public final double maximCooldownSeconds;
    public final double maximAimArcDegrees;
    public final double maximHealth;

    // --- Barbed wire deployable ---
    public final double barbedRadius;
    public final int barbedSlowAmplifier;
    public final double barbedTickDamage;
    public final int barbedLifeSeconds;
    public final int barbedMaxPerPlayer;
    public final int barbedSegments;
    public final double barbedSpacing;

    // --- Trench shovel ---
    public final double trenchDigSeconds;
    public final int trenchCooldownSeconds;

    // --- Molotov cocktail ---
    public final double molotovRadius;
    public final int molotovFireSeconds;
    public final double molotovThrowSpeed;

    // --- Suicide vest ---
    public final double suicideRadius;
    public final double suicideMaxDamage;
    public final double suicideArmRange;
    public final double suicidePower;
    public final boolean suicideBreakBlocks;

    // --- Firing wall ---
    public final int firingWallWidth;
    public final int firingWallHeight;

    // --- Tripwire ---
    public final int tripwireMaxWidth;
    public final double tripwirePower;
    public final int tripwireLifeSeconds;
    public final int tripwireMaxPerPlayer;

    // --- C4 ---
    public final double c4Power;
    public final boolean c4BreakBlocks;
    public final int c4MaxPerPlayer;
    public final int c4LifeSeconds;

    // --- Sleep gas ---
    public final int gasDurationSeconds;
    public final double gasRadius;
    public final int gasImmobilizeAfterSeconds;
    public final double gasThrowSpeed;

    // --- Grappling hook ---
    public final double hookRange;
    public final double hookPullStrength;
    public final double hookUpBoost;
    public final double hookCooldownSeconds;
    public final int hookNoFallSeconds;
    public final int hookCharges;

    // --- Jump jet ---
    public final int jetFuel;
    public final int jetCostPerBurst;
    public final double jetRefuelSeconds;
    public final double jetUp;
    public final double jetForward;
    public final int jetNoFallSeconds;

    // --- Combat stim ---
    public final int stimSpeedAmplifier;
    public final int stimJumpAmplifier;
    public final int stimRegenSeconds;
    public final int stimBuffSeconds;
    public final int stimCrashSeconds;
    public final int stimCooldownSeconds;

    // --- Recon scanner: distance to the nearest living player ---
    public final int scannerDurationSeconds;

    // --- Proximity mine ---
    public final double minePower;
    public final double mineTriggerRadius;
    public final double mineArmSeconds;
    public final int mineMaxPerPlayer;
    public final boolean mineBreakBlocks;
    public final int mineLifeSeconds;

    // --- Auto sentry ---
    public final double sentryDamage;
    public final double sentryRange;
    public final int sentryFireCooldownTicks;
    public final double sentrySpreadDeg;
    public final double sentryHealth;
    public final int sentryAmmo;
    public final int sentryLifeSeconds;
    public final int sentryMaxPerPlayer;

    public WeaponConfig(ConfigurationSection c) {
        rifleDamage = c.getDouble("weapons.rifle.damage", 4.0);
        rifleRange = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.rifle.range", 50.0), 1.0, 128.0, 50.0);
        rifleMag = c.getInt("weapons.rifle.magazine", 30);
        rifleReloadSeconds = c.getDouble("weapons.rifle.reload-seconds", 2.5);
        rifleFireCooldownTicks = c.getInt("weapons.rifle.fire-cooldown-ticks", 3);
        rifleSpreadDeg = c.getDouble("weapons.rifle.spread-degrees", 4.0);

        pistolDamage = c.getDouble("weapons.pistol.damage", 3.0);
        pistolRange = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.pistol.range", 30.0), 1.0, 128.0, 30.0);
        pistolMag = c.getInt("weapons.pistol.magazine", 12);
        pistolReloadSeconds = c.getDouble("weapons.pistol.reload-seconds", 1.6);
        pistolFireCooldownTicks = c.getInt("weapons.pistol.fire-cooldown-ticks", 5);
        pistolSpreadDeg = c.getDouble("weapons.pistol.spread-degrees", 3.2);

        headshotMultiplier = c.getDouble("weapons.headshot-multiplier", 1.5);
        vehicleBulletDamageMultiplier = Math.max(0.0,
                c.getDouble("weapons.vehicle-bullet-damage-multiplier", 0.35));

        glExplosionPower = c.getDouble("weapons.grenade-launcher.explosion-power", 2.5);
        glMag = c.getInt("weapons.grenade-launcher.magazine", 4);
        glReloadSeconds = c.getDouble("weapons.grenade-launcher.reload-seconds", 3.0);
        glFireCooldownTicks = c.getInt("weapons.grenade-launcher.fire-cooldown-ticks", 24);
        glSpeed = c.getDouble("weapons.grenade-launcher.speed", 0.9);
        glFuseSeconds = c.getDouble("weapons.grenade-launcher.fuse-seconds", 1.5);
        glBounceDamping = c.getDouble("weapons.grenade-launcher.bounce-damping", 0.45);

        patriotLockRange = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.patriot.lock-range", 60.0), 1.0, 128.0, 60.0);
        patriotLockConeDeg = c.getDouble("weapons.patriot.lock-cone-degrees", 12.0);
        patriotExplosionPower = c.getDouble("weapons.patriot.explosion-power", 3.0);
        patriotVehicleDirectDamage = Math.max(0.0,
                c.getDouble("weapons.patriot.vehicle-direct-damage", 35.0));
        patriotSpeed = c.getDouble("weapons.patriot.speed", 0.75);
        patriotTurnRate = c.getDouble("weapons.patriot.turn-rate", 0.25);
        patriotMagazine = c.getInt("weapons.patriot.magazine", 8);
        patriotCooldownSeconds = c.getInt("weapons.patriot.cooldown-seconds", 10);

        fragFuseSeconds = c.getDouble("weapons.frag-grenade.fuse-seconds", 1.5);
        fragDamage = c.getDouble("weapons.frag-grenade.damage", 9.0);
        fragRadius = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.frag-grenade.radius", 4.0), 0.0, 64.0, 4.0);
        fragThrowSpeed = c.getDouble("weapons.frag-grenade.throw-speed", 1.3);

        smokeDurationSeconds = c.getDouble("weapons.smoke-grenade.duration-seconds", 12.0);
        smokeRadius = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.smoke-grenade.radius", 6.0), 0.0, 64.0, 6.0);
        smokeThrowSpeed = c.getDouble("weapons.smoke-grenade.throw-speed", 1.2);

        flashRadius = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.flash-grenade.radius", 16.0), 0.0, 64.0, 16.0);
        flashBlindSeconds = c.getInt("weapons.flash-grenade.blind-seconds", 7);
        flashThrowSpeed = c.getDouble("weapons.flash-grenade.throw-speed", 1.2);

        impulseRadius = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.impulse-grenade.radius", 3.0), 0.0, 64.0, 3.0);
        impulseForward = c.getDouble("weapons.impulse-grenade.forward", 2.2);
        impulseUp = c.getDouble("weapons.impulse-grenade.up", 1.0);
        impulseNoFallSeconds = c.getInt("weapons.impulse-grenade.no-fall-seconds", 8);
        impulseThrowSpeed = c.getDouble("weapons.impulse-grenade.throw-speed", 1.2);
        impulseFuseSeconds = c.getDouble("weapons.impulse-grenade.fuse-seconds", 0.7);

        flameRange = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.flamethrower.range", 5.0), 0.0, 64.0, 5.0);
        flameConeDeg = c.getDouble("weapons.flamethrower.cone-degrees", 25.0);
        flameTickDamage = c.getDouble("weapons.flamethrower.tick-damage", 1.0);
        flameFireTicks = c.getInt("weapons.flamethrower.fire-ticks", 60);
        flameFuel = c.getInt("weapons.flamethrower.fuel", 220);
        flameRefuelSeconds = c.getDouble("weapons.flamethrower.refuel-seconds", 0.0);
        flameFireBlocks = c.getBoolean("weapons.flamethrower.leave-fire", true);

        chemRange = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.chemical.range", 6.0), 0.0, 64.0, 6.0);
        chemConeDeg = c.getDouble("weapons.chemical.cone-degrees", 22.0);
        chemCloudSeconds = c.getInt("weapons.chemical.cloud-seconds", 8);
        chemRadius = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.chemical.radius", 3.0), 0.0, 64.0, 3.0);
        chemFuel = c.getInt("weapons.chemical.fuel", 200);
        chemCost = c.getInt("weapons.chemical.cost-per-spray", 10);
        chemRefuelSeconds = c.getDouble("weapons.chemical.refuel-seconds", 0.0);

        maximDamage = c.getDouble("weapons.maxim.damage", 5.0);
        maximRange = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.maxim.range", 60.0), 1.0, 128.0, 60.0);
        maximFireCooldownTicks = c.getInt("weapons.maxim.fire-cooldown-ticks", 2);
        maximSpreadDeg = c.getDouble("weapons.maxim.spread-degrees", 3.5);
        maximOverheatShots = c.getInt("weapons.maxim.overheat-shots", 80);
        maximCooldownSeconds = c.getDouble("weapons.maxim.overheat-cooldown-seconds", 6.0);
        maximAimArcDegrees = c.getDouble("weapons.maxim.aim-arc-degrees", 70.0);
        maximHealth = c.getDouble("weapons.maxim.health", 20.0);

        barbedRadius = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.barbed-wire.radius", 1.6), 0.0, 64.0, 1.6);
        barbedSlowAmplifier = c.getInt("weapons.barbed-wire.slow-amplifier", 4);
        barbedTickDamage = c.getDouble("weapons.barbed-wire.tick-damage", 3.0);
        barbedLifeSeconds = c.getInt("weapons.barbed-wire.life-seconds", 120);
        barbedMaxPerPlayer = c.getInt("weapons.barbed-wire.max-per-player", 6);
        barbedSegments = c.getInt("weapons.barbed-wire.segments", 8);
        barbedSpacing = c.getDouble("weapons.barbed-wire.spacing", 1.0);

        trenchDigSeconds = c.getDouble("weapons.trench-shovel.dig-seconds", 3.0);
        trenchCooldownSeconds = c.getInt("weapons.trench-shovel.cooldown-seconds", 6);

        molotovRadius = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.molotov.radius", 3.0), 0.0, 64.0, 3.0);
        molotovFireSeconds = c.getInt("weapons.molotov.fire-ticks", 100);
        molotovThrowSpeed = c.getDouble("weapons.molotov.throw-speed", 1.3);

        suicideRadius = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.suicide-vest.radius", 6.0), 0.0, 64.0, 6.0);
        suicideMaxDamage = c.getDouble("weapons.suicide-vest.max-damage", 40.0);
        suicideArmRange = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.suicide-vest.arm-range", 7.0), 0.0, 64.0, 7.0);
        suicidePower = c.getDouble("weapons.suicide-vest.explosion-power", 8.0);
        suicideBreakBlocks = c.getBoolean("weapons.suicide-vest.break-blocks", true);

        firingWallWidth = c.getInt("weapons.firing-wall.width", 3);
        firingWallHeight = c.getInt("weapons.firing-wall.height", 3);

        tripwireMaxWidth = c.getInt("weapons.tripwire.max-width", 4);
        tripwirePower = c.getDouble("weapons.tripwire.power", 1.0);
        tripwireLifeSeconds = c.getInt("weapons.tripwire.life-seconds", 300);
        tripwireMaxPerPlayer = c.getInt("weapons.tripwire.max-per-player", 8);

        c4Power = c.getDouble("weapons.c4.power", 4.0);
        c4BreakBlocks = c.getBoolean("weapons.c4.break-blocks", true);
        c4MaxPerPlayer = c.getInt("weapons.c4.max-per-player", 8);
        c4LifeSeconds = c.getInt("weapons.c4.life-seconds", 300);

        gasDurationSeconds = c.getInt("weapons.sleep-gas.duration-seconds", 14);
        gasRadius = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.sleep-gas.radius", 4.5), 0.0, 64.0, 4.5);
        gasImmobilizeAfterSeconds = c.getInt("weapons.sleep-gas.immobilize-after-seconds", 4);
        gasThrowSpeed = c.getDouble("weapons.sleep-gas.throw-speed", 1.2);

        hookRange = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.grappling-hook.range", 30.0), 1.0, 128.0, 30.0);
        hookPullStrength = c.getDouble("weapons.grappling-hook.pull-strength", 1.35);
        hookUpBoost = c.getDouble("weapons.grappling-hook.up-boost", 0.45);
        hookCooldownSeconds = c.getDouble("weapons.grappling-hook.cooldown-seconds", 2.5);
        hookNoFallSeconds = c.getInt("weapons.grappling-hook.no-fall-seconds", 6);
        hookCharges = c.getInt("weapons.grappling-hook.charges", 12);

        jetFuel = c.getInt("weapons.jump-jet.fuel", 30);
        jetCostPerBurst = c.getInt("weapons.jump-jet.cost-per-burst", 3);
        jetRefuelSeconds = c.getDouble("weapons.jump-jet.refuel-seconds", 0.0);
        jetUp = c.getDouble("weapons.jump-jet.up", 0.85);
        jetForward = c.getDouble("weapons.jump-jet.forward", 0.35);
        jetNoFallSeconds = c.getInt("weapons.jump-jet.no-fall-seconds", 4);

        stimSpeedAmplifier = c.getInt("weapons.combat-stim.speed-amplifier", 1);
        stimJumpAmplifier = c.getInt("weapons.combat-stim.jump-amplifier", 1);
        stimRegenSeconds = c.getInt("weapons.combat-stim.regen-seconds", 5);
        stimBuffSeconds = c.getInt("weapons.combat-stim.buff-seconds", 10);
        stimCrashSeconds = c.getInt("weapons.combat-stim.crash-seconds", 5);
        stimCooldownSeconds = c.getInt("weapons.combat-stim.cooldown-seconds", 25);

        scannerDurationSeconds = c.getInt("weapons.recon-scanner.duration-seconds", 60);

        minePower = c.getDouble("weapons.proximity-mine.power", 4.0);
        mineTriggerRadius = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.proximity-mine.trigger-radius", 2.2), 0.0, 64.0, 2.2);
        mineArmSeconds = c.getDouble("weapons.proximity-mine.arm-seconds", 1.5);
        mineMaxPerPlayer = c.getInt("weapons.proximity-mine.max-per-player", 6);
        mineBreakBlocks = c.getBoolean("weapons.proximity-mine.break-blocks", true);
        mineLifeSeconds = c.getInt("weapons.proximity-mine.life-seconds", 300);

        sentryDamage = c.getDouble("weapons.sentry-gun.damage", 2.5);
        sentryRange = me.bibo.militarycraft.core.util.Bounds.ranged(c.getDouble("weapons.sentry-gun.range", 24.0), 1.0, 128.0, 24.0);
        sentryFireCooldownTicks = c.getInt("weapons.sentry-gun.fire-cooldown-ticks", 10);
        sentrySpreadDeg = c.getDouble("weapons.sentry-gun.spread-degrees", 4.0);
        sentryHealth = c.getDouble("weapons.sentry-gun.health", 18.0);
        sentryAmmo = c.getInt("weapons.sentry-gun.ammo", 40);
        sentryLifeSeconds = c.getInt("weapons.sentry-gun.life-seconds", 90);
        sentryMaxPerPlayer = c.getInt("weapons.sentry-gun.max-per-player", 2);
    }
}
