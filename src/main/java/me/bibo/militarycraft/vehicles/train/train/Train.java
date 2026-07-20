package me.bibo.militarycraft.vehicles.train.train;

import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import me.bibo.militarycraft.vehicles.train.TrainRuntime;
import me.bibo.militarycraft.vehicles.train.config.TrainConfig;
import me.bibo.militarycraft.vehicles.train.model.CarTransforms;
import me.bibo.militarycraft.vehicles.train.model.TrainModel;
import me.bibo.militarycraft.vehicles.train.rail.RailCursor;
import me.bibo.militarycraft.vehicles.train.rail.RailEdge;
import me.bibo.militarycraft.vehicles.train.rail.RailTracer;
import me.bibo.militarycraft.vehicles.train.util.Keys;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A whole train: the locomotive and three wagons riding one shared
 * {@link TrainPath}. Each tick the nose advances along the rails at cruise
 * speed, the path is extended ahead just enough to see an upcoming dead end,
 * and every car is posed from two "bogie" anchors read off the path at fixed
 * distances behind the nose - rigid couplings, no corner cutting.
 */
public final class Train implements VehicleHandle {

    /** Extra stopping margin before the last rail (blocks). */
    private static final double BRAKE_MARGIN = 0.45;
    private static final int EXTEND_GUARD = 64;
    private static final int CAR_COUNT = 4;
    private static final int CHUNK_EDGE_PRELOAD_BLOCKS = 3;

    private final TrainRuntime plugin;
    private final UUID id = UUID.randomUUID();
    private final World world;
    private final List<TrainCar> cars = new ArrayList<>(CAR_COUNT);
    private final TrainPath path;
    private final RailCursor cursor;
    private final Map<UUID, Long> hitCooldown = new HashMap<>();
    private final Set<Long> tickets = new HashSet<>();

    private double frontDist;   // arc length of the locomotive's nose
    private double speed;       // blocks per tick
    private boolean braking;
    private boolean stopped;
    private boolean removed;
    private long tickCount;

    // Rolling-distance counters per wheel class, in degrees (distance/radius),
    // wrapped to keep the numbers small - this is what spins the wheels and
    // orbits the coupling/piston rods in lock-step across every car.
    private double driverPhaseDeg;
    private double leadingPhaseDeg;
    private double bogiePhaseDeg;

    private Train(TrainRuntime plugin, World world, TrainPath path, RailCursor cursor, double frontDist) {
        this.plugin = plugin;
        this.world = world;
        this.path = path;
        this.cursor = cursor;
        this.frontDist = frontDist;
        for (int i = 0; i < CAR_COUNT; i++) {
            cars.add(new TrainCar(plugin, id, world, i));
        }
    }

    // ------------------------------------------------------------- spawning

    /**
     * Build a train on the clicked rail, nose pointing the way the player
     * faces.
     */
    public static Train spawn(TrainRuntime plugin, Block rail, Player placer) {
        return spawn(plugin, rail, placer.getLocation().getYaw());
    }

    /**
     * Build a train on the given rail, nose pointing toward the supplied yaw.
     * The wagons need history BEHIND the nose, so the track is traced
     * backwards first; if it is shorter than the train, the tail is
     * extrapolated straight and the wagons simply pull onto the rails as the
     * train moves off.
     */
    public static Train spawn(TrainRuntime plugin, Block rail, double yawDegrees) {
        TrainConfig cfg = plugin.cfg();
        RailTracer.Connection[] cs = RailTracer.connections(
                ((org.bukkit.block.data.Rail) rail.getBlockData()).getShape());

        double yawRad = Math.toRadians(yawDegrees);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double s0 = cs[0].edge().dx * fx + cs[0].edge().dz * fz;
        double s1 = cs[1].edge().dx * fx + cs[1].edge().dz * fz;
        RailTracer.Connection exitConn = s0 >= s1 ? cs[0] : cs[1];

        double totalLen = totalLength(cfg);

        // Trace backwards: enter the clicked block from its exit side.
        TrainPath back = new TrainPath();
        RailCursor backCursor = new RailCursor(rail, exitConn.edge());
        double need = totalLen + 4.0;
        int guard = 0;
        while (!backCursor.isDeadEnd() && back.maxDist() < need && guard++ < 256) {
            backCursor.extend(back);
        }
        List<Vector> pts = back.snapshotPoints();
        if (pts.size() < 2) {
            return null; // clicked block produced no polyline: shouldn't happen
        }
        if (back.maxDist() < need) {
            Vector a = pts.get(pts.size() - 2);
            Vector b = pts.get(pts.size() - 1);
            Vector dir = b.clone().subtract(a);
            if (dir.lengthSquared() < 1e-9) {
                dir = new Vector(-exitConn.edge().dx, 0, -exitConn.edge().dz);
            }
            dir.normalize();
            pts.add(b.clone().add(dir.multiply(need - back.maxDist())));
        }
        Collections.reverse(pts);

        TrainPath path = new TrainPath();
        for (Vector p : pts) {
            path.append(p);
        }
        RailCursor cursor = RailCursor.afterExit(rail, exitConn);

        Train t = new Train(plugin, rail.getWorld(), path, cursor, path.maxDist());
        t.speed = cfg.cruisePerTick; // no wind-up: full speed from tick one
        t.extendAhead();
        t.refreshChunkTickets();
        t.updateCars();
        t.departureEffects(true);
        return t;
    }

    public static double totalLength(TrainConfig cfg) {
        return TrainModel.LOCO_LENGTH + 3 * (cfg.carGap + TrainModel.WAGON_LENGTH);
    }

    // ------------------------------------------------------------------ tick

    public void tick() {
        if (removed) {
            return;
        }
        tickCount++;
        refreshChunkTickets();
        repairCarModels();
        if (!cars.get(0).isValid() && tickCount > 1) {
            // Model lost (unloaded without tickets, killed entities…): fold up.
            plugin.getLogger().warning("Train " + id + " lost its model - removing leftovers.");
            remove();
            return;
        }
        if (tickCount % 40 == 0) {
            for (TrainCar car : cars) {
                car.pruneSeats();
            }
        }

        TrainConfig cfg = plugin.cfg();
        if (stopped) {
            if (tickCount % 20 == 0 && cursor.tryResume()) {
                stopped = false;
                braking = false;
                departureEffects(false);
            }
            return;
        }

        extendAhead();

        double distToEnd = cursor.isDeadEnd() ? path.maxDist() - frontDist : Double.MAX_VALUE;
        double brakeDist = speed * speed / (2 * cfg.decelPerTick);
        if (distToEnd < brakeDist + BRAKE_MARGIN) {
            braking = true;
            speed = Math.max(0.0, speed - cfg.decelPerTick);
        } else {
            braking = false;
            speed = cfg.cruisePerTick;
        }

        frontDist = Math.min(frontDist + speed, path.maxDist() - 0.001);

        if (cursor.isDeadEnd() && speed <= 1e-4 && distToEnd < 1.5) {
            stopped = true;
            speed = 0.0;
            if (cfg.sounds) {
                Location at = cars.get(0).worldCenter();
                if (at != null) {
                    world.playSound(at, Sound.BLOCK_LAVA_EXTINGUISH, 1.2f, 0.9f);
                }
            }
        }

        advancePhases();
        refreshChunkTickets();
        updateCars();
        collide(cfg);
        effects(cfg);

        if (tickCount % 40 == 0) {
            path.trimBefore(frontDist - totalLength(cfg) - 8.0);
        }
    }

    /** Keep enough sampled track ahead to spot a dead end at cruise speed. */
    private void extendAhead() {
        TrainConfig cfg = plugin.cfg();
        double cruise = cfg.cruisePerTick;
        double lookahead = cruise * cruise / (2 * cfg.decelPerTick) + 4.0;
        int guard = 0;
        while (!cursor.isDeadEnd() && path.maxDist() < frontDist + cruise + lookahead
                && guard++ < EXTEND_GUARD) {
            if (!cursor.extend(path)) {
                break;
            }
        }
    }

    /** Roll the wheel/rod phase counters by however far the train moved this tick. */
    private void advancePhases() {
        double visualSpeed = speed * plugin.cfg().wheelSpinMultiplier;
        driverPhaseDeg = (driverPhaseDeg + Math.toDegrees(visualSpeed / TrainModel.DRIVER_WHEEL_RADIUS)) % 360.0;
        leadingPhaseDeg = (leadingPhaseDeg + Math.toDegrees(visualSpeed / TrainModel.LEADING_WHEEL_RADIUS)) % 360.0;
        bogiePhaseDeg = (bogiePhaseDeg + Math.toDegrees(visualSpeed / TrainModel.BOGIE_WHEEL_RADIUS)) % 360.0;
    }

    private void updateCars() {
        TrainConfig cfg = plugin.cfg();
        CarTransforms.WheelPhases phases =
                new CarTransforms.WheelPhases(driverPhaseDeg, leadingPhaseDeg, bogiePhaseDeg);
        double front = frontDist;
        for (TrainCar car : cars) {
            double len = car.length();
            Vector pf = path.pointAt(front - len * TrainCar.BOGIE_INSET);
            Vector pr = path.pointAt(front - len * (1.0 - TrainCar.BOGIE_INSET));
            double dx = pf.getX() - pr.getX();
            double dy = pf.getY() - pr.getY();
            double dz = pf.getZ() - pr.getZ();
            double horiz = Math.hypot(dx, dz);
            double yaw = horiz < 1e-6 ? car.yaw() : Math.toDegrees(Math.atan2(-dx, dz));
            double pitch = Math.toDegrees(Math.atan2(dy, Math.max(1e-6, horiz)));
            Location center = new Location(world,
                    (pf.getX() + pr.getX()) / 2.0,
                    (pf.getY() + pr.getY()) / 2.0,
                    (pf.getZ() + pr.getZ()) / 2.0);
            car.refresh(center, yaw, pitch, phases);
            front -= len + cfg.carGap;
        }
    }

    private void repairCarModels() {
        for (TrainCar car : cars) {
            car.repairModelIfNeeded();
        }
    }

    // ------------------------------------------------------------- collision

    /**
     * Anything standing ON the track as the train passes gets hit hard: the
     * check is done in car-local coordinates, so "near the rails" is safe and
     * "between the rails" is not.
     */
    private void collide(TrainConfig cfg) {
        if (!cfg.collisionEnabled || speed < cfg.minHitSpeedPerTick) {
            return;
        }
        long now = System.currentTimeMillis();
        for (TrainCar car : cars) {
            Location c = car.worldCenter();
            if (c == null) {
                continue;
            }
            double yawRad = Math.toRadians(car.yaw());
            double fx = -Math.sin(yawRad);
            double fz = Math.cos(yawRad);
            double half = car.length() / 2.0 + 0.8;
            for (Entity e : world.getNearbyEntities(c, half, 3.5, half)) {
                if (!(e instanceof LivingEntity le) || e instanceof ArmorStand) {
                    continue;
                }
                if (!cfg.affectMobs && !(e instanceof Player)) {
                    continue;
                }
                Entity vehicle = e.getVehicle();
                if (vehicle != null && vehicle.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
                    continue; // riding a train
                }
                double ddx = e.getLocation().getX() - c.getX();
                double ddz = e.getLocation().getZ() - c.getZ();
                double dy = e.getLocation().getY() - c.getY();
                double lz = ddx * fx + ddz * fz;              // along the car
                double lx = ddx * -fz + ddz * fx;             // across the car
                if (Math.abs(lx) > TrainModel.WIDTH / 2.0 + cfg.widthMargin
                        || Math.abs(lz) > car.length() / 2.0 + 0.7
                        || dy < -1.2 || dy > 3.4) {
                    continue;
                }
                Long last = hitCooldown.get(e.getUniqueId());
                if (last != null && now - last < cfg.hitCooldownMs) {
                    continue;
                }
                hitCooldown.put(e.getUniqueId(), now);
                le.damage(cfg.collisionDamage);
                Vector kb = new Vector(fx, 0, fz).multiply(cfg.knockback);
                kb.add(new Vector(-fz, 0, fx).multiply(Math.signum(lx) * 0.8));
                kb.setY(cfg.knockbackUp);
                le.setVelocity(kb);
                world.spawnParticle(Particle.CRIT, le.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.15);
                if (cfg.sounds) {
                    world.playSound(le.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 1.4f, 0.55f);
                    world.playSound(le.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.7f);
                }
            }
        }
        if (hitCooldown.size() > 64) {
            long horizon = now - cfg.hitCooldownMs * 4;
            hitCooldown.values().removeIf(t -> t < horizon);
        }
    }

    // --------------------------------------------------------------- effects

    private void effects(TrainConfig cfg) {
        TrainCar loco = cars.get(0);
        if (loco.worldCenter() == null) {
            return;
        }
        if (cfg.smoke && tickCount % 2 == 0) {
            Location chimney = loco.worldPoint(TrainModel.CHIMNEY);
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, chimney, 1, 0.06, 0.05, 0.06, 0.012);
            if (tickCount % 6 == 0) {
                world.spawnParticle(Particle.LARGE_SMOKE, chimney, 2, 0.12, 0.08, 0.12, 0.02);
            }
        }
        if (!cfg.sounds) {
            return;
        }
        // steam chuffs, faster with speed
        int chuffPeriod = (int) Math.max(4, Math.min(14, Math.round(3.2 / Math.max(0.05, speed))));
        if (tickCount % chuffPeriod == 0) {
            world.playSound(loco.worldPoint(TrainModel.CHIMNEY), Sound.BLOCK_FIRE_EXTINGUISH, 0.35f, 1.65f);
        }
        // wheel clatter, wandering down the cars
        if (tickCount % 8 == 0) {
            TrainCar car = cars.get((int) ((tickCount / 8) % cars.size()));
            Location at = car.worldCenter();
            if (at != null) {
                world.playSound(at, Sound.BLOCK_METAL_STEP, 1.2f, 0.55f);
            }
        }
        if (braking && speed > 0.02) {
            if (tickCount % 6 == 0) {
                world.playSound(loco.worldCenter(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 0.55f);
            }
            if (tickCount % 3 == 0) {
                for (TrainCar car : cars) {
                    Location at = car.worldCenter();
                    if (at != null) {
                        world.spawnParticle(Particle.ELECTRIC_SPARK, at.add(0, 0.35, 0), 3, 1.0, 0.15, 1.0, 0.05);
                    }
                }
            }
        }
    }

    /** Whistle + steam burst on spawn and on leaving a fixed dead end. */
    private void departureEffects(boolean big) {
        TrainConfig cfg = plugin.cfg();
        TrainCar loco = cars.get(0);
        Location at = loco.worldCenter() != null ? loco.worldPoint(TrainModel.CHIMNEY) : null;
        if (at == null) {
            return;
        }
        if (cfg.whistle) {
            world.playSound(at, Sound.ITEM_GOAT_HORN_SOUND_0, 3.0f, big ? 0.6f : 0.85f);
            if (big) {
                plugin.getServer().getScheduler().runTaskLater(plugin.bukkitPlugin(), () -> {
                    if (!removed && loco.isValid()) {
                        world.playSound(loco.worldPoint(TrainModel.CHIMNEY),
                                Sound.ITEM_GOAT_HORN_SOUND_0, 3.0f, 0.75f);
                    }
                }, 14L);
            }
        }
        if (cfg.smoke) {
            world.spawnParticle(Particle.CLOUD, at, 20, 0.25, 0.2, 0.25, 0.05);
            world.spawnParticle(Particle.CLOUD, loco.worldPoint(TrainModel.CYLINDER_L), 12, 0.15, 0.1, 0.15, 0.08);
            world.spawnParticle(Particle.CLOUD, loco.worldPoint(TrainModel.CYLINDER_R), 12, 0.15, 0.1, 0.15, 0.08);
        }
        if (cfg.sounds) {
            world.playSound(at, Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.4f);
        }
    }

    // ---------------------------------------------------------------- chunks

    private void refreshChunkTickets() {
        if (!plugin.cfg().keepChunksLoaded) {
            return;
        }
        Set<Long> needed = new HashSet<>();
        TrainConfig cfg = plugin.cfg();
        double front = frontDist;
        for (TrainCar car : cars) {
            double len = car.length();
            Vector pf = path.pointAt(front - len * TrainCar.BOGIE_INSET);
            Vector pr = path.pointAt(front - len * (1.0 - TrainCar.BOGIE_INSET));
            addTicketChunkNear(needed, pf);
            addTicketChunkNear(needed, pr);
            addTicketChunkNear(needed, midpoint(pf, pr));
            front -= len + cfg.carGap;
        }
        double aheadDist = Math.min(frontDist + Math.max(12.0, cfg.cruisePerTick * 30.0), path.maxDist());
        addTicketChunkNear(needed, path.pointAt(aheadDist));

        for (Long key : needed) {
            if (tickets.add(key)) {
                me.bibo.militarycraft.core.util.ChunkTickets.acquire(world, plugin.bukkitPlugin(), (int) (key >> 32), key.intValue());
            }
        }
        tickets.removeIf(key -> {
            if (!needed.contains(key)) {
                me.bibo.militarycraft.core.util.ChunkTickets.release(world, plugin.bukkitPlugin(), (int) (key >> 32), key.intValue());
                return true;
            }
            return false;
        });
    }

    private static Vector midpoint(Vector a, Vector b) {
        return new Vector(
                (a.getX() + b.getX()) / 2.0,
                (a.getY() + b.getY()) / 2.0,
                (a.getZ() + b.getZ()) / 2.0);
    }

    private static void addTicketChunkNear(Set<Long> needed, Vector point) {
        int blockX = (int) Math.floor(point.getX());
        int blockZ = (int) Math.floor(point.getZ());
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        int localX = Math.floorMod(blockX, 16);
        int localZ = Math.floorMod(blockZ, 16);

        int minChunkX = localX < CHUNK_EDGE_PRELOAD_BLOCKS ? chunkX - 1 : chunkX;
        int maxChunkX = localX >= 16 - CHUNK_EDGE_PRELOAD_BLOCKS ? chunkX + 1 : chunkX;
        int minChunkZ = localZ < CHUNK_EDGE_PRELOAD_BLOCKS ? chunkZ - 1 : chunkZ;
        int maxChunkZ = localZ >= 16 - CHUNK_EDGE_PRELOAD_BLOCKS ? chunkZ + 1 : chunkZ;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                needed.add(chunkKey(cx, cz));
            }
        }
    }

    private static long chunkKey(int x, int z) {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }

    // --------------------------------------------------------------- removal

    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        for (TrainCar car : cars) {
            for (Player p : car.passengers()) {
                plugin.trains().grantFallProtection(p);
            }
        }
        for (TrainCar car : cars) {
            Location c = car.worldCenter();
            if (c != null) {
                world.spawnParticle(Particle.POOF, c.add(0, 1.5, 0), 25, 1.2, 1.0, 2.5, 0.02);
            }
            car.remove();
        }
        for (Long key : tickets) {
            me.bibo.militarycraft.core.util.ChunkTickets.release(world, plugin.bukkitPlugin(), (int) (key >> 32), key.intValue());
        }
        tickets.clear();
    }

    // ---------------------------------------------------------------- access

    public UUID id() {
        return id;
    }

    public World world() {
        return world;
    }

    public boolean isRemoved() {
        return removed;
    }

    public boolean isStopped() {
        return stopped;
    }

    public List<TrainCar> cars() {
        return cars;
    }

    public TrainCar car(int index) {
        return index >= 0 && index < cars.size() ? cars.get(index) : null;
    }

    public Location locoCenter() {
        return cars.get(0).worldCenter();
    }

    @Override
    public String type() {
        return "train";
    }

    @Override
    public Entity coreEntity() {
        for (TrainCar car : cars) {
            for (Interaction interaction : car.interactions()) {
                if (interaction != null && interaction.isValid()) {
                    return interaction;
                }
            }
        }
        return null;
    }

    @Override
    public Location location() {
        return locoCenter();
    }

    @Override
    public boolean isActive() {
        return !removed && locoCenter() != null;
    }

    @Override
    public double health() {
        return 1.0;
    }

    @Override
    public double maxHealth() {
        return 1.0;
    }

    @Override
    public boolean damage(double amount) {
        return false;
    }

    @Override
    public double repair(double amount) {
        return 0.0;
    }

    @Override
    public void applyAntiAirHit() {
        // TrainCraft is intentionally indestructible in the original gameplay.
    }

    @Override
    public void applyExplosion(Location loc, double power) {
        // TrainCraft is intentionally indestructible in the original gameplay.
    }

    @Override
    public List<Entity> collisionEntities() {
        List<Entity> hitboxes = new ArrayList<>();
        for (TrainCar car : cars) {
            hitboxes.addAll(car.interactions());
        }
        return List.copyOf(hitboxes);
    }
}
