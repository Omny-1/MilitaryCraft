package me.bibo.militarycraft.vehicles.tank.control;

import me.bibo.militarycraft.vehicles.tank.config.TankConfig;
import me.bibo.militarycraft.vehicles.tank.model.TankModel;
import me.bibo.militarycraft.vehicles.tank.tank.Tank;
import me.bibo.militarycraft.vehicles.tank.util.Keys;
import me.bibo.militarycraft.vehicles.tank.util.MathUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Arcade driving: the hull turns toward where the driver looks while moving
 * forward, W/S are throttle, the turret and gun track the camera.
 *
 * <p>The vertical model samples the ground under the whole footprint (centre +
 * four corners) and rides on the highest support found. Sampling the footprint
 * instead of a single point is what keeps the tank from jittering on block
 * edges, sinking into terrain, or refusing to mount a step that one corner has
 * already reached.
 */
public final class DriveController {

    private DriveController() {
    }

    public static void drive(Tank tank, Player driver, TankConfig cfg) {
        Input in = driver.getCurrentInput();
        Location eye = driver.getLocation();
        boolean drowned = tank.isSubmerged(); // engine stalls while fully underwater

        // --- turret + barrel track the camera ---
        tank.setTurretYaw(eye.getYaw());
        double targetPitch = MathUtil.clamp(eye.getPitch(), -cfg.maxElevation, cfg.maxDepression);
        tank.setBarrelPitch(MathUtil.approach(tank.barrelPitch(), targetPitch, cfg.pitchSpeed));

        // --- throttle with a separate brake rate (no power while stalled in water) ---
        double target = drowned ? 0.0
                : in.isForward() ? cfg.maxForwardSpeed
                : in.isBackward() ? -cfg.maxReverseSpeed : 0.0;
        double speed = tank.speed();
        double rate;
        if (target == 0.0) {
            rate = cfg.friction;
        } else if (Math.signum(target) != Math.signum(speed) && Math.abs(speed) > 1e-6) {
            rate = cfg.braking;
        } else {
            rate = cfg.acceleration;
        }
        speed = MathUtil.approach(speed, target, rate);
        tank.setSpeed(speed);

        // --- hull turns toward the camera only while driving forward ---
        if (!drowned && speed > 0.02) {
            tank.setHullYaw(MathUtil.approachAngle(tank.hullYaw(), eye.getYaw(), cfg.turnSpeed));
        }

        Location anchor = tank.anchor();
        World world = tank.world();
        double oldX = anchor.getX();
        double oldY = anchor.getY();
        double oldZ = anchor.getZ();

        // --- idle fast-path: a parked, grounded tank does no horizontal work, so
        //     skip the footprint sweep, wall test and ramming scan entirely. We
        //     still let the turret/gun track the camera and keep one cheap centre
        //     probe so the tank reacts if the ground is pulled out from under it. ---
        if (Math.abs(speed) < 1e-6 && tank.verticalVelocity() == 0.0
                && !in.isForward() && !in.isBackward()) {
            Double g = groundTopAt(world, oldX, oldZ, oldY, cfg);
            if (g != null && (oldY - g) <= cfg.maxStepUp + 1e-3) {
                // Supported at/near the current height: settle DOWN onto small drops,
                // but never lift the tank up while it's parked (no camera-shake
                // ejection, no creeping up a wall you happen to be resting against).
                if (g < oldY - 1e-4) {
                    anchor.setY(g);
                }
                tank.refreshModel();
                if (world.getFullTime() % cfg.hudInterval == 0) {
                    if (drowned) {
                        driver.sendActionBar(Component.text("⚠ Engine stalled - tank is sinking!",
                                NamedTextColor.RED));
                    } else {
                        sendHud(tank, driver);
                    }
                }
                return;
            }
            // ground vanished / dropped far away -> fall through to gravity physics
        }

        double yawRad = Math.toRadians(tank.hullYaw());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);

        double halfLen = TankModel.LENGTH / 2.0 - 0.4;
        double halfWid = TankModel.WIDTH / 2.0 - 0.3;

        double newX = oldX + fx * speed;
        double newZ = oldZ + fz * speed;

        // --- wall test: forward corners must not face a step taller than we can
        //     climb, nor a low ceiling sitting on that step ---
        if (speed != 0 && leadingEdgeBlocked(world, newX, newZ, oldY, fx, fz,
                Math.signum(speed), halfLen, halfWid, cfg)) {
            newX = oldX;
            newZ = oldZ;
            tank.setSpeed(0.0);
        }

        // --- vertical: rest on the footprint ground, but only RISE for a genuine
        //     forward step or when landing. This stops a single corner clipping a
        //     tree/wall from ratcheting the whole tank upward when you nudge into it
        //     or swing the camera. ---
        Double support = footprintGround(world, newX, newZ, fx, fz, halfLen, halfWid, oldY, cfg);
        double newY;
        if (support == null) {
            // nothing under the footprint -> fall
            double vy = Math.max(tank.verticalVelocity() - cfg.gravity, -cfg.maxFallSpeed);
            tank.setVerticalVelocity(vy);
            newY = oldY + vy;
        } else {
            double rise = support - oldY;
            if (rise <= 1e-3) {
                // ground at or below us -> settle / descend (apply landing damage)
                double vy = tank.verticalVelocity();
                if (cfg.fallDamage > 0 && vy < -cfg.fallDamageThreshold) {
                    double dmg = cfg.fallDamage * (-vy - cfg.fallDamageThreshold);
                    if (tank.damage(dmg)) {
                        return; // destroyed by the landing
                    }
                }
                tank.setVerticalVelocity(0.0);
                newY = support;
            } else {
                // ground is ABOVE us. Climb only if we're actually driving onto a
                // step we can mount, or settling a fall onto a ledge; otherwise hold
                // height so a parked/blocked tank is never shoved upward.
                boolean falling = tank.verticalVelocity() < -1e-6;
                boolean driving = Math.abs(tank.speed()) > 0.03;
                tank.setVerticalVelocity(0.0);
                if (driving && rise <= cfg.maxStepUp + 1e-3) {
                    newY = oldY + Math.min(rise, cfg.climbRate); // ramp up the step
                } else if (falling) {
                    newY = support; // land on the ledge we dropped onto
                } else {
                    newY = oldY; // parked / blocked -> do not get pushed up
                }
            }
        }

        // --- void guard ---
        if (newY < world.getMinHeight() - 6) {
            tank.destroy(true);
            return;
        }

        anchor.setX(newX);
        anchor.setY(newY);
        anchor.setZ(newZ);
        tank.refreshModel();

        // --- ramming ---
        if (cfg.rammingEnabled && Math.abs(tank.speed()) >= cfg.rammingMinSpeed) {
            double sign = Math.signum(tank.speed());
            ram(tank, driver, fx * sign, fz * sign, cfg);
        }

        movementEffects(tank, cfg, drowned);

        // --- throttled HUD ---
        if (world.getFullTime() % cfg.hudInterval == 0) {
            if (drowned) {
                driver.sendActionBar(Component.text("⚠ Engine stalled - tank is sinking!",
                        NamedTextColor.RED));
            } else {
                sendHud(tank, driver);
            }
        }
    }

    private static void ram(Tank tank, Player driver, double dirX, double dirZ, TankConfig cfg) {
        Location centre = tank.anchor().clone().add(0, 1.0, 0);
        double reach = TankModel.WIDTH / 2.0 + 0.6;
        Vector push = new Vector(dirX, 0, dirZ);
        if (push.lengthSquared() > 1e-6) {
            push.normalize().multiply(cfg.rammingKnockback).setY(0.35);
        }
        Vector forward = new Vector(dirX, 0, dirZ);
        if (forward.lengthSquared() <= 1e-6) {
            return;
        }
        forward.normalize();
        double minDot = Math.cos(Math.toRadians(cfg.rammingConeDegrees * 0.5));
        long now = System.currentTimeMillis();
        for (Entity e : tank.world().getNearbyEntities(centre, reach, 1.4, reach)) {
            if (!(e instanceof LivingEntity living) || e.equals(driver)) {
                continue;
            }
            if (e.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
                continue; // don't ram tank parts
            }
            if (living instanceof Player p
                    && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)) {
                continue;
            }
            Vector to = living.getLocation().toVector().subtract(centre.toVector()).setY(0);
            double dist = to.length();
            if (dist < 1e-4 || to.normalize().dot(forward) < minDot) {
                continue;
            }
            if (tank.world().rayTraceBlocks(centre, to, Math.max(0.1, dist - 0.35),
                    FluidCollisionMode.NEVER, true) != null) {
                continue;
            }
            if (!tank.tryRam(living.getUniqueId(), now, cfg.rammingCooldownMs)) {
                continue;
            }
            living.damage(cfg.rammingDamage, driver);
            living.setVelocity(living.getVelocity().add(push));
        }
    }

    private static void movementEffects(Tank tank, TankConfig cfg, boolean drowned) {
        if (drowned || Math.abs(tank.speed()) < 0.035 || tank.verticalVelocity() != 0.0) {
            return;
        }
        World world = tank.world();
        long time = world.getFullTime();
        Location anchor = tank.anchor();
        if (cfg.trackDust && time % 4 == 0) {
            double yawRad = Math.toRadians(tank.hullYaw());
            double rx = Math.cos(yawRad);
            double rz = Math.sin(yawRad);
            double side = TankModel.WIDTH * 0.42;
            spawnTrackDust(world, anchor, rx * side, rz * side);
            spawnTrackDust(world, anchor, -rx * side, -rz * side);
        }
        if (cfg.engineSound && time % 10 == 0) {
            double ratio = Math.min(1.0, Math.abs(tank.speed()) / Math.max(0.01, cfg.maxForwardSpeed));
            world.playSound(anchor, Sound.ENTITY_MINECART_RIDING, 0.35f, (float) (0.55 + ratio * 0.45));
        }
    }

    private static void spawnTrackDust(World world, Location anchor, double dx, double dz) {
        Location at = anchor.clone().add(dx, 0.08, dz);
        Block ground = world.getBlockAt(at.getBlockX(), (int) Math.floor(anchor.getY() - 0.05), at.getBlockZ());
        if (!ground.getType().isSolid()) {
            return;
        }
        world.spawnParticle(Particle.BLOCK, at, 3, 0.25, 0.04, 0.25, 0.015, ground.getBlockData());
    }

    /** Highest support under the tank: max ground over centre + four corners. */
    private static Double footprintGround(World world, double cx, double cz, double fx, double fz,
                                          double halfLen, double halfWid, double curY, TankConfig cfg) {
        double rx = fz;   // right vector, perpendicular to forward
        double rz = -fx;
        double[][] offsets = {
                {0, 0},
                {halfLen, halfWid}, {halfLen, -halfWid},
                {-halfLen, halfWid}, {-halfLen, -halfWid}
        };
        double best = Double.NEGATIVE_INFINITY;
        boolean found = false;
        for (double[] o : offsets) {
            double sx = cx + fx * o[0] + rx * o[1];
            double sz = cz + fz * o[0] + rz * o[1];
            Double g = groundTopAt(world, sx, sz, curY, cfg);
            if (g != null) {
                found = true;
                best = Math.max(best, g);
            }
        }
        return found ? best : null;
    }

    /** Check the three points along the leading edge for an impassable wall. */
    private static boolean leadingEdgeBlocked(World world, double cx, double cz, double curY,
                                              double fx, double fz, double moveSign,
                                              double halfLen, double halfWid, TankConfig cfg) {
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

    /**
     * World Y of the highest solid surface in the column (x,z) within reach of
     * the current height, using the block's real collision top (so slabs, stairs
     * and snow give a smooth ride). Returns null when nothing is reachable.
     */
    private static Double groundTopAt(World world, double x, double z, double currentY, TankConfig cfg) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int startY = (int) Math.floor(currentY + cfg.maxStepUp);
        int endY = (int) Math.floor(currentY - cfg.groundSnapDistance) - 1;
        int min = world.getMinHeight();
        int max = world.getMaxHeight();
        for (int by = Math.min(startY, max - 1); by >= Math.max(endY, min); by--) {
            Block b = world.getBlockAt(bx, by, bz);
            if (!b.isPassable() && b.getType().isSolid()) {
                double topY = b.getBoundingBox().getMaxY();
                return topY > by + 0.01 ? topY : by + 1.0;
            }
        }
        return null;
    }

    /** Is there a solid block sitting just above the given ground (a low ceiling)? */
    private static boolean blockedAhead(World world, double x, double groundTop, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int by = (int) Math.floor(groundTop + 0.6);
        Block b = world.getBlockAt(bx, by, bz);
        return !b.isPassable() && b.getType().isSolid();
    }

    private static void sendHud(Tank tank, Player driver) {
        int hpPct = (int) Math.round(100.0 * tank.health() / tank.maxHealth());
        int kmh = (int) Math.round(Math.abs(tank.speed()) * 20 * 3.6);
        String reload = tank.reload() <= 0 ? "READY"
                : String.format("%.1fs", tank.reload() / 20.0);
        Component hud = Component.text("HP ", NamedTextColor.GRAY)
                .append(Component.text(hpPct + "%", hpPct > 50 ? NamedTextColor.GREEN
                        : hpPct > 25 ? NamedTextColor.YELLOW : NamedTextColor.RED))
                .append(Component.text("   ⚙ " + kmh + " km/h", NamedTextColor.AQUA))
                .append(Component.text("   ☄ ", NamedTextColor.GRAY))
                .append(Component.text(reload, tank.reload() <= 0 ? NamedTextColor.GREEN : NamedTextColor.GOLD));
        driver.sendActionBar(hud);
    }
}
