package me.bibo.militarycraft.vehicles.drone.combat;

import me.bibo.militarycraft.core.combat.VehicleHit;
import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import me.bibo.militarycraft.vehicles.drone.DroneRuntime;
import me.bibo.militarycraft.vehicles.drone.config.DroneConfig;
import me.bibo.militarycraft.vehicles.drone.util.Keys;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * A lightweight unguided rocket: a particle-trailed point that ray-marches in
 * sub-steps and detonates on the first block or living entity it meets. Weaker
 * than the jet's rockets, but a direct hit is lethal to soft targets. Four of
 * these are carried per UAV and do not reload.
 */
public final class Rocket {

    private final DroneRuntime plugin;
    private final World world;
    private final Vector dir;       // unit travel direction
    private final UUID shooter;     // operator (immune to own blast, never self-hit)
    private final UUID ownerVehicle;
    private final double step;      // blocks per sub-step
    private final int subOps;       // sub-steps per tick

    private double x, y, z;
    private double travelled;

    public Rocket(DroneRuntime plugin, Location start, Vector direction, UUID shooter) {
        this.plugin = plugin;
        this.world = start.getWorld();
        this.x = start.getX();
        this.y = start.getY();
        this.z = start.getZ();
        this.dir = direction.clone().normalize();
        this.shooter = shooter;
        var owner = shooter == null ? null : plugin.drones().byDriver(shooter);
        this.ownerVehicle = owner == null ? null : owner.id();
        DroneConfig cfg = plugin.config();
        this.subOps = cfg.rocketSubsteps;
        this.step = cfg.rocketSpeed / cfg.rocketSubsteps;
    }

    public void launch() {
        DroneConfig cfg = plugin.config();
        world.playSound(new Location(world, x, y, z), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.3f);
        new BukkitRunnable() {
            int ticks = cfg.rocketLifetimeTicks;

            @Override
            public void run() {
                if (ticks-- <= 0) {
                    cancel();
                    return;
                }
                for (int i = 0; i < subOps; i++) {
                    x += dir.getX() * step;
                    y += dir.getY() * step;
                    z += dir.getZ() * step;
                    travelled += step;

                    Location at = new Location(world, x, y, z);
                    if (y < world.getMinHeight() || y > world.getMaxHeight() + 16
                            || travelled > cfg.rocketMaxRange) {
                        cancel();
                        return;
                    }
                    if (travelled < 1.2) {
                        continue; // arming distance: never detonate right at the launcher
                    }
                    Block b = world.getBlockAt(at);
                    if (!b.isPassable() && b.getType().isSolid()) {
                        detonate(at, null, null, cfg);
                        cancel();
                        return;
                    }
                    VehicleHit vehicle = plugin.core().combat().vehicleNear(at, 0.7, ownerVehicle);
                    if (vehicle != null) {
                        detonate(vehicle.point(), null, vehicle.vehicle(), cfg);
                        cancel();
                        return;
                    }
                    LivingEntity hit = firstEntity(at, 0.7);
                    if (hit != null) {
                        detonate(hit.getLocation().add(0, hit.getHeight() * 0.5, 0), hit, null, cfg);
                        cancel();
                        return;
                    }
                }
                Location p = new Location(world, x, y, z);
                world.spawnParticle(Particle.FLAME, p, 2, 0.02, 0.02, 0.02, 0.0);
                world.spawnParticle(Particle.SMOKE, p, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }.runTaskTimer(plugin.bukkitPlugin(), 0L, 1L);
    }

    private LivingEntity firstEntity(Location at, double radius) {
        LivingEntity best = null;
        double bestSq = radius * radius;
        for (Entity e : world.getNearbyEntities(at, radius, radius, radius)) {
            if (!(e instanceof LivingEntity le) || e.isDead()) {
                continue;
            }
            if (shooter != null && e.getUniqueId().equals(shooter)) {
                continue;
            }
            if (e.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
                continue; // our own UAV entities
            }
            double dSq = e.getLocation().add(0, le.getHeight() * 0.5, 0).distanceSquared(at);
            if (dSq < bestSq) {
                bestSq = dSq;
                best = le;
            }
        }
        return best;
    }

    private void detonate(Location at, LivingEntity directHit, VehicleHandle directVehicle, DroneConfig cfg) {
        if (directHit != null) {
            directHit.damage(cfg.rocketDirectDamage);
        }
        if (directVehicle != null) {
            plugin.core().combat().directDamage(directVehicle, cfg.rocketDirectDamage);
        }
        plugin.drones().setInternalExplosion(true);
        plugin.drones().setMunitionImmunePilot(shooter);
        try {
            me.bibo.militarycraft.core.combat.Explosions.createExplosion(
                    world, at, cfg.rocketExplosionPower, cfg.rocketSetFire, cfg.rocketBreakBlocks);
        } finally {
            plugin.drones().setInternalExplosion(false);
            plugin.drones().setMunitionImmunePilot(null);
        }
        plugin.core().combat().explosionDamage(at, cfg.rocketExplosionPower, ownerVehicle);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1, 0.2, 0.2, 0.2, 0);
        world.spawnParticle(Particle.LARGE_SMOKE, at, 20, 0.8, 0.6, 0.8, 0.04);
        world.spawnParticle(Particle.FLAME, at, 16, 0.6, 0.5, 0.6, 0.06);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 1.3f);
    }
}
