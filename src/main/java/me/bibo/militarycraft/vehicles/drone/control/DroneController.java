package me.bibo.militarycraft.vehicles.drone.control;

import me.bibo.militarycraft.vehicles.drone.config.DroneConfig;
import me.bibo.militarycraft.vehicles.drone.drone.Drone;
import me.bibo.militarycraft.vehicles.drone.drone.DroneManager;
import me.bibo.militarycraft.vehicles.drone.util.MathUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.joml.Vector3f;

/**
 * Flight for the twin-boom UAV. It flies forward on its own at a constant speed
 * (as if W were always held); the operator only steers with the camera (look up
 * to climb, down to dive). The airframe banks into turns for feel. Ramming the
 * nose into terrain or a target detonates the warhead; rockets fire on right
 * click. After the operator leaves, the UAV keeps flying straight until it hits
 * something or its loiter time runs out.
 */
public final class DroneController {

    private DroneController() {
    }

    public static void fly(Drone drone, Player driver, DroneManager manager, DroneConfig cfg) {
        drone.tickRocketReload();
        if (cfg.batteryEnabled) {
            drone.setBattery(drone.battery() - 1);
            if (drone.battery() <= 0) {
                manager.brownout(drone);
                return;
            }
        }

        Location eye = driver.getLocation();
        double targetYaw = eye.getYaw();
        double targetPitch = MathUtil.clamp(eye.getPitch(), -cfg.maxPitch, cfg.maxPitch);
        double prevYaw = drone.yaw();
        // ease the heading toward the camera so the path arcs like an aircraft
        // instead of snapping (also bounds the bank from runaway camera flicks).
        double newYaw = MathUtil.approachAngle(prevYaw, targetYaw, cfg.turnRate);
        double newPitch = MathUtil.approach(drone.pitch(), targetPitch, cfg.turnRate);
        double yawRate = MathUtil.angleDelta(prevYaw, newYaw);
        drone.setYaw(newYaw);
        drone.setPitch(newPitch);
        double autoBank = MathUtil.clamp(-yawRate * cfg.autoBankFactor, -cfg.maxBank, cfg.maxBank);
        drone.setRoll(MathUtil.approachAngle(drone.roll(), autoBank, cfg.rollReturnSpeed));

        if (!advance(drone, manager, cfg, driver)) {
            return; // detonated / destroyed
        }

        drone.advanceProp(cfg.propSpinPerTick);
        drone.refreshModel();
        if (cfg.propWash) {
            emitExhaust(drone, cfg.speed);
        }
        if (drone.world().getFullTime() % cfg.motorInterval == 0) {
            drone.world().playSound(drone.anchor(), cfg.motorSound, cfg.motorVolume, cfg.motorPitch);
        }
        if (drone.world().getFullTime() % cfg.hudInterval == 0) {
            sendHud(drone, driver, cfg);
        }
    }

    /**
     * Lost-signal flight after the operator leaves (2×Shift) or a brown-out: the
     * UAV keeps flying straight ahead - it does NOT fall - until it rams something
     * or its loiter timer expires (then it self-destructs).
     */
    public static void glide(Drone drone, DroneManager manager, DroneConfig cfg) {
        drone.tickUnmanned();
        if (drone.unmannedTicks() > cfg.unmannedLifetimeTicks) {
            manager.detonate(drone, drone.nose());
            return;
        }
        drone.setRoll(MathUtil.approachAngle(drone.roll(), 0.0, cfg.rollReturnSpeed));
        if (!advance(drone, manager, cfg, null)) {
            return;
        }
        drone.advanceProp(cfg.propSpinPerTick);
        drone.refreshModel();
        if (cfg.propWash) {
            emitExhaust(drone, cfg.speed);
        }
    }

    /**
     * Move one step forward along the nose, guard void/ceiling, and check the
     * kamikaze triggers (nose in a solid block, or a living target in range).
     * Returns false if the UAV detonated / was removed.
     */
    private static boolean advance(Drone drone, DroneManager manager, DroneConfig cfg, Player driver) {
        World world = drone.world();
        Vector3f fwd = drone.orientation().transform(new Vector3f(0f, 0f, 1f));
        double sp = cfg.speed;
        drone.setVelocity(fwd.x * sp, fwd.y * sp, fwd.z * sp);

        double nx = drone.anchor().getX() + fwd.x * sp;
        double ny = drone.anchor().getY() + fwd.y * sp;
        double nz = drone.anchor().getZ() + fwd.z * sp;

        if (ny < world.getMinHeight() - 4) {
            drone.destroy(false); // gone into the void: just remove it
            return false;
        }
        double ceiling = world.getMaxHeight() + 16;
        if (ny > ceiling) {
            ny = ceiling;
        }
        drone.setAnchor(nx, ny, nz);
        drone.ensureChunkLoaded(); // keep the flying UAV's own chunk loaded

        if (drone.isArmed()) {
            Location nose = drone.nose();
            if (isSolidAt(world, nose.getX(), nose.getY(), nose.getZ())) {
                manager.detonate(drone, nose);
                return false;
            }
            // Flying close to a target only detonates if explicitly enabled - otherwise
            // you can approach a target and shoot rockets without ramming it.
            double vehicleContactRadius = cfg.detonateOnEntityContact ? cfg.proximityRadius : 0.7;
            var vehicle = manager.vehicleImpact(nose, vehicleContactRadius, drone.id());
            if (vehicle != null) {
                manager.detonate(drone, vehicle.point());
                return false;
            }
            if (cfg.detonateOnEntityContact) {
                LivingEntity target = nearestTarget(drone, driver, cfg.proximityRadius);
                if (target != null) {
                    manager.detonate(drone, target.getLocation().add(0, target.getHeight() * 0.5, 0));
                    return false;
                }
            }
        }
        return true;
    }

    private static LivingEntity nearestTarget(Drone drone, Player driver, double radius) {
        Location c = drone.nose();
        LivingEntity best = null;
        double bestSq = radius * radius;
        for (Entity e : drone.world().getNearbyEntities(c, radius, radius, radius)) {
            if (!(e instanceof LivingEntity le) || e.isDead()) {
                continue;
            }
            if (driver != null && e.getUniqueId().equals(driver.getUniqueId())) {
                continue;
            }
            if (e.getScoreboardTags().contains(me.bibo.militarycraft.vehicles.drone.util.Keys.SCOREBOARD_TAG)) {
                continue;
            }
            double dSq = e.getLocation().distanceSquared(c);
            if (dSq < bestSq) {
                bestSq = dSq;
                best = le;
            }
        }
        return best;
    }

    /**
     * Engine smoke from the rear, plus faint wingtip vapour. The interpolated model
     * trails the true anchor by about one tick at speed, so we spawn the trail a
     * step or two behind the rear point to keep it visually at the tail (not ahead
     * of the nose).
     */
    private static void emitExhaust(Drone drone, double sp) {
        World world = drone.world();
        Vector3f fwd = drone.orientation().transform(new Vector3f(0f, 0f, 1f));
        double bx = fwd.x * sp, by = fwd.y * sp, bz = fwd.z * sp;
        for (Location ex : drone.exhaustLocations()) {
            Location a = ex.clone().subtract(bx, by, bz);
            Location b = a.clone().subtract(bx, by, bz);
            world.spawnParticle(Particle.SMOKE, a, 2, 0.04, 0.04, 0.04, 0.004);
            world.spawnParticle(Particle.SMOKE, b, 1, 0.05, 0.05, 0.05, 0.0);
        }
        if (world.getFullTime() % 2 == 0) {
            for (Location wt : drone.wingtipLocations()) {
                world.spawnParticle(Particle.CLOUD, wt.subtract(bx, by, bz), 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    private static boolean isSolidAt(World world, double x, double y, double z) {
        Block b = world.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        return !b.isPassable() && b.getType().isSolid();
    }

    private static void sendHud(Drone drone, Player driver, DroneConfig cfg) {
        int hpPct = (int) Math.round(100.0 * drone.health() / drone.maxHealth());
        int kmh = (int) Math.round(drone.velLength() * 72);
        int alt = (int) Math.round(drone.anchor().getY());

        Component armed = drone.isArmed()
                ? Component.text(" ◉ARMED", NamedTextColor.RED)
                : Component.text(" ○ARMING", NamedTextColor.GRAY);

        Component battery;
        boolean low = false;
        if (cfg.batteryEnabled) {
            int batPct = (int) Math.round(100.0 * drone.battery() / cfg.batteryFlightTicks);
            low = batPct < cfg.batteryLowPercent;
            NamedTextColor col = batPct > 50 ? NamedTextColor.GREEN
                    : batPct > cfg.batteryLowPercent ? NamedTextColor.YELLOW : NamedTextColor.RED;
            battery = Component.text("  🔋 " + batPct + "%", col);
        } else {
            battery = Component.text("");
        }

        Component hud = Component.text("UAV ", NamedTextColor.AQUA)
                .append(Component.text(kmh + " km/h", NamedTextColor.WHITE))
                .append(Component.text("  ▲" + alt, NamedTextColor.GRAY))
                .append(battery)
                .append(Component.text("  🚀 " + drone.rocketAmmo() + "/" + cfg.rocketCount,
                        drone.rocketAmmo() > 0 ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(armed)
                .append(Component.text("  HP ", NamedTextColor.GRAY))
                .append(Component.text(hpPct + "%", hpPct > 50 ? NamedTextColor.GREEN
                        : hpPct > 25 ? NamedTextColor.YELLOW : NamedTextColor.RED));
        if (low) {
            hud = hud.append(Component.text("  ⚠BATTERY", NamedTextColor.RED));
        }
        driver.sendActionBar(hud);
    }
}
