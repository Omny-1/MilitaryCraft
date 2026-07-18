package me.bibo.militarycraft.vehicles.airship.control;

import me.bibo.militarycraft.vehicles.airship.airship.Airship;
import me.bibo.militarycraft.vehicles.airship.config.AirshipConfig;
import me.bibo.militarycraft.vehicles.airship.model.AirshipModel;
import me.bibo.militarycraft.vehicles.airship.util.MathUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Lighter-than-air arcade flight. The airship hovers when no vertical input is
 * given (neutral buoyancy). The mouse steers heading (look left/right) and trims
 * altitude (look up to rise, down to sink); W/S throttle forward/reverse; Space
 * is the gas-burner lift boost, limited by a heat gauge so you cannot climb
 * forever. It is huge and ponderous — everything eases in slowly.
 *
 * <p>Bumping terrain just stops the airship (it is far too big and slow to
 * "crash"); it can only be destroyed by accumulated blast damage.
 */
public final class AirshipController {

    private AirshipController() {
    }

    public static void fly(Airship ship, Player driver, AirshipConfig cfg) {
        Input in = driver.getCurrentInput();
        Location eye = driver.getLocation();

        // --- heading: the nose eases toward where the camera looks ---
        double prevYaw = ship.yaw();
        double newYaw = MathUtil.approachAngle(prevYaw, eye.getYaw(), cfg.turnRate);
        double yawRate = MathUtil.angleDelta(prevYaw, newYaw);
        ship.setYaw(newYaw);

        // --- gentle visual bank into the turn ---
        double targetRoll = MathUtil.clamp(yawRate * cfg.bankFactor, -cfg.maxBank, cfg.maxBank);
        ship.setRoll(MathUtil.approachAngle(ship.roll(), targetRoll, cfg.rollReturnSpeed));

        // --- horizontal throttle ---
        double targetSpeed;
        if (in.isForward() && !in.isBackward()) {
            targetSpeed = cfg.maxSpeed;
        } else if (in.isBackward() && !in.isForward()) {
            targetSpeed = -cfg.reverseSpeed;
        } else {
            targetSpeed = 0.0; // coast to a stop (drag)
        }
        double sRate = (Math.abs(targetSpeed) < Math.abs(ship.speed())) ? cfg.braking : cfg.acceleration;
        double speed = MathUtil.approach(ship.speed(), targetSpeed, sRate);
        ship.setSpeed(speed);

        // --- gas-burner heat: Space boosts lift until the burner overheats ---
        boolean wantBoost = in.isJump();
        boolean boosting = false;
        if (wantBoost && !ship.isBurnerLocked()) {
            boosting = true;
            ship.setBurnerHeat(ship.burnerHeat() + cfg.burnerHeatPerTick);
            if (ship.burnerHeat() >= 1.0) {
                ship.setBurnerLocked(true);
            }
        } else {
            ship.setBurnerHeat(ship.burnerHeat() - cfg.burnerCoolPerTick);
            if (ship.isBurnerLocked() && ship.burnerHeat() <= cfg.burnerResumeThreshold) {
                ship.setBurnerLocked(false);
            }
        }

        // --- vectored buoyancy: look up/down trims climb/sink; Space adds lift ---
        double trim = MathUtil.clamp(-eye.getPitch() / cfg.trimFullPitch, -1.0, 1.0);
        double targetV = trim * cfg.trimClimbRate + cfg.neutralDrift + (boosting ? cfg.boostClimb : 0.0);
        targetV = MathUtil.clamp(targetV, -cfg.maxSink, cfg.trimClimbRate + cfg.boostClimb);
        double vSpeed = MathUtil.approach(ship.vSpeed(), targetV, cfg.verticalAccel);
        ship.setVSpeed(vSpeed);

        // --- gentle visual nose tilt with vertical motion ---
        double targetPitch = MathUtil.clamp(vSpeed * 24.0, -12.0, 12.0);
        ship.setPitch(MathUtil.approach(ship.pitch(), targetPitch, 1.2));

        applyMotion(ship, cfg, speed, vSpeed);
        if (!ship.isActive()) {
            return; // crashed into terrain this tick — nothing left to render
        }

        // --- effects ---
        if (cfg.engineTrail) {
            emitEngineTrail(ship, speed);
        }
        if (cfg.burnerSteam && boosting) {
            Location bp = ship.burnerPoint();
            ship.world().spawnParticle(Particle.FLAME, bp, 4, 0.2, 0.2, 0.2, 0.01);
            ship.world().spawnParticle(Particle.LARGE_SMOKE, bp, 3, 0.2, 0.3, 0.2, 0.01);
        }

        if (ship.world().getFullTime() % cfg.hudInterval == 0) {
            sendHud(ship, driver, cfg);
        }
    }

    /**
     * Drift after a mid-air bail-out: roll levels, the burner is untended so the
     * airship slowly loses lift and sinks while momentum bleeds away, until it
     * settles on the ground and parks. No explosion — it is too gentle to crash.
     */
    public static void glide(Airship ship, AirshipConfig cfg) {
        ship.setRoll(MathUtil.approachAngle(ship.roll(), 0.0, cfg.rollReturnSpeed));
        ship.setPitch(MathUtil.approach(ship.pitch(), -4.0, 1.0));

        double speed = MathUtil.approach(ship.speed(), 0.0, cfg.braking);
        ship.setSpeed(speed);

        // untended: a slow, capped descent
        double vSpeed = MathUtil.approach(ship.vSpeed(), -cfg.maxSink * 0.5, cfg.verticalAccel);
        ship.setVSpeed(vSpeed);

        boolean rested = applyMotion(ship, cfg, speed, vSpeed);
        if (!ship.isActive()) {
            return; // crashed into terrain while drifting
        }
        if (rested && Math.abs(speed) < 0.02) {
            ship.setUnmanned(false);
            ship.setVSpeed(0);
            ship.setSpeed(0);
        }

        if (cfg.engineTrail) {
            for (Location e : ship.enginePoints()) {
                ship.world().spawnParticle(Particle.LARGE_SMOKE, e, 2, 0.1, 0.1, 0.1, 0.01);
            }
        }
    }

    /**
     * Integrate one step of motion with terrain collision (ground rest, ceiling
     * stop, wall stop). Returns true if the airship is resting on the ground.
     */
    private static boolean applyMotion(Airship ship, AirshipConfig cfg, double speed, double vSpeed) {
        World world = ship.world();
        Location anchor = ship.anchor();
        double yawRad = Math.toRadians(ship.yaw());
        double hx = -Math.sin(yawRad);
        double hz = Math.cos(yawRad);
        double rx = Math.cos(yawRad);
        double rz = Math.sin(yawRad);

        double oldX = anchor.getX(), oldY = anchor.getY(), oldZ = anchor.getZ();
        double newX = oldX + hx * speed;
        double newY = oldY + vSpeed;
        double newZ = oldZ + hz * speed;

        // --- void / ceiling guards ---
        if (newY < world.getMinHeight() - 8) {
            ship.destroy(true);
            return false;
        }

        boolean resting = false;

        // --- horizontal wall: sample the nose and the gondola front ---
        double halfLen = AirshipModel.TOTAL_LENGTH / 2.0;
        double envelopeY = oldY + AirshipModel.ENV_CY;
        double fore = halfLen * 0.65;
        double side = AirshipModel.ENV_RX;
        boolean wall = isSolidAt(world, newX + hx * halfLen, oldY + AirshipModel.ENV_CY, newZ + hz * halfLen)
                || isSolidAt(world, newX + hx * (halfLen * 0.5), envelopeY, newZ + hz * (halfLen * 0.5))
                || isSolidAt(world, newX + hx * fore + rx * side, envelopeY, newZ + hz * fore + rz * side)
                || isSolidAt(world, newX + hx * fore - rx * side, envelopeY, newZ + hz * fore - rz * side)
                || isSolidAt(world, newX + rx * side, envelopeY, newZ + rz * side)
                || isSolidAt(world, newX - rx * side, envelopeY, newZ - rz * side)
                || isSolidAt(world, newX + hx * 3.0, oldY, newZ + hz * 3.0);
        if (wall && speed != 0.0) {
            if (Math.abs(speed) >= cfg.crashSpeed) {
                // full-speed ram: destroy the airship and crater the impact point
                Location impact = new Location(world,
                        oldX + hx * (halfLen * 0.55), oldY + AirshipModel.ENV_CY * 0.35,
                        oldZ + hz * (halfLen * 0.55));
                ship.destroyByCrash(impact);
                return false;
            }
            newX = oldX;
            newZ = oldZ;
            ship.setSpeed(0.0);
        }

        // --- ceiling: stop upward motion if the envelope top hits something ---
        if (vSpeed > 0) {
            double topY = newY + AirshipModel.ENV_CY + AirshipModel.ENV_RY;
            if (isSolidAt(world, newX, topY, newZ)) {
                newY = oldY;
                ship.setVSpeed(0.0);
            }
        }

        // --- ground: rest the gondola gently on the surface below ---
        if (cfg.groundRest && vSpeed <= 0) {
            double gondolaBottom = newY + AirshipModel.GONDOLA_BOTTOM_Y;
            Double ground = groundTopAt(world, newX, newZ, gondolaBottom + 3.0);
            if (ground != null && gondolaBottom < ground + 0.15) {
                newY = ground - AirshipModel.GONDOLA_BOTTOM_Y + 0.05;
                ship.setVSpeed(0.0);
                resting = true;
            }
        }

        anchor.setX(newX);
        anchor.setY(newY);
        anchor.setZ(newZ);
        ship.refreshModel();
        return resting;
    }

    private static void emitEngineTrail(Airship ship, double speed) {
        if (Math.abs(speed) < 0.03) {
            return;
        }
        World world = ship.world();
        for (Location e : ship.enginePoints()) {
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, e, 1, 0.08, 0.08, 0.08, 0.0);
            if (Math.abs(speed) > 0.18) {
                world.spawnParticle(Particle.SMOKE, e, 1, 0.06, 0.06, 0.06, 0.0);
            }
        }
    }

    private static boolean isSolidAt(World world, double x, double y, double z) {
        Block b = world.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        return !b.isPassable() && b.getType().isSolid();
    }

    private static Double groundTopAt(World world, double x, double z, double fromY) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int startY = (int) Math.floor(fromY);
        int min = world.getMinHeight();
        for (int by = Math.min(startY, world.getMaxHeight() - 1); by >= Math.max(min, startY - 8); by--) {
            Block b = world.getBlockAt(bx, by, bz);
            if (!b.isPassable() && b.getType().isSolid()) {
                double topY = b.getBoundingBox().getMaxY();
                return topY > by + 0.01 ? topY : by + 1.0;
            }
        }
        return null;
    }

    private static void sendHud(Airship ship, Player driver, AirshipConfig cfg) {
        int hpPct = (int) Math.round(100.0 * ship.health() / ship.maxHealth());
        int kmh = (int) Math.round(ship.speed() * 72);
        int alt = (int) Math.round(ship.anchor().getY());
        double v = ship.vSpeed();

        Component vert;
        if (v > 0.02) {
            vert = Component.text(" ▲", NamedTextColor.GREEN);
        } else if (v < -0.02) {
            vert = Component.text(" ▼", NamedTextColor.GOLD);
        } else {
            vert = Component.text(" ●", NamedTextColor.GRAY); // hovering
        }

        Component burner;
        if (ship.isBurnerLocked()) {
            burner = Component.text(" ⚠BURNER", NamedTextColor.RED);
        } else {
            int heat = (int) Math.round(ship.burnerHeat() * 100);
            burner = heat > 5 ? Component.text(" 🔥" + heat + "%", NamedTextColor.YELLOW)
                    : Component.text("");
        }

        Component hud = Component.text("HP ", NamedTextColor.GRAY)
                .append(Component.text(hpPct + "%", hpPct > 50 ? NamedTextColor.GREEN
                        : hpPct > 25 ? NamedTextColor.YELLOW : NamedTextColor.RED))
                .append(Component.text("  ⚓ " + Math.abs(kmh) + " km/h" + (kmh < 0 ? " reverse" : ""), NamedTextColor.AQUA))
                .append(vert)
                .append(Component.text("  ▲" + alt, NamedTextColor.GRAY))
                .append(burner)
                .append(Component.text("  💣 " + ship.bombAmmo() + "/" + cfg.bombLoad,
                        ship.bombAmmo() > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
        driver.sendActionBar(hud);
    }
}
