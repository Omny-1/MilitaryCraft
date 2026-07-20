package me.bibo.militarycraft.weapons.airstrike.task;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import me.bibo.militarycraft.core.airsupport.ChunkWindow;
import me.bibo.militarycraft.weapons.airstrike.AirstrikeRuntime;
import me.bibo.militarycraft.weapons.airstrike.model.Su57Model;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class AirstrikeSequence extends BukkitRunnable {

    // --- geometry / pacing constants ---
    /** Force-load radius (in chunks) around the jet. 2 => a 5x5 window. */
    private static final int CHUNK_RADIUS = 2;
    /** Force-load radius around the target, so dropped TNT always ticks/explodes. */
    private static final int TARGET_CHUNK_RADIUS = 2;
    /** Hard safety cap so a sequence can never run forever (60s). */
    private static final int MAX_TICKS = 20 * 60;

    private final AirstrikeRuntime plugin;
    private final World world;
    private final Location target;

    // config (clamped to safe ranges)
    private final int tntCount;
    private final int tntSpread;
    private final int tntFuseTicks;
    private final double jetSpeed;
    private final float engineVolume;
    private final int trailDensity;
    private final int exitDistance;
    private final double warningRadius;
    /** Along-track length of the bomb carpet, centred on the target. */
    private final double bombRunLength;
    private final double bombRunHalf;

    // heading (constant for the whole pass) - precomputed once
    private final double dirX;
    private final double dirZ;
    private final float heading;
    private final float headingCos;
    private final float headingSin;

    private final ChunkWindow chunkWindow;

    private Su57Model model;
    private Phase phase = Phase.APPROACH;
    private int tick = 0;
    private int tntDropped = 0;
    private final Location jetPos;

    public AirstrikeSequence(AirstrikeRuntime plugin, Location target, Location jetStart, double dirX, double dirZ) {
        this.plugin = plugin;
        this.chunkWindow = new ChunkWindow(plugin.bukkitPlugin());
        this.world = target.getWorld();
        this.target = target;
        this.jetPos = jetStart.clone();
        this.dirX = dirX;
        this.dirZ = dirZ;
        this.heading = (float) Math.atan2(dirX, dirZ);
        this.headingCos = (float) Math.cos(heading);
        this.headingSin = (float) Math.sin(heading);

        var config = plugin.getConfig();
        // Hard caps so a bad/huge config cannot drop a server-killing TNT storm in one pass.
        // jet-speed is bounded too: at unbounded speed the jet crosses the whole bomb run in a
        // single tick and drops every bomb at once; capping it spreads the drops across ticks.
        this.tntCount = Math.min(200, Math.max(0, config.getInt("tnt-count", 12)));
        this.tntSpread = Math.min(64, Math.max(0, config.getInt("tnt-spread", 8)));
        this.tntFuseTicks = Math.max(1, config.getInt("tnt-fuse-ticks", 80));
        this.jetSpeed = Math.max(0.1, Math.min(10.0, config.getDouble("jet-speed", 1.8)));
        this.engineVolume = (float) config.getDouble("engine-sound-volume", 8.0);
        // trail-density is an INTERVAL (emit every N ticks): only a lower bound. An upper cap
        // would perversely RAISE the particle rate for a config that meant "emit rarely".
        this.trailDensity = Math.max(1, config.getInt("trail-density", 1));
        this.exitDistance = Math.max(10, config.getInt("jet-exit-distance", 100));
        this.warningRadius = Math.max(0.0, config.getInt("warning-radius", 150));
        this.bombRunLength = Math.max(0.0, Math.min(150.0, config.getDouble("bomb-run-length", 50.0)));
        this.bombRunHalf = bombRunLength / 2.0;
    }

    public void spawnJet() {
        refreshForcedChunks();
        this.model = new Su57Model(jetPos, heading);
    }

    @Override
    public void run() {
        if (++tick > MAX_TICKS || model == null) {
            finish();
            return;
        }
        if (tick % 5 == 0) {
            refreshForcedChunks();
        }

        advanceJet();
        spawnExhaustParticles();
        playEngineSounds();
        flickerAfterburners();

        // Signed distance past the target along the flight path:
        // negative before the target, 0 over it, positive after it.
        double along = alongTrack();

        // Start the run early enough that the carpet is centred on the target.
        if (phase == Phase.APPROACH && along >= -bombRunHalf) {
            beginBombingRun();
        }
        if (phase == Phase.BOMBING) {
            dropDueBombs(along);
            if (tntDropped >= tntCount && along > bombRunHalf) {
                phase = Phase.EXIT;
            }
        }
        if (phase == Phase.EXIT && horizontalDistanceToTarget() > exitDistance) {
            finish();
        }
        if (phase == Phase.DONE) {
            finish();
        }
    }

    private void beginBombingRun() {
        phase = Phase.BOMBING;
        world.playSound(target, Sound.ENTITY_IRON_GOLEM_ATTACK, engineVolume * 0.6f, 0.5f);
        if (!plugin.getConfig().getBoolean("warning-enabled", true) || warningRadius <= 0) {
            return;
        }
        String raw = plugin.getConfig().getString("warning-message", "&c&l⚠ AIRSTRIKE! Take cover!");
        Component warning = AirstrikeRuntime.text(raw);
        // Action bar instead of a full-screen title - compact but still prominent.
        for (Player p : world.getNearbyPlayers(target, warningRadius)) {
            p.sendActionBar(warning);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 2.0f, 0.5f);
        }
    }

    /** Drops every bomb whose scheduled along-track position the jet has reached. */
    private void dropDueBombs(double along) {
        while (tntDropped < tntCount && along >= dropAlongFor(tntDropped)) {
            dropTNT();
            tntDropped++;
        }
    }

    /** Along-track position (relative to target) at which bomb {@code index} should fall. */
    private double dropAlongFor(int index) {
        if (tntCount <= 1) {
            return 0.0; // single bomb lands on the target
        }
        return -bombRunHalf + index * (bombRunLength / (tntCount - 1));
    }

    private void advanceJet() {
        jetPos.add(dirX * jetSpeed, 0.0, dirZ * jetSpeed);
        model.moveTo(jetPos);
    }

    private double horizontalDistanceToTarget() {
        double dx = jetPos.getX() - target.getX();
        double dz = jetPos.getZ() - target.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Projection of (jet - target) onto the flight direction; signed distance past the target. */
    private double alongTrack() {
        double dx = jetPos.getX() - target.getX();
        double dz = jetPos.getZ() - target.getZ();
        return dx * dirX + dz * dirZ;
    }

    private void dropTNT() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double offsetX = (rng.nextDouble() * 2.0 - 1.0) * tntSpread;
        double offsetZ = (rng.nextDouble() * 2.0 - 1.0) * tntSpread;
        Location dropLoc = jetPos.clone().add(offsetX, -1.0, offsetZ);
        world.spawn(dropLoc, TNTPrimed.class, tnt -> {
            tnt.setFuseTicks(tntFuseTicks);
            tnt.setVelocity(new Vector((rng.nextDouble() - 0.5) * 0.3, -0.1, (rng.nextDouble() - 0.5) * 0.3));
        });
        world.playSound(dropLoc, Sound.ENTITY_ARROW_SHOOT, engineVolume * 0.5f, 0.3f);
    }

    private void spawnExhaustParticles() {
        if (tick % trailDensity != 0) {
            return;
        }
        // Two engine nozzles at the rear, offset in local space then rotated into world space.
        double[][] nozzles = {{-0.4, -0.05, -3.9}, {0.4, -0.05, -3.9}};
        boolean critTick = tick % 3 == 0;
        for (double[] off : nozzles) {
            double lx = off[0], ly = off[1], lz = off[2];
            double wx = jetPos.getX() + (lx * headingCos + lz * headingSin);
            double wy = jetPos.getY() + ly;
            double wz = jetPos.getZ() + (-lx * headingSin + lz * headingCos);
            Location exhaust = new Location(world, wx, wy, wz);
            world.spawnParticle(Particle.FLAME, exhaust, 5, dirX * -0.3, 0.0, dirZ * -0.3, 0.08);
            world.spawnParticle(Particle.CLOUD, exhaust, 4, dirX * -0.5, 0.05, dirZ * -0.5, 0.05);
            if (critTick) {
                world.spawnParticle(Particle.CRIT, exhaust, 3, dirX * -0.2, 0.1, dirZ * -0.2, 0.15);
            }
        }
        if (tick % 4 == 0) {
            Location trail = jetPos.clone().add(dirX * -5.0, 0.0, dirZ * -5.0);
            world.spawnParticle(Particle.CLOUD, trail, 6, 0.3, 0.1, 0.3, 0.02);
        }
    }

    private void playEngineSounds() {
        if (tick % 8 != 0) {
            return;
        }
        world.playSound(jetPos, Sound.ENTITY_ENDER_DRAGON_FLAP, engineVolume, 0.4f);
        if (tick % 16 == 0) {
            world.playSound(jetPos, Sound.ENTITY_BLAZE_AMBIENT, engineVolume * 0.6f, 1.8f);
        }
    }

    private void flickerAfterburners() {
        if (tick % 6 == 0) {
            model.flickerAfterburners(tick % 12 == 0);
        }
    }

    // --- chunk management: keep only a small sliding window force-loaded ---

    private void refreshForcedChunks() {
        Set<Long> desired = new HashSet<>();
        addChunkWindow(desired, jetPos, CHUNK_RADIUS);
        // Keep the target area loaded too, so falling bombs always tick down
        // and explode even when the jet has flown out of range.
        addChunkWindow(desired, target, TARGET_CHUNK_RADIUS);
        // Refcounted plugin tickets (shared across all air-support sequences) instead of
        // the global setChunkForceLoaded flag: two concurrent strikes no longer unload
        // each other's chunks, and admin/other-plugin force-load state is left untouched.
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

    /** Normal end of the pass: dramatic finish, then tear down. */
    private void finish() {
        if (phase == Phase.DONE) {
            return;
        }
        if (model != null && model.isValid()) {
            world.strikeLightningEffect(jetPos);
        }
        teardown();
        plugin.getAirstrikeManager().onSequenceFinished(this);
    }

    /** Silent teardown used on plugin disable (no effects). */
    public void shutdown() {
        teardown();
    }

    private void teardown() {
        phase = Phase.DONE;
        if (model != null) {
            model.remove();
            model = null;
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
        APPROACH, BOMBING, EXIT, DONE
    }

}
