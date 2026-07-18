package me.bibo.militarycraft.vehicles.jet.jet;

import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import me.bibo.militarycraft.vehicles.jet.JetRuntime;
import me.bibo.militarycraft.vehicles.jet.config.JetConfig;
import me.bibo.militarycraft.vehicles.jet.model.JetModel;
import me.bibo.militarycraft.vehicles.jet.model.JetPart;
import me.bibo.militarycraft.vehicles.jet.model.Transforms;
import me.bibo.militarycraft.vehicles.jet.util.Keys;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
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
 * A single fighter jet: a cluster of entities (an invisible core the pilot rides,
 * an Interaction hitbox, and a set of block-display model parts) plus its current
 * attitude (yaw/pitch/roll), speed and health. Its full state is mirrored onto the
 * core's persistent data so the jet can be rebuilt after a chunk reload or restart.
 */
public final class Jet implements VehicleHandle {

    private final JetRuntime plugin;
    private final UUID id;
    private final World world;
    private final Location anchor; // x/y/z only; orientation lives in yaw/pitch/roll

    private double yaw;     // heading, degrees
    private double pitch;   // Minecraft pitch: nose down (+) / up (-), degrees
    private double roll;    // bank right (+) / left (-), degrees
    private double speed;   // blocks/tick along the nose
    private double health;

    private ArmorStand core;
    private Interaction hitbox;
    private final List<Display> displays = new ArrayList<>();
    private final List<JetPart> partDefs = new ArrayList<>();
    private boolean spawned;

    private UUID driver;
    private int rocketReload;
    private int bombReload;
    private int weaponLock; // ticks after boarding during which weapons are "loading"
    private int hardpointIndex;
    private boolean boosting;
    private int rocketAmmo;
    private int bombAmmo;
    private int rocketRegen;
    private int bombRegen;
    private double boostHeat;
    private boolean boostLocked;
    private boolean unmanned; // pilot bailed mid-air: fly on until it crashes

    // Orientation is read several times per tick (forward, nozzles, every model
    // part); cache the quaternion and rebuild it only when an angle changes.
    private Quaternionf orientCache;
    private boolean orientDirty = true;

    // last-pushed transform state, for cheap dirty checking
    private double lastX = Double.NaN, lastY, lastZ, lastYaw, lastPitch, lastRoll;

    private Jet(JetRuntime plugin, UUID id, World world, double x, double y, double z,
                double yaw, double pitch, double roll, double speed, double health) {
        this.plugin = plugin;
        this.id = id;
        this.world = world;
        this.anchor = new Location(world, x, y, z);
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.speed = speed;
        this.health = health;
        this.rocketAmmo = plugin.config().rocketMagazine;
        this.bombAmmo = plugin.config().bombLoad;
    }

    // ----------------------------------------------------------------- factories

    public static Jet create(JetRuntime plugin, Location at, double yaw) {
        Jet jet = new Jet(plugin, UUID.randomUUID(), at.getWorld(),
                at.getX(), at.getY(), at.getZ(), yaw, 0, 0, 0, plugin.config().maxHealth);
        jet.spawnEntities();
        return jet;
    }

    /**
     * Rebuild a jet from its existing (persistent) entities found in a chunk.
     * Returns null if the group is incomplete/mismatched (caller should clean up).
     */
    public static Jet rehydrate(JetRuntime plugin, UUID id, List<Entity> entities) {
        JetConfig cfg = plugin.config();
        List<JetPart> parts = JetModel.parts(cfg.tailNumber);
        ArmorStand core = null;
        Interaction hitbox = null;
        Display[] arr = new Display[parts.size()];
        int highestPartIndex = -1;

        for (Entity e : entities) {
            String role = e.getPersistentDataContainer().get(Keys.JET_PART, PersistentDataType.STRING);
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
                        highestPartIndex = Math.max(highestPartIndex, idx);
                    }
                }
                default -> {
                }
            }
        }
        if (core == null || hitbox == null) {
            return null;
        }
        for (int i = 0; i <= highestPartIndex; i++) {
            if (arr[i] == null) {
                return null; // incomplete model
            }
        }

        // Bring an already-spawned jet's click hitbox up to the current model size,
        // so jets placed before we enlarged HEIGHT (for the zoomed-out-pilot bomb fix)
        // also get the taller hitbox on the next chunk load / restart.
        hitbox.setInteractionHeight(JetModel.HEIGHT);
        hitbox.setInteractionWidth(JetModel.WIDTH);

        PersistentDataContainer pdc = core.getPersistentDataContainer();
        double yaw = pdc.getOrDefault(Keys.STATE_YAW, PersistentDataType.DOUBLE, 0.0);
        double pitch = pdc.getOrDefault(Keys.STATE_PITCH, PersistentDataType.DOUBLE, 0.0);
        double roll = pdc.getOrDefault(Keys.STATE_ROLL, PersistentDataType.DOUBLE, 0.0);
        double speed = pdc.getOrDefault(Keys.STATE_SPEED, PersistentDataType.DOUBLE, 0.0);
        double hp = pdc.getOrDefault(Keys.STATE_HEALTH, PersistentDataType.DOUBLE, cfg.maxHealth);
        boolean unmanned = pdc.getOrDefault(Keys.STATE_UNMANNED, PersistentDataType.BYTE, (byte) 0) != 0;
        Location loc = core.getLocation();

        Jet jet = new Jet(plugin, id, core.getWorld(),
                loc.getX(), loc.getY(), loc.getZ(), yaw, pitch, roll, speed, hp);
        jet.unmanned = unmanned; // keep gliding after a reload, instead of freezing
        jet.core = core;
        jet.hitbox = hitbox;
        boolean addedParts = false;
        for (int i = 0; i < parts.size(); i++) {
            Display d = arr[i];
            if (d == null) {
                d = jet.spawnPartDisplay(parts.get(i), i, loc);
                addedParts = true;
            }
            jet.displays.add(d);
            jet.partDefs.add(parts.get(i));
        }
        jet.spawned = true;
        jet.forceRefresh();
        if (addedParts) {
            jet.persistState();
        }
        return jet;
    }

    // ----------------------------------------------------------------- spawning

    private void spawnEntities() {
        JetConfig cfg = plugin.config();
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
            i.setInteractionWidth(JetModel.WIDTH);
            i.setInteractionHeight(JetModel.HEIGHT);
            i.setResponsive(true);
            i.setPersistent(true);
            tagEntity(i, "hitbox", -1);
        });

        List<JetPart> parts = JetModel.parts(cfg.tailNumber);
        for (int index = 0; index < parts.size(); index++) {
            JetPart part = parts.get(index);
            Display d = spawnPartDisplay(part, index, base);
            displays.add(d);
            partDefs.add(part);
        }

        spawned = true;
        forceRefresh();
        persistState();
    }

    private Display spawnPartDisplay(JetPart part, int index, Location base) {
        if (part.isText()) {
            return world.spawn(base, TextDisplay.class, t -> {
                t.text(net.kyori.adventure.text.Component.text(part.text));
                t.setBillboard(Display.Billboard.FIXED);
                t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                t.setSeeThrough(false);
                t.setShadowed(false);
                configureDisplay(t, index);
            });
        }
        Material mat = part.material(plugin.config());
        return world.spawn(base, BlockDisplay.class, b -> {
            b.setBlock(mat.createBlockData());
            configureDisplay(b, index);
        });
    }

    private void configureDisplay(Display d, int index) {
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(2.0f);
        // Render smoothing is configurable (model.teleport-duration / -interpolation
        // -duration). The ridden camera SNAPS each tick (RETAIN_PASSENGERS forces an
        // absolute teleport), so a smoothly-lerped model lags behind it and looks
        // like it jerks; teleport-duration 0 snaps the model in lockstep instead.
        d.setTeleportDuration(plugin.config().modelTeleportDuration);
        d.setInterpolationDuration(plugin.config().modelInterpolationDuration);
        d.setPersistent(true);
        tagEntity(d, "part", index);
    }

    private void tagEntity(Entity e, String role, int index) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        pdc.set(Keys.JET_ID, PersistentDataType.STRING, id.toString());
        pdc.set(Keys.JET_PART, PersistentDataType.STRING, role);
        if (index >= 0) {
            pdc.set(Keys.PART_INDEX, PersistentDataType.INTEGER, index);
        }
        e.addScoreboardTag(Keys.SCOREBOARD_TAG);
    }

    /** Mirror the live state onto the core so a reload can restore it. */
    public void persistState() {
        if (core == null || !core.isValid()) {
            return;
        }
        PersistentDataContainer pdc = core.getPersistentDataContainer();
        pdc.set(Keys.STATE_YAW, PersistentDataType.DOUBLE, yaw);
        pdc.set(Keys.STATE_PITCH, PersistentDataType.DOUBLE, pitch);
        pdc.set(Keys.STATE_ROLL, PersistentDataType.DOUBLE, roll);
        pdc.set(Keys.STATE_SPEED, PersistentDataType.DOUBLE, speed);
        pdc.set(Keys.STATE_HEALTH, PersistentDataType.DOUBLE, health);
        pdc.set(Keys.STATE_UNMANNED, PersistentDataType.BYTE, (byte) (unmanned ? 1 : 0));
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
    }

    private void forceRefresh() {
        lastX = Double.NaN;
        refreshModel();
    }

    /**
     * Push only what changed: teleport the entities when the anchor moved, and
     * recompute transformations only when the attitude angles changed. While
     * flying both are true every tick; when parked this is essentially free.
     */
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
        if (!moved && !angles) {
            return;
        }

        // The anchor already carries our exact position (yaw/pitch are 0 on it,
        // which is what every part wants — rotation lives in the transformation).
        // Reusing it avoids allocating a Location every tick.
        Location base = anchor;
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
                d.teleport(base);
            }
            if (angles) {
                Transformation t = Transforms.forPart(partDefs.get(i), q);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(plugin.config().modelInterpolationDuration);
                d.setTransformation(t);
            }
        }
        markClean();
    }

    /**
     * Re-apply the configurable render smoothing to the live display parts, so a
     * {@code /jet reload} takes effect on already-spawned jets without needing a
     * respawn. The new values are used by the next teleport/transformation tick.
     */
    public void applyRenderSettings() {
        int tp = plugin.config().modelTeleportDuration;
        int interp = plugin.config().modelInterpolationDuration;
        for (Display d : displays) {
            if (d != null && d.isValid()) {
                d.setTeleportDuration(tp);
                d.setInterpolationDuration(interp);
            }
        }
        forceRefresh(); // push a fresh transform so the change shows even when parked
    }

    // ----------------------------------------------------------------- geometry helpers

    /** World location of a point given in jet space. */
    public Location localToWorld(Vector3f local) {
        Vector3f off = Transforms.localPointToWorld(local, orientation());
        return new Location(world, anchor.getX() + off.x, anchor.getY() + off.y, anchor.getZ() + off.z);
    }

    /** Unit world vector the nose points along. */
    public Vector forward() {
        Vector3f f = Transforms.forward(orientation());
        return new Vector(f.x, f.y, f.z);
    }

    public Location bombBay() {
        return localToWorld(JetModel.BOMB_BAY);
    }

    /** The next rocket hardpoint, cycling through them for a ripple-fire look. */
    public Location nextHardpoint() {
        List<Vector3f> hp = JetModel.HARDPOINTS;
        Vector3f p = hp.get(hardpointIndex % hp.size());
        hardpointIndex = (hardpointIndex + 1) % hp.size();
        return localToWorld(p);
    }

    public List<Location> nozzleLocations() {
        List<Location> out = new ArrayList<>(JetModel.NOZZLES.size());
        for (Vector3f n : JetModel.NOZZLES) {
            out.add(localToWorld(n));
        }
        return out;
    }

    // ----------------------------------------------------------------- combat

    /** @return true if the jet was destroyed by this hit. */
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
        world.spawnParticle(org.bukkit.Particle.CRIT, anchor.clone().add(0, 0.6, 0),
                8, 1.0, 0.6, 1.0, 0.0);
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
            Location c = anchor.clone().add(0, 0.4, 0);
            plugin.jets().setInternalExplosion(true);
            try {
                world.createExplosion(c, 3.0f, false, false);
            } finally {
                plugin.jets().setInternalExplosion(false);
            }
            world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, c, 4, 1.6, 1.0, 1.6, 0);
            world.spawnParticle(org.bukkit.Particle.LARGE_SMOKE, c, 80, 1.8, 1.2, 1.8, 0.06);
            world.spawnParticle(org.bukkit.Particle.FLAME, c, 40, 1.4, 1.0, 1.4, 0.08);
            world.playSound(c, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 5f, 0.6f);
            if (plugin.config().debris) {
                flingDebris(c);
            }
            if (plugin.config().dropItemOnDestroy) {
                world.dropItemNaturally(c, me.bibo.militarycraft.vehicles.jet.items.JetItem.create(plugin));
            }
        }
        removeEntities();
    }

    /**
     * Kamikaze: the jet slams into terrain. The impact point itself explodes
     * (crater + fire, terrain-breaking), then the jet is destroyed with its own
     * fireball and debris — leaving the consequences of the crash on the world.
     */
    public void destroyByCrash(Location impact) {
        JetConfig cfg = plugin.config();
        if (spawned && impact != null && impact.getWorld() != null) {
            plugin.jets().setInternalExplosion(true);
            try {
                impact.getWorld().createExplosion(impact, cfg.crashExplosionPower,
                        cfg.crashSetFire, cfg.crashBreakBlocks);
            } finally {
                plugin.jets().setInternalExplosion(false);
            }
            impact.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, impact, 4, 1.4, 1.0, 1.4, 0);
            impact.getWorld().spawnParticle(org.bukkit.Particle.LARGE_SMOKE, impact, 90, 2.0, 1.4, 2.0, 0.06);
            impact.getWorld().spawnParticle(org.bukkit.Particle.FLAME, impact, 55, 1.6, 1.0, 1.6, 0.1);
            impact.getWorld().playSound(impact, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 6f, 0.5f);
        }
        destroy(true);
    }

    private void flingDebris(Location c) {
        java.util.Random rng = new java.util.Random();
        Material mat = plugin.config().bodyBlock;
        final int debrisLife = 40;
        // Tagged + given an expiry tick so the manager can clean up any chunk
        // whose animation state is lost by plugin reload, crash or lag.
        final long expireAt = world.getFullTime() + debrisLife;
        for (int i = 0; i < 12; i++) {
            BlockDisplay chunk = world.spawn(c, BlockDisplay.class, b -> {
                b.setBlock(mat.createBlockData());
                b.setBrightness(new Display.Brightness(15, 15));
                b.setPersistent(false);
                Transformation t = b.getTransformation();
                t.getScale().set(0.5f);
                b.setTransformation(t);
                b.addScoreboardTag(Keys.DEBRIS_TAG);
                b.getPersistentDataContainer().set(
                        Keys.DEBRIS_EXPIRE, PersistentDataType.LONG, expireAt);
            });
            Vector v = new Vector(rng.nextGaussian() * 0.4, 0.5 + rng.nextDouble() * 0.5,
                    rng.nextGaussian() * 0.4);
            plugin.jets().trackDebris(chunk, v);
        }
    }

    /** Delete every entity belonging to this jet (does not touch the registry). */
    public void removeEntities() {
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
        core.addPassenger(player);
        driver = player.getUniqueId();
        yaw = player.getLocation().getYaw();
        pitch = 0;
        roll = 0;
        speed = 0;
        unmanned = false;
        orientDirty = true;
        weaponLock = plugin.config().mountGraceTicks; // brief "loading ammo" after boarding
    }

    public void clearDriver() {
        driver = null;
        if (core != null) {
            core.setVelocity(new Vector());
        }
        persistState();
    }

    public void eject() {
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

    public double speed() {
        return speed;
    }

    public void setSpeed(double v) {
        this.speed = v;
    }

    public double health() {
        return health;
    }

    public double maxHealth() {
        return plugin.config().maxHealth;
    }

    @Override
    public String type() {
        return "jet";
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
        me.bibo.militarycraft.vehicles.jet.combat.Explosions.applyBlastTo(
                this, loc, power, plugin.config());
    }

    @Override
    public boolean handlesBukkitExplosionEvents() {
        return true;
    }

    public int weaponLock() {
        return weaponLock;
    }

    public void setWeaponLock(int t) {
        this.weaponLock = t;
    }

    public int rocketReload() {
        return rocketReload;
    }

    public void setRocketReload(int r) {
        this.rocketReload = r;
    }

    public int bombReload() {
        return bombReload;
    }

    public void setBombReload(int r) {
        this.bombReload = r;
    }

    public int rocketAmmo() {
        return rocketAmmo;
    }

    public void useRocket() {
        if (rocketAmmo > 0) {
            rocketAmmo--;
        }
    }

    public int bombAmmo() {
        return bombAmmo;
    }

    public void useBomb() {
        if (bombAmmo > 0) {
            bombAmmo--;
        }
    }

    public double boostHeat() {
        return boostHeat;
    }

    public void setBoostHeat(double v) {
        this.boostHeat = Math.max(0.0, Math.min(1.0, v));
    }

    public boolean isBoostLocked() {
        return boostLocked;
    }

    public void setBoostLocked(boolean v) {
        this.boostLocked = v;
    }

    /** Replenish one rocket / bomb when its regen timer elapses (called each tick). */
    public void regenAmmo(JetConfig cfg) {
        if (rocketAmmo < cfg.rocketMagazine && ++rocketRegen >= cfg.rocketRegenTicks) {
            rocketAmmo++;
            rocketRegen = 0;
        }
        if (bombAmmo < cfg.bombLoad && ++bombRegen >= cfg.bombRegenTicks) {
            bombAmmo++;
            bombRegen = 0;
        }
    }

    public boolean isBoosting() {
        return boosting;
    }

    public void setBoosting(boolean b) {
        this.boosting = b;
    }

    public boolean isUnmanned() {
        return unmanned;
    }

    public void setUnmanned(boolean v) {
        this.unmanned = v;
        persistState(); // write immediately so a crash mid-glide isn't lost
    }

    /** True when there is no solid block just beneath the jet (it's flying). */
    public boolean isAirborne() {
        if (world == null) {
            return false;
        }
        int by = (int) Math.floor(anchor.getY() - 0.5);
        return world.getBlockAt(anchor.getBlockX(), by, anchor.getBlockZ()).isPassable();
    }
}
