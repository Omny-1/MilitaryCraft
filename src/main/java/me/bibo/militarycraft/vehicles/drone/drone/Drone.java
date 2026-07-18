package me.bibo.militarycraft.vehicles.drone.drone;

import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import me.bibo.militarycraft.vehicles.drone.DroneRuntime;
import me.bibo.militarycraft.vehicles.drone.config.DroneConfig;
import me.bibo.militarycraft.vehicles.drone.model.DroneModel;
import me.bibo.militarycraft.vehicles.drone.model.DronePart;
import me.bibo.militarycraft.vehicles.drone.model.Transforms;
import me.bibo.militarycraft.vehicles.drone.util.Keys;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single twin-boom kamikaze/strike UAV: a cluster of entities (an invisible
 * core the operator rides, an Interaction hitbox, and a set of block-display
 * model parts) plus its attitude, health, battery and rocket load.
 *
 * <p>The operator rides the core and the UAV flies forward on its own — they only
 * steer with the camera. It carries four one-shot rockets and rams its warhead
 * into targets/terrain. State is mirrored onto the core so it survives reloads.
 */
public final class Drone implements VehicleHandle {

    private final DroneRuntime plugin;
    private final UUID id;
    private final World world;
    private final Location anchor;

    private double yaw;
    private double pitch;
    private double roll;
    private double health;
    private double battery;
    private int rocketAmmo;

    private double velX, velY, velZ; // last movement step (for HUD speed / arm threshold)
    private float propPhase;

    private ArmorStand core;
    private Interaction hitbox;
    private final List<Display> displays = new ArrayList<>();
    private final List<DronePart> partDefs = new ArrayList<>();
    private boolean spawned;

    private UUID driver;
    private int armTimer;
    private int rocketReload;
    private int hardpointIndex;
    private boolean unmanned;
    private int unmannedTicks;
    // True only while WE are removing the rider (destruction / forced eject), so the
    // dismount listener lets that one through while cancelling every other dismount
    // (shift and the spurious client dismounts that happen flying fast across chunks).
    private boolean dismountAllowed;

    // Launch "control stand": a breakable marker placed where the operator boarded,
    // and the spot they are returned to when they leave / it blows up / is broken.
    private ArmorStand controlStand;
    private Location standLocation;

    // Plugin chunk ticket that travels with the UAV while it flies, so flying far
    // from spawn never lets its chunk unload (which would invalidate the entities
    // and drop the operator). Exactly one chunk is force-held at a time.
    private long ticketChunkKey = Long.MIN_VALUE;

    private Quaternionf orientCache;
    private boolean orientDirty = true;

    private double lastX = Double.NaN, lastY, lastZ, lastYaw, lastPitch, lastRoll;
    private float lastProp;

    private Drone(DroneRuntime plugin, UUID id, World world, double x, double y, double z,
                  double yaw, double pitch, double roll, double health, double battery, int rockets) {
        this.plugin = plugin;
        this.id = id;
        this.world = world;
        this.anchor = new Location(world, x, y, z);
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.health = health;
        this.battery = battery;
        this.rocketAmmo = rockets;
    }

    // ----------------------------------------------------------------- factories

    public static Drone create(DroneRuntime plugin, Location at, double yaw) {
        DroneConfig cfg = plugin.config();
        Drone drone = new Drone(plugin, UUID.randomUUID(), at.getWorld(),
                at.getX(), at.getY(), at.getZ(), yaw, 0, 0, cfg.maxHealth, cfg.batteryFlightTicks, cfg.rocketCount);
        drone.spawnEntities();
        return drone;
    }

    public static Drone rehydrate(DroneRuntime plugin, UUID id, List<Entity> entities) {
        DroneConfig cfg = plugin.config();
        List<DronePart> parts = DroneModel.parts();
        ArmorStand core = null;
        Interaction hitbox = null;
        Display[] arr = new Display[parts.size()];

        for (Entity e : entities) {
            String role = e.getPersistentDataContainer().get(Keys.DRONE_PART, PersistentDataType.STRING);
            if (role == null) {
                continue;
            }
            switch (role) {
                case "core" -> {
                    if (e instanceof ArmorStand a) core = a;
                }
                case "hitbox" -> {
                    if (e instanceof Interaction i) hitbox = i;
                }
                case "part" -> {
                    Integer idx = e.getPersistentDataContainer().get(Keys.PART_INDEX, PersistentDataType.INTEGER);
                    if (e instanceof Display d && idx != null && idx >= 0 && idx < arr.length) {
                        arr[idx] = d;
                    }
                }
                default -> {
                }
            }
        }
        if (core == null || hitbox == null) {
            return null;
        }
        for (Display d : arr) {
            if (d == null) {
                return null;
            }
        }

        hitbox.setInteractionHeight(DroneModel.HEIGHT);
        hitbox.setInteractionWidth(DroneModel.WIDTH);

        PersistentDataContainer pdc = core.getPersistentDataContainer();
        double yaw = pdc.getOrDefault(Keys.STATE_YAW, PersistentDataType.DOUBLE, 0.0);
        double pitch = pdc.getOrDefault(Keys.STATE_PITCH, PersistentDataType.DOUBLE, 0.0);
        double roll = pdc.getOrDefault(Keys.STATE_ROLL, PersistentDataType.DOUBLE, 0.0);
        double hp = pdc.getOrDefault(Keys.STATE_HEALTH, PersistentDataType.DOUBLE, cfg.maxHealth);
        double bat = pdc.getOrDefault(Keys.STATE_BATTERY, PersistentDataType.DOUBLE, (double) cfg.batteryFlightTicks);
        int rk = pdc.getOrDefault(Keys.STATE_ROCKETS, PersistentDataType.INTEGER, cfg.rocketCount);
        Location loc = core.getLocation();

        Drone drone = new Drone(plugin, id, core.getWorld(),
                loc.getX(), loc.getY(), loc.getZ(), yaw, pitch, roll, hp, bat, rk);
        drone.core = core;
        drone.hitbox = hitbox;
        int interp = cfg.interpolationTicks;
        for (int i = 0; i < arr.length; i++) {
            arr[i].setTeleportDuration(interp);   // match the transform interpolation
            arr[i].setInterpolationDuration(interp);
            drone.displays.add(arr[i]);
            drone.partDefs.add(parts.get(i));
        }
        drone.spawned = true;
        drone.markClean();
        return drone;
    }

    // ----------------------------------------------------------------- spawning

    private void spawnEntities() {
        DroneConfig cfg = plugin.config();
        Location base = anchor.clone();

        core = world.spawn(base, ArmorStand.class, a -> {
            a.setInvisible(true);
            a.setGravity(false);
            a.setMarker(false);
            a.setSmall(true);
            a.setBasePlate(false);
            a.setArms(false);
            a.setSilent(true);
            a.setInvulnerable(true);
            a.setCollidable(false);
            a.setPersistent(true);
            tagEntity(a, "core", -1);
        });

        hitbox = world.spawn(base, Interaction.class, i -> {
            i.setInteractionWidth(DroneModel.WIDTH);
            i.setInteractionHeight(DroneModel.HEIGHT);
            i.setResponsive(true);
            i.setPersistent(true);
            tagEntity(i, "hitbox", -1);
        });

        List<DronePart> parts = DroneModel.parts();
        for (int index = 0; index < parts.size(); index++) {
            DronePart part = parts.get(index);
            final int idx = index;
            Material mat = part.material(cfg);
            BlockDisplay d = world.spawn(base, BlockDisplay.class, b -> {
                b.setBlock(mat.createBlockData());
                configureDisplay(b, idx);
            });
            displays.add(d);
            partDefs.add(part);
        }

        spawned = true;
        forceRefresh();
        persistState();
    }

    private void configureDisplay(Display d, int index) {
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(2.0f);
        // Position and transform interpolation MUST share the same duration, or the
        // two client lerps "beat" against each other and the model jitters back and
        // forth at speed. We also predict the teleport forward (see refreshModel) so
        // this duration adds no visible lag behind the camera.
        int n = plugin.config().interpolationTicks;
        d.setTeleportDuration(n);
        d.setInterpolationDuration(n);
        d.setPersistent(true);
        tagEntity(d, "part", index);
    }

    private void tagEntity(Entity e, String role, int index) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        pdc.set(Keys.DRONE_ID, PersistentDataType.STRING, id.toString());
        pdc.set(Keys.DRONE_PART, PersistentDataType.STRING, role);
        if (index >= 0) {
            pdc.set(Keys.PART_INDEX, PersistentDataType.INTEGER, index);
        }
        e.addScoreboardTag(Keys.SCOREBOARD_TAG);
    }

    public void persistState() {
        if (core == null || !core.isValid()) {
            return;
        }
        PersistentDataContainer pdc = core.getPersistentDataContainer();
        pdc.set(Keys.STATE_YAW, PersistentDataType.DOUBLE, yaw);
        pdc.set(Keys.STATE_PITCH, PersistentDataType.DOUBLE, pitch);
        pdc.set(Keys.STATE_ROLL, PersistentDataType.DOUBLE, roll);
        pdc.set(Keys.STATE_HEALTH, PersistentDataType.DOUBLE, health);
        pdc.set(Keys.STATE_BATTERY, PersistentDataType.DOUBLE, battery);
        pdc.set(Keys.STATE_ROCKETS, PersistentDataType.INTEGER, rocketAmmo);
    }

    // ----------------------------------------------------------------- rendering

    public Quaternionf orientation() {
        Quaternionf c = orientCache;
        if (c == null || orientDirty) {
            c = Transforms.orientation(yaw, pitch, roll);
            orientCache = c;
            orientDirty = false;
        }
        return c;
    }

    private void markClean() {
        lastX = anchor.getX();
        lastY = anchor.getY();
        lastZ = anchor.getZ();
        lastYaw = yaw;
        lastPitch = pitch;
        lastRoll = roll;
        lastProp = propPhase;
    }

    private void forceRefresh() {
        lastX = Double.NaN;
        refreshModel();
    }

    public void refreshModel() {
        if (!spawned || core == null) {
            return;
        }
        boolean first = Double.isNaN(lastX);
        boolean moved = first
                || Math.abs(lastX - anchor.getX()) > 1e-5
                || Math.abs(lastY - anchor.getY()) > 1e-5
                || Math.abs(lastZ - anchor.getZ()) > 1e-5;
        boolean angles = first
                || Math.abs(lastYaw - yaw) > 1e-4
                || Math.abs(lastPitch - pitch) > 1e-4
                || Math.abs(lastRoll - roll) > 1e-4;
        boolean spun = first || Math.abs(lastProp - propPhase) > 1e-3;
        if (!moved && !angles && !spun) {
            return;
        }

        int interp = plugin.config().interpolationTicks;
        Location base = anchor;
        // The camera (the ridden core) sits at the true anchor. The visual model is
        // teleported to where the drone WILL be in `interp` ticks, so its interp lerp
        // "arrives" exactly on the real position each frame — smooth, and no lag
        // behind the camera (for constant velocity, which steering keeps near-constant).
        Location predicted = moved
                ? new Location(world, anchor.getX() + velX * interp,
                anchor.getY() + velY * interp, anchor.getZ() + velZ * interp)
                : base;
        if (moved) {
            try {
                core.teleport(base, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
            } catch (Exception ignored) {
            }
            hitbox.teleport(base);
        }
        Quaternionf q = orientation();
        for (int i = 0; i < displays.size(); i++) {
            Display d = displays.get(i);
            if (d == null || !d.isValid()) {
                continue;
            }
            if (moved) {
                d.teleport(predicted);
            }
            DronePart part = partDefs.get(i);
            if (angles || (spun && part.spin)) {
                Transformation t = Transforms.forPart(part, q, propPhase);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(interp);
                d.setTransformation(t);
            }
        }
        markClean();
    }

    // ----------------------------------------------------------------- geometry helpers

    public Location localToWorld(Vector3f local) {
        Vector3f off = Transforms.localPointToWorld(local, orientation());
        return new Location(world, anchor.getX() + off.x, anchor.getY() + off.y, anchor.getZ() + off.z);
    }

    public Vector forward() {
        Vector3f f = Transforms.forward(orientation());
        return new Vector(f.x, f.y, f.z);
    }

    public Location nose() {
        return localToWorld(DroneModel.NOSE);
    }

    /** Next rocket hardpoint, cycling for a left/right ripple. */
    public Location nextHardpoint() {
        List<Vector3f> hp = DroneModel.HARDPOINTS;
        Vector3f v = hp.get(hardpointIndex % hp.size());
        hardpointIndex = (hardpointIndex + 1) % hp.size();
        return localToWorld(v);
    }

    public List<Location> exhaustLocations() {
        List<Location> out = new ArrayList<>(DroneModel.EXHAUST.size());
        for (Vector3f r : DroneModel.EXHAUST) {
            out.add(localToWorld(r));
        }
        return out;
    }

    public List<Location> wingtipLocations() {
        List<Location> out = new ArrayList<>(DroneModel.WINGTIPS.size());
        for (Vector3f r : DroneModel.WINGTIPS) {
            out.add(localToWorld(r));
        }
        return out;
    }

    public void setAnchor(double x, double y, double z) {
        anchor.setX(x);
        anchor.setY(y);
        anchor.setZ(z);
    }

    /**
     * Keep the UAV's current chunk force-loaded while it flies (one chunk only, the
     * ticket travels with it). This is async/cheap; we deliberately do NOT call a
     * synchronous getChunkAt here — that risked main-thread stalls / watchdog and
     * was not the cause of the far-flight bug anyway.
     */
    public void ensureChunkLoaded() {
        int cx = anchor.getBlockX() >> 4;
        int cz = anchor.getBlockZ() >> 4;
        long key = (((long) cx) << 32) | (cz & 0xffffffffL);
        if (key != ticketChunkKey) {
            releaseChunkTicket();
            world.addPluginChunkTicket(cx, cz, plugin.bukkitPlugin());
            ticketChunkKey = key;
        }
    }

    /** Drop our force-load ticket so the chunk can unload normally again. */
    public void releaseChunkTicket() {
        if (ticketChunkKey != Long.MIN_VALUE) {
            int ox = (int) (ticketChunkKey >> 32);
            int oz = (int) ticketChunkKey;
            world.removePluginChunkTicket(ox, oz, plugin.bukkitPlugin());
            ticketChunkKey = Long.MIN_VALUE;
        }
    }

    // ----------------------------------------------------------------- combat

    public boolean damage(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0 || !isActive()) {
            return false;
        }
        health -= amount;
        persistState();
        if (health <= 0) {
            destroy(true);
            return true;
        }
        world.spawnParticle(org.bukkit.Particle.CRIT, anchor.clone().add(0, 0.2, 0),
                6, 0.5, 0.3, 0.5, 0.0);
        return false;
    }

    @Override
    public double repair(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0 || !isActive()) {
            return 0.0;
        }
        double before = health;
        health = Math.min(maxHealth(), health + amount);
        double restored = health - before;
        if (restored > 0.0) {
            persistState();
        }
        return restored;
    }

    public void destroy(boolean effects) {
        if (effects && spawned) {
            detonate(anchor.clone().add(0, 0.2, 0));
            return;
        }
        removeEntities();
    }

    /**
     * Kamikaze detonation at an impact point: a big blast plus heavy direct damage
     * to anything caught right in it (so a point-blank hit is always lethal). The
     * explosion also propagates to other vehicle plugins via the vanilla event.
     */
    public void detonate(Location impact) {
        DroneConfig cfg = plugin.config();
        Location at = (impact != null && impact.getWorld() != null) ? impact : anchor.clone().add(0, 0.2, 0);
        if (spawned) {
            // heavy direct damage to nearby living things first (the warhead core)
            double r = Math.max(2.0, cfg.proximityRadius + 1.5);
            UUID immune = plugin.drones().munitionImmunePilot();
            for (Entity e : at.getWorld().getNearbyEntities(at, r, r, r)) {
                if (e instanceof LivingEntity le && !e.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
                    if (driver != null && e.getUniqueId().equals(driver)) {
                        continue;
                    }
                    if (immune != null && e.getUniqueId().equals(immune)) {
                        continue; // the operator who just bailed out of this UAV
                    }
                    le.damage(cfg.directDamage);
                }
            }
            plugin.drones().setInternalExplosion(true);
            try {
                me.bibo.militarycraft.core.combat.Explosions.createExplosion(
                        at.getWorld(), at, cfg.explosionPower, cfg.setFire, cfg.breakBlocks);
            } finally {
                plugin.drones().setInternalExplosion(false);
            }
            plugin.core().combat().explosionDamage(at, cfg.explosionPower, id);
            spawnBlastFx(at);
            if (cfg.debris) {
                flingDebris(at);
            }
            if (cfg.dropItemOnDestroy) {
                world.dropItemNaturally(at, me.bibo.militarycraft.vehicles.drone.items.DroneItem.create(plugin));
            }
        }
        removeEntities();
    }

    private void spawnBlastFx(Location c) {
        World w = c.getWorld();
        if (w == null) {
            return;
        }
        w.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, c, 3, 0.8, 0.6, 0.8, 0);
        w.spawnParticle(org.bukkit.Particle.LARGE_SMOKE, c, 70, 1.8, 1.2, 1.8, 0.06);
        w.spawnParticle(org.bukkit.Particle.FLAME, c, 45, 1.4, 1.0, 1.4, 0.09);
        w.playSound(c, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 5f, 0.9f);
    }

    private void flingDebris(Location c) {
        java.util.Random rng = new java.util.Random();
        Material mat = plugin.config().frameBlock;
        final int debrisLife = 30;
        final long expireAt = world.getFullTime() + debrisLife + 40L;
        for (int i = 0; i < 10; i++) {
            BlockDisplay chunk = world.spawn(c, BlockDisplay.class, b -> {
                b.setBlock(mat.createBlockData());
                b.setBrightness(new Display.Brightness(15, 15));
                b.setPersistent(false);
                Transformation t = b.getTransformation();
                t.getScale().set(0.3f);
                b.setTransformation(t);
                b.addScoreboardTag(Keys.DEBRIS_TAG);
                b.getPersistentDataContainer().set(
                        Keys.DEBRIS_EXPIRE, PersistentDataType.LONG, expireAt);
            });
            Vector v = new Vector(rng.nextGaussian() * 0.35, 0.4 + rng.nextDouble() * 0.5,
                    rng.nextGaussian() * 0.35);
            new org.bukkit.scheduler.BukkitRunnable() {
                int life = debrisLife;
                final Vector vel = v;

                @Override
                public void run() {
                    if (life-- <= 0 || !chunk.isValid()) {
                        chunk.remove();
                        cancel();
                        return;
                    }
                    vel.setY(vel.getY() - 0.04);
                    chunk.teleport(chunk.getLocation().add(vel));
                }
            }.runTaskTimer(plugin.bukkitPlugin(), 1, 1);
        }
    }

    public void removeEntities() {
        dismountAllowed = true; // we're tearing down: let the rider's dismount through
        releaseChunkTicket();
        if (core != null) {
            if (!core.getPassengers().isEmpty()) {
                core.eject();
            }
            core.remove();
        }
        if (hitbox != null) {
            hitbox.remove();
        }
        for (Display d : displays) {
            if (d != null) {
                d.remove();
            }
        }
        displays.clear();
        partDefs.clear();
        spawned = false;
    }

    // ----------------------------------------------------------------- riding

    public boolean isOccupied() {
        return driver != null;
    }

    public void mount(Player player) {
        dismountAllowed = false;
        core.addPassenger(player);
        driver = player.getUniqueId();
        yaw = player.getLocation().getYaw();
        pitch = 0;
        roll = 0;
        velX = velY = velZ = 0;
        unmanned = false;
        unmannedTicks = 0;
        orientDirty = true;
        armTimer = plugin.config().armDelayTicks;
    }

    /** Driver left without us forcing it (vanilla dismount): just forget them. */
    public void onDriverLost() {
        driver = null;
        persistState();
    }

    public void setStand(ArmorStand stand, Location location) {
        this.controlStand = stand;
        this.standLocation = location;
    }

    public Location standLocation() {
        return standLocation;
    }

    /** Remove the control stand entity (the return point itself is kept until reset). */
    public void removeStand() {
        if (controlStand != null) {
            controlStand.remove();
            controlStand = null;
        }
    }

    public boolean isDismountAllowed() {
        return dismountAllowed;
    }

    /** Force the rider off (used before detonation / cleanup). */
    public void eject() {
        dismountAllowed = true;
        if (core != null) {
            core.eject();
            core.setVelocity(new Vector());
        }
        driver = null;
    }

    // ----------------------------------------------------------------- getters / setters

    public UUID id() {
        return id;
    }

    public World world() {
        return world;
    }

    public Location anchor() {
        return anchor;
    }

    public boolean isSpawned() {
        return spawned;
    }

    public boolean isActive() {
        return spawned && core != null && core.isValid();
    }

    public ArmorStand core() {
        return core;
    }

    public Interaction hitbox() {
        return hitbox;
    }

    public UUID driver() {
        return driver;
    }

    public double yaw() {
        return yaw;
    }

    public void setYaw(double v) {
        this.yaw = v;
        this.orientDirty = true;
    }

    public double pitch() {
        return pitch;
    }

    public void setPitch(double v) {
        this.pitch = v;
        this.orientDirty = true;
    }

    public double roll() {
        return roll;
    }

    public void setRoll(double v) {
        this.roll = v;
        this.orientDirty = true;
    }

    public double velX() {
        return velX;
    }

    public double velY() {
        return velY;
    }

    public double velZ() {
        return velZ;
    }

    public void setVelocity(double x, double y, double z) {
        this.velX = x;
        this.velY = y;
        this.velZ = z;
    }

    public double velLength() {
        return Math.sqrt(velX * velX + velY * velY + velZ * velZ);
    }

    public float propPhase() {
        return propPhase;
    }

    public void advanceProp(double deg) {
        this.propPhase = (float) ((propPhase + deg) % 360.0);
    }

    public double health() {
        return health;
    }

    public double maxHealth() {
        return plugin.config().maxHealth;
    }

    @Override
    public String type() {
        return "drone";
    }

    @Override
    public Entity coreEntity() {
        return core;
    }

    @Override
    public Location location() {
        return anchor.clone();
    }

    @Override
    public List<Interaction> collisionEntities() {
        return hitbox == null ? List.of() : List.of(hitbox);
    }

    @Override
    public void applyAntiAirHit() {
        damage(plugin.config().creeperDamage);
    }

    @Override
    public void applyExplosion(Location loc, double power) {
        plugin.drones().applyExplosionTo(this, loc, power);
    }

    @Override
    public boolean handlesBukkitExplosionEvents() {
        return true;
    }

    public double battery() {
        return battery;
    }

    public void setBattery(double v) {
        this.battery = Math.max(0.0, v);
    }

    public int rocketAmmo() {
        return rocketAmmo;
    }

    public void useRocket() {
        if (rocketAmmo > 0) {
            rocketAmmo--;
            persistState();
        }
    }

    public int rocketReload() {
        return rocketReload;
    }

    public void setRocketReload(int v) {
        this.rocketReload = v;
    }

    public void tickRocketReload() {
        if (rocketReload > 0) {
            rocketReload--;
        }
    }

    public int armTimer() {
        return armTimer;
    }

    public void tickArmTimer() {
        if (armTimer > 0) {
            armTimer--;
        }
    }

    public boolean isArmed() {
        return armTimer <= 0;
    }

    public boolean isUnmanned() {
        return unmanned;
    }

    public void setUnmanned(boolean v) {
        this.unmanned = v;
        if (v) {
            this.unmannedTicks = 0;
        }
    }

    public int unmannedTicks() {
        return unmannedTicks;
    }

    public void tickUnmanned() {
        unmannedTicks++;
    }

    public boolean isAirborne() {
        if (world == null) {
            return false;
        }
        int by = (int) Math.floor(anchor.getY() - 0.4);
        return world.getBlockAt(anchor.getBlockX(), by, anchor.getBlockZ()).isPassable();
    }
}
