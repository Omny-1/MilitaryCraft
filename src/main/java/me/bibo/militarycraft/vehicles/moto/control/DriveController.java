package me.bibo.militarycraft.vehicles.moto.control;

import me.bibo.militarycraft.vehicles.moto.config.MotoConfig;
import me.bibo.militarycraft.vehicles.moto.model.MotorcycleModel;
import me.bibo.militarycraft.vehicles.moto.motorcycle.Motorcycle;
import me.bibo.militarycraft.vehicles.moto.util.Keys;
import me.bibo.militarycraft.vehicles.moto.util.MathUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Arcade driving for the motorcycle, mirroring the proven Kamaz controller: the
 * hull turns toward where the driver looks while moving forward; W/S are throttle.
 * The visual handlebar/front wheel follows the same steer angle. There is no
 * A/D bicycle model - you steer with the camera, exactly like the truck.
 *
 * <p>The vertical model samples the ground under the three wheels and rides on the
 * highest support found, so the bike doesn't jitter on block edges or sink in.
 *
 * <p>{@link #sweptImpact} keeps the narrow vehicle from tunnelling through victims
 * between ticks: at/above the high-speed fraction they are flung, otherwise crushed
 * for a capped, non-lethal amount.
 */
public final class DriveController {

    private static final double MAX_HORIZONTAL_SUBSTEP = 0.18;
    private static final double MAX_YAW_SUBSTEP_DEGREES = 2.0;
    private static final double COLLISION_EPSILON = 0.015;
    private static final double IMPACT_VERTICAL_REACH = 0.35;
    private static final double MAX_FALL_SPEED = 1.50;

    private DriveController() {
    }

    public static void drive(Motorcycle motorcycle, Player driver, MotoConfig cfg) {
        org.bukkit.Input in = driver.getCurrentInput();
        Location eye = driver.getLocation();
        boolean drowned = cfg.drownEnabled && motorcycle.isSubmerged();
        double preYaw = motorcycle.hullYaw();

        // --- throttle with a separate brake rate (no power while stalled in water) ---
        double target = drowned ? 0.0
                : in.isForward() ? cfg.maxForwardSpeed
                : in.isBackward() ? -cfg.maxReverseSpeed : 0.0;
        double speed = motorcycle.speed();
        double rate;
        if (target == 0.0) {
            rate = cfg.friction;
        } else if (Math.signum(target) != Math.signum(speed) && Math.abs(speed) > 1e-6) {
            rate = cfg.braking;
        } else {
            rate = cfg.acceleration;
        }
        speed = MathUtil.approach(speed, target, rate);
        motorcycle.setSpeed(speed);

        // --- hull turns toward the camera only while driving forward ---
        double steerTarget = 0.0;
        if (!drowned && speed > 0.02) {
            double yawDiff = MathUtil.wrapDegrees(eye.getYaw() - motorcycle.hullYaw());
            steerTarget = MathUtil.clamp(yawDiff, -cfg.handlebarAngle, cfg.handlebarAngle);
            motorcycle.setHullYaw(MathUtil.approachAngle(motorcycle.hullYaw(), eye.getYaw(), cfg.maxTurnSpeed));
        }
        double steerStep = Math.max(4.0, cfg.maxTurnSpeed * 1.5);
        motorcycle.setWheelSteer(MathUtil.approachAngle(motorcycle.wheelSteer(), steerTarget, steerStep));

        Location anchor = motorcycle.anchor();
        World world = motorcycle.world();
        double oldX = anchor.getX();
        double oldY = anchor.getY();
        double oldZ = anchor.getZ();

        // --- idle fast-path: a parked, grounded bike does no horizontal work ---
        if (Math.abs(speed) < 1e-6 && Math.abs(motorcycle.verticalVelocity()) < 1e-6
                && !in.isForward() && !in.isBackward()) {
            Double g = groundTopAt(world, oldX, oldZ, oldY, cfg);
            if (g != null && (oldY - g) <= cfg.maxStepUp + 1e-3) {
                if (g < oldY - 1e-4) {
                    anchor.setY(g);
                }
                motorcycle.refreshModel();
                hud(motorcycle, driver, drowned, cfg);
                return;
            }
            // ground vanished / dropped far away -> fall through to gravity physics
        }

        double yawRad = Math.toRadians(motorcycle.hullYaw());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);

        double halfLen = MotorcycleModel.LENGTH / 2.0 - 0.4;
        double halfWid = MotorcycleModel.WIDTH / 2.0 - 0.3;

        double newX = oldX + fx * speed;
        double newZ = oldZ + fz * speed;

        // --- wall test: forward edge must not face a step taller than we can climb,
        //     nor a low ceiling sitting on that step ---
        if (speed != 0 && leadingEdgeBlocked(world, newX, newZ, oldY, fx, fz,
                Math.signum(speed), halfLen, halfWid, cfg)) {
            newX = oldX;
            newZ = oldZ;
            speed = 0.0;
            motorcycle.setSpeed(0.0);
        }

        // --- vertical: rest on the footprint ground, but only RISE for a genuine
        //     forward step or when landing. ---
        Double support = footprintGround(world, newX, newZ, motorcycle.hullYaw(), oldY,
                motorcycle.hasSidecar(), cfg);
        double newY;
        if (support == null) {
            double safeTerminal = Math.min(MAX_FALL_SPEED,
                    Math.max(0.08, cfg.groundSnapDistance * 0.8));
            double vy = DriveMath.nextVerticalVelocity(
                    motorcycle.verticalVelocity(), cfg.gravity, safeTerminal);
            motorcycle.setVerticalVelocity(vy);
            newY = oldY + vy;
        } else {
            double rise = support - oldY;
            if (rise <= 1e-3) {
                double vy = motorcycle.verticalVelocity();
                if (cfg.fallDamage > 0 && vy < -cfg.fallDamageThreshold) {
                    double dmg = cfg.fallDamage * (-vy - cfg.fallDamageThreshold);
                    if (motorcycle.damage(dmg)) {
                        return; // destroyed by the landing
                    }
                }
                motorcycle.setVerticalVelocity(0.0);
                newY = support;
            } else {
                boolean falling = motorcycle.verticalVelocity() < -1e-6;
                boolean driving = Math.abs(speed) > 0.03;
                motorcycle.setVerticalVelocity(0.0);
                if (driving && rise <= cfg.maxStepUp + 1e-3) {
                    newY = oldY + Math.min(rise, cfg.climbRate);
                } else if (falling) {
                    newY = support;
                } else {
                    newY = oldY;
                }
            }
        }

        // --- void guard ---
        if (!Double.isFinite(newY) || newY < world.getMinHeight() - 6.0) {
            motorcycle.destroy(false);
            return;
        }

        double horizontalTravel = Math.hypot(newX - oldX, newZ - oldZ);
        motorcycle.advanceWheelSpin(horizontalTravel * Math.signum(speed));

        anchor.setX(newX);
        anchor.setY(newY);
        anchor.setZ(newZ);
        motorcycle.refreshModel();

        // --- narrow-vehicle impact sweep (never tunnels a victim between ticks) ---
        if (cfg.impactEnabled && horizontalTravel > 1e-6
                && Math.abs(speed) >= cfg.impactMinSpeed) {
            sweptImpact(motorcycle, driver,
                    oldX, oldY, oldZ, preYaw,
                    newX, newY, newZ, motorcycle.hullYaw(),
                    newX - oldX, newZ - oldZ, Math.abs(speed), cfg);
        }

        hud(motorcycle, driver, drowned, cfg);
    }

    // ------------------------------------------------------------------- wall test

    /** Check the three points along the leading edge for an impassable wall. */
    private static boolean leadingEdgeBlocked(World world, double cx, double cz, double curY,
                                              double fx, double fz, double moveSign,
                                              double halfLen, double halfWid, MotoConfig cfg) {
        if (moveSign == 0) {
            moveSign = 1;
        }
        double rx = fz;
        double rz = -fx;
        double leadX = cx + fx * halfLen * moveSign;
        double leadZ = cz + fz * halfLen * moveSign;
        for (double s : new double[]{-halfWid, 0.0, halfWid}) {
            double sx = leadX + rx * s;
            double sz = leadZ + rz * s;
            Double g = groundTopAt(world, sx, sz, curY, cfg);
            if (g != null && (g - curY > cfg.maxStepUp || blockedAhead(world, sx, g, sz))) {
                return true;
            }
        }
        return false;
    }

    /** Is there a solid block sitting just above the given ground (a low ceiling)? */
    private static boolean blockedAhead(World world, double x, double groundTop, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int by = (int) Math.floor(groundTop + 0.6);
        Block b = world.getBlockAt(bx, by, bz);
        return !b.isPassable() && b.getType().isSolid();
    }

    // --------------------------------------------------------------- impact sweep

    private static void sweptImpact(Motorcycle motorcycle, Player driver,
                                    double oldX, double oldY, double oldZ, double oldYaw,
                                    double newX, double newY, double newZ, double newYaw,
                                    double travelX, double travelZ, double speed, MotoConfig cfg) {
        Vector direction = new Vector(travelX, 0.0, travelZ);
        if (direction.lengthSquared() < 1.0e-9) {
            return;
        }
        direction.normalize();

        double halfWidth = MotorcycleModel.WIDTH / 2.0 + cfg.impactReach;
        double halfLength = MotorcycleModel.LENGTH / 2.0 + cfg.impactReach;
        double localCentreX = (MotorcycleModel.MIN_X + MotorcycleModel.MAX_X) * 0.5;
        double localCentreZ = (MotorcycleModel.MIN_Z + MotorcycleModel.MAX_Z) * 0.5;
        double oldCentreX = DriveMath.localToWorldX(oldX, oldYaw, localCentreX, localCentreZ);
        double oldCentreZ = DriveMath.localToWorldZ(oldZ, oldYaw, localCentreX, localCentreZ);
        double newCentreX = DriveMath.localToWorldX(newX, newYaw, localCentreX, localCentreZ);
        double newCentreZ = DriveMath.localToWorldZ(newZ, newYaw, localCentreX, localCentreZ);
        double radius = Math.hypot(halfWidth, halfLength);
        BoundingBox broadPhase = new BoundingBox(
                Math.min(oldCentreX, newCentreX) - radius,
                Math.min(oldY, newY) - IMPACT_VERTICAL_REACH,
                Math.min(oldCentreZ, newCentreZ) - radius,
                Math.max(oldCentreX, newCentreX) + radius,
                Math.max(oldY, newY) + MotorcycleModel.HEIGHT + IMPACT_VERTICAL_REACH,
                Math.max(oldCentreZ, newCentreZ) + radius);

        int steps = DriveMath.substepCount(Math.hypot(newX - oldX, newZ - oldZ),
                DriveMath.wrapDegrees(newYaw - oldYaw),
                MAX_HORIZONTAL_SUBSTEP, MAX_YAW_SUBSTEP_DEGREES);
        boolean highSpeed = speed >= cfg.maxForwardSpeed * cfg.highSpeedFraction;
        Set<UUID> processed = new HashSet<>();

        for (Entity entity : motorcycle.world().getNearbyEntities(broadPhase)) {
            if (!(entity instanceof LivingEntity living)
                    || (!(entity instanceof Mob) && !(entity instanceof Player))) {
                continue;
            }
            if (entity.equals(driver)
                    || entity.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)
                    || entity.getVehicle() != null) {
                continue;
            }
            if (entity instanceof Player player
                    && (player.getGameMode() == GameMode.CREATIVE
                    || player.getGameMode() == GameMode.SPECTATOR)) {
                continue;
            }
            if (!processed.add(entity.getUniqueId())
                    || !intersectsImpactSweep(entity.getBoundingBox(),
                    oldX, oldY, oldZ, oldYaw,
                    newX, newY, newZ, newYaw,
                    halfWidth, halfLength, localCentreX, localCentreZ, steps)) {
                continue;
            }
            if (!motorcycle.tryImpact(entity.getUniqueId(), cfg.impactCooldownMs)) {
                continue;
            }

            if (highSpeed) {
                highSpeedImpact(living, driver, direction, cfg);
            } else {
                lowSpeedImpact(living, driver, direction, cfg);
            }
        }
    }

    private static boolean intersectsImpactSweep(BoundingBox target,
                                                 double oldX, double oldY, double oldZ, double oldYaw,
                                                 double newX, double newY, double newZ, double newYaw,
                                                 double halfWidth, double halfLength,
                                                 double localCentreX, double localCentreZ,
                                                 int steps) {
        for (int step = 0; step <= steps; step++) {
            double fraction = (double) step / steps;
            double x = oldX + (newX - oldX) * fraction;
            double y = oldY + (newY - oldY) * fraction;
            double z = oldZ + (newZ - oldZ) * fraction;
            double yaw = DriveMath.interpolateAngle(oldYaw, newYaw, fraction);
            double centreX = DriveMath.localToWorldX(x, yaw, localCentreX, localCentreZ);
            double centreZ = DriveMath.localToWorldZ(z, yaw, localCentreX, localCentreZ);
            if (target.getMaxY() < y - IMPACT_VERTICAL_REACH
                    || target.getMinY() > y + MotorcycleModel.HEIGHT + IMPACT_VERTICAL_REACH) {
                continue;
            }
            if (DriveMath.obbIntersectsAabb(centreX, centreZ, yaw, halfWidth, halfLength,
                    target.getMinX(), target.getMinZ(), target.getMaxX(), target.getMaxZ())) {
                return true;
            }
        }
        return false;
    }

    private static void highSpeedImpact(LivingEntity living, Player driver,
                                        Vector direction, MotoConfig cfg) {
        if (cfg.highSpeedDamage > 0.0) {
            living.damage(cfg.highSpeedDamage, driver);
        }
        Vector push = direction.clone().multiply(cfg.highSpeedKnockback);
        push.setY(cfg.highSpeedVertical);
        living.setVelocity(living.getVelocity().multiply(0.15).add(push));
        Location at = living.getLocation();
        World world = at.getWorld();
        if (world != null) {
            world.playSound(at, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.85f);
            world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.9f, 1.0f);
            world.spawnParticle(Particle.SWEEP_ATTACK, at.clone().add(0, 1, 0),
                    3, 0.45, 0.45, 0.45, 0.0);
        }
    }

    private static void lowSpeedImpact(LivingEntity living, Player driver,
                                       Vector direction, MotoConfig cfg) {
        double damage = DriveMath.cappedNonLethalDamage(
                living.getHealth(), cfg.lowSpeedDamage, cfg.lowSpeedMinHealth);
        if (damage > 0.0) {
            living.damage(damage, driver);
        }
        Vector push = direction.clone().multiply(cfg.lowSpeedKnockback);
        push.setY(0.16);
        living.setVelocity(living.getVelocity().multiply(0.4).add(push));
        Location at = living.getLocation();
        World world = at.getWorld();
        if (world != null) {
            world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 0.8f);
            world.spawnParticle(Particle.CRIT, at.clone().add(0, 0.8, 0),
                    5, 0.3, 0.35, 0.3, 0.06);
        }
    }

    // -------------------------------------------------------------------- terrain

    /** Highest reachable collision surface under the wheels (sidecar wheel only if present). */
    private static Double footprintGround(World world, double anchorX, double anchorZ,
                                          double yaw, double currentY, boolean withSidecar,
                                          MotoConfig cfg) {
        org.joml.Vector3f front = MotorcycleModel.frontWheelCenter();
        org.joml.Vector3f rear = MotorcycleModel.rearWheelCenter();
        double[][] offsets = withSidecar
                ? new double[][]{{front.x, front.z}, {rear.x, rear.z},
                {MotorcycleModel.sidecarWheelCenter().x, MotorcycleModel.sidecarWheelCenter().z}}
                : new double[][]{{front.x, front.z}, {rear.x, rear.z}};
        double best = Double.NEGATIVE_INFINITY;
        boolean found = false;
        for (double[] offset : offsets) {
            double sampleX = DriveMath.localToWorldX(anchorX, yaw, offset[0], offset[1]);
            double sampleZ = DriveMath.localToWorldZ(anchorZ, yaw, offset[0], offset[1]);
            Double ground = groundTopAt(world, sampleX, sampleZ, currentY, cfg);
            if (ground != null) {
                best = Math.max(best, ground);
                found = true;
            }
        }
        return found ? best : null;
    }

    /** Uses actual voxel-shape boxes, so slabs, stairs, snow and open doors behave correctly. */
    private static Double groundTopAt(World world, double x, double z,
                                      double currentY, MotoConfig cfg) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(currentY)) {
            return null;
        }
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
            return null;
        }
        int startY = Math.min(world.getMaxHeight() - 1,
                (int) Math.floor(currentY + cfg.maxStepUp));
        int endY = Math.max(world.getMinHeight(),
                (int) Math.floor(currentY - cfg.groundSnapDistance) - 1);
        double best = Double.NEGATIVE_INFINITY;
        for (int blockY = startY; blockY >= endY; blockY--) {
            Block block = world.getBlockAt(blockX, blockY, blockZ);
            for (BoundingBox shape : block.getCollisionShape().getBoundingBoxes()) {
                double shapeMinX = blockX + shape.getMinX();
                double shapeMinZ = blockZ + shape.getMinZ();
                double shapeMaxX = blockX + shape.getMaxX();
                double shapeMaxZ = blockZ + shape.getMaxZ();
                if (x >= shapeMinX - COLLISION_EPSILON
                        && x <= shapeMaxX + COLLISION_EPSILON
                        && z >= shapeMinZ - COLLISION_EPSILON
                        && z <= shapeMaxZ + COLLISION_EPSILON) {
                    best = Math.max(best, blockY + shape.getMaxY());
                }
            }
        }
        return best == Double.NEGATIVE_INFINITY ? null : best;
    }

    // ------------------------------------------------------------------------ HUD

    private static void hud(Motorcycle motorcycle, Player driver,
                            boolean submerged, MotoConfig cfg) {
        if (motorcycle.world().getFullTime() % cfg.hudInterval != 0) {
            return;
        }
        if (submerged) {
            driver.sendActionBar(Component.text(
                    "⚠ Engine stalled - motorcycle is underwater!", NamedTextColor.RED));
            return;
        }
        int healthPercent = (int) Math.round(100.0 * motorcycle.health() / motorcycle.maxHealth());
        int kilometresPerHour = (int) Math.round(Math.abs(motorcycle.speed()) * 20.0 * 3.6);
        Component bar = Component.text("HP ", NamedTextColor.GRAY)
                .append(Component.text(healthPercent + "%", healthPercent > 50
                        ? NamedTextColor.GREEN : healthPercent > 25
                        ? NamedTextColor.YELLOW : NamedTextColor.RED))
                .append(Component.text("   🏍 " + kilometresPerHour + " km/h", NamedTextColor.AQUA));
        driver.sendActionBar(bar);
    }
}
