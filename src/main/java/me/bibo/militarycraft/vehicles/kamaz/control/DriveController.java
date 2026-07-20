package me.bibo.militarycraft.vehicles.kamaz.control;

import me.bibo.militarycraft.vehicles.kamaz.config.KamazConfig;
import me.bibo.militarycraft.vehicles.kamaz.model.TruckModel;
import me.bibo.militarycraft.vehicles.kamaz.truck.Truck;
import me.bibo.militarycraft.vehicles.kamaz.util.Keys;
import me.bibo.militarycraft.vehicles.kamaz.util.MathUtil;
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
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Arcade driving for the Kamaz: the hull turns toward where the driver looks while
 * moving forward; W/S are throttle. There is no turret - it is one rigid body.
 *
 * <p>The vertical model samples the ground under the whole (long) footprint and
 * rides on the highest support found, so the truck doesn't jitter on block edges,
 * sink into terrain, or refuse a step one corner has already reached.
 *
 * <p>The signature mechanic lives in {@link #runOver}: at (near-)top speed the
 * truck FLINGS living things away; below that it CRUSHES them for a painful but
 * deliberately non-lethal amount.
 */
public final class DriveController {

    private DriveController() {
    }

    public static void drive(Truck truck, Player driver, KamazConfig cfg) {
        org.bukkit.Input in = driver.getCurrentInput();
        Location eye = driver.getLocation();
        boolean drowned = truck.isSubmerged(); // engine stalls while fully underwater

        // --- throttle with a separate brake rate (no power while stalled in water) ---
        double target = drowned ? 0.0
                : in.isForward() ? cfg.maxForwardSpeed
                : in.isBackward() ? -cfg.maxReverseSpeed : 0.0;
        double speed = truck.speed();
        double rate;
        if (target == 0.0) {
            rate = cfg.friction;
        } else if (Math.signum(target) != Math.signum(speed) && Math.abs(speed) > 1e-6) {
            rate = cfg.braking;
        } else {
            rate = cfg.acceleration;
        }
        speed = MathUtil.approach(speed, target, rate);
        truck.setSpeed(speed);

        // --- hull turns toward the camera only while driving forward ---
        double steerTarget = 0.0;
        if (!drowned && speed > 0.02) {
            double yawDiff = MathUtil.wrapDegrees(eye.getYaw() - truck.hullYaw());
            steerTarget = MathUtil.clamp(yawDiff,
                    -cfg.frontWheelSteerAngle, cfg.frontWheelSteerAngle);
            truck.setHullYaw(MathUtil.approachAngle(truck.hullYaw(), eye.getYaw(), cfg.turnSpeed));
        }
        double steerStep = Math.max(4.0, cfg.turnSpeed * 1.5);
        truck.setWheelSteer(MathUtil.approachAngle(truck.wheelSteer(), steerTarget, steerStep));

        Location anchor = truck.anchor();
        World world = truck.world();
        double oldX = anchor.getX();
        double oldY = anchor.getY();
        double oldZ = anchor.getZ();

        // --- idle fast-path: a parked, grounded truck does no horizontal work, so
        //     skip the footprint sweep, wall test and run-over scan entirely. ---
        if (Math.abs(speed) < 1e-6 && Math.abs(truck.verticalVelocity()) < 1e-6
                && !in.isForward() && !in.isBackward()) {
            Double g = groundTopAt(world, oldX, oldZ, oldY, cfg);
            if (g != null && (oldY - g) <= cfg.maxStepUp + 1e-3) {
                if (g < oldY - 1e-4) {
                    anchor.setY(g);
                }
                truck.refreshModel();
                hud(truck, driver, drowned, cfg);
                return;
            }
            // ground vanished / dropped far away -> fall through to gravity physics
        }

        double yawRad = Math.toRadians(truck.hullYaw());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);

        double halfLen = TruckModel.LENGTH / 2.0 - 0.4;
        double halfWid = TruckModel.WIDTH / 2.0 - 0.3;

        double newX = oldX + fx * speed;
        double newZ = oldZ + fz * speed;

        // --- wall test: forward edge must not face a step taller than we can climb,
        //     nor a low ceiling sitting on that step ---
        if (speed != 0 && leadingEdgeBlocked(world, newX, newZ, oldY, fx, fz,
                Math.signum(speed), halfLen, halfWid, cfg)) {
            newX = oldX;
            newZ = oldZ;
            speed = 0.0;
            truck.setSpeed(0.0);
        }

        // --- vertical: rest on the footprint ground, but only RISE for a genuine
        //     forward step or when landing. ---
        Double support = footprintGround(world, newX, newZ, fx, fz, halfLen, halfWid, oldY, cfg);
        double newY;
        if (support == null) {
            double vy = truck.verticalVelocity() - cfg.gravity;
            truck.setVerticalVelocity(vy);
            newY = oldY + vy;
        } else {
            double rise = support - oldY;
            if (rise <= 1e-3) {
                double vy = truck.verticalVelocity();
                if (cfg.fallDamage > 0 && vy < -cfg.fallDamageThreshold) {
                    double dmg = cfg.fallDamage * (-vy - cfg.fallDamageThreshold);
                    if (truck.damage(dmg)) {
                        return; // destroyed by the landing
                    }
                }
                truck.setVerticalVelocity(0.0);
                newY = support;
            } else {
                boolean falling = truck.verticalVelocity() < -1e-6;
                boolean driving = Math.abs(truck.speed()) > 0.03;
                truck.setVerticalVelocity(0.0);
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
        if (newY < world.getMinHeight() - 6) {
            truck.destroy(true);
            return;
        }

        double horizontalTravel = Math.hypot(newX - oldX, newZ - oldZ);
        truck.advanceWheelSpin(horizontalTravel * Math.signum(speed));

        anchor.setX(newX);
        anchor.setY(newY);
        anchor.setZ(newZ);
        truck.refreshModel();

        // --- the signature run-over ---
        boolean fullSpeed = false;
        if (cfg.runOverEnabled
                && Math.abs(truck.speed()) >= cfg.runOverMinSpeed
                && truck.world().getFullTime() % cfg.runOverScanInterval == 0) {
            double sign = Math.signum(truck.speed());
            fullSpeed = runOver(truck, driver, fx * sign, fz * sign, Math.abs(truck.speed()), cfg);
        }

        hud(truck, driver, drowned, cfg);
    }

    // ----------------------------------------------------------------- run-over

    /**
     * Hit every living thing inside the truck's oriented body box. At/above the
     * fling-speed threshold they are launched away (and lightly hurt); below it
     * they are crushed for a painful amount that is capped so it never kills.
     *
     * @return true if the truck was travelling at fling speed this tick.
     */
    private static boolean runOver(Truck truck, Player driver, double dirX, double dirZ,
                                   double speed, KamazConfig cfg) {
        boolean fullSpeed = speed >= cfg.maxForwardSpeed * cfg.flingSpeedFraction;
        Vector travel = new Vector(dirX, 0, dirZ);
        if (travel.lengthSquared() < 1e-9) {
            return fullSpeed;
        }
        travel.normalize();

        Location a = truck.anchor();
        double yaw = Math.toRadians(truck.hullYaw());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        double halfLen = TruckModel.LENGTH / 2.0 + cfg.runOverReach;
        double halfWid = TruckModel.WIDTH / 2.0 + cfg.runOverReach;
        // AABB around the anchor that surely contains the rotated body, for the query.
        double radius = Math.sqrt(halfLen * halfLen + halfWid * halfWid);
        Location centre = a.clone().add(0, TruckModel.HEIGHT / 2.0, 0);

        for (Entity e : truck.world().getNearbyEntities(centre, radius, TruckModel.HEIGHT / 2.0 + 1.2, radius)) {
            if (!(e instanceof LivingEntity living)) {
                continue;
            }
            if (!(e instanceof Mob || e instanceof Player)) {
                continue; // never touch armour stands, our own parts, etc.
            }
            if (e.equals(driver) || e.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
                continue;
            }
            if (e.getVehicle() != null) {
                continue; // anyone seated (our own passengers, riders) isn't run over
            }
            if (e instanceof Player pl
                    && (pl.getGameMode() == GameMode.CREATIVE || pl.getGameMode() == GameMode.SPECTATOR)) {
                continue;
            }
            // oriented-box containment in the truck's local frame
            double dx = e.getX() - a.getX();
            double dz = e.getZ() - a.getZ();
            double lx = dx * cos + dz * sin;
            double lz = -dx * sin + dz * cos;
            double dy = e.getY() - a.getY();
            if (Math.abs(lx) > halfWid || Math.abs(lz) > halfLen) {
                continue;
            }
            if (dy < -1.0 || dy > TruckModel.HEIGHT + 1.0) {
                continue;
            }
            if (!truck.tryRunOver(e.getUniqueId(), cfg.hitCooldownMs)) {
                continue;
            }
            if (fullSpeed) {
                fling(living, driver, travel, cfg);
            } else {
                crush(living, driver, travel, cfg);
            }
        }
        return fullSpeed;
    }

    /** Full speed: "Pushinka" launches the victim away. */
    private static void fling(LivingEntity living, Player driver, Vector travel, KamazConfig cfg) {
        if (cfg.flingDamage > 0) {
            living.damage(cfg.flingDamage, driver);
        }
        // Set velocity AFTER damage so our launch wins over vanilla knockback.
        Vector push = travel.clone().multiply(cfg.flingKnockback);
        push.setY(cfg.flingVertical);
        living.setVelocity(living.getVelocity().multiply(0.15).add(push));
        Location at = living.getLocation();
        World w = at.getWorld();
        if (w != null) {
            w.playSound(at, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.2f, 0.7f);
            w.playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
            w.spawnParticle(Particle.SWEEP_ATTACK, at.clone().add(0, 1, 0), 4, 0.6, 0.6, 0.6, 0);
            w.spawnParticle(Particle.CLOUD, at.clone().add(0, 0.4, 0), 14, 0.4, 0.2, 0.4, 0.12);
        }
    }

    /** Below full speed: a painful crush, capped so it can't kill. */
    private static void crush(LivingEntity living, Player driver, Vector travel, KamazConfig cfg) {
        double dmg = cfg.crushDamage;
        double hp = living.getHealth();
        if (hp - dmg < cfg.crushMinHealth) {
            dmg = Math.max(0.0, hp - cfg.crushMinHealth); // never below the configured health floor
        }
        if (dmg > 0) {
            living.damage(dmg, driver);
        }
        Vector push = travel.clone().multiply(cfg.crushKnockback);
        push.setY(0.22);
        living.setVelocity(living.getVelocity().multiply(0.4).add(push));
        Location at = living.getLocation();
        World w = at.getWorld();
        if (w != null) {
            w.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
            w.playSound(at, Sound.BLOCK_GRAVEL_BREAK, 1.0f, 0.5f);
            w.spawnParticle(Particle.CRIT, at.clone().add(0, 1, 0), 8, 0.4, 0.5, 0.4, 0.1);
        }
    }

    // ----------------------------------------------------------------- terrain

    /** Highest support under the truck: max ground over centre + corners + mid-edges. */
    private static Double footprintGround(World world, double cx, double cz, double fx, double fz,
                                          double halfLen, double halfWid, double curY, KamazConfig cfg) {
        double rx = fz;   // right vector, perpendicular to forward
        double rz = -fx;
        // Extra mid-length samples because the body is long; riding the highest of
        // them keeps a long truck from sagging over a single mid-span bump.
        double[][] offsets = {
                {0, 0},
                {halfLen, halfWid}, {halfLen, -halfWid},
                {-halfLen, halfWid}, {-halfLen, -halfWid},
                {halfLen * 0.5, halfWid}, {halfLen * 0.5, -halfWid},
                {-halfLen * 0.5, halfWid}, {-halfLen * 0.5, -halfWid}
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
                                              double halfLen, double halfWid, KamazConfig cfg) {
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
     * World Y of the highest solid surface in the column (x,z) within reach of the
     * current height, using the block's real collision top (so slabs, stairs and
     * snow give a smooth ride). Returns null when nothing is reachable.
     */
    private static Double groundTopAt(World world, double x, double z, double currentY, KamazConfig cfg) {
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

    // ----------------------------------------------------------------- hud

    private static void hud(Truck truck, Player driver, boolean drowned, KamazConfig cfg) {
        if (truck.world().getFullTime() % cfg.hudInterval != 0) {
            return;
        }
        if (drowned) {
            driver.sendActionBar(Component.text("⚠ Engine stalled - \"Pushinka\" is sinking!",
                    NamedTextColor.RED));
            return;
        }
        int hpPct = (int) Math.round(100.0 * truck.health() / truck.maxHealth());
        int kmh = (int) Math.round(Math.abs(truck.speed()) * 20 * 3.6);
        Component bar = Component.text("HP ", NamedTextColor.GRAY)
                .append(Component.text(hpPct + "%", hpPct > 50 ? NamedTextColor.GREEN
                        : hpPct > 25 ? NamedTextColor.YELLOW : NamedTextColor.RED))
                .append(Component.text("   🚛 " + kmh + " km/h", NamedTextColor.AQUA));
        driver.sendActionBar(bar);
    }
}
