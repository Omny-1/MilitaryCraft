package me.bibo.militarycraft.vehicles.helicopter.combat;

import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import me.bibo.militarycraft.vehicles.helicopter.HelicopterRuntime;
import me.bibo.militarycraft.vehicles.helicopter.config.HelicopterConfig;
import me.bibo.militarycraft.vehicles.helicopter.helicopter.Helicopter;
import me.bibo.militarycraft.vehicles.helicopter.util.Keys;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.UUID;

/**
 * A custom munition - a ROCKET (fast, flat, fiery, particle-only) or a BOMB
 * (arcing, heavy, with a visible tumbling block-display body). It is not a
 * real entity: we move a point each tick (in sub-steps for accurate hit
 * detection), apply gravity, leave a trail, and detonate on the first block
 * or entity it meets.
 *
 * <p>Optimisation: entities are gathered with a single spatial query per tick
 * (covering the travel plus a helicopter's wide hitbox); each sub-step then
 * only does a cheap bounding-box test against that small candidate list.
 */
public final class Projectile {

    public enum Type {ROCKET, BOMB}

    private static final Material BOMB_BLOCK = Material.COAL_BLOCK;

    private final HelicopterRuntime plugin;
    private final World world;
    private final Type type;
    private final Vector pos;
    private final Vector vel;
    private final Vector origin;
    private final Location scratch;
    private final UUID ownerShipId;
    private final UUID ownerDriver;

    private final double gravity;
    private final int substeps;
    private final double maxRangeSq;
    private final float explosionPower;
    private final boolean breakBlocks;
    private final boolean setFire;
    private final double heliDamage;

    private BlockDisplay body; // BOMB only: the visible tumbling block
    private float spin;
    private int life;
    private boolean dead;

    public Projectile(HelicopterRuntime plugin, Type type, Location start, Vector velocity, Helicopter owner) {
        HelicopterConfig cfg = plugin.config();
        this.plugin = plugin;
        this.world = start.getWorld();
        this.type = type;
        this.pos = start.toVector();
        this.origin = start.toVector();
        this.scratch = start.clone();
        this.vel = velocity.clone();
        this.ownerShipId = owner != null ? owner.id() : null;
        this.ownerDriver = owner != null ? owner.driver() : null;

        if (type == Type.ROCKET) {
            this.gravity = cfg.rocketGravity;
            this.substeps = cfg.rocketSubsteps;
            this.maxRangeSq = cfg.rocketMaxRange * cfg.rocketMaxRange;
            this.explosionPower = cfg.rocketExplosionPower;
            this.breakBlocks = cfg.rocketBreakBlocks;
            this.setFire = cfg.rocketSetFire;
            this.heliDamage = cfg.rocketHeliDamage;
            this.life = cfg.rocketLifetimeTicks;
        } else {
            this.gravity = cfg.bombGravity;
            this.substeps = cfg.bombSubsteps;
            this.maxRangeSq = Double.MAX_VALUE; // bombs run on lifetime, not range
            this.explosionPower = cfg.bombExplosionPower;
            this.breakBlocks = cfg.bombBreakBlocks;
            this.setFire = cfg.bombSetFire;
            this.heliDamage = cfg.bombHeliDamage;
            this.life = cfg.bombLifetimeTicks;
            spawnBody(start);
        }
    }

    private void spawnBody(Location at) {
        try {
            body = world.spawn(at, BlockDisplay.class, b -> {
                b.setBlock(BOMB_BLOCK.createBlockData());
                b.setBrightness(new Display.Brightness(15, 15));
                b.setPersistent(false);
                b.setViewRange(2.5f);
                b.setTeleportDuration(1);
                b.addScoreboardTag(Keys.SCOREBOARD_TAG);
                Transformation t = b.getTransformation();
                t.getScale().set(0.7f);
                t.getTranslation().set(-0.35f, -0.35f, -0.35f); // centre the cube on the point
                b.setTransformation(t);
            });
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not spawn bomb display: " + ex.getMessage());
            body = null;
        }
    }

    private void moveBody() {
        if (body == null || !body.isValid()) {
            return;
        }
        spin += 14f;
        Quaternionf rot = new Quaternionf()
                .rotateY((float) Math.toRadians(spin))
                .rotateX((float) Math.toRadians(spin * 0.6));
        // keep the spin centred on the point: undo the rotated half-extent
        Vector3f half = rot.transform(new Vector3f(0.35f, 0.35f, 0.35f));
        Transformation t = new Transformation(
                new Vector3f(-half.x, -half.y, -half.z), rot,
                new Vector3f(0.7f), new Quaternionf());
        body.setInterpolationDelay(0);
        body.setInterpolationDuration(1);
        body.setTransformation(t);
        body.teleport(toLocation());
    }

    private void removeBody() {
        if (body != null && body.isValid()) {
            body.remove();
        }
        body = null;
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
        // ONE entity query for the whole tick (covers the travel plus a
        // helicopter's wide hitbox), then cheap bounding-box tests per sub-step.
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
            // can't skip through a one-block-thick wall in the gap between sub-steps.
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
        if (type == Type.BOMB) {
            moveBody();
        }
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
            if (e.equals(body)) {
                continue;
            }
            // reproduce a 0.6-radius overlap with a cheap point-in-box test
            if (!e.getBoundingBox().clone().expand(0.6).contains(x, y, z)) {
                continue;
            }
            VehicleHandle vehicle = plugin.bukkitPlugin().core().vehicles().vehicleOf(e);
            if (vehicle != null) {
                if (ownerShipId != null && ownerShipId.equals(vehicle.id())) {
                    continue; // never hit our own helicopter
                }
                Helicopter hit = vehicle instanceof Helicopter helicopter ? helicopter : null;
                detonate(l, hit);
                return true;
            }
            if (e instanceof LivingEntity living) {
                if (ownerDriver != null && living.getUniqueId().equals(ownerDriver)) {
                    continue; // don't hit the pilot who fired/dropped it
                }
                detonate(l, null);
                return true;
            }
        }
        return false;
    }

    private void detonate(Location l, Helicopter directHit) {
        if (dead) {
            return;
        }
        dead = true;
        removeBody();
        if (directHit != null) {
            directHit.damage(heliDamage);
        }
        UUID directHitId = directHit != null ? directHit.id() : null;
        Explosions.detonate(plugin, l, ownerShipId, directHitId, explosionPower, breakBlocks, setFire);
    }

    private void fizzle() {
        if (dead) {
            return;
        }
        dead = true;
        removeBody();
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

    /** Cleanup hook for shutdown/clear so no orphan display is left behind. */
    public void discard() {
        dead = true;
        removeBody();
    }
}
