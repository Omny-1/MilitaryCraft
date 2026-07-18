package me.bibo.militarycraft.vehicles.jet.control;

import me.bibo.militarycraft.vehicles.jet.config.JetConfig;
import me.bibo.militarycraft.vehicles.jet.jet.Jet;
import me.bibo.militarycraft.vehicles.jet.model.JetModel;
import me.bibo.militarycraft.vehicles.jet.util.MathUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Arcade flight. The mouse (camera) steers where the nose points — look up to
 * climb, look down to dive. A/D roll the jet (hold for a full barrel roll); W
 * throttles up, S throttles down, Space is the afterburner (limited by engine
 * heat).
 *
 * <p>Lift cancels gravity at speed; at or below {@code stall-speed} (~10 km/h)
 * the jet stalls and drops fast. Touching terrain faster than {@code crash-speed}
 * is a KAMIKAZE crash: the jet is destroyed and the impact point explodes
 * (crater + fire). Slower contact just stops / lands.
 *
 * <p>All the sign conventions are gathered at the top so the flight feel can be
 * tuned or mirrored in one place.
 */
public final class FlightController {

    private static final int TRAIL_INTERVAL_TICKS = 2;
    private static final double STOP_SPEED_EPSILON = 0.05;
    private static final Vector3f[] FOOTPRINT_POINTS = {
            new Vector3f(0f, 0f, 0f),
            new Vector3f(0f, 0f, JetModel.LENGTH * 0.46f),
            new Vector3f(0f, 0f, -JetModel.LENGTH * 0.42f),
            new Vector3f(JetModel.WIDTH * 0.46f, 0f, -0.3f),
            new Vector3f(-JetModel.WIDTH * 0.46f, 0f, -0.3f)
    };
    private static final Vector3f[] COLLISION_PROBES = {
            new Vector3f(0f, 0.55f, JetModel.LENGTH * 0.50f),
            new Vector3f(0f, 0.15f, JetModel.LENGTH * 0.35f),
            new Vector3f(0f, 0.50f, -JetModel.LENGTH * 0.42f),
            new Vector3f(JetModel.WIDTH * 0.48f, 0.35f, -0.4f),
            new Vector3f(-JetModel.WIDTH * 0.48f, 0.35f, -0.4f),
            new Vector3f(0f, 1.35f, 1.0f)
    };

    private FlightController() {
    }

    public static void fly(Jet jet, Player driver, JetConfig cfg) {
        Input in = driver.getCurrentInput();
        Location eye = driver.getLocation();

        // --- attitude: the nose follows the camera. Minecraft look pitch is
        //     +down / -up, and the transform uses the same sign, so "look up"
        //     is negative pitch and climbs, while "look down" dives. ---
        double targetYaw = eye.getYaw();
        double targetPitch = MathUtil.clamp(eye.getPitch(), -cfg.maxPitch, cfg.maxPitch);

        double prevYaw = jet.yaw();
        double newYaw = MathUtil.approachAngle(prevYaw, targetYaw, cfg.turnRate);
        double yawRate = MathUtil.angleDelta(prevYaw, newYaw); // signed degrees this tick
        jet.setYaw(newYaw);
        jet.setPitch(MathUtil.approach(jet.pitch(), targetPitch, cfg.pitchRate));

        // --- roll: A/D spin continuously (barrel roll); released, ease to the
        //     bank the current turn wants (auto-bank). ---
        double roll = jet.roll();
        if (in.isLeft() && !in.isRight()) {
            roll = MathUtil.wrapDegrees(roll + cfg.rollSpeed);
        } else if (in.isRight() && !in.isLeft()) {
            roll = MathUtil.wrapDegrees(roll - cfg.rollSpeed);
        } else {
            double autoBank = MathUtil.clamp(yawRate * cfg.autoBankFactor, -cfg.maxBank, cfg.maxBank);
            roll = MathUtil.approachAngle(roll, autoBank, cfg.rollReturnSpeed);
        }
        jet.setRoll(roll);

        Location anchor = jet.anchor();
        World world = jet.world();
        double oldX = anchor.getX();
        double oldY = anchor.getY();
        double oldZ = anchor.getZ();
        Quaternionf orientation = jet.orientation();
        boolean airborne = clearanceBelowJet(world, oldX, oldY, oldZ, orientation, cfg.stallClearance)
                >= cfg.stallClearance;

        // --- afterburner heat: Space boosts until the engines overheat ---
        boolean wantBoost = in.isJump();
        boolean boosting = false;
        double target;
        if (wantBoost && !jet.isBoostLocked()) {
            boosting = true;
            target = cfg.boostSpeed;
            jet.setBoostHeat(jet.boostHeat() + cfg.boostHeatPerTick);
            if (jet.boostHeat() >= 1.0) {
                jet.setBoostLocked(true); // overheated: cut out until cooled
            }
        } else {
            target = in.isForward() ? cfg.maxSpeed
                    : in.isBackward() ? 0.0
                    : 0.0;
            target = Math.min(target, cfg.maxSpeed);
            jet.setBoostHeat(jet.boostHeat() - cfg.boostCoolPerTick);
            if (jet.isBoostLocked() && jet.boostHeat() <= cfg.boostResumeThreshold) {
                jet.setBoostLocked(false);
            }
        }
        jet.setBoosting(boosting);
        double rate = cfg.acceleration;
        if (target < jet.speed()) {
            rate = in.isBackward() ? cfg.braking : cfg.coastDeceleration;
        }
        double speed = Math.max(0.0, MathUtil.approach(jet.speed(), target, rate));
        jet.setSpeed(speed);

        // --- vertical model: keep your speed up or lose altitude. This is the
        //     anti-camp rule — slowing to a crawl to aim makes the jet sink, and
        //     the slower it goes the harder it drops. It only applies once there
        //     is real clearance below, so taking off and landing are never
        //     penalised (otherwise the jet could never leave the ground). ---
        Vector3f fwd = orientation.transform(new Vector3f(0f, 0f, 1f));

        double sink;
        if (!airborne) {
            // near the ground (takeoff / landing): gentle lift, can always climb out
            double liftFrac = MathUtil.clamp(speed / cfg.liftSpeed, 0.0, 1.0);
            sink = cfg.gravity * (1.0 - liftFrac);
        } else {
            double kmh = speed * 72.0;
            if (kmh >= cfg.stallFallKmh) {
                sink = 0.0;                   // fast enough — holds altitude
            } else if (kmh >= cfg.stallFastKmh) {
                sink = cfg.stallSinkMild;     // < 90 km/h: starts losing altitude
            } else if (kmh >= cfg.stallCriticalKmh) {
                sink = cfg.stallSinkFast;     // < 60 km/h: dropping fairly fast
            } else {
                sink = cfg.stallSinkCritical; // < 30 km/h: drops almost at once
            }
        }

        double newX = oldX + fwd.x * speed;
        double newY = oldY + fwd.y * speed - sink;
        double newZ = oldZ + fwd.z * speed;

        // --- void / ceiling guards ---
        if (newY < world.getMinHeight() - 6) {
            jet.destroy(true);
            return;
        }
        double ceiling = world.getMaxHeight() + 12;
        if (newY > ceiling) {
            newY = ceiling;
        }

        if (!chunksLoadedForJet(world, newX, newZ, orientation)) {
            jet.setSpeed(Math.min(jet.speed(), STOP_SPEED_EPSILON));
            if (world.getFullTime() % cfg.hudInterval == 0) {
                driver.sendActionBar(Component.text("Chunk ahead is not loaded - thrust cut",
                        NamedTextColor.RED));
            }
            return;
        }

        boolean crashed = false;
        Location impact = null;

        // --- vertical contact: a steep, fast nose-down into the ground is a
        //     kamikaze crash; otherwise the jet settles on the ground (landing). ---
        if (cfg.groundRest) {
            GroundContact ground = groundContact(world, newX, newY, newZ, orientation);
            if (ground != null) {
                if (speed > cfg.crashSpeed && fwd.y < -0.25) {
                    crashed = true;
                    impact = ground.impact;
                } else {
                    newY += ground.lift;
                    if (target <= 0.0) {
                        jet.setSpeed(0.0);
                    }
                }
            }
        }

        // --- horizontal contact: walls ahead, sampled at fuselage height so
        //     resting / taxiing over the ground is never read as a wall. ---
        if (!crashed) {
            Location wall = firstSolidProbe(world, newX, newY, newZ, orientation);
            if (wall != null) {
                if (speed > cfg.crashSpeed) {
                    crashed = true;
                    impact = wall;
                } else {
                    newX = oldX; // low-speed nudge: don't tunnel into the wall
                    newZ = oldZ;
                    jet.setSpeed(Math.min(jet.speed(), speed * 0.15));
                }
            }
        }

        // --- KAMIKAZE: destroy the jet and blow a crater where it hit ---
        if (crashed) {
            jet.destroyByCrash(impact);
            return;
        }

        anchor.setX(newX);
        anchor.setY(newY);
        anchor.setZ(newZ);
        jet.refreshModel();

        // --- engine afterburner trail ---
        if (cfg.engineTrail) {
            emitTrail(jet, boosting, speed);
            emitDamageTrail(jet);
        }

        // --- throttled HUD ---
        if (world.getFullTime() % cfg.hudInterval == 0) {
            sendHud(jet, driver, cfg);
        }
    }

    /**
     * Pilotless flight after a mid-air bail-out. The jet keeps its heading, the
     * wings level off and the nose sags into a dive while momentum carries it
     * forward; the dead engines bleed speed and it sinks until it slams into
     * terrain and explodes (the kamikaze crater). If it reaches the ground with
     * almost no speed it simply settles and stops being unmanned.
     */
    public static void glide(Jet jet, JetConfig cfg) {
        // level the wings and let the nose drop into a steady dive
        jet.setRoll(MathUtil.approachAngle(jet.roll(), 0.0, cfg.rollReturnSpeed));
        jet.setPitch(MathUtil.approach(jet.pitch(), 30.0, cfg.pitchRate * 0.4));

        // engines are out: momentum bleeds away each tick
        double speed = Math.max(0.0, jet.speed() - cfg.braking * 0.25);
        jet.setSpeed(speed);

        Quaternionf orientation = jet.orientation();
        Vector3f fwd = orientation.transform(new Vector3f(0f, 0f, 1f));
        // no controlled lift: always sinking, harder the slower it flies
        double liftFrac = MathUtil.clamp(speed / cfg.liftSpeed, 0.0, 1.0);
        double sink = (speed <= cfg.stallSpeed)
                ? cfg.stallSink
                : cfg.gravity * (1.5 - liftFrac);

        Location anchor = jet.anchor();
        World world = jet.world();
        double oldX = anchor.getX();
        double oldY = anchor.getY();
        double oldZ = anchor.getZ();

        double newX = oldX + fwd.x * speed;
        double newY = oldY + fwd.y * speed - sink;
        double newZ = oldZ + fwd.z * speed;

        if (newY < world.getMinHeight() - 6) {
            jet.destroy(true);
            return;
        }

        if (!chunksLoadedForJet(world, newX, newZ, orientation)) {
            jet.setSpeed(0.0);
            jet.setUnmanned(false);
            return;
        }

        // ground contact: crash (explode) if still moving, else land and rest
        GroundContact ground = groundContact(world, newX, newY, newZ, orientation);
        if (ground != null) {
            if (speed > cfg.stallSpeed) {
                jet.destroyByCrash(ground.impact);
                return;
            }
            anchor.setX(newX);
            anchor.setY(newY + ground.lift);
            anchor.setZ(newZ);
            jet.setSpeed(0.0);
            jet.setUnmanned(false);
            jet.refreshModel();
            return;
        }

        // wall ahead: any meaningful speed into it is a crash
        Location wall = firstSolidProbe(world, newX, newY, newZ, orientation);
        if (wall != null) {
            if (speed > cfg.stallSpeed) {
                jet.destroyByCrash(wall);
                return;
            }
            newX = oldX;
            newZ = oldZ;
        }

        anchor.setX(newX);
        anchor.setY(newY);
        anchor.setZ(newZ);
        jet.refreshModel();

        // a wounded smoke trail as it goes down
        if (cfg.engineTrail) {
            for (Location nozzle : jet.nozzleLocations()) {
                world.spawnParticle(Particle.LARGE_SMOKE, nozzle, 3, 0.1, 0.1, 0.1, 0.01);
                world.spawnParticle(Particle.SMOKE, nozzle, 2, 0.08, 0.08, 0.08, 0.0);
            }
        }
    }

    private static void emitTrail(Jet jet, boolean boost, double speed) {
        World world = jet.world();
        if (!boost && world.getFullTime() % TRAIL_INTERVAL_TICKS != 0) {
            return;
        }
        Vector back = jet.forward().multiply(-1);
        for (Location nozzle : jet.nozzleLocations()) {
            if (boost) {
                // long, bright afterburner cone
                world.spawnParticle(Particle.FLAME, nozzle, 5, 0.06, 0.06, 0.06, 0.02);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, nozzle, 3, 0.05, 0.05, 0.05, 0.01);
                world.spawnParticle(Particle.LARGE_SMOKE, nozzle.clone().add(back.clone().multiply(0.6)),
                        2, 0.08, 0.08, 0.08, 0.01);
            } else if (speed > 0.05) {
                world.spawnParticle(Particle.SMOKE, nozzle, 1, 0.05, 0.05, 0.05, 0.0);
                world.spawnParticle(Particle.FLAME, nozzle, 1, 0.03, 0.03, 0.03, 0.0);
            }
        }
    }

    private static void emitDamageTrail(Jet jet) {
        double hpFrac = jet.health() / Math.max(1.0, jet.maxHealth());
        if (hpFrac >= 0.5 || jet.world().getFullTime() % TRAIL_INTERVAL_TICKS != 0) {
            return;
        }
        World world = jet.world();
        for (Location nozzle : jet.nozzleLocations()) {
            world.spawnParticle(Particle.LARGE_SMOKE, nozzle, hpFrac < 0.25 ? 2 : 1,
                    0.12, 0.12, 0.12, 0.01);
        }
        if (hpFrac < 0.25) {
            world.spawnParticle(Particle.FLAME, jet.anchor().clone().add(0, 0.8, 0),
                    2, 0.5, 0.3, 0.5, 0.02);
        }
    }

    private static boolean chunksLoadedForJet(World world, double x, double z, Quaternionf orientation) {
        if (!isChunkLoadedAt(world, x, z)) {
            return false;
        }
        for (Vector3f point : FOOTPRINT_POINTS) {
            Vector3f off = orientation.transform(new Vector3f(point));
            if (!isChunkLoadedAt(world, x + off.x, z + off.z)) {
                return false;
            }
        }
        for (Vector3f point : COLLISION_PROBES) {
            Vector3f off = orientation.transform(new Vector3f(point));
            if (!isChunkLoadedAt(world, x + off.x, z + off.z)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isChunkLoadedAt(World world, double x, double z) {
        return world.isChunkLoaded(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
    }

    private static Location firstSolidProbe(World world, double x, double y, double z, Quaternionf orientation) {
        for (Vector3f point : COLLISION_PROBES) {
            Vector3f off = orientation.transform(new Vector3f(point));
            double px = x + off.x;
            double py = y + off.y;
            double pz = z + off.z;
            if (isSolidAt(world, px, py, pz)) {
                return new Location(world, px, py, pz);
            }
        }
        return null;
    }

    private static GroundContact groundContact(World world, double x, double y, double z,
                                               Quaternionf orientation) {
        GroundContact best = null;
        for (Vector3f point : FOOTPRINT_POINTS) {
            Vector3f off = orientation.transform(new Vector3f(point));
            double px = x + off.x;
            double py = y + off.y;
            double pz = z + off.z;
            Double ground = groundTopAt(world, px, pz, py + 2.0);
            if (ground == null) {
                continue;
            }
            double lift = ground + 0.25 - py;
            if (lift > 0 && (best == null || lift > best.lift)) {
                best = new GroundContact(new Location(world, px, ground + 0.3, pz), lift);
            }
        }
        return best;
    }

    /** True if the block at this point is a solid the jet can't fly through. */
    private static boolean isSolidAt(World world, double x, double y, double z) {
        Block b = world.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        return !b.isPassable() && b.getType().isSolid();
    }

    /**
     * How many blocks of clear air sit directly below the jet, capped at
     * {@code max}. Used to tell "high in the air" (anti-camp sink applies) from
     * "on/near the ground" (taking off or landing — never penalised).
     */
    private static int clearanceBelowJet(World world, double x, double y, double z,
                                         Quaternionf orientation, int max) {
        int best = max;
        for (Vector3f point : FOOTPRINT_POINTS) {
            Vector3f off = orientation.transform(new Vector3f(point));
            best = Math.min(best, clearanceBelow(world, x + off.x, y + off.y, z + off.z, max));
            if (best == 0) {
                return 0;
            }
        }
        return best;
    }

    private static int clearanceBelow(World world, double x, double y, double z, int max) {
        if (!isChunkLoadedAt(world, x, z)) {
            return max;
        }
        int min = world.getMinHeight();
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int startY = (int) Math.floor(y);
        for (int dy = 1; dy <= max; dy++) {
            int by = startY - dy;
            if (by < min) {
                return max; // void below: treat as fully airborne
            }
            Block b = world.getBlockAt(bx, by, bz);
            if (!b.isPassable() && b.getType().isSolid()) {
                return dy - 1;
            }
        }
        return max;
    }

    /**
     * World Y of the highest solid surface in the column (x,z), searched from a
     * little above the jet down a short way. Returns null if nothing is near.
     */
    private static Double groundTopAt(World world, double x, double z, double fromY) {
        if (!isChunkLoadedAt(world, x, z)) {
            return null;
        }
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int startY = (int) Math.floor(fromY);
        int min = world.getMinHeight();
        for (int by = Math.min(startY, world.getMaxHeight() - 1); by >= Math.max(min, startY - 6); by--) {
            Block b = world.getBlockAt(bx, by, bz);
            if (!b.isPassable() && b.getType().isSolid()) {
                double topY = b.getBoundingBox().getMaxY();
                return topY > by + 0.01 ? topY : by + 1.0;
            }
        }
        return null;
    }

    private record GroundContact(Location impact, double lift) {
    }

    private static void sendHud(Jet jet, Player driver, JetConfig cfg) {
        int hpPct = (int) Math.round(100.0 * jet.health() / jet.maxHealth());
        int kmh = (int) Math.round(jet.speed() * 72);
        int alt = (int) Math.round(jet.anchor().getY());

        Component boost;
        if (jet.isBoostLocked()) {
            boost = Component.text(" ⚠OVERHEAT", NamedTextColor.RED);
        } else if (jet.isBoosting()) {
            boost = Component.text(" ⏵AFTERBURNER", NamedTextColor.GOLD);
        } else {
            int heat = (int) Math.round(jet.boostHeat() * 100);
            boost = heat > 5 ? Component.text(" 🔥" + heat + "%", NamedTextColor.YELLOW)
                    : Component.text("", NamedTextColor.GRAY);
        }
        // speed warning when slow enough to start losing altitude (anti-camp)
        boolean stalling = (jet.speed() * 72.0) < cfg.stallFallKmh;
        Component stall = stalling
                ? Component.text(" ⚠SPEED", NamedTextColor.RED) : Component.text("");
        if (stalling && jet.world().getFullTime() % 20 == 0) {
            driver.playSound(driver.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.35f, 0.6f);
        }

        Component hud = Component.text("HP ", NamedTextColor.GRAY)
                .append(Component.text(hpPct + "%", hpPct > 50 ? NamedTextColor.GREEN
                        : hpPct > 25 ? NamedTextColor.YELLOW : NamedTextColor.RED))
                .append(Component.text("  ✈ " + kmh + " km/h", NamedTextColor.AQUA))
                .append(boost)
                .append(stall)
                .append(Component.text("  ▲" + alt, NamedTextColor.GRAY))
                .append(Component.text("  🚀 " + jet.rocketAmmo() + "/" + cfg.rocketMagazine,
                        jet.rocketAmmo() > 0 ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text("  💣 " + jet.bombAmmo() + "/" + cfg.bombLoad,
                        jet.bombAmmo() > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
        driver.sendActionBar(hud);
    }
}
