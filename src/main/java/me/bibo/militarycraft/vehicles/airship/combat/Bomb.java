package me.bibo.militarycraft.vehicles.airship.combat;

import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import me.bibo.militarycraft.vehicles.airship.AirshipRuntime;
import me.bibo.militarycraft.vehicles.airship.airship.Airship;
import me.bibo.militarycraft.vehicles.airship.config.AirshipConfig;
import me.bibo.militarycraft.vehicles.airship.util.Keys;
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
 * A dropped bomb: a visible falling block-display with a smoke trail. It is not a
 * real entity-physics body - we move a point each tick (in sub-steps for accurate
 * hit detection), apply gravity into an arc, leave smoke, and detonate the first
 * block or entity it meets into an area blast with a short impact afterglow.
 *
 * <p>Optimisation: entities are gathered with a single spatial query per tick;
 * each sub-step then only does a cheap bounding-box test against that small list.
 */
public final class Bomb {

    private static final Material BOMB_BLOCK = Material.COAL_BLOCK;

    private final AirshipRuntime plugin;
    private final World world;
    private final Vector pos;
    private final Vector vel;
    private final UUID ownerShipId;
    private final UUID ownerDriver;

    private final double gravity;
    private final int substeps;
    private final float explosionPower;
    private final boolean breakBlocks;
    private final boolean setFire;
    private final double airshipDamage;
    private final int trailDensity;

    private BlockDisplay body;
    private float spin;
    private int life;
    private boolean dead;

    public Bomb(AirshipRuntime plugin, Location start, Vector velocity, Airship ownerShip) {
        AirshipConfig cfg = plugin.config();
        this.plugin = plugin;
        this.world = start.getWorld();
        this.pos = start.toVector();
        this.vel = velocity.clone();
        this.ownerShipId = ownerShip != null ? ownerShip.id() : null;
        this.ownerDriver = ownerShip != null ? ownerShip.driver() : null;
        this.gravity = cfg.bombGravity;
        this.substeps = cfg.bombSubsteps;
        this.explosionPower = cfg.bombExplosionPower;
        this.breakBlocks = cfg.bombBreakBlocks;
        this.setFire = cfg.bombSetFire;
        this.airshipDamage = cfg.bombAirshipDamage;
        this.trailDensity = cfg.bombTrailDensity;
        this.life = cfg.bombLifetimeTicks;
        spawnBody(start);
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

    /** @return true while the bomb is still falling. */
    public boolean tick() {
        if (dead) {
            return false;
        }
        double reach = vel.length() + 4.0;
        Collection<Entity> near = world.getNearbyEntities(toLocation(), reach, reach, reach);

        Vector stepVel = vel.clone().multiply(1.0 / substeps);
        for (int s = 0; s < substeps; s++) {
            pos.add(stepVel);

            int cx = ((int) Math.floor(pos.getX())) >> 4;
            int cz = ((int) Math.floor(pos.getZ())) >> 4;
            if (!world.isChunkLoaded(cx, cz)) {
                fizzle();
                return false;
            }
            if (pos.getY() < world.getMinHeight() - 4) {
                fizzle();
                return false;
            }

            Location l = toLocation();
            if ((s & 1) == 0) {
                trail(l);
            }
            if (checkCollision(l, near)) {
                return false;
            }
        }

        vel.setY(vel.getY() - gravity);
        moveBody();

        if (--life <= 0) {
            detonate(toLocation(), null);
            return false;
        }
        return true;
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

    private void trail(Location l) {
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, l, trailDensity, 0.04, 0.04, 0.04, 0.0);
        world.spawnParticle(Particle.SMOKE, l, trailDensity, 0.03, 0.03, 0.03, 0.0);
    }

    private boolean checkCollision(Location l, Collection<Entity> near) {
        Block b = l.getBlock();
        if (!b.isPassable() && b.getType().isSolid()) {
            detonate(l, null);
            return true;
        }
        Vector pv = l.toVector();
        for (Entity e : near) {
            if (e.equals(body)) {
                continue;
            }
            if (!e.getBoundingBox().clone().expand(0.6).contains(pv)) {
                continue;
            }
            VehicleHandle vehicle = plugin.bukkitPlugin().core().vehicles().vehicleOf(e);
            if (vehicle != null) {
                if (ownerShipId != null && ownerShipId.equals(vehicle.id())) {
                    continue; // never hit our own airship
                }
                Airship hit = vehicle instanceof Airship airship ? airship : null;
                detonate(l, hit);
                return true;
            }
            if (e instanceof LivingEntity living) {
                if (ownerDriver != null && living.getUniqueId().equals(ownerDriver)) {
                    continue; // don't blow up on the pilot who dropped it
                }
                detonate(l, null);
                return true;
            }
        }
        return false;
    }

    private void detonate(Location l, Airship directHit) {
        if (dead) {
            return;
        }
        dead = true;
        removeBody();
        if (directHit != null) {
            directHit.damage(airshipDamage);
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

    private void removeBody() {
        if (body != null && body.isValid()) {
            body.remove();
        }
        body = null;
    }

    private Location toLocation() {
        return new Location(world, pos.getX(), pos.getY(), pos.getZ());
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
