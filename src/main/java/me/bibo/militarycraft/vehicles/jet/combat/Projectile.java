package me.bibo.militarycraft.vehicles.jet.combat;

import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import me.bibo.militarycraft.vehicles.jet.JetRuntime;
import me.bibo.militarycraft.vehicles.jet.config.JetConfig;
import me.bibo.militarycraft.vehicles.jet.jet.Jet;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.UUID;

/**
 * A custom munition — a rocket (fast, flat, fiery trail) or a bomb (arcing,
 * heavy, smoke trail). It is not a real entity: we move a point each tick (in
 * sub-steps for accurate hit detection), apply gravity, leave a trail, and
 * detonate on the first block or entity it meets.
 *
 * <p>Optimisation: entities are gathered with a single spatial query per tick
 * (covering the whole step plus a jet's wide hitbox); each sub-step then only
 * does a cheap bounding-box test against that small candidate list, instead of
 * a fresh world scan per sub-step.
 */
public final class Projectile {

    public enum Type {ROCKET, BOMB}

    private final JetRuntime plugin;
    private final World world;
    private final Type type;
    private final Vector pos;
    private final Vector vel;
    private final Vector origin;
    private final Location scratch;
    private final UUID ownerJetId;
    private final UUID ownerDriver;

    private final double gravity;
    private final int substeps;
    private final double maxRangeSq;
    private final float explosionPower;
    private final boolean breakBlocks;
    private final boolean setFire;
    private final double jetDamage;

    private int life;
    private boolean dead;

    public Projectile(JetRuntime plugin, Type type, Location start, Vector velocity, Jet ownerJet) {
        JetConfig cfg = plugin.config();
        this.plugin = plugin;
        this.world = start.getWorld();
        this.type = type;
        this.pos = start.toVector();
        this.origin = start.toVector();
        this.scratch = start.clone();
        this.vel = velocity.clone();
        this.ownerJetId = ownerJet != null ? ownerJet.id() : null;
        this.ownerDriver = ownerJet != null ? ownerJet.driver() : null;

        if (type == Type.ROCKET) {
            this.gravity = cfg.rocketGravity;
            this.substeps = cfg.rocketSubsteps;
            this.maxRangeSq = cfg.rocketMaxRange * cfg.rocketMaxRange;
            this.explosionPower = cfg.rocketExplosionPower;
            this.breakBlocks = cfg.rocketBreakBlocks;
            this.setFire = cfg.rocketSetFire;
            this.jetDamage = cfg.rocketJetDamage;
            this.life = cfg.rocketLifetimeTicks;
        } else {
            this.gravity = cfg.bombGravity;
            this.substeps = cfg.bombSubsteps;
            this.maxRangeSq = Double.MAX_VALUE; // bombs run on lifetime, not range
            this.explosionPower = cfg.bombExplosionPower;
            this.breakBlocks = cfg.bombBreakBlocks;
            this.setFire = cfg.bombSetFire;
            this.jetDamage = cfg.bombJetDamage;
            this.life = cfg.bombLifetimeTicks;
        }
    }

    /** @return true while the munition is still flying. */
    public boolean tick() {
        if (dead) {
            return false;
        }
        if (!isChunkLoaded(pos.getX(), pos.getZ())) {
            fizzle();
            return false;
        }
        // ONE entity query for the whole tick (covers the travel plus a jet's
        // wide hitbox), then cheap bounding-box tests per sub-step.
        double reach = vel.length() + 6.0;
        Collection<Entity> near = world.getNearbyEntities(toLocation(), reach, reach, reach);

        Vector stepVel = vel.clone().multiply(1.0 / substeps);
        double hx = stepVel.getX() * 0.5, hy = stepVel.getY() * 0.5, hz = stepVel.getZ() * 0.5;
        for (int s = 0; s < substeps; s++) {
            pos.add(stepVel);

            double distSq = pos.distanceSquared(origin);
            if (distSq > maxRangeSq) {
                fizzle();
                return false;
            }
            if (!isChunkLoaded(pos.getX(), pos.getZ())) {
                fizzle();
                return false;
            }

            // Extra solid-block sample at the sub-step midpoint, so a fast munition
            // (rocket + jet speed) can't skip through a one-block-thick wall in the
            // gap between sub-step end points.
            if (distSq > 2.25) {
                double mx = pos.getX() - hx, my = pos.getY() - hy, mz = pos.getZ() - hz;
                Block mb = world.getBlockAt((int) Math.floor(mx), (int) Math.floor(my), (int) Math.floor(mz));
                if (!mb.isPassable() && mb.getType().isSolid()) {
                    detonate(new Location(world, mx, my, mz), null);
                    return false;
                }
            }

            // one Location reused for both the trail and the collision test
            Location l = toLocation();
            if ((s & 1) == 0) {
                trail(l); // trail every other sub-step (fewer packets)
            }
            if (distSq > 2.25 && checkCollision(l, near)) {
                return false;
            }
        }

        vel.setY(vel.getY() - gravity);
        if (--life <= 0) {
            detonate(toLocation(), null);
            return false;
        }
        return true;
    }

    private void trail(Location l) {
        if (type == Type.ROCKET) {
            world.spawnParticle(Particle.FLAME, l, 1, 0.02, 0.02, 0.02, 0.0);
            world.spawnParticle(Particle.SMOKE, l, 1, 0.02, 0.02, 0.02, 0.0);
        } else {
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, l, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    private boolean checkCollision(Location l, Collection<Entity> near) {
        Block b = l.getBlock();
        if (!b.isPassable() && b.getType().isSolid()) {
            detonate(l, null);
            return true;
        }
        double x = l.getX();
        double y = l.getY();
        double z = l.getZ();
        for (Entity e : near) {
            // reproduce the original 0.7-radius overlap with a cheap point-in-box test
            if (!e.getBoundingBox().clone().expand(0.6).contains(x, y, z)) {
                continue;
            }
            VehicleHandle vehicle = plugin.bukkitPlugin().core().vehicles().vehicleOf(e);
            if (vehicle != null) {
                if (ownerJetId != null && ownerJetId.equals(vehicle.id())) {
                    continue; // never hit our own jet
                }
                Jet hit = vehicle instanceof Jet jet ? jet : null;
                detonate(l, hit);
                return true;
            }
            if (e instanceof LivingEntity living) {
                if (ownerDriver != null && living.getUniqueId().equals(ownerDriver)) {
                    continue; // don't hit the pilot
                }
                detonate(l, null);
                return true;
            }
        }
        return false;
    }

    private void detonate(Location l, Jet directJetHit) {
        if (dead) {
            return;
        }
        dead = true;
        if (directJetHit != null) {
            directJetHit.damage(jetDamage);
        }
        UUID directJetToSkip = directJetHit != null ? directJetHit.id() : null;
        Explosions.detonate(plugin, l, ownerJetId, directJetToSkip, explosionPower, breakBlocks, setFire);
    }

    private void fizzle() {
        if (dead) {
            return;
        }
        dead = true;
        world.spawnParticle(Particle.LARGE_SMOKE, toLocation(), 6, 0.2, 0.2, 0.2, 0.01);
    }

    private Location toLocation() {
        scratch.setX(pos.getX());
        scratch.setY(pos.getY());
        scratch.setZ(pos.getZ());
        return scratch;
    }

    private boolean isChunkLoaded(double x, double z) {
        return world.isChunkLoaded(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
    }

    public boolean isDead() {
        return dead;
    }
}
