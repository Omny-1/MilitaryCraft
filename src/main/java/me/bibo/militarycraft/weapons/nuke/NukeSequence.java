package me.bibo.militarycraft.weapons.nuke;

import me.bibo.militarycraft.core.airsupport.ChunkWindow;
import me.bibo.militarycraft.core.util.Bounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * The full life of one nuclear strike: a bomber approaches the target, releases
 * a single bomb that tips from level to nose-down and plummets into the exact
 * point, then detonates into a huge irregular crater, a display-block mushroom
 * cloud, area damage and lingering effects.
 * <p>
 * Structured like {@code AirstrikeSequence}: a per-tick {@link BukkitRunnable}
 * phase machine driving the rigid rigs, with a small sliding force-load chunk
 * window so the drop and blast always tick.
 */
public class NukeSequence extends BukkitRunnable {

    private static final int BOMBER_CHUNK_RADIUS = 2;
    private static final int TARGET_CHUNK_RADIUS = 3;
    private static final int MAX_TICKS = 20 * 90;

    // aftermath pacing
    private static final int CLOUD_GROW_TICKS = 48;   // slower, smoother ground-up bloom of the display cloud
    private static final int CLOUD_LIFETIME = 150;    // how long the cloud lingers before it clears

    private final NukeManager manager;
    private final World world;
    private final Location target;

    // config (clamped to safe ranges)
    private final double bomberSpeed;
    private final float engineVolume;
    private final int bomberExitDistance;
    private final double bombMaxFall;
    private final int bombAccelTicks;
    private final double bombVerticalAt;
    private final float fallVolume;
    private final double damageRadius;
    private final double maxDamage;
    private final double maxKnockback;
    private final int craterRadius;
    private final int craterDepth;
    private final int craterColumnsPerTick;
    private final double blindnessRadius;
    private final int blindnessTicks;
    private final int radiationSeconds;
    private final boolean warningEnabled;
    private final double warningRadius;
    private final String inBlastTitle;

    // heading (constant for the whole pass) - precomputed once
    private final double dirX;
    private final double dirZ;
    private final float heading;
    private final float headingCos;
    private final float headingSin;

    private final ChunkWindow chunkWindow;
    private final DamageSource magic = DamageSource.builder(DamageType.MAGIC).build();

    private Phase phase = Phase.APPROACH;
    private int tick = 0;

    // bomber
    private BomberModel bomber;
    private boolean bomberActive = true;
    private final Location bomberPos;

    // bomb
    private NukeBombModel bomb;
    private Location bombPos;
    private double bombFallVel;
    private double bombFallStartY;
    private int bombDropTick;
    private boolean bombVertical;

    // aftermath
    private int detonationTick = -1;
    private NukeCloudModel cloud;
    private List<int[]> craterColumns;
    private int craterIndex;

    public NukeSequence(NukeManager manager, Location target, Location bomberStart, double dirX, double dirZ) {
        this.manager = manager;
        this.chunkWindow = new ChunkWindow(manager.core().plugin());
        this.world = target.getWorld();
        this.target = target;
        this.bomberPos = bomberStart.clone();
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.heading = (float) Math.atan2(dirX, dirZ);
        this.headingCos = (float) Math.cos(heading);
        this.headingSin = (float) Math.sin(heading);

        // Every amplifying value below is capped, not just floored. The crater is built from
        // a (2r+1)^2 column list assembled and sorted on the main thread before any block is
        // touched, so an unbounded crater-radius is not a big explosion - it is an out-of-memory
        // stall. damage-radius feeds a nearby-entity scan, and the per-tick budget decides how
        // much of the crater is dug synchronously each tick.
        NukeSettings c = manager.settings();
        this.bomberSpeed = Bounds.ranged(c.getDouble("bomber-speed", 1.0), 0.1, 20.0, 1.0);
        this.engineVolume = (float) Bounds.ranged(c.getDouble("engine-sound-volume", 9.0), 0.0, 12.0, 9.0);
        this.bomberExitDistance = Bounds.ranged(c.getInt("bomber-exit-distance", 150), 20, 2048);
        this.bombMaxFall = Bounds.ranged(c.getDouble("bomb-fall-speed", 0.8), 0.05, 10.0, 0.8);
        this.bombAccelTicks = Bounds.ranged(c.getInt("bomb-accel-ticks", 16), 1, 1200);
        // Fraction of the descent by which the bomb is fully nose-down (tips gradually, vertical near the ground).
        this.bombVerticalAt = Bounds.ranged(c.getDouble("bomb-vertical-at", 0.85), 0.1, 0.99, 0.85);
        this.fallVolume = (float) Bounds.ranged(c.getDouble("fall-sound-volume", 6.0), 0.0, 12.0, 6.0);
        this.damageRadius = Bounds.ranged(c.getDouble("damage-radius", 128), 1.0, 512.0, 128);
        this.maxDamage = Bounds.ranged(c.getDouble("max-damage", 100.0), 0.0, 10000.0, 100.0);
        this.maxKnockback = Bounds.ranged(c.getDouble("max-knockback", 3.5), 0.0, 100.0, 3.5);
        this.craterRadius = Bounds.ranged(c.getInt("crater-radius", 64), 0, 128);
        this.craterDepth = Bounds.ranged(c.getInt("crater-depth", 24), 0, 96);
        this.craterColumnsPerTick = Bounds.ranged(c.getInt("crater-columns-per-tick", 700), 32, 5000);
        this.blindnessRadius = Bounds.ranged(c.getDouble("blindness-radius", 64), 0.0, 512.0, 64);
        this.blindnessTicks = Bounds.ranged(c.getInt("blindness-seconds", 10), 0, 600) * 20;
        this.radiationSeconds = Bounds.ranged(c.getInt("radiation-seconds", 14), 0, 600);
        this.warningEnabled = c.getBoolean("warning-enabled", true);
        this.warningRadius = Bounds.ranged(c.getDouble("warning-radius", 200), 0.0, 1024.0, 200);
        this.inBlastTitle = c.getString("in-blast-title", "Do not hurry, you are already there");
    }

    public void spawnBomber() {
        refreshForcedChunks();
        this.bomber = new BomberModel(bomberPos, heading);
    }

    @Override
    public void run() {
        if (++tick > MAX_TICKS) {
            finish();
            return;
        }
        if (tick % 5 == 0) {
            refreshForcedChunks();
        }

        if (bomberActive) {
            tickBomber();
        }

        switch (phase) {
            case FALLING -> tickFallingBomb();
            case AFTERMATH -> tickAftermath();
            default -> { /* APPROACH handled in tickBomber; DONE is terminal */ }
        }
    }

    // ---------------------------------------------------------------- bomber

    private void tickBomber() {
        bomberPos.add(dirX * bomberSpeed, 0.0, dirZ * bomberSpeed);
        if (bomber != null) {
            bomber.moveTo(bomberPos);
        }
        spawnBomberTrail();
        playEngineSounds();
        if (tick % 4 == 0 && bomber != null) {
            bomber.spinProps(tick % 8 == 0);
        }

        double along = alongTrack();
        if (phase == Phase.APPROACH && along >= 0.0) {
            dropBomb();
        }
        if (along > bomberExitDistance) {
            bomberActive = false;
            if (bomber != null) {
                bomber.remove();
                bomber = null;
            }
        }
    }

    private void spawnBomberTrail() {
        double[][] engines = {{-3.2, -0.55, 2.6}, {3.2, -0.55, 2.6}, {-6.2, -0.50, 2.4}, {6.2, -0.50, 2.4}};
        for (double[] off : engines) {
            double wx = bomberPos.getX() + (off[0] * headingCos + off[2] * headingSin);
            double wy = bomberPos.getY() + off[1];
            double wz = bomberPos.getZ() + (-off[0] * headingSin + off[2] * headingCos);
            Location e = new Location(world, wx, wy, wz);
            world.spawnParticle(Particle.SMOKE, e, 2, dirX * -0.2, 0.02, dirZ * -0.2, 0.02);
            if (tick % 3 == 0) {
                world.spawnParticle(Particle.CLOUD, e, 2, 0.1, 0.05, 0.1, 0.01);
            }
        }
        if (tick % 2 == 0) {
            for (double side : new double[]{-9.3, 9.3}) {
                double wx = bomberPos.getX() + side * headingCos;
                double wy = bomberPos.getY() + 0.16;
                double wz = bomberPos.getZ() - side * headingSin;
                world.spawnParticle(Particle.CLOUD, new Location(world, wx, wy, wz), 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    private void playEngineSounds() {
        if (tick % 6 != 0) {
            return;
        }
        world.playSound(bomberPos, Sound.ENTITY_ENDER_DRAGON_FLAP, engineVolume, 0.45f);
        if (tick % 18 == 0) {
            world.playSound(bomberPos, Sound.ENTITY_BLAZE_AMBIENT, engineVolume * 0.5f, 0.6f);
        }
    }

    // ---------------------------------------------------------------- bomb drop + fall

    private void dropBomb() {
        phase = Phase.FALLING;
        bombDropTick = tick;
        // Spawn directly over the target (level, aligned to flight) so it drops into the exact point.
        Location spawn = new Location(world, target.getX(), bomberPos.getY() - 2.0, target.getZ());
        this.bomb = new NukeBombModel(spawn, heading);
        this.bombPos = spawn.clone();
        this.bombFallVel = 0.0;
        this.bombFallStartY = spawn.getY();

        world.playSound(target, Sound.BLOCK_PISTON_EXTEND, engineVolume, 0.6f);
        world.playSound(target, Sound.ENTITY_WITHER_SPAWN, engineVolume, 0.4f);

        if (warningEnabled && warningRadius > 0) {
            Title.Times times = Title.Times.times(
                    Duration.ofMillis(200), Duration.ofMillis(1600), Duration.ofMillis(600));
            Title incoming = Title.title(
                    me.bibo.militarycraft.core.text.Text.of("&4&l☢ ALERT ☢"),
                    me.bibo.militarycraft.core.text.Text.of("&cNuclear bomb inbound!"), times);
            for (Player p : world.getNearbyPlayers(target, warningRadius)) {
                p.showTitle(incoming);
                p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 2.0f, 0.4f);
            }
        }
    }

    private void tickFallingBomb() {
        // Accelerate up to terminal fall speed - heavy, but faster than a feather.
        bombFallVel = Math.min(bombMaxFall, bombFallVel + bombMaxFall / bombAccelTicks);
        bombPos.setY(bombPos.getY() - bombFallVel);

        // Tip from level to nose-down GRADUALLY across the whole descent, reaching
        // fully vertical only near the ground (at the configured fraction of the fall).
        if (bomb != null && !bombVertical) {
            double denom = Math.max(1.0, bombFallStartY - target.getY());
            double fallProgress = (bombFallStartY - bombPos.getY()) / denom;
            double rp = Math.max(0.0, Math.min(1.0, fallProgress / bombVerticalAt));
            double eased = rp * rp * (3.0 - 2.0 * rp);       // smoothstep
            bomb.setPitch(eased * (Math.PI / 2.0));
            if (rp >= 1.0) {
                bombVertical = true;
            }
        }
        if (bomb != null) {
            bomb.moveTo(bombPos);
        }

        Location nose = (bomb != null) ? bomb.getNoseTip() : bombPos;

        // Rising smoke trail + arming sparks.
        Location top = bombPos.clone().add(0, 2.0, 0);
        world.spawnParticle(Particle.LARGE_SMOKE, top, 5, 0.4, 0.25, 0.4, 0.01);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, top, 2, 0.3, 0.1, 0.3, 0.005);
        if (tick % 2 == 0) {
            world.spawnParticle(Particle.SMALL_FLAME, nose, 3, 0.3, 0.3, 0.3, 0.01);
        }

        // Descending whistle - pitch drops as it plummets, so everyone hears it coming.
        double travel = bombFallStartY - nose.getY();
        double total = Math.max(1.0, bombFallStartY - target.getY());
        double progress = Math.max(0.0, Math.min(1.0, travel / total));
        if (tick % 3 == 0) {
            float pitch = (float) (1.7 - progress);
            world.playSound(bombPos, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, fallVolume, pitch);
            world.playSound(target, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, fallVolume * 0.8f, pitch);
        }
        if (tick % 10 == 0) {
            world.playSound(target, Sound.ENTITY_WITHER_AMBIENT, fallVolume, 0.35f);
        }
        if (warningEnabled && warningRadius > 0 && tick % 8 == 0) {
            Component bar = me.bibo.militarycraft.core.text.Text.of("&4☢ &cNUCLEAR BOMB FALLING! &4☢");
            for (Player p : world.getNearbyPlayers(target, warningRadius)) {
                p.sendActionBar(bar);
            }
        }

        if (nose.getY() <= target.getY() || bombPos.getY() <= target.getY()) {
            detonate();
        }
    }

    // ---------------------------------------------------------------- detonation

    private void detonate() {
        phase = Phase.AFTERMATH;
        detonationTick = tick;
        if (bomb != null) {
            bomb.remove();
            bomb = null;
        }

        Location center = new Location(world, target.getX(), target.getY(), target.getZ());
        // The cloud rises out of the crater floor, so the blast starts at the bottom of the hole.
        this.cloud = new NukeCloudModel(center, craterDepth);

        playBlastSounds(center);
        spawnDetonationFlash(center);
        applyBlindnessAndShock(center);
        applyBlastDamage(center);
        buildCraterQueue(center);
    }

    private void playBlastSounds(Location center) {
        // Everyone in the world feels a distant thunderclap; the near zone gets the full roar.
        for (Player p : world.getPlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.6f, 0.35f);
        }
        double soundRadius = Math.max(blindnessRadius, damageRadius);
        for (Player p : world.getNearbyPlayers(center, soundRadius)) {
            Location at = p.getLocation();
            p.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 10.0f, 0.4f);
            p.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 10.0f, 0.5f);
            p.playSound(at, Sound.ENTITY_WITHER_DEATH, 6.0f, 0.4f);
            p.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 6.0f, 0.3f);
        }
    }

    private void spawnDetonationFlash(Location center) {
        world.strikeLightningEffect(center);
        world.spawnParticle(Particle.FLASH, center, 8, 3.0, 3.0, 3.0, 0.0);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 6, 4.0, 3.0, 4.0, 0.0);
        world.spawnParticle(Particle.EXPLOSION, center, 60, 8.0, 5.0, 8.0, 0.0);
        world.spawnParticle(Particle.FLAME, center, 300, 8.0, 5.0, 8.0, 0.3);
        world.spawnParticle(Particle.LAVA, center, 160, 6.0, 4.0, 6.0, 0.0);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 200, 8.0, 6.0, 8.0, 0.2);
    }

    /** Only players NEAR the blast are blinded; the near zone also gets the shockwave "feel". */
    private void applyBlindnessAndShock(Location center) {
        for (Player p : world.getNearbyPlayers(center, blindnessRadius)) {
            if (blindnessTicks > 0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessTicks, 0, false, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, true));
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 80, 0, false, false, true));
        }
    }

    /** Scaled magic (armour-piercing) damage + knockback in the kill zone; survivors are irradiated. */
    private void applyBlastDamage(Location center) {
        Title.Times times = Title.Times.times(
                Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(900));
        Component titleMain;
        Component titleSub;
        int comma = inBlastTitle.indexOf(',');
        if (comma > 0 && comma < inBlastTitle.length() - 1) {
            titleMain = me.bibo.militarycraft.core.text.Text.of("&4&l" + inBlastTitle.substring(0, comma + 1).trim());
            titleSub = me.bibo.militarycraft.core.text.Text.of("&c" + inBlastTitle.substring(comma + 1).trim());
        } else {
            titleMain = me.bibo.militarycraft.core.text.Text.of("&4&l☢");
            titleSub = me.bibo.militarycraft.core.text.Text.of("&c" + inBlastTitle);
        }
        Title blastTitle = Title.title(titleMain, titleSub, times);

        Collection<LivingEntity> victims = world.getNearbyLivingEntities(center, damageRadius);
        for (LivingEntity le : victims) {
            double dist = le.getLocation().distance(center);
            double falloff = Math.max(0.0, 1.0 - dist / damageRadius);
            if (falloff <= 0.0) {
                continue;
            }

            Vector push = le.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 1.0e-4) {
                push = new Vector(0, 1, 0);
            }
            push.normalize().multiply(maxKnockback * falloff);
            push.setY(Math.max(0.35, push.getY()) + 0.4 * falloff);
            le.setVelocity(le.getVelocity().add(push));

            if (le instanceof Player player) {
                GameMode mode = player.getGameMode();
                player.showTitle(blastTitle);
                if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
                    continue;
                }
                player.damage(maxDamage * falloff, magic);
                if (!player.isDead() && player.getHealth() > 0.0 && radiationSeconds > 0) {
                    manager.radiation().irradiate(player, radiationSeconds);
                }
            } else {
                le.damage(maxDamage * falloff, magic);
            }
        }
        manager.core().combat().radiusDamage(center, damageRadius, maxDamage, null, null);
    }

    // ---------------------------------------------------------------- aftermath: cloud + crater

    private void tickAftermath() {
        int t = tick - detonationTick;

        // Bloom the display mushroom from the ground up.
        if (cloud != null) {
            int want = (int) Math.ceil(cloud.totalLumps() * Math.min(1.0, t / (double) CLOUD_GROW_TICKS));
            cloud.spawnUpTo(want);
        }
        drawCloudParticles(t);
        carveCraterBatch();

        boolean craterDone = craterColumns == null || craterIndex >= craterColumns.size();
        if (t > CLOUD_LIFETIME && craterDone) {
            finish();
        }
    }

    /**
     * Dense fire + smoke that wraps the whole display cloud (fireball, stem, collar
     * ring and cap) so it reads as billowing smoke rather than bare blocks. The
     * particle shape matches {@link NukeCloudModel}'s crater-floor-based geometry
     * and rises with the display bloom.
     */
    private void drawCloudParticles(int t) {
        NukeCloudModel c = cloud;
        if (c == null) {
            return;
        }
        double bx = target.getX();
        double bz = target.getZ();
        double baseY = c.baseY();        // crater floor
        double groundY = c.groundY();    // original surface
        double grow = Math.min(1.0, t / (double) CLOUD_GROW_TICKS);
        double topNow = grow * (c.capTopLocal() + 3.0);   // local height reached so far
        double phase = t * 0.25;

        // Fireball glow, deep at the crater floor.
        Location fb = new Location(world, bx, baseY + 2.0, bz);
        world.spawnParticle(Particle.FLAME, fb, 26, 4.5, 2.5, 4.5, 0.04);
        world.spawnParticle(Particle.LAVA, fb, 6, 3.5, 1.5, 3.5, 0.0);
        world.spawnParticle(Particle.LARGE_SMOKE, fb, 14, 4.0, 2.0, 4.0, 0.02);

        // Expanding shockwave along the GROUND surface (early).
        if (t < 26) {
            double shockR = t * 3.6;
            drawRing(bx, groundY + 1.0, bz, shockR, 60, Particle.LARGE_SMOKE, 0.02);
            drawRing(bx, groundY + 1.0, bz, shockR * 0.8, 46, Particle.CLOUD, 0.01);
        }

        // Dark smoke wrapping the stem, up to the currently grown height.
        double stemLimit = Math.min(c.stemTopLocal(), topNow);
        for (double y = 2.0; y <= stemLimit; y += 3.0) {
            double r = c.stemRadiusAt(y) + 0.6;
            double wy = baseY + y;
            boolean fire = wy < groundY + 3.0;   // still burning inside the crater
            int pts = 7;
            for (int i = 0; i < pts; i++) {
                double a = 2.0 * Math.PI * i / pts + phase + y * 0.2;
                Location sp = new Location(world, bx + Math.cos(a) * r, wy, bz + Math.sin(a) * r);
                world.spawnParticle(Particle.LARGE_SMOKE, sp, 1, 0.5, 0.5, 0.5, 0.006);
                if (fire && i % 2 == 0) {
                    world.spawnParticle(Particle.FLAME, sp, 1, 0.3, 0.4, 0.3, 0.01);
                } else if (i % 2 == 0) {
                    world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, sp, 1, 0.4, 0.4, 0.4, 0.004);
                }
            }
            world.spawnParticle(Particle.SMOKE, new Location(world, bx, wy, bz), 2, 1.2, 0.4, 1.2, 0.01);
        }

        // Yellow collar ring - a band of fire + smoke around the stem.
        if (topNow >= c.ringLocal()) {
            double rr = c.stemRadiusAt(c.ringLocal()) + 2.2;
            drawRing(bx, baseY + c.ringLocal(), bz, rr, 24, Particle.FLAME, 0.01);
            drawRing(bx, baseY + c.ringLocal(), bz, rr, 24, Particle.LARGE_SMOKE, 0.01);
        }

        // Smoke + lit underside over the cap dome once it has grown in.
        if (topNow >= c.capBaseLocal()) {
            int capLevels = 6;
            for (int lvl = 0; lvl <= capLevels; lvl++) {
                double ct = lvl / (double) capLevels;
                double cyLocal = c.capBaseLocal() + ct * (c.capTopLocal() - c.capBaseLocal());
                if (cyLocal > topNow) {
                    break;
                }
                double crr = NukeCloudModel.CAP_R * Math.pow(Math.cos(ct * Math.PI * 0.5 * 0.85), 0.7);
                int pts = Math.max(6, (int) Math.round(2.0 * Math.PI * crr / 4.5));
                for (int i = 0; i < pts; i++) {
                    double a = 2.0 * Math.PI * i / pts + phase * 0.5;
                    Location cp = new Location(world, bx + Math.cos(a) * crr, baseY + cyLocal, bz + Math.sin(a) * crr);
                    world.spawnParticle(Particle.LARGE_SMOKE, cp, 1, 0.6, 0.5, 0.6, 0.004);
                    if (lvl == 0) {
                        world.spawnParticle(Particle.FLAME, cp, 1, 0.3, 0.3, 0.3, 0.01);
                    }
                }
            }
            world.spawnParticle(Particle.LARGE_SMOKE,
                    new Location(world, bx, baseY + c.capTopLocal(), bz),
                    18, NukeCloudModel.CAP_R * 0.4, 3.0, NukeCloudModel.CAP_R * 0.4, 0.01);
        }
    }

    private void drawRing(double cx, double cy, double cz, double radius, int points, Particle particle, double extra) {
        if (radius <= 0.1) {
            return;
        }
        double step = 2.0 * Math.PI / points;
        for (int i = 0; i < points; i++) {
            double a = i * step;
            world.spawnParticle(particle,
                    new Location(world, cx + Math.cos(a) * radius, cy, cz + Math.sin(a) * radius),
                    1, 0.0, 0.0, 0.0, extra);
        }
    }

    // ---------------------------------------------------------------- crater

    // pseudo-noise helpers: blocky value noise gives a jagged, natural rim
    private static double hash(int i, int j) {
        double s = Math.sin(i * 127.1 + j * 311.7) * 43758.5453;
        return (s - Math.floor(s)) * 2.0 - 1.0; // [-1, 1]
    }

    /** Medium-frequency lumps: big bites out of the rim + finer jitter. */
    private static double coarseNoise(int x, int z, int cell, int seed) {
        return hash(Math.floorDiv(x, cell) + seed, Math.floorDiv(z, cell) - seed);
    }

    private void buildCraterQueue(Location center) {
        craterColumns = new ArrayList<>();
        craterIndex = 0;
        if (craterRadius <= 0 || craterDepth <= 0) {
            return;
        }
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int iterR = (int) Math.ceil(craterRadius * 1.15) + 6; // room for noisy rim + ejecta
        int lim2 = iterR * iterR;
        for (int dx = -iterR; dx <= iterR; dx++) {
            for (int dz = -iterR; dz <= iterR; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 <= lim2) {
                    craterColumns.add(new int[]{cx + dx, cz + dz, d2});
                }
            }
        }
        // Carve from the centre outward so the crater visibly blossoms with the shockwave.
        craterColumns.sort((a, b) -> Integer.compare(a[2], b[2]));
    }

    private void carveCraterBatch() {
        if (craterColumns == null || craterIndex >= craterColumns.size()) {
            return;
        }
        int cx = target.getBlockX();
        int cz = target.getBlockZ();
        int cy = target.getBlockY();
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 1;

        int processed = 0;
        while (craterIndex < craterColumns.size() && processed < craterColumnsPerTick) {
            int[] col = craterColumns.get(craterIndex++);
            processed++;
            try {
                carveColumn(col[0], col[1], cx, cz, cy, minY, maxY);
            } catch (Exception ignored) {
                // never let one bad block abort the whole crater
            }
        }
    }

    private void carveColumn(int x, int z, int cx, int cz, int cy, int minY, int maxY) {
        int dx = x - cx;
        int dz = z - cz;
        double d = Math.sqrt(dx * dx + dz * dz);

        double nRim = 0.6 * coarseNoise(x, z, 6, 0) + 0.4 * coarseNoise(x, z, 13, 7);
        double nFine = hash(x, z);
        double effR = craterRadius * (1.0 + 0.12 * nRim) + 2.0 * nFine;

        if (d <= effR) {
            // ---- bowl interior ----
            double ratio = d / Math.max(1.0, effR);
            double depthFrac = Math.max(0.0, 1.0 - ratio * ratio);
            double depthNoise = 1.0 + 0.22 * coarseNoise(x, z, 9, 3);
            int depth = (int) Math.round(craterDepth * depthFrac * depthNoise + 1.4 * nFine);
            int floorY = Math.max(minY, cy - depth);
            int topY = Math.min(maxY, Math.max(world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE), cy + 3) + 2);

            // Scoop out everything solid (grass, flowers, snow layers included) down to the floor.
            for (int y = topY; y > floorY; y--) {
                Block b = world.getBlockAt(x, y, z);
                Material m = b.getType();
                if (m == Material.AIR || m == Material.BEDROCK) {
                    continue;
                }
                b.setType(Material.AIR, false);
            }
            scorchFloor(x, floorY, z, depthFrac, nFine);
        } else if (d <= effR + 4.0) {
            // ---- ejecta lip: debris thrown up just outside the rim ----
            double outEdge = (d - effR) / 4.0;                 // 0 at rim .. 1 outer
            double chance = (1.0 - outEdge) * 0.8;
            if ((hash(x * 3, z * 3) * 0.5 + 0.5) < chance) {
                int surface = Math.min(maxY, world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE));
                int pile = 1 + (int) Math.round((hash(x, z * 2) * 0.5 + 0.5) * 2.0 * (1.0 - outEdge));
                for (int i = 1; i <= pile; i++) {
                    Block b = world.getBlockAt(x, surface + i, z);
                    if (b.getType() == Material.AIR) {
                        b.setType(debrisMaterial(nFine + i), false);
                    }
                }
            }
        }
    }

    private void scorchFloor(int x, int floorY, int z, double depthFrac, double nFine) {
        Block floor = world.getBlockAt(x, floorY, z);
        Material cur = floor.getType();
        if (cur == Material.BEDROCK || cur == Material.AIR) {
            return;
        }
        Material scorch;
        if (depthFrac > 0.75 && nFine > 0.2) {
            scorch = Material.MAGMA_BLOCK;            // molten heart
        } else if (depthFrac > 0.45) {
            scorch = (nFine > 0.0) ? Material.BLACKSTONE : Material.COBBLED_DEEPSLATE;
        } else {
            scorch = (nFine > 0.3) ? Material.COARSE_DIRT : Material.BLACKSTONE;
        }
        floor.setType(scorch, false);
    }

    private Material debrisMaterial(double n) {
        double r = n - Math.floor(n);
        if (r < 0.4) return Material.BLACKSTONE;
        if (r < 0.7) return Material.COBBLED_DEEPSLATE;
        if (r < 0.9) return Material.COARSE_DIRT;
        return Material.DIRT;
    }

    // ---------------------------------------------------------------- geometry helpers

    private double alongTrack() {
        double dx = bomberPos.getX() - target.getX();
        double dz = bomberPos.getZ() - target.getZ();
        return dx * dirX + dz * dirZ;
    }

    // ---------------------------------------------------------------- chunk management

    private void refreshForcedChunks() {
        Set<Long> desired = new HashSet<>();
        if (bomberActive) {
            addChunkWindow(desired, bomberPos, BOMBER_CHUNK_RADIUS);
        }
        addChunkWindow(desired, target, TARGET_CHUNK_RADIUS);
        // Refcounted plugin tickets (shared across all air-support sequences) instead of
        // the global setChunkForceLoaded flag: concurrent strikes no longer unload each
        // other's chunks, and admin/other-plugin force-load state is left untouched.
        chunkWindow.updateChunks(world, desired);
    }

    private void addChunkWindow(Set<Long> set, Location loc, int radius) {
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                set.add(((long) (cx + dx) << 32) | ((cz + dz) & 0xffffffffL));
            }
        }
    }

    private void releaseChunks() {
        chunkWindow.releaseAll();
    }

    // ---------------------------------------------------------------- teardown

    private void finish() {
        teardown();
        manager.onSequenceFinished(this);
    }

    /** Silent teardown used on plugin disable. */
    public void shutdown() {
        teardown();
    }

    private void teardown() {
        if (phase == Phase.DONE) {
            return;
        }
        phase = Phase.DONE;
        if (bomber != null) {
            bomber.remove();
            bomber = null;
        }
        if (bomb != null) {
            bomb.remove();
            bomb = null;
        }
        if (cloud != null) {
            cloud.remove();
            cloud = null;
        }
        releaseChunks();
        try {
            cancel();
        } catch (IllegalStateException ignored) {
            // task was never scheduled or already cancelled
        }
    }

    public Location getTarget() {
        return target;
    }

    private enum Phase {
        APPROACH, FALLING, AFTERMATH, DONE
    }

}

