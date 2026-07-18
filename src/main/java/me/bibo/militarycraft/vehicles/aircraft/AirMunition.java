package me.bibo.militarycraft.vehicles.aircraft;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.key.EntityTag;
import me.bibo.militarycraft.core.vehicle.DisplayVehicle;
import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** A point-marched rocket/bomb shared by aircraft modules. */
public final class AirMunition {

    private final Core core;
    private final AirMunitionSpec spec;
    private final World world;
    private final Vector pos;
    private final Vector vel;
    private final Vector origin;
    private final UUID ownerVehicle;
    private final UUID ownerDriver;
    private final double maxRangeSq;
    private final Consumer<BukkitTask> effectTaskSink;
    private int life;
    private boolean dead;

    public AirMunition(Core core, AirMunitionSpec spec, Location start, Vector velocity, DisplayVehicle owner,
                       Consumer<BukkitTask> effectTaskSink) {
        this.core = Objects.requireNonNull(core, "core");
        this.spec = Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(velocity, "velocity");
        if (!AircraftPlacement.isFinite(start)
                || !AircraftSafety.coordinatesFinite(velocity.getX(), velocity.getY(), velocity.getZ())) {
            throw new IllegalArgumentException("Munition position and velocity must be finite");
        }
        this.world = start.getWorld();
        this.pos = start.toVector();
        this.origin = start.toVector();
        this.vel = velocity.clone();
        double maxSpeedSq = AircraftSafety.MAX_MUNITION_SPEED * AircraftSafety.MAX_MUNITION_SPEED;
        if (this.vel.lengthSquared() > maxSpeedSq) {
            this.vel.normalize().multiply(AircraftSafety.MAX_MUNITION_SPEED);
        }
        this.ownerVehicle = owner != null ? owner.id() : null;
        this.ownerDriver = owner != null ? owner.driver() : null;
        this.effectTaskSink = Objects.requireNonNull(effectTaskSink, "effectTaskSink");
        this.life = spec.lifetimeTicks();
        this.maxRangeSq = spec.maxRange() > 0 ? spec.maxRange() * spec.maxRange() : Double.MAX_VALUE;
    }

    public boolean tick() {
        if (dead) {
            return false;
        }
        if (!chunkLoaded(pos.getX(), pos.getZ())) {
            fizzle();
            return false;
        }
        double reach = Math.max(4.0, vel.length() + 6.0);
        Location current = toLocation();
        Collection<Entity> nearby = AircraftPlacement.isAreaLoaded(current, reach)
                ? world.getNearbyEntities(current, reach, reach, reach) : List.of();
        int sub = spec.substeps();
        Vector step = vel.clone().multiply(1.0 / sub);
        Vector mid = step.clone().multiply(0.5);
        for (int i = 0; i < sub; i++) {
            pos.add(step);
            if (pos.distanceSquared(origin) > maxRangeSq || !chunkLoaded(pos.getX(), pos.getZ())
                    || pos.getY() < world.getMinHeight() - 6 || pos.getY() > world.getMaxHeight() + 24) {
                fizzle();
                return false;
            }
            if (pos.distanceSquared(origin) > 2.25) {
                double mx = pos.getX() - mid.getX();
                double my = pos.getY() - mid.getY();
                double mz = pos.getZ() - mid.getZ();
                if (solid(mx, my, mz)) {
                    detonate(new Location(world, mx, my, mz), null);
                    return false;
                }
            }
            Location at = toLocation();
            if ((i & 1) == 0) {
                trail(at);
            }
            VehicleHandle direct = collision(at, nearby);
            if (dead) {
                return false;
            }
            if (direct != null) {
                detonate(at, direct);
                return false;
            }
        }
        vel.setY(vel.getY() - spec.gravity());
        if (--life <= 0) {
            detonate(toLocation(), null);
            return false;
        }
        return true;
    }

    private VehicleHandle collision(Location at, Collection<Entity> nearby) {
        Block b = at.getBlock();
        if (!b.isPassable() && b.getType().isSolid()) {
            return nullHit(at);
        }
        double x = at.getX();
        double y = at.getY();
        double z = at.getZ();
        for (Entity e : nearby) {
            if (!e.getBoundingBox().clone().expand(0.6).contains(x, y, z)) {
                continue;
            }
            VehicleHandle handle = core.vehicles().vehicleOf(e);
            if (handle != null) {
                if (ownerVehicle != null && ownerVehicle.equals(handle.id())) {
                    continue;
                }
                return handle;
            }
            if (e instanceof LivingEntity living && !EntityTag.isTagged(e)) {
                if (ownerDriver != null && ownerDriver.equals(living.getUniqueId())) {
                    continue;
                }
                if (spec.directLivingDamage() > 0) {
                    living.damage(spec.directLivingDamage());
                }
                return nullHit(at);
            }
        }
        return null;
    }

    private VehicleHandle nullHit(Location at) {
        detonate(at, null);
        return null;
    }

    private void detonate(Location at, VehicleHandle direct) {
        if (dead) {
            return;
        }
        dead = true;
        if (direct != null && spec.directVehicleDamage() > 0) {
            core.combat().directDamage(direct, spec.directVehicleDamage());
        }
        UUID directId = direct != null ? direct.id() : null;
        AircraftExplosion.detonate(core, at, spec.explosionPower(), spec.setFire(), spec.breakBlocks(),
                ownerVehicle, directId);
        impactAfterglow(at.clone());
    }

    private void impactAfterglow(Location loc) {
        BukkitTask task = me.bibo.militarycraft.core.combat.Explosions.impactAfterglow(
                core.plugin(), loc, spec.impactSmokeDuration(), spec.impactSmokeRadius());
        if (task != null) {
            effectTaskSink.accept(task);
        }
    }

    private void trail(Location at) {
        Particle particle = spec.trailParticle();
        if (particle == null || spec.trailCount() <= 0) {
            return;
        }
        world.spawnParticle(particle, at, spec.trailCount(), 0.03, 0.03, 0.03, 0.0);
    }

    private void fizzle() {
        dead = true;
        if (chunkLoaded(pos.getX(), pos.getZ())) {
            world.spawnParticle(Particle.LARGE_SMOKE, toLocation(), 6, 0.2, 0.2, 0.2, 0.01);
        }
    }

    private boolean solid(double x, double y, double z) {
        Block b = world.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        return !b.isPassable() && b.getType().isSolid();
    }

    private boolean chunkLoaded(double x, double z) {
        return world.isChunkLoaded(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
    }

    private Location toLocation() {
        return new Location(world, pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean isDead() {
        return dead;
    }
}
