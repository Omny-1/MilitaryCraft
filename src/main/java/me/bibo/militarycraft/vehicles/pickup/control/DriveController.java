package me.bibo.militarycraft.vehicles.pickup.control;

import me.bibo.militarycraft.vehicles.pickup.config.PickupConfig;
import me.bibo.militarycraft.vehicles.pickup.util.MathUtil;
import me.bibo.militarycraft.vehicles.pickup.vehicle.Pickup;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * One tick of driving: throttle, steering, ground contact and what happens when the pickup hits
 * something.
 *
 * <p>The camera steers - where the driver looks is where the truck goes. Speed ramps in over a few
 * ticks of held input instead of snapping to full, which is what makes it feel like a heavy vehicle
 * rather than a moving platform. Deep water floods the engine and the pickup stops responding.
 */
public final class DriveController {
    private static final double HALF_LENGTH = 3.2f;
    private static final double HALF_WIDTH = 1.35f;
    private static final double BLOCK_CHECK_HALF_LENGTH = 2.95f;
    private static final double BLOCK_CHECK_HALF_WIDTH = 1.200000023841858;
    private static final double[][] FOOTPRINT_OFFSETS = new double[][]{{0.0, 0.0}, {1.6, -1.05}, {1.6, 1.05}, {-1.6, -1.05}, {-1.6, 1.05}, {2.0800000309944155, 0.0}, {-2.0800000309944155, 0.0}, {0.0, -1.1475000202655792}, {0.0, 1.1475000202655792}};

    private DriveController() {
    }

    public static void drive(Pickup pickup, Player driver, PickupConfig cfg) {
        double newY;
        Double support;
        Double g;
        double rate;
        Input in = driver.getCurrentInput();
        Location eye = driver.getLocation();
        boolean drowned = pickup.isSubmerged();
        boolean wantForward = !drowned && in.isForward();
        boolean wantBackward = !drowned && in.isBackward();
        pickup.setForwardHoldTicks(wantForward ? Math.min(cfg.accelRampTicks, pickup.forwardHoldTicks() + 1) : 0);
        pickup.setBackwardHoldTicks(wantBackward ? Math.min(cfg.accelRampTicks, pickup.backwardHoldTicks() + 1) : 0);
        double target = wantForward ? cfg.maxForwardSpeed : (wantBackward ? -cfg.maxReverseSpeed : 0.0);
        double speed = pickup.speed();
        if (target == 0.0) {
            rate = cfg.friction;
        } else if (Math.signum(target) != Math.signum(speed) && Math.abs(speed) > 1.0E-6) {
            rate = cfg.braking;
        } else {
            int holdTicks = wantForward ? pickup.forwardHoldTicks() : pickup.backwardHoldTicks();
            double ramp = Math.min(1.0, (double)holdTicks / (double)cfg.accelRampTicks);
            rate = cfg.acceleration + (cfg.accelerationMax - cfg.acceleration) * ramp;
        }
        speed = MathUtil.approach(speed, target, rate);
        pickup.setSpeed(speed);
        double steerTarget = 0.0;
        if (!drowned && (wantForward || wantBackward || Math.abs(speed) > 0.02)) {
            double yawDiff = MathUtil.wrapDegrees((double)eye.getYaw() - pickup.hullYaw());
            steerTarget = MathUtil.clamp(yawDiff, -cfg.frontWheelSteerAngle, cfg.frontWheelSteerAngle);
            pickup.setHullYaw(MathUtil.approachAngle(pickup.hullYaw(), eye.getYaw(), cfg.turnSpeed));
        }
        double steerStep = Math.max(4.0, cfg.turnSpeed * 1.5);
        pickup.setWheelSteer(MathUtil.approachAngle(pickup.wheelSteer(), steerTarget, steerStep));
        Location anchor = pickup.anchor();
        World world = pickup.world();
        double oldX = anchor.getX();
        double oldY = anchor.getY();
        double oldZ = anchor.getZ();
        if (Math.abs(speed) < 1.0E-6 && pickup.verticalVelocity() == 0.0 && !in.isForward() && !in.isBackward() && (g = groundTopAt(world, oldX, oldZ, oldY, cfg)) != null && oldY - g <= cfg.maxStepUp + 0.001) {
            if (g < oldY - 1.0E-4) {
                anchor.setY(g);
            }
            if (world.getFullTime() % (long)cfg.hudInterval == 0L) {
                if (drowned) {
                    Hud.sendSubmergedWarning(driver);
                } else {
                    Hud.send(pickup, driver);
                }
            }
            return;
        }
        double yawRad = Math.toRadians(pickup.hullYaw());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double newX = oldX + fx * speed;
        double newZ = oldZ + fz * speed;
        if (speed != 0.0 && leadingEdgeBlocked(world, newX, newZ, oldY, fx, fz, Math.signum(speed), cfg)) {
            newX = oldX;
            newZ = oldZ;
            pickup.setSpeed(0.0);
        }
        if ((support = footprintGround(world, newX, newZ, fx, fz, oldY, cfg)) == null) {
            double vy = Math.max(pickup.verticalVelocity() - cfg.gravity, -cfg.maxFallSpeed);
            pickup.setVerticalVelocity(vy);
            newY = oldY + vy;
        } else {
            double rise = support - oldY;
            if (rise <= 0.001) {
                double vy = pickup.verticalVelocity();
                if (cfg.fallDamage > 0.0 && vy < -cfg.fallDamageThreshold && pickup.damage(cfg.fallDamage * (-vy - cfg.fallDamageThreshold))) {
                    return;
                }
                pickup.setVerticalVelocity(0.0);
                newY = support;
            } else {
                boolean falling = pickup.verticalVelocity() < -1.0E-6;
                boolean driving = Math.abs(pickup.speed()) > 0.03;
                pickup.setVerticalVelocity(0.0);
                newY = driving && rise <= cfg.maxStepUp + 0.001 ? oldY + Math.min(rise, cfg.climbRate) : (falling ? support : oldY);
            }
        }
        if (newY < (double)(world.getMinHeight() - 6)) {
            pickup.destroy(true);
            return;
        }
        double horizontalTravel = Math.hypot(newX - oldX, newZ - oldZ);
        pickup.advanceWheelSpin(horizontalTravel * Math.signum(pickup.speed()));
        anchor.setX(newX);
        anchor.setY(newY);
        anchor.setZ(newZ);
        if (cfg.rammingEnabled && world.getFullTime() % 3L == 0L && Math.abs(pickup.speed()) >= cfg.rammingMinSpeed) {
            double sign = Math.signum(pickup.speed());
            ram(pickup, driver, fx * sign, fz * sign, cfg);
        }
        movementEffects(pickup, cfg, drowned);
        if (world.getFullTime() % (long)cfg.hudInterval == 0L) {
            if (drowned) {
                Hud.sendSubmergedWarning(driver);
            } else {
                Hud.send(pickup, driver);
            }
        }
    }

    private static void ram(Pickup pickup, Player driver, double dirX, double dirZ, PickupConfig cfg) {
        Vector travel = new Vector(dirX, 0.0, dirZ);
        if (travel.lengthSquared() <= 1.0E-6) {
            return;
        }
        travel.normalize();
        Location anchor = pickup.anchor();
        double yaw = Math.toRadians(pickup.hullYaw());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        double halfLen = 3.550000047683716;
        double halfWid = 1.700000023841858;
        double radius = Math.sqrt(halfLen * halfLen + halfWid * halfWid);
        Location centre = anchor.clone().add(0.0, 1.65f, 0.0);
        long now = System.currentTimeMillis();
        for (Entity e : pickup.world().getNearbyEntities(centre, radius, 2.449999976158142, radius)) {
            Vector to;
            double dist;
            Player p;
            if (!(e instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity)e;
            if (!(e instanceof Mob) && !(e instanceof Player) || e.equals(driver) || e.getScoreboardTags().contains("pickupcraft_entity") || e.getVehicle() != null || living instanceof Player && ((p = (Player)living).getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)) continue;
            double dx = e.getX() - anchor.getX();
            double dz = e.getZ() - anchor.getZ();
            double lx = dx * cos + dz * sin;
            double lz = -dx * sin + dz * cos;
            double dy = e.getY() - anchor.getY();
            if (Math.abs(lx) > halfWid || Math.abs(lz) > halfLen || dy < -0.8 || dy > 4.099999952316284 || (dist = (to = living.getLocation().toVector().subtract(centre.toVector()).setY(0)).length()) > 1.0E-4 && pickup.world().rayTraceBlocks(centre, to.normalize(), Math.max(0.1, dist - 0.3), FluidCollisionMode.NEVER, true) != null || !pickup.tryRam(living.getUniqueId(), now, cfg.rammingCooldownMs)) continue;
            living.damage(cfg.rammingDamage, driver);
            Vector push = travel.clone().multiply(cfg.rammingKnockback).setY(0.28);
            living.setVelocity(living.getVelocity().multiply(0.35).add(push));
        }
    }

    private static void movementEffects(Pickup pickup, PickupConfig cfg, boolean drowned) {
        double ratio;
        long interval;
        if (drowned || Math.abs(pickup.speed()) < 0.035 || pickup.verticalVelocity() != 0.0) {
            return;
        }
        World world = pickup.world();
        long time = world.getFullTime();
        Location anchor = pickup.anchor();
        if (cfg.dustTrail && time % 4L == 0L) {
            double yawRad = Math.toRadians(pickup.hullYaw());
            double rx = Math.cos(yawRad);
            double rz = Math.sin(yawRad);
            double side = 1.05;
            spawnDust(world, anchor, rx * side, rz * side);
            spawnDust(world, anchor, -rx * side, -rz * side);
        }
        if (cfg.engineSound && time % (interval = Math.max(4L, Math.round(14.0 - (ratio = Math.min(1.0, Math.abs(pickup.speed()) / Math.max(0.01, cfg.maxForwardSpeed))) * 9.0))) == 0L) {
            world.playSound(anchor, Sound.ENTITY_RAVAGER_STEP, 0.4f, (float)(0.6 + ratio * 0.5));
        }
    }

    private static void spawnDust(World world, Location anchor, double dx, double dz) {
        Location at = anchor.clone().add(dx, 0.08, dz);
        Block ground = world.getBlockAt(at.getBlockX(), (int)Math.floor(anchor.getY() - 0.05), at.getBlockZ());
        if (!ground.getType().isSolid()) {
            return;
        }
        world.spawnParticle(Particle.BLOCK, at, 3, 0.22, 0.04, 0.22, 0.015, ground.getBlockData());
    }

    private static Double footprintGround(World world, double cx, double cz, double fx, double fz, double curY, PickupConfig cfg) {
        double rx = fz;
        double rz = -fx;
        double best = Double.NEGATIVE_INFINITY;
        boolean found = false;
        for (double[] o : FOOTPRINT_OFFSETS) {
            double sx = cx + fx * o[0] + rx * o[1];
            double sz = cz + fz * o[0] + rz * o[1];
            Double g = groundTopAt(world, sx, sz, curY, cfg);
            if (g == null) continue;
            found = true;
            best = Math.max(best, g);
        }
        return found ? Double.valueOf(best) : null;
    }

    private static boolean leadingEdgeBlocked(World world, double cx, double cz, double curY, double fx, double fz, double moveSign, PickupConfig cfg) {
        if (moveSign == 0.0) {
            moveSign = 1.0;
        }
        double rx = fz;
        double rz = -fx;
        double leadX = cx + fx * 2.95f * moveSign;
        double leadZ = cz + fz * 2.95f * moveSign;
        for (double s : new double[]{-1.200000023841858, 0.0, 1.200000023841858}) {
            double sx = leadX + rx * s;
            double sz = leadZ + rz * s;
            Double g = groundTopAt(world, sx, sz, curY, cfg);
            if (g == null || !(g - curY > cfg.maxStepUp) && !blockedAhead(world, sx, g, sz)) continue;
            return true;
        }
        return false;
    }

    private static Double groundTopAt(World world, double x, double z, double currentY, PickupConfig cfg) {
        int bx = (int)Math.floor(x);
        int bz = (int)Math.floor(z);
        int startY = (int)Math.floor(currentY + cfg.maxStepUp);
        int endY = (int)Math.floor(currentY - cfg.groundSnapDistance) - 1;
        int min = world.getMinHeight();
        int max = world.getMaxHeight();
        for (int by = Math.min(startY, max - 1); by >= Math.max(endY, min); --by) {
            Block b = world.getBlockAt(bx, by, bz);
            if (b.isPassable() || !b.getType().isSolid()) continue;
            double topY = b.getBoundingBox().getMaxY();
            return topY > (double)by + 0.01 ? topY : (double)by + 1.0;
        }
        return null;
    }

    private static boolean blockedAhead(World world, double x, double groundTop, double z) {
        int bx = (int)Math.floor(x);
        int bz = (int)Math.floor(z);
        int by = (int)Math.floor(groundTop + 0.6);
        Block b = world.getBlockAt(bx, by, bz);
        return !b.isPassable() && b.getType().isSolid();
    }
}
