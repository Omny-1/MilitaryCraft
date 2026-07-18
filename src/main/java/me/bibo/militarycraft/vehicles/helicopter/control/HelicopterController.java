package me.bibo.militarycraft.vehicles.helicopter.control;

import me.bibo.militarycraft.vehicles.helicopter.config.HelicopterConfig;
import me.bibo.militarycraft.vehicles.helicopter.helicopter.Helicopter;
import me.bibo.militarycraft.vehicles.helicopter.model.HelicopterModel;
import me.bibo.militarycraft.vehicles.helicopter.util.MathUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Nimble helicopter flight. The helicopter hovers when no vertical input is
 * given (neutral lift). The mouse steers heading (look left/right) and trims
 * altitude (look up to rise, down to sink); W/S throttle forward/reverse;
 * Space is the collective boost, limited by a heat gauge so you cannot climb
 * forever. Much snappier than the airship — quick turns, a livelier bank.
 *
 * <p>Bumping terrain just stops the helicopter at low speed; ramming a wall
 * at speed is a kamikaze crash.
 */
public final class HelicopterController {

    private HelicopterController() {
    }

    public static void fly(Helicopter heli, Player driver, HelicopterConfig cfg) {
        Input in = driver.getCurrentInput();
        Location eye = driver.getLocation();

        // --- heading: the nose eases toward where the camera looks ---
        double prevYaw = heli.yaw();
        double newYaw = MathUtil.approachAngle(prevYaw, eye.getYaw(), cfg.turnRate);
        double yawRate = MathUtil.angleDelta(prevYaw, newYaw);
        heli.setYaw(newYaw);

        // --- gentle visual bank into the turn ---
        double targetRoll = MathUtil.clamp(yawRate * cfg.bankFactor, -cfg.maxBank, cfg.maxBank);
        heli.setRoll(MathUtil.approachAngle(heli.roll(), targetRoll, cfg.rollReturnSpeed));

        // --- horizontal throttle ---
        double targetSpeed;
        if (in.isForward() && !in.isBackward()) {
            targetSpeed = cfg.maxSpeed;
        } else if (in.isBackward() && !in.isForward()) {
            targetSpeed = -cfg.reverseSpeed;
        } else {
            targetSpeed = 0.0; // coast to a stop (drag)
        }
        double sRate = (Math.abs(targetSpeed) < Math.abs(heli.speed())) ? cfg.braking : cfg.acceleration;
        double speed = MathUtil.approach(heli.speed(), targetSpeed, sRate);
        heli.setSpeed(speed);

        // --- engine heat: Space is the collective boost until the engine overheats ---
        boolean wantBoost = in.isJump();
        boolean boosting = false;
        if (wantBoost && !heli.isBurnerLocked()) {
            boosting = true;
            heli.setBurnerHeat(heli.burnerHeat() + cfg.burnerHeatPerTick);
            if (heli.burnerHeat() >= 1.0) {
                heli.setBurnerLocked(true);
            }
        } else {
            heli.setBurnerHeat(heli.burnerHeat() - cfg.burnerCoolPerTick);
            if (heli.isBurnerLocked() && heli.burnerHeat() <= cfg.burnerResumeThreshold) {
                heli.setBurnerLocked(false);
            }
        }

        // --- vectored lift: look up/down trims climb/sink; Space adds lift ---
        double trim = MathUtil.clamp(-eye.getPitch() / cfg.trimFullPitch, -1.0, 1.0);
        double targetV = trim * cfg.trimClimbRate + cfg.neutralDrift + (boosting ? cfg.boostClimb : 0.0);
        targetV = MathUtil.clamp(targetV, -cfg.maxSink, cfg.trimClimbRate + cfg.boostClimb);
        double vSpeed = MathUtil.approach(heli.vSpeed(), targetV, cfg.verticalAccel);
        heli.setVSpeed(vSpeed);

        // --- gentle visual nose tilt with vertical motion ---
        double targetPitch = MathUtil.clamp(vSpeed * 24.0, -12.0, 12.0);
        heli.setPitch(MathUtil.approach(heli.pitch(), targetPitch, 1.2));

        applyMotion(heli, cfg, speed, vSpeed);
        if (!heli.isActive()) {
            return; // crashed into terrain this tick — nothing left to render
        }

        // --- effects ---
        if (cfg.engineTrail) {
            emitEngineTrail(heli, speed);
        }
        if (cfg.burnerSteam && boosting) {
            Location bp = heli.burnerPoint();
            heli.world().spawnParticle(Particle.FLAME, bp, 4, 0.2, 0.2, 0.2, 0.01);
            heli.world().spawnParticle(Particle.LARGE_SMOKE, bp, 3, 0.2, 0.3, 0.2, 0.01);
        }

        if (heli.world().getFullTime() % cfg.hudInterval == 0) {
            sendHud(heli, driver, cfg);
        }
    }

    /**
     * Drift after a mid-air bail-out: roll levels, the collective is untended
     * so the helicopter slowly loses lift and sinks while momentum bleeds
     * away, until it settles on the ground and parks. No explosion — it is
     * too gentle to crash on its own.
     */
    public static void glide(Helicopter heli, HelicopterConfig cfg) {
        heli.setRoll(MathUtil.approachAngle(heli.roll(), 0.0, cfg.rollReturnSpeed));
        heli.setPitch(MathUtil.approach(heli.pitch(), -4.0, 1.0));

        double speed = MathUtil.approach(heli.speed(), 0.0, cfg.braking);
        heli.setSpeed(speed);

        // untended: a slow, capped descent
        double vSpeed = MathUtil.approach(heli.vSpeed(), -cfg.maxSink * 0.5, cfg.verticalAccel);
        heli.setVSpeed(vSpeed);

        boolean rested = applyMotion(heli, cfg, speed, vSpeed);
        if (!heli.isActive()) {
            return; // crashed into terrain while drifting
        }
        if (rested && Math.abs(speed) < 0.02) {
            heli.setUnmanned(false);
            heli.setVSpeed(0);
            heli.setSpeed(0);
        }

        if (cfg.engineTrail) {
            for (Location e : heli.enginePoints()) {
                heli.world().spawnParticle(Particle.LARGE_SMOKE, e, 2, 0.1, 0.1, 0.1, 0.01);
            }
        }
    }

    /**
     * Integrate one step of motion with terrain collision (ground rest, ceiling
     * stop, wall stop). Returns true if the helicopter is resting on the ground.
     */
    private static boolean applyMotion(Helicopter heli, HelicopterConfig cfg, double speed, double vSpeed) {
        World world = heli.world();
        Location anchor = heli.anchor();
        double yawRad = Math.toRadians(heli.yaw());
        double hx = -Math.sin(yawRad);
        double hz = Math.cos(yawRad);
        double rx = Math.cos(yawRad);
        double rz = Math.sin(yawRad);

        double oldX = anchor.getX(), oldY = anchor.getY(), oldZ = anchor.getZ();
        double newX = oldX + hx * speed;
        double newY = oldY + vSpeed;
        double newZ = oldZ + hz * speed;
        double halfLen = HelicopterModel.TOTAL_LENGTH / 2.0;

        ensureLoaded(world, newX, newZ);
        ensureLoaded(world, newX + hx * halfLen, newZ + hz * halfLen);
        ensureLoaded(world, newX - hx * halfLen, newZ - hz * halfLen);

        // --- void / ceiling guards ---
        if (newY < world.getMinHeight() - 8) {
            heli.destroy(true);
            return false;
        }

        boolean resting = false;

        // --- horizontal wall: sample the nose and the cabin front ---
        double bodyY = oldY + HelicopterModel.ENV_CY;
        double fore = halfLen * 0.65;
        double side = HelicopterModel.ENV_RX;
        boolean wall = isSolidAt(world, newX + hx * halfLen, oldY + HelicopterModel.ENV_CY, newZ + hz * halfLen)
                || isSolidAt(world, newX + hx * (halfLen * 0.5), bodyY, newZ + hz * (halfLen * 0.5))
                || isSolidAt(world, newX + hx * fore + rx * side, bodyY, newZ + hz * fore + rz * side)
                || isSolidAt(world, newX + hx * fore - rx * side, bodyY, newZ + hz * fore - rz * side)
                || isSolidAt(world, newX + rx * side, bodyY, newZ + rz * side)
                || isSolidAt(world, newX - rx * side, bodyY, newZ - rz * side)
                || isSolidAt(world, newX + hx * 3.0, oldY, newZ + hz * 3.0);
        if (wall && speed != 0.0) {
            if (Math.abs(speed) >= cfg.crashSpeed) {
                // full-speed ram: destroy the helicopter and crater the impact point
                Location impact = new Location(world,
                        oldX + hx * (halfLen * 0.55), oldY + HelicopterModel.ENV_CY * 0.35,
                        oldZ + hz * (halfLen * 0.55));
                heli.destroyByCrash(impact);
                return false;
            }
            newX = oldX;
            newZ = oldZ;
            heli.setSpeed(0.0);
        }

        // --- ceiling: stop upward motion if the rotor disc hits something ---
        if (vSpeed > 0) {
            double topY = newY + HelicopterModel.ENV_CY + HelicopterModel.ENV_RY;
            if (isSolidAt(world, newX, topY, newZ)) {
                newY = oldY;
                heli.setVSpeed(0.0);
            }
        }

        // --- ground: rest the gear gently on the surface below ---
        if (cfg.groundRest && vSpeed <= 0) {
            double bottom = newY + HelicopterModel.GONDOLA_BOTTOM_Y;
            Double ground = groundTopAt(world, newX, newZ, bottom + 3.0);
            if (ground != null && bottom < ground + 0.15) {
                newY = ground - HelicopterModel.GONDOLA_BOTTOM_Y + 0.05;
                heli.setVSpeed(0.0);
                resting = true;
            }
        }

        anchor.setX(newX);
        anchor.setY(newY);
        anchor.setZ(newZ);
        heli.refreshModel();
        return resting;
    }

    private static void emitEngineTrail(Helicopter heli, double speed) {
        if (Math.abs(speed) < 0.03) {
            return;
        }
        World world = heli.world();
        for (Location e : heli.enginePoints()) {
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

    private static void ensureLoaded(World world, double x, double z) {
        int cx = ((int) Math.floor(x)) >> 4;
        int cz = ((int) Math.floor(z)) >> 4;
        if (!world.isChunkLoaded(cx, cz)) {
            world.loadChunk(cx, cz, true);
        }
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

    private static void sendHud(Helicopter heli, Player driver, HelicopterConfig cfg) {
        int hpPct = (int) Math.round(100.0 * heli.health() / heli.maxHealth());
        int kmh = (int) Math.round(heli.speed() * 72);
        int alt = (int) Math.round(heli.anchor().getY());
        double v = heli.vSpeed();

        Component vert;
        if (v > 0.02) {
            vert = Component.text(" ▲", NamedTextColor.GREEN);
        } else if (v < -0.02) {
            vert = Component.text(" ▼", NamedTextColor.GOLD);
        } else {
            vert = Component.text(" ●", NamedTextColor.GRAY); // hovering
        }

        Component engine;
        if (heli.isBurnerLocked()) {
            engine = Component.text(" ⚠ENGINE", NamedTextColor.RED);
        } else {
            int heat = (int) Math.round(heli.burnerHeat() * 100);
            engine = heat > 5 ? Component.text(" 🔥" + heat + "%", NamedTextColor.YELLOW)
                    : Component.text("");
        }

        Component hud = Component.text("HP ", NamedTextColor.GRAY)
                .append(Component.text(hpPct + "%", hpPct > 50 ? NamedTextColor.GREEN
                        : hpPct > 25 ? NamedTextColor.YELLOW : NamedTextColor.RED))
                .append(Component.text("  🚁 " + Math.abs(kmh) + " km/h" + (kmh < 0 ? " reverse" : ""), NamedTextColor.AQUA))
                .append(vert)
                .append(Component.text("  ▲" + alt, NamedTextColor.GRAY))
                .append(engine)
                .append(Component.text("  🚀 " + heli.rocketAmmo() + "/" + cfg.rocketMagazine,
                        heli.rocketAmmo() > 0 ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text("  💣 " + heli.bombAmmo() + "/" + cfg.bombLoad,
                        heli.bombAmmo() > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
        driver.sendActionBar(hud);
    }
}
