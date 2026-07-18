package me.bibo.militarycraft.vehicles.tank.combat;

import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import me.bibo.militarycraft.vehicles.tank.TankRuntime;
import me.bibo.militarycraft.vehicles.tank.config.TankConfig;
import me.bibo.militarycraft.vehicles.tank.tank.Tank;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * A custom ballistic shell. It is not a real entity: we move a point each tick
 * (in sub-steps for accurate hit detection), apply gravity to bend the path into
 * an arc, leave a smoke trail, and detonate on the first block or entity it meets.
 */
public final class Shell {

    private final TankRuntime plugin;
    private final World world;
    private final TankConfig cfg;
    private final Vector pos;
    private final Vector vel;
    private final Vector origin;
    private final UUID ownerTankId;
    private final UUID ownerDriver;
    private final double maxRangeSq;

    private int life;
    private boolean dead;

    public Shell(TankRuntime plugin, Location start, Vector direction, Tank ownerTank) {
        this.plugin = plugin;
        this.cfg = plugin.config();
        this.world = start.getWorld();
        this.pos = start.toVector();
        this.origin = start.toVector();
        this.vel = direction.clone().normalize().multiply(cfg.shellSpeed);
        this.ownerTankId = ownerTank != null ? ownerTank.id() : null;
        this.ownerDriver = ownerTank != null ? ownerTank.driver() : null;
        this.life = cfg.shellLifetimeTicks;
        this.maxRangeSq = cfg.shellMaxRange * cfg.shellMaxRange;
    }

    /** @return true while the shell is still flying. */
    public boolean tick() {
        if (dead) {
            return false;
        }
        int sub = cfg.shellSubsteps;
        Vector tickStart = pos.clone();
        Vector stepVel = vel.clone().multiply(1.0 / sub);
        for (int s = 0; s < sub; s++) {
            pos.add(stepVel);

            double travelledSq = pos.distanceSquared(origin);
            if (travelledSq > maxRangeSq) {
                fizzle();
                return false;
            }
            int cx = ((int) Math.floor(pos.getX())) >> 4;
            int cz = ((int) Math.floor(pos.getZ())) >> 4;
            if (!world.isChunkLoaded(cx, cz)) {
                fizzle();
                return false;
            }

        }
        CollisionHit hit = pos.distanceSquared(origin) > 2.25 ? firstCollision(tickStart, pos) : null;
        if (hit != null) {
            detonate(hit.location(), hit.directTankHit());
            return false;
        }

        Location head = toLocation();
        world.spawnParticle(Particle.DUST, head, 1, 0.02, 0.02, 0.02, 0.0,
                new Particle.DustOptions(Color.fromRGB(255, 198, 64), 0.7f));
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, head, 2, 0.03, 0.03, 0.03, 0.0);

        vel.setY(vel.getY() - cfg.shellGravity);
        if (--life <= 0) {
            detonate(toLocation(), null);
            return false;
        }
        return true;
    }

    private CollisionHit firstCollision(Vector from, Vector to) {
        Vector delta = to.clone().subtract(from);
        double distance = delta.length();
        if (distance <= 1e-6) {
            return null;
        }
        Vector direction = delta.multiply(1.0 / distance);
        Location start = vectorLocation(from);
        CollisionHit block = null;
        RayTraceResult blockResult = world.rayTraceBlocks(start, direction, distance,
                FluidCollisionMode.NEVER, true);
        if (blockResult != null && blockResult.getHitPosition() != null) {
            Location hitAt = vectorLocation(blockResult.getHitPosition());
            block = new CollisionHit(hitAt, null, from.distance(blockResult.getHitPosition()));
        }

        CollisionHit entity = null;
        RayTraceResult entityResult = world.rayTraceEntities(start, direction, distance, 0.7,
                this::canHit);
        if (entityResult != null && entityResult.getHitEntity() != null) {
            Entity hitEntity = entityResult.getHitEntity();
            VehicleHandle vehicle = plugin.bukkitPlugin().core().vehicles().vehicleOf(hitEntity);
            Tank hitTank = vehicle instanceof Tank tank ? tank : null;
            Vector hitPos = entityResult.getHitPosition() == null ? to : entityResult.getHitPosition();
            entity = new CollisionHit(vectorLocation(hitPos), hitTank, from.distance(hitPos));
        }

        if (block == null) {
            return entity;
        }
        if (entity == null) {
            return block;
        }
        return entity.distance() <= block.distance() ? entity : block;
    }

    private boolean canHit(Entity e) {
        VehicleHandle vehicle = plugin.bukkitPlugin().core().vehicles().vehicleOf(e);
        if (vehicle != null) {
            return ownerTankId == null || !ownerTankId.equals(vehicle.id());
        }
        if (e instanceof LivingEntity living) {
            return ownerDriver == null || !living.getUniqueId().equals(ownerDriver);
        }
        return e instanceof Interaction
                || e instanceof ArmorStand
                || e instanceof Display;
    }

    private void detonate(Location l, Tank directTankHit) {
        if (dead) {
            return;
        }
        dead = true;
        if (directTankHit != null) {
            directTankHit.damage(cfg.shellTankDamage);
        }
        Explosions.detonate(plugin, l, ownerTankId);
    }

    private void fizzle() {
        if (dead) {
            return;
        }
        dead = true;
        world.spawnParticle(Particle.LARGE_SMOKE, toLocation(), 6, 0.2, 0.2, 0.2, 0.01);
    }

    private Location toLocation() {
        return new Location(world, pos.getX(), pos.getY(), pos.getZ());
    }

    private Location vectorLocation(Vector v) {
        return new Location(world, v.getX(), v.getY(), v.getZ());
    }

    private record CollisionHit(Location location, Tank directTankHit, double distance) {
    }

    public boolean isDead() {
        return dead;
    }
}
