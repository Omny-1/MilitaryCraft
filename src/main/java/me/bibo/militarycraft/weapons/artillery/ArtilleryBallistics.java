package me.bibo.militarycraft.weapons.artillery;

import me.bibo.militarycraft.core.combat.Explosions;
import org.bukkit.Location;
import org.bukkit.HeightMap;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Plans, launches and owns every shell in a three-round artillery salvo. */
final class ArtilleryBallistics {

    static final int SALVO_SIZE = 3;
    private static final long[] SALVO_DELAYS = {0L, 10L, 20L};

    private final ArtilleryManager manager;
    private final ArtilleryTaskTracker tasks;
    private final Set<ActiveShell> activeShells = new HashSet<>();
    private final Set<SmokeCloud> smokeClouds = new HashSet<>();

    ArtilleryBallistics(ArtilleryManager manager, ArtilleryTaskTracker tasks) {
        this.manager = manager;
        this.tasks = tasks;
    }

    ValidatedTarget validate(Artillery artillery, double targetX, double targetZ) {
        Location origin = manager.models().muzzleTip(artillery);
        if (origin == null || origin.getWorld() == null) {
            return ValidatedTarget.unavailable();
        }
        ArtillerySettings settings = manager.settings();
        double distance = ArtilleryMath.horizontalDistance(
                origin.getX(), origin.getZ(), targetX, targetZ);
        double spread = ArtilleryMath.spreadRadius(distance, settings.minSpread, settings.maxSpread,
                settings.accuracyReferenceRange, settings.accuracyExponent);
        WorldBorder border = origin.getWorld().getWorldBorder();
        Location borderCenter = border.getCenter();
        ArtilleryTargetValidator.Validation validation = ArtilleryTargetValidator.validate(
                origin.getX(), origin.getZ(), targetX, targetZ, settings.maxRange,
                borderCenter.getX(), borderCenter.getZ(), border.getSize(), spread);
        return new ValidatedTarget(origin, targetX, targetZ, distance, spread, validation);
    }

    PreparedSalvo prepare(ValidatedTarget target) {
        if (target == null || !target.available() || !target.validation().valid()) {
            return null;
        }
        World world = target.origin().getWorld();
        ArtillerySettings settings = manager.settings();
        List<ShellPlan> shells = new ArrayList<>(SALVO_SIZE);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        try {
            for (int i = 0; i < SALVO_SIZE; i++) {
                ArtilleryMath.Offset error = ArtilleryMath.sampleUniformDisc(
                        target.spreadRadius(), random.nextDouble(), random.nextDouble());
                double impactX = target.targetX() + error.x();
                double impactZ = target.targetZ() + error.z();
                int blockX = (int) Math.floor(impactX);
                int blockZ = (int) Math.floor(impactZ);
                if (!world.getWorldBorder().isInside(new Location(world, impactX, 0.0, impactZ))
                        || !world.isChunkGenerated(blockX >> 4, blockZ >> 4)) {
                    return null;
                }
                int terrainY = world.getHighestBlockYAt(blockX, blockZ,
                        HeightMap.MOTION_BLOCKING_NO_LEAVES);
                int impactY = terrainY + 1;
                if (terrainY < world.getMinHeight() || impactY >= world.getMaxHeight()
                        || world.getBlockAt(blockX, terrainY, blockZ).isEmpty()) {
                    return null;
                }
                Location impact = new Location(world, impactX, impactY, impactZ);
                double actualDistance = ArtilleryMath.horizontalDistance(
                        target.origin().getX(), target.origin().getZ(), impactX, impactZ);
                int flightTicks = ArtilleryMath.flightTicks(actualDistance, settings.maxRange,
                        settings.minFlightTicks, settings.maxFlightTicks);
                double apex = ArtilleryMath.clamp(actualDistance * 0.6, 18.0, 140.0);
                shells.add(new ShellPlan(target.origin().clone(), impact, flightTicks, apex));
            }
        } catch (RuntimeException ex) {
            manager.core().logger().warning("Could not resolve artillery target terrain: " + ex.getMessage());
            return null;
        }
        return new PreparedSalvo(List.copyOf(shells), settings);
    }

    boolean launch(PreparedSalvo salvo) {
        if (salvo == null || salvo.shells().size() != SALVO_SIZE) {
            throw new IllegalArgumentException("An artillery salvo must contain exactly three shells");
        }
        List<BukkitTask> scheduled = new ArrayList<>(SALVO_SIZE);
        try {
            for (int i = 0; i < SALVO_SIZE; i++) {
                ShellPlan plan = salvo.shells().get(i);
                BukkitTask task = tasks.later(() -> launchShell(plan, salvo.settings()), SALVO_DELAYS[i]);
                if (task == null) {
                    throw new IllegalStateException("artillery task tracker is shutting down");
                }
                scheduled.add(task);
            }
            return true;
        } catch (RuntimeException ex) {
            for (BukkitTask task : scheduled) {
                tasks.cancel(task);
            }
            manager.core().logger().warning("Could not schedule artillery salvo: " + ex.getMessage());
            return false;
        }
    }

    void shutdown() {
        for (ActiveShell shell : Set.copyOf(activeShells)) {
            shell.stop();
        }
        for (SmokeCloud cloud : Set.copyOf(smokeClouds)) {
            cloud.stop();
        }
        activeShells.clear();
        smokeClouds.clear();
    }

    private void launchShell(ShellPlan plan, ArtillerySettings settings) {
        muzzleEffects(plan.origin(), settings);
        ActiveShell shell = new ActiveShell(plan, settings);
        activeShells.add(shell);
        if (!shell.start()) {
            shell.stop();
        }
    }

    private void muzzleEffects(Location at, ArtillerySettings settings) {
        World world = at.getWorld();
        if (settings.sounds) {
            world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.9f, 0.45f);
            world.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.4f, 0.7f);
        }
        if (settings.particles) {
            world.spawnParticle(Particle.FLASH, at, 2, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticle(Particle.EXPLOSION, at, 5, 0.4, 0.4, 0.4, 0.0);
            world.spawnParticle(Particle.FLAME, at, 70, 0.6, 0.6, 0.6, 0.12);
            world.spawnParticle(Particle.LARGE_SMOKE, at, 45, 0.6, 0.7, 0.6, 0.05);
        }
    }

    private void detonate(Location impact, ArtillerySettings settings) {
        World world = impact.getWorld();
        Explosions.createExplosion(world, impact, settings.explosionPower,
                settings.setFire, settings.breakBlocks);
        manager.core().combat().explosionDamage(impact, settings.explosionPower);

        if (settings.sounds) {
            world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.7f, 0.5f);
        }
        if (settings.particles) {
            world.spawnParticle(Particle.EXPLOSION_EMITTER, impact, 2, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticle(Particle.EXPLOSION, impact, 8, 1.6, 1.0, 1.6, 0.0);
            world.spawnParticle(Particle.LARGE_SMOKE, impact, 80, 2.5, 1.8, 2.5, 0.06);
        }
        if (settings.particles && settings.impactSmokeDurationTicks > 0
                && settings.impactSmokeRadius > 0.0) {
            SmokeCloud cloud = new SmokeCloud(impact.clone(), settings);
            smokeClouds.add(cloud);
            cloud.start();
        }
    }

    record ValidatedTarget(Location origin, double targetX, double targetZ, double distance,
                           double spreadRadius, ArtilleryTargetValidator.Validation validation) {

        static ValidatedTarget unavailable() {
            return new ValidatedTarget(null, 0.0, 0.0, Double.NaN, 0.0,
                    new ArtilleryTargetValidator.Validation(
                            ArtilleryTargetValidator.Error.NOT_FINITE, Double.NaN));
        }

        boolean available() {
            return origin != null && origin.getWorld() != null;
        }
    }

    record PreparedSalvo(List<ShellPlan> shells, ArtillerySettings settings) {
    }

    private record ShellPlan(Location origin, Location impact, int flightTicks, double apex) {
    }

    private final class ActiveShell implements Runnable {

        private final ShellPlan plan;
        private final ArtillerySettings settings;
        private final BlockDisplay display;
        private BukkitTask task;
        private int tick;
        private boolean stopped;
        private boolean detonated;

        private ActiveShell(ShellPlan plan, ArtillerySettings settings) {
            this.plan = plan;
            this.settings = settings;
            BlockDisplay spawned = null;
            try {
                spawned = manager.models().spawnShell(plan.origin());
            } catch (RuntimeException ex) {
                manager.core().logger().warning("Could not spawn artillery shell display: " + ex.getMessage());
            }
            this.display = spawned;
        }

        private boolean start() {
            task = tasks.repeating(this, 0L, 1L);
            return task != null;
        }

        @Override
        public void run() {
            try {
                tickShell();
            } catch (RuntimeException ex) {
                stop();
                manager.core().logger().warning("Artillery shell task failed: " + ex.getMessage());
            }
        }

        private void tickShell() {
            if (stopped) {
                return;
            }
            if (tick >= plan.flightTicks()) {
                Location impact = plan.impact().clone();
                stop();
                detonateOnce(impact);
                return;
            }
            double fraction = tick / (double) plan.flightTicks();
            double x = ArtilleryMath.lerp(plan.origin().getX(), plan.impact().getX(), fraction);
            double z = ArtilleryMath.lerp(plan.origin().getZ(), plan.impact().getZ(), fraction);
            double baseY = ArtilleryMath.lerp(plan.origin().getY(), plan.impact().getY(), fraction);
            double y = baseY + 4.0 * plan.apex() * fraction * (1.0 - fraction);
            Location at = new Location(plan.origin().getWorld(), x, y, z);
            if (display != null && display.isValid()) {
                display.teleport(at);
            }
            if (settings.particles) {
                at.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                        at, 4, 0.06, 0.06, 0.06, 0.004);
                at.getWorld().spawnParticle(Particle.SMOKE,
                        at, 3, 0.06, 0.06, 0.06, 0.0);
                at.getWorld().spawnParticle(Particle.FLAME,
                        at, 1, 0.03, 0.03, 0.03, 0.0);
            }
            tick++;
        }

        private void detonateOnce(Location impact) {
            if (detonated) {
                return;
            }
            detonated = true;
            detonate(impact, settings);
        }

        private void stop() {
            if (stopped) {
                return;
            }
            stopped = true;
            tasks.cancel(task);
            if (display != null && display.isValid()) {
                display.remove();
            }
            activeShells.remove(this);
        }
    }

    private final class SmokeCloud implements Runnable {

        private final Location impact;
        private final ArtillerySettings settings;
        private BukkitTask task;
        private int remaining;
        private boolean stopped;

        private SmokeCloud(Location impact, ArtillerySettings settings) {
            this.impact = impact;
            this.settings = settings;
            this.remaining = settings.impactSmokeDurationTicks;
        }

        private void start() {
            task = tasks.repeating(this, 1L, 4L);
        }

        @Override
        public void run() {
            try {
                tickSmoke();
            } catch (RuntimeException ex) {
                stop();
                manager.core().logger().warning("Artillery smoke task failed: " + ex.getMessage());
            }
        }

        private void tickSmoke() {
            if (remaining <= 0 || impact.getWorld() == null) {
                stop();
                return;
            }
            remaining -= 4;
            double radius = settings.impactSmokeRadius;
            impact.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                    impact, 14, radius, 0.8, radius, 0.02);
            impact.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                    impact, 22, radius, 1.0, radius, 0.03);
            impact.getWorld().spawnParticle(Particle.SMOKE,
                    impact, 16, radius, 0.8, radius, 0.02);
        }

        private void stop() {
            if (stopped) {
                return;
            }
            stopped = true;
            tasks.cancel(task);
            smokeClouds.remove(this);
        }
    }
}
