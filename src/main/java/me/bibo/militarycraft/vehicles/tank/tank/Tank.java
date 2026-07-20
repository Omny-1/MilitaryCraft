package me.bibo.militarycraft.vehicles.tank.tank;

import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import me.bibo.militarycraft.vehicles.tank.TankRuntime;
import me.bibo.militarycraft.vehicles.tank.config.TankConfig;
import me.bibo.militarycraft.vehicles.tank.model.PartGroup;
import me.bibo.militarycraft.vehicles.tank.model.TankModel;
import me.bibo.militarycraft.vehicles.tank.model.TankPart;
import me.bibo.militarycraft.vehicles.tank.model.Transforms;
import me.bibo.militarycraft.vehicles.tank.util.Keys;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A single tank: a cluster of entities (an invisible seat the driver rides, an
 * Interaction hitbox, and a set of block-display model parts) plus its current
 * articulation, speed and health. Its full state is mirrored onto the seat's
 * persistent data so the tank can be rebuilt after a chunk reload or restart.
 */
public final class Tank implements VehicleHandle {

    private static final int CENTER_HITBOX_INDEX = 1;
    private static final float[] HITBOX_Z_OFFSETS = {
            -TankModel.LENGTH / 3.0f,
            0.0f,
            TankModel.LENGTH / 3.0f
    };

    private final TankRuntime plugin;
    private final UUID id;
    private final World world;
    private final Location anchor; // x/y/z only; yaw/pitch unused

    private double hullYaw;
    private double turretYaw;
    private double barrelPitch;
    private double speed;
    private double verticalVelocity;
    private double health;

    private ArmorStand seat;
    private Interaction hitbox;
    private final List<Interaction> hitboxes = new ArrayList<>();
    private final List<Display> displays = new ArrayList<>();
    private final List<TankPart> partDefs = new ArrayList<>();
    private boolean spawned;

    private UUID driver;
    private int reload;
    private int overheatSwings;
    private int weaponLock; // ticks after boarding during which the gun is "loading"
    private boolean submerged; // whole body underwater -> stalled + slowly flooding
    private int submergedTicks;
    private boolean stateDirty;
    private int persistCooldown;
    private final Map<UUID, Long> ramCooldown = new HashMap<>();

    // last-pushed transform state, for cheap dirty checking
    private double lastX = Double.NaN, lastY, lastZ, lastHull, lastTurret, lastPitch;

    private Tank(TankRuntime plugin, UUID id, World world, double x, double y, double z,
                 double hullYaw, double turretYaw, double barrelPitch, double health) {
        this.plugin = plugin;
        this.id = id;
        this.world = world;
        this.anchor = new Location(world, x, y, z);
        this.hullYaw = hullYaw;
        this.turretYaw = turretYaw;
        this.barrelPitch = barrelPitch;
        this.health = health;
    }

    // ----------------------------------------------------------------- factories

    public static Tank create(TankRuntime plugin, Location at, double yaw) {
        Tank tank = new Tank(plugin, UUID.randomUUID(), at.getWorld(),
                at.getX(), at.getY(), at.getZ(), yaw, yaw, 0, plugin.config().maxHealth);
        tank.spawnEntities();
        return tank;
    }

    /**
     * Rebuild a tank from its existing (persistent) entities found in a chunk.
     * Returns null if the group is incomplete/mismatched (caller should clean up).
     */
    public static Tank rehydrate(TankRuntime plugin, UUID id, List<Entity> entities) {
        TankConfig cfg = plugin.config();
        List<TankPart> parts = TankModel.parts(cfg.turretNumber);
        ArmorStand seat = null;
        List<Interaction> allHitboxes = new ArrayList<>();
        Interaction[] hitboxArr = new Interaction[HITBOX_Z_OFFSETS.length];
        Display[] arr = new Display[parts.size()];

        for (Entity e : entities) {
            String role = e.getPersistentDataContainer().get(Keys.TANK_PART, PersistentDataType.STRING);
            if (role == null) {
                continue;
            }
            switch (role) {
                case "seat" -> {
                    if (e instanceof ArmorStand a) seat = a;
                }
                case "hitbox" -> {
                    if (e instanceof Interaction i) {
                        allHitboxes.add(i);
                        Integer idx = e.getPersistentDataContainer().get(Keys.PART_INDEX, PersistentDataType.INTEGER);
                        if (idx != null && idx >= 0 && idx < hitboxArr.length) {
                            hitboxArr[idx] = i;
                        }
                    }
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
        Interaction anchorHitbox = hitboxArr[CENTER_HITBOX_INDEX] != null
                ? hitboxArr[CENTER_HITBOX_INDEX]
                : (!allHitboxes.isEmpty() ? allHitboxes.get(0) : null);
        if (seat == null || anchorHitbox == null) {
            return null;
        }
        for (Display d : arr) {
            if (d == null) {
                return null; // incomplete model
            }
        }

        PersistentDataContainer pdc = seat.getPersistentDataContainer();
        double hull = pdc.getOrDefault(Keys.STATE_HULL_YAW, PersistentDataType.DOUBLE, 0.0);
        double turret = pdc.getOrDefault(Keys.STATE_TURRET_YAW, PersistentDataType.DOUBLE, hull);
        double pitch = pdc.getOrDefault(Keys.STATE_BARREL_PITCH, PersistentDataType.DOUBLE, 0.0);
        double hp = pdc.getOrDefault(Keys.STATE_HEALTH, PersistentDataType.DOUBLE, plugin.config().maxHealth);
        // Anchor (ground point) comes from the hitbox, which sits at the anchor;
        // the seat is offset upward by seat-height, so it must not define the anchor.
        Location loc = anchorHitbox.getLocation();

        Tank tank = new Tank(plugin, id, anchorHitbox.getWorld(),
                loc.getX(), loc.getY(), loc.getZ(), hull, turret, pitch, hp);
        tank.seat = seat;
        boolean completeHitboxes = true;
        for (Interaction box : hitboxArr) {
            if (box == null) {
                completeHitboxes = false;
                break;
            }
        }
        if (completeHitboxes) {
            for (Interaction box : hitboxArr) {
                tank.hitboxes.add(box);
            }
            tank.hitbox = hitboxArr[CENTER_HITBOX_INDEX];
        } else {
            for (Interaction box : allHitboxes) {
                if (box != null) {
                    box.remove();
                }
            }
            tank.buildHitboxes(loc);
        }
        for (int i = 0; i < arr.length; i++) {
            tank.displays.add(arr[i]);
            tank.partDefs.add(parts.get(i));
        }
        tank.spawned = true;
        tank.markClean();
        tank.reapplyAppearance(cfg); // pull colours/number from the current config
        return tank;
    }

    // ----------------------------------------------------------------- spawning

    private void spawnEntities() {
        TankConfig cfg = plugin.config();
        Location base = anchor.clone();
        Location seatBase = base.clone().add(0, cfg.seatHeight, 0);

        seat = world.spawn(seatBase, ArmorStand.class, a -> {
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
            tagEntity(a, "seat", -1);
        });

        buildHitboxes(base);

        List<TankPart> parts = TankModel.parts(cfg.turretNumber);
        for (int index = 0; index < parts.size(); index++) {
            TankPart part = parts.get(index);
            final int idx = index;
            Display d;
            if (part.isText()) {
                d = world.spawn(base, TextDisplay.class, t -> {
                    t.text(net.kyori.adventure.text.Component.text(part.text));
                    t.setBillboard(Display.Billboard.FIXED);
                    t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                    t.setSeeThrough(false);
                    t.setShadowed(false);
                    configureDisplay(t, idx);
                });
            } else {
                Material mat = part.material(cfg);
                d = world.spawn(base, BlockDisplay.class, b -> {
                    b.setBlock(mat.createBlockData());
                    configureDisplay(b, idx);
                });
            }
            displays.add(d);
            partDefs.add(part);
        }

        spawned = true;
        forceRefresh();
        persistState();
    }

    private void buildHitboxes(Location base) {
        hitboxes.clear();
        for (int i = 0; i < HITBOX_Z_OFFSETS.length; i++) {
            final int idx = i;
            Interaction box = world.spawn(hitboxLocation(base, idx), Interaction.class, in -> {
                in.setInteractionWidth(TankModel.WIDTH);
                in.setInteractionHeight(TankModel.HEIGHT);
                in.setResponsive(true);
                in.setPersistent(true);
                tagEntity(in, "hitbox", idx);
            });
            hitboxes.add(box);
        }
        hitbox = hitboxes.get(CENTER_HITBOX_INDEX);
    }

    private Location hitboxLocation(Location base, int index) {
        Vector3f off = Transforms.localPointToWorld(
                new Vector3f(0f, 0f, HITBOX_Z_OFFSETS[index]),
                PartGroup.HULL, hullYaw, turretYaw, barrelPitch);
        return new Location(world, base.getX() + off.x, base.getY() + off.y, base.getZ() + off.z);
    }

    private void configureDisplay(Display d, int index) {
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(2.2f);
        d.setTeleportDuration(2);
        d.setInterpolationDuration(2);
        d.setPersistent(true);
        tagEntity(d, "part", index);
    }

    private void tagEntity(Entity e, String role, int index) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        pdc.set(Keys.TANK_ID, PersistentDataType.STRING, id.toString());
        pdc.set(Keys.TANK_PART, PersistentDataType.STRING, role);
        if (index >= 0) {
            pdc.set(Keys.PART_INDEX, PersistentDataType.INTEGER, index);
        }
        e.addScoreboardTag(Keys.SCOREBOARD_TAG);
    }

    /** Mirror the live state onto the seat so a reload can restore it. */
    public void persistState() {
        if (seat == null || !seat.isValid()) {
            return;
        }
        PersistentDataContainer pdc = seat.getPersistentDataContainer();
        pdc.set(Keys.STATE_HULL_YAW, PersistentDataType.DOUBLE, hullYaw);
        pdc.set(Keys.STATE_TURRET_YAW, PersistentDataType.DOUBLE, turretYaw);
        pdc.set(Keys.STATE_BARREL_PITCH, PersistentDataType.DOUBLE, barrelPitch);
        pdc.set(Keys.STATE_HEALTH, PersistentDataType.DOUBLE, health);
        stateDirty = false;
        persistCooldown = 0;
    }

    public void tickPersist() {
        if (!stateDirty || !isActive()) {
            return;
        }
        if (++persistCooldown >= 20) {
            persistState();
        }
    }

    private void markStateDirty() {
        stateDirty = true;
    }

    // ----------------------------------------------------------------- rendering

    private void markClean() {
        lastX = anchor.getX();
        lastY = anchor.getY();
        lastZ = anchor.getZ();
        lastHull = hullYaw;
        lastTurret = turretYaw;
        lastPitch = barrelPitch;
    }

    private void forceRefresh() {
        lastX = Double.NaN;
        refreshModel();
    }

    /**
     * Push only what changed: teleport the entities when the anchor moved, and
     * recompute transformations only for the sub-assemblies whose angles changed.
     *
     * <p>A part's transform depends only on certain angles: HULL parts on the hull
     * yaw; TURRET parts on the hull and turret yaw; BARREL parts on those plus the
     * barrel pitch. While the driver merely aims (hull still), the hull, tracks and
     * wheels - the bulk of the model - skip {@code setTransformation} entirely, which
     * spares a transform packet per part every tick. A teleport keeps each part's
     * existing transform, so when only the anchor moves we re-push nothing.</p>
     */
    public void refreshModel() {
        if (!spawned || seat == null) {
            return;
        }
        boolean first = Double.isNaN(lastX);
        boolean moved = first
                || Math.abs(lastX - anchor.getX()) > 1e-5
                || Math.abs(lastY - anchor.getY()) > 1e-5
                || Math.abs(lastZ - anchor.getZ()) > 1e-5;
        boolean hullChanged = first || Math.abs(lastHull - hullYaw) > 1e-4;
        boolean turretChanged = hullChanged || Math.abs(lastTurret - turretYaw) > 1e-4;
        boolean pitchChanged = turretChanged || Math.abs(lastPitch - barrelPitch) > 1e-4;
        if (!moved && !pitchChanged) {
            return;
        }

        Location base = new Location(world, anchor.getX(), anchor.getY(), anchor.getZ());
        if (moved) {
            // The seat (and thus the driver's camera) rides above the hull so a
            // big tank doesn't bury the view inside its own body.
            Location seatBase = new Location(world, anchor.getX(),
                    anchor.getY() + plugin.config().seatHeight, anchor.getZ());
            try {
                seat.teleport(seatBase, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
            } catch (Exception ex) {
                plugin.getLogger().fine("Tank seat teleport failed: " + ex.getMessage());
            }
        }
        if (moved || hullChanged) {
            for (int i = 0; i < hitboxes.size(); i++) {
                Interaction box = hitboxes.get(i);
                if (box == null || !box.isValid()) {
                    continue;
                }
                box.teleport(hitboxLocation(base, i));
            }
        }
        for (int i = 0; i < displays.size(); i++) {
            Display d = displays.get(i);
            if (d == null || !d.isValid()) {
                continue;
            }
            if (moved) {
                d.teleport(base);
            }
            TankPart part = partDefs.get(i);
            boolean needTransform = switch (part.group) {
                case HULL -> hullChanged;
                case TURRET -> turretChanged;
                case BARREL -> pitchChanged;
            };
            if (needTransform) {
                Transformation t = Transforms.forPart(part, hullYaw, turretYaw, barrelPitch);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(2);
                d.setTransformation(t);
            }
        }
        markClean();
    }

    /**
     * Re-apply the configured block materials (and turret number) to every part
     * from the current config. Block colour is baked into each display entity at
     * spawn, so without this a tank rebuilt from saved entities keeps its old
     * colour forever; calling this on rehydrate and on /tank reload lets a config
     * recolour reach existing tanks too.
     */
    public void reapplyAppearance(TankConfig cfg) {
        for (int i = 0; i < displays.size(); i++) {
            Display d = displays.get(i);
            if (d == null || !d.isValid()) {
                continue;
            }
            TankPart part = partDefs.get(i);
            if (part.isText()) {
                if (d instanceof TextDisplay td) {
                    td.text(net.kyori.adventure.text.Component.text(
                            cfg.turretNumber == null ? "" : cfg.turretNumber));
                }
            } else if (d instanceof BlockDisplay bd) {
                bd.setBlock(part.material(cfg).createBlockData());
            }
        }
    }

    // ----------------------------------------------------------------- water

    /**
     * Recompute whether the whole tank is under water (the centre column is water
     * from the body up past the turret). Resets the flood timer when it surfaces.
     */
    public boolean refreshSubmerged() {
        submerged = spawned && fullyUnderWater();
        if (!submerged) {
            submergedTicks = 0;
        }
        return submerged;
    }

    public boolean isSubmerged() {
        return submerged;
    }

    private boolean fullyUnderWater() {
        int bx = anchor.getBlockX();
        int bz = anchor.getBlockZ();
        // low body, mid hull, just under the turret top: all water => fully sunk.
        double[] heights = {0.6, TankModel.HEIGHT * 0.45, TankModel.HEIGHT * 0.85};
        for (double dy : heights) {
            org.bukkit.block.Block b = world.getBlockAt(bx, (int) Math.floor(anchor.getY() + dy), bz);
            if (!isWater(b)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWater(org.bukkit.block.Block b) {
        if (b.getType() == Material.WATER) {
            return true;
        }
        return b.getBlockData() instanceof org.bukkit.block.data.Waterlogged w && w.isWaterlogged();
    }

    /**
     * Tick while fully submerged: the engine has stalled (movement is blocked in
     * the drive loop) and the hull slowly floods, taking damage about once a
     * second until it breaks apart. Emits bubbles and a stalling sputter.
     */
    public void tickWater(TankConfig cfg) {
        submergedTicks++;
        if (submergedTicks % 6 == 0) {
            world.spawnParticle(org.bukkit.Particle.BUBBLE_COLUMN_UP,
                    anchor.clone().add(0, TankModel.HEIGHT * 0.5, 0),
                    12, 1.4, 1.8, 1.4, 0.02);
        }
        if (submergedTicks % 20 == 0) {
            world.playSound(anchor, org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.5f);
            double dps = maxHealth() * cfg.drownDamagePercent / 100.0;
            if (dps > 0) {
                damage(dps); // ~once per second
            }
        }
    }

    // ----------------------------------------------------------------- combat

    public Location muzzleLocation() {
        Vector3f off = Transforms.localPointToWorld(TankModel.MUZZLE_TIP,
                PartGroup.BARREL, hullYaw, turretYaw, barrelPitch);
        return new Location(world, anchor.getX() + off.x, anchor.getY() + off.y, anchor.getZ() + off.z);
    }

    public Vector barrelDirection() {
        Vector3f dir = Transforms.barrelDirection(turretYaw, barrelPitch);
        return new Vector(dir.x, dir.y, dir.z);
    }

    /** @return true if the tank was destroyed by this hit. */
    public boolean damage(double amount) {
        if (!Double.isFinite(amount) || amount <= 0 || !isActive()) {
            return false;
        }
        health = Math.max(0.0, health - amount);
        markStateDirty();
        if (plugin.config().debug) {
            plugin.getLogger().info("[debug] tank -"
                    + String.format(java.util.Locale.US, "%.1f", amount) + " HP -> "
                    + String.format(java.util.Locale.US, "%.1f", health));
        }
        if (health <= 0) {
            destroy(true);
            return true;
        }
        world.spawnParticle(org.bukkit.Particle.CRIT, anchor.clone().add(0, 1.4, 0),
                8, 0.8, 0.6, 0.8, 0.0);
        return false;
    }

    @Override
    public double repair(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0 || !isActive()) {
            return 0.0;
        }
        double before = health;
        health = Math.min(maxHealth(), health + amount);
        double restored = Math.max(0.0, health - before);
        if (restored > 0.0) {
            markStateDirty();
        }
        return restored;
    }

    public void tickDamageEffects(long tickCounter, TankConfig cfg) {
        if (!cfg.damageSmoke || !isActive() || maxHealth() <= 0) {
            return;
        }
        double ratio = health / maxHealth();
        if (ratio > 0.5) {
            return;
        }
        int period = ratio > 0.25 ? 30 : 12;
        if (tickCounter % period != 0) {
            return;
        }
        Location vent = anchor.clone().add(0, TankModel.HEIGHT * 0.72, 0);
        world.spawnParticle(org.bukkit.Particle.LARGE_SMOKE, vent,
                ratio > 0.25 ? 2 : 4, 0.45, 0.25, 0.45, 0.015);
        if (ratio <= 0.25) {
            world.spawnParticle(org.bukkit.Particle.SMOKE, vent, 5, 0.5, 0.25, 0.5, 0.02);
            world.spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK, vent, 2, 0.3, 0.2, 0.3, 0.01);
        }
        if (ratio <= 0.1 && tickCounter % 60 == 0) {
            world.playSound(vent, org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1.8f);
        }
    }

    public void destroy(boolean effects) {
        if (effects && spawned) {
            Location c = anchor.clone().add(0, 1.0, 0);
            plugin.tanks().setInternalExplosion(true);
            try {
                world.createExplosion(c, 2.5f, false, false);
            } finally {
                plugin.tanks().setInternalExplosion(false);
            }
            world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, c, 3, 1.2, 0.8, 1.2, 0);
            world.spawnParticle(org.bukkit.Particle.LARGE_SMOKE, c, 60, 1.4, 1.0, 1.4, 0.05);
            world.playSound(c, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.7f);
            if (plugin.config().debris) {
                flingDebris(c);
            }
            if (plugin.config().dropItemOnDestroy) {
                world.dropItemNaturally(c, me.bibo.militarycraft.vehicles.tank.items.TankItem.create(plugin));
            }
        }
        removeEntities();
    }

    private void flingDebris(Location c) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Material mat = plugin.config().hullBlock;
        for (int i = 0; i < 8; i++) {
            BlockDisplay chunk = world.spawn(c, BlockDisplay.class, b -> {
                b.setBlock(mat.createBlockData());
                b.setBrightness(new Display.Brightness(15, 15));
                b.setPersistent(false);
                Transformation t = b.getTransformation();
                t.getScale().set(0.4f);
                b.setTransformation(t);
            });
            Vector v = new Vector(rng.nextGaussian() * 0.3, 0.4 + rng.nextDouble() * 0.4,
                    rng.nextGaussian() * 0.3);
            new org.bukkit.scheduler.BukkitRunnable() {
                int life = 30;
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

    /** Delete every entity belonging to this tank (does not touch the registry). */
    public void removeEntities() {
        if (seat != null) {
            if (!seat.getPassengers().isEmpty()) {
                seat.eject();
            }
            seat.remove();
        }
        for (Interaction box : hitboxes) {
            if (box != null) {
                box.remove();
            }
        }
        hitboxes.clear();
        hitbox = null;
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

    public boolean mount(Player player) {
        if (seat == null || !seat.isValid() || !seat.addPassenger(player)) {
            return false;
        }
        driver = player.getUniqueId();
        turretYaw = player.getLocation().getYaw();
        weaponLock = plugin.config().mountGraceTicks; // brief "loading ammo" after boarding
        markStateDirty();
        return true;
    }

    public void clearDriver() {
        driver = null;
        persistState();
    }

    public void eject() {
        if (seat != null) {
            seat.eject();
        }
        driver = null;
    }

    public boolean tryRam(UUID victim, long nowMs, long cooldownMs) {
        Long last = ramCooldown.get(victim);
        if (last != null && nowMs - last < cooldownMs) {
            return false;
        }
        ramCooldown.put(victim, nowMs);
        if (ramCooldown.size() > 64) {
            ramCooldown.entrySet().removeIf(e -> nowMs - e.getValue() > cooldownMs * 4);
        }
        return true;
    }

    // ----------------------------------------------------------------- getters

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
        return spawned && seat != null && seat.isValid();
    }

    public ArmorStand seat() {
        return seat;
    }

    public Interaction hitbox() {
        return hitbox;
    }

    public List<Interaction> hitboxes() {
        return hitboxes;
    }

    public UUID driver() {
        return driver;
    }

    public double hullYaw() {
        return hullYaw;
    }

    public void setHullYaw(double v) {
        if (Math.abs(this.hullYaw - v) > 1e-4) {
            markStateDirty();
        }
        this.hullYaw = v;
    }

    public double turretYaw() {
        return turretYaw;
    }

    public void setTurretYaw(double v) {
        if (Math.abs(this.turretYaw - v) > 1e-4) {
            markStateDirty();
        }
        this.turretYaw = v;
    }

    public double barrelPitch() {
        return barrelPitch;
    }

    public void setBarrelPitch(double v) {
        if (Math.abs(this.barrelPitch - v) > 1e-4) {
            markStateDirty();
        }
        this.barrelPitch = v;
    }

    public double speed() {
        return speed;
    }

    public void setSpeed(double v) {
        this.speed = v;
    }

    public double verticalVelocity() {
        return verticalVelocity;
    }

    public void setVerticalVelocity(double v) {
        this.verticalVelocity = v;
    }

    public double health() {
        return health;
    }

    public double maxHealth() {
        return plugin.config().maxHealth;
    }

    @Override
    public String type() {
        return "tank";
    }

    @Override
    public Entity coreEntity() {
        return seat;
    }

    @Override
    public Location location() {
        return anchor.clone();
    }

    @Override
    public List<Interaction> collisionEntities() {
        return List.copyOf(hitboxes);
    }

    @Override
    public void applyAntiAirHit() {
        damage(plugin.config().creeperDamage);
    }

    @Override
    public void applyExplosion(Location loc, double power) {
        me.bibo.militarycraft.vehicles.tank.combat.Explosions.applyBlastTo(
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

    public int reload() {
        return reload;
    }

    public void setReload(int r) {
        this.reload = r;
    }

    public int overheatSwings() {
        return overheatSwings;
    }

    public void setOverheatSwings(int n) {
        this.overheatSwings = n;
    }
}
