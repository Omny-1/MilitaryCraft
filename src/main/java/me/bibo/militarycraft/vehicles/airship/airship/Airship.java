package me.bibo.militarycraft.vehicles.airship.airship;

import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import me.bibo.militarycraft.vehicles.airship.AirshipRuntime;
import me.bibo.militarycraft.vehicles.airship.config.AirshipConfig;
import me.bibo.militarycraft.vehicles.airship.model.AirshipModel;
import me.bibo.militarycraft.vehicles.airship.model.Part;
import me.bibo.militarycraft.vehicles.airship.model.Transforms;
import me.bibo.militarycraft.vehicles.airship.util.DriverCloak;
import me.bibo.militarycraft.vehicles.airship.util.Keys;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A single giant airship: an invisible core the pilot rides (the helm + the
 * position anchor), a set of invisible passenger seats, several Interaction
 * hitboxes spread over the gondola and the envelope so the whole craft is
 * clickable/hittable, and a set of block-display model parts. Up to
 * {@code seats.capacity} players ride; only the FIRST to board (the driver)
 * flies it — the rest are passengers. Full state is mirrored onto the core's
 * persistent data so the airship survives chunk reloads and restarts.
 */
public final class Airship implements VehicleHandle {

    private static final double MOVE_EPSILON = 0.01;
    private static final double ANGLE_EPSILON = 1e-4;

    private final AirshipRuntime plugin;
    private final UUID id;
    private final World world;
    private final Location anchor; // gondola helm position; orientation in yaw/roll

    private double yaw;     // heading, degrees
    private double pitch;   // gentle visual nose tilt, degrees
    private double roll;    // gentle visual bank, degrees
    private double speed;   // horizontal blocks/tick along the nose
    private double vSpeed;  // vertical blocks/tick (+ up)
    private double health;
    private float propellerAngle;

    private ArmorStand core;
    private final List<ArmorStand> seats = new ArrayList<>();
    private final List<Interaction> hitboxes = new ArrayList<>();
    private final List<Display> displays = new ArrayList<>();
    private final List<Part> partDefs = new ArrayList<>();
    private boolean spawned;

    private UUID driver;     // the pilot (first to board), rides the core
    private UUID owner;      // who placed it (for per-player limits)
    private int bombCooldown;
    private int weaponLock; // ticks after boarding during which bombs are "loading"
    private int bombAmmo;
    private int bombRegen;
    private double burnerHeat;
    private boolean burnerLocked;
    private boolean unmanned; // pilot left while aloft: drift down gently and settle

    // Orientation is read several times per tick; cache it and rebuild only on change.
    private Quaternionf orientCache;
    private boolean orientDirty = true;

    // last-pushed transform state, for cheap dirty checking
    private double lastX = Double.NaN, lastY, lastZ, lastYaw, lastPitch, lastRoll;

    private Airship(AirshipRuntime plugin, UUID id, World world, double x, double y, double z,
                    double yaw, double roll, double speed, double vSpeed, double health) {
        this.plugin = plugin;
        this.id = id;
        this.world = world;
        this.anchor = new Location(world, x, y, z);
        this.yaw = yaw;
        this.roll = roll;
        this.speed = speed;
        this.vSpeed = vSpeed;
        this.health = health;
        this.bombAmmo = plugin.config().bombLoad;
    }

    // ----------------------------------------------------------------- factories

    public static Airship create(AirshipRuntime plugin, Location at, double yaw, UUID owner) {
        Airship ship = new Airship(plugin, UUID.randomUUID(), at.getWorld(),
                at.getX(), at.getY(), at.getZ(), yaw, 0, 0, 0, plugin.config().maxHealth);
        ship.owner = owner;
        ship.spawnEntities();
        return ship;
    }

    /**
     * Rebuild an airship from its existing (persistent) entities found in a chunk.
     * Robust: only the core is mandatory; seats, hitboxes and model displays are
     * rebuilt from the current definition if their saved set no longer matches
     * (e.g. the model/config changed), instead of deleting the whole airship.
     */
    public static Airship rehydrate(AirshipRuntime plugin, UUID id, List<Entity> entities) {
        AirshipConfig cfg = plugin.config();
        List<Part> parts = AirshipModel.parts(cfg.roundedEnvelope, cfg.shipName);

        ArmorStand core = null;
        Map<Integer, Display> partByIndex = new HashMap<>();
        List<Display> allParts = new ArrayList<>();
        Map<Integer, Interaction> hbByIndex = new HashMap<>();
        List<Interaction> allHitboxes = new ArrayList<>();
        Map<Integer, ArmorStand> seatByIndex = new HashMap<>();
        List<ArmorStand> allSeats = new ArrayList<>();

        for (Entity e : entities) {
            String role = e.getPersistentDataContainer().get(Keys.SHIP_PART, PersistentDataType.STRING);
            if (role == null) {
                continue;
            }
            Integer idx = e.getPersistentDataContainer().get(Keys.PART_INDEX, PersistentDataType.INTEGER);
            switch (role) {
                case "core" -> {
                    if (e instanceof ArmorStand a) core = a;
                }
                case "seat" -> {
                    if (e instanceof ArmorStand a) {
                        allSeats.add(a);
                        if (idx != null && idx >= 0) seatByIndex.putIfAbsent(idx, a);
                    }
                }
                case "hitbox" -> {
                    if (e instanceof Interaction in) {
                        allHitboxes.add(in);
                        if (idx != null && idx >= 0) hbByIndex.putIfAbsent(idx, in);
                    }
                }
                case "part" -> {
                    if (e instanceof Display d) {
                        allParts.add(d);
                        if (idx != null && idx >= 0) partByIndex.putIfAbsent(idx, d);
                    }
                }
                default -> {
                }
            }
        }
        if (core == null) {
            return null; // the anchor is gone — drop whatever is left of the group
        }

        PersistentDataContainer pdc = core.getPersistentDataContainer();
        double yaw = pdc.getOrDefault(Keys.STATE_YAW, PersistentDataType.DOUBLE, 0.0);
        double roll = pdc.getOrDefault(Keys.STATE_ROLL, PersistentDataType.DOUBLE, 0.0);
        double speed = pdc.getOrDefault(Keys.STATE_SPEED, PersistentDataType.DOUBLE, 0.0);
        double vSpeed = pdc.getOrDefault(Keys.STATE_VSPEED, PersistentDataType.DOUBLE, 0.0);
        double hp = pdc.getOrDefault(Keys.STATE_HEALTH, PersistentDataType.DOUBLE, cfg.maxHealth);
        Location loc = core.getLocation();

        Airship ship = new Airship(plugin, id, core.getWorld(),
                loc.getX(), loc.getY(), loc.getZ(), yaw, roll, speed, vSpeed, hp);
        ship.core = core;
        ship.spawned = true;
        String ownerStr = pdc.get(Keys.OWNER, PersistentDataType.STRING);
        if (ownerStr != null) {
            try {
                ship.owner = UUID.fromString(ownerStr);
            } catch (IllegalArgumentException ignored) {
            }
        }

        // ---- model displays: adopt if exact, else rebuild ----
        if (indexedComplete(allParts.size(), partByIndex, parts.size())) {
            for (int i = 0; i < parts.size(); i++) {
                ship.displays.add(partByIndex.get(i));
                ship.partDefs.add(parts.get(i));
            }
        } else {
            for (Display d : allParts) {
                if (d != null) d.remove();
            }
            ship.buildDisplays(loc);
        }

        // ---- hitboxes ----
        int hn = AirshipModel.HITBOXES.size();
        if (indexedComplete(allHitboxes.size(), hbByIndex, hn)) {
            for (int i = 0; i < hn; i++) {
                ship.hitboxes.add(hbByIndex.get(i));
            }
        } else {
            for (Interaction in : allHitboxes) {
                if (in != null) in.remove();
            }
            ship.buildHitboxes(loc);
        }

        // ---- seats ----
        int sn = AirshipModel.SEAT_OFFSETS.size();
        if (indexedComplete(allSeats.size(), seatByIndex, sn)) {
            for (int i = 0; i < sn; i++) {
                ship.seats.add(seatByIndex.get(i));
            }
        } else {
            for (ArmorStand a : allSeats) {
                if (a != null) a.remove();
            }
            ship.buildSeats(loc);
        }

        ship.forceRefresh(); // position everything correctly for the loaded state
        return ship;
    }

    private static boolean indexedComplete(int total, Map<Integer, ?> byIndex, int expected) {
        if (total != expected || byIndex.size() != expected) {
            return false;
        }
        for (int i = 0; i < expected; i++) {
            if (byIndex.get(i) == null) {
                return false;
            }
        }
        return true;
    }

    // ----------------------------------------------------------------- spawning

    private void spawnEntities() {
        Location base = anchor.clone();

        core = world.spawn(base, ArmorStand.class, a -> {
            configureSeatStand(a);
            tagEntity(a, "core", -1);
        });
        if (owner != null) {
            core.getPersistentDataContainer().set(Keys.OWNER, PersistentDataType.STRING, owner.toString());
        }

        buildSeats(base);
        buildHitboxes(base);
        buildDisplays(base);

        spawned = true;
        forceRefresh();
        persistState();
    }

    private static void configureSeatStand(ArmorStand a) {
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
    }

    private void buildSeats(Location base) {
        for (int i = 0; i < AirshipModel.SEAT_OFFSETS.size(); i++) {
            final int idx = i;
            ArmorStand seat = world.spawn(base, ArmorStand.class, a -> {
                configureSeatStand(a);
                tagEntity(a, "seat", idx);
            });
            seats.add(seat);
        }
    }

    private void buildHitboxes(Location base) {
        for (int i = 0; i < AirshipModel.HITBOXES.size(); i++) {
            AirshipModel.HitboxSpec spec = AirshipModel.HITBOXES.get(i);
            final int idx = i;
            Interaction box = world.spawn(base, Interaction.class, in -> {
                in.setInteractionWidth(spec.width());
                in.setInteractionHeight(spec.height());
                in.setResponsive(true);
                in.setPersistent(true);
                tagEntity(in, "hitbox", idx);
            });
            hitboxes.add(box);
        }
    }

    /** Spawn one display per model part at {@code base}, tagging each by index. */
    private void buildDisplays(Location base) {
        AirshipConfig cfg = plugin.config();
        List<Part> parts = AirshipModel.parts(cfg.roundedEnvelope, cfg.shipName);
        for (int index = 0; index < parts.size(); index++) {
            Part part = parts.get(index);
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
    }

    private void configureDisplay(Display d, int index) {
        AirshipConfig cfg = plugin.config();
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(cfg.viewRange);
        d.setTeleportDuration(Math.max(1, cfg.interpolationTicks));
        d.setInterpolationDuration(cfg.interpolationTicks);
        d.setPersistent(true);
        tagEntity(d, "part", index);
    }

    private void tagEntity(Entity e, String role, int index) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        pdc.set(Keys.SHIP_ID, PersistentDataType.STRING, id.toString());
        pdc.set(Keys.SHIP_PART, PersistentDataType.STRING, role);
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
        pdc.set(Keys.STATE_ROLL, PersistentDataType.DOUBLE, roll);
        pdc.set(Keys.STATE_SPEED, PersistentDataType.DOUBLE, speed);
        pdc.set(Keys.STATE_VSPEED, PersistentDataType.DOUBLE, vSpeed);
        pdc.set(Keys.STATE_HEALTH, PersistentDataType.DOUBLE, health);
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
     * Push only what changed: teleport the carrier/seats/hitboxes when the anchor
     * moved or the attitude rotated (their offsets follow the orientation), and
     * recompute model-part transforms only when the angles changed.
     */
    public void refreshModel() {
        if (!spawned || core == null) {
            return;
        }
        boolean first = Double.isNaN(lastX);
        boolean moved = first
                || Math.abs(lastX - anchor.getX()) > MOVE_EPSILON
                || Math.abs(lastY - anchor.getY()) > MOVE_EPSILON
                || Math.abs(lastZ - anchor.getZ()) > MOVE_EPSILON;
        boolean angles = first
                || Math.abs(lastYaw - yaw) > ANGLE_EPSILON
                || Math.abs(lastPitch - pitch) > ANGLE_EPSILON
                || Math.abs(lastRoll - roll) > ANGLE_EPSILON;
        boolean propellers = updatePropellerAngle();
        if (!moved && !angles && !propellers) {
            return;
        }

        Location base = anchor;
        if (moved) {
            try {
                core.teleport(base, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
            } catch (Exception ignored) {
            }
        }
        // seats & hitboxes follow the orientation (their offsets rotate with yaw)
        if (moved || angles) {
            for (int i = 0; i < seats.size(); i++) {
                ArmorStand seat = seats.get(i);
                if (seat == null || !seat.isValid()) {
                    continue;
                }
                Location w = localToWorld(AirshipModel.SEAT_OFFSETS.get(i));
                try {
                    seat.teleport(w, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
                } catch (Exception ignored) {
                }
            }
            for (int i = 0; i < hitboxes.size(); i++) {
                Interaction box = hitboxes.get(i);
                if (box == null || !box.isValid()) {
                    continue;
                }
                AirshipModel.HitboxSpec spec = AirshipModel.HITBOXES.get(i);
                Location w = localToWorld(spec.center());
                w.setY(w.getY() - spec.height() / 2.0); // box base sits below its centre
                box.teleport(w);
            }
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
            Part part = partDefs.get(i);
            if (angles || (propellers && part.isPropeller())) {
                Transformation t = part.isPropeller()
                        ? Transforms.forPart(part, q, propellerAngle)
                        : Transforms.forPart(part, q);
                d.setTransformation(t);
            }
        }
        markClean();
    }

    private boolean updatePropellerAngle() {
        double s = Math.abs(speed);
        if (s < 0.02) {
            return false;
        }
        propellerAngle = (float) ((propellerAngle + 18.0 + s * 180.0) % 360.0);
        return true;
    }

    /**
     * Snap the whole rig back onto its anchor if something external (an explosion,
     * a piston, another plugin) shoved the invisible core while the airship was
     * hovering. Without this the model + rider could drift away from the anchor.
     * Cheap: a distance check; only re-teleports when actually displaced.
     */
    public void stabilize() {
        if (!spawned || core == null || !core.isValid()) {
            return;
        }
        Location cl = core.getLocation();
        if (cl.getWorld() != anchor.getWorld()) {
            return;
        }
        if (Math.abs(cl.getX() - anchor.getX()) > 0.03
                || Math.abs(cl.getY() - anchor.getY()) > 0.03
                || Math.abs(cl.getZ() - anchor.getZ()) > 0.03) {
            forceRefresh();
        }
    }

    // ----------------------------------------------------------------- geometry helpers

    /** World location of a point given in airship space. */
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
        return localToWorld(AirshipModel.BOMB_BAY);
    }

    public Location burnerPoint() {
        return localToWorld(AirshipModel.BURNER_POINT);
    }

    public List<Location> enginePoints() {
        List<Location> out = new ArrayList<>(AirshipModel.ENGINE_POINTS.size());
        for (Vector3f e : AirshipModel.ENGINE_POINTS) {
            out.add(localToWorld(e));
        }
        return out;
    }

    /** Smallest distance from {@code loc} to any key point of the (huge) hull. */
    public double minBlastDistance(Location loc) {
        double best = Double.MAX_VALUE;
        for (Vector3f kp : AirshipModel.BLAST_POINTS) {
            Location w = localToWorld(kp);
            double d = w.distanceSquared(loc);
            if (d < best) {
                best = d;
            }
        }
        return Math.sqrt(best);
    }

    // ----------------------------------------------------------------- combat

    /** @return true if the airship was destroyed by this hit. */
    public boolean damage(double amount) {
        if (!Double.isFinite(amount) || amount <= 0 || !spawned) {
            return false;
        }
        health -= amount;
        persistState();
        if (health <= 0) {
            destroy(true);
            return true;
        }
        Location c = localToWorld(new Vector3f(0f, AirshipModel.ENV_CY, 0f));
        world.spawnParticle(org.bukkit.Particle.CRIT, c, 8, 1.4, 1.0, 1.4, 0.0);
        world.spawnParticle(org.bukkit.Particle.LARGE_SMOKE, c, 6, 1.4, 1.0, 1.4, 0.02);
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
            persistState();
        }
        return restored;
    }

    public void destroy(boolean effects) {
        if (effects && spawned) {
            Location env = localToWorld(new Vector3f(0f, AirshipModel.ENV_CY, 0f));
            Location gon = anchor.clone().add(0, 0.4, 0);
            // Cosmetic-only: the kill blow already landed; a real explosion here
            // would chain-damage bailing riders and nearby players and lag, so the
            // wreck goes up in pure particles + sound instead.
            world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, env, 6, 2.4, 1.6, 2.4, 0);
            world.spawnParticle(org.bukkit.Particle.LARGE_SMOKE, env, 120, 3.0, 2.0, 3.0, 0.06);
            world.spawnParticle(org.bukkit.Particle.FLAME, env, 60, 2.4, 1.6, 2.4, 0.08);
            world.spawnParticle(org.bukkit.Particle.LARGE_SMOKE, gon, 40, 1.6, 1.0, 1.6, 0.04);
            world.playSound(env, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 6f, 0.5f);
            world.playSound(env, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 6f, 0.7f);
            if (plugin.config().debris) {
                flingDebris(env);
            }
            if (plugin.config().dropItemOnDestroy) {
                world.dropItemNaturally(gon, me.bibo.militarycraft.vehicles.airship.items.AirshipItem.create(plugin));
            }
        }
        removeEntities();
    }

    /**
     * Kamikaze: the airship rams terrain at full speed. The impact point itself
     * explodes (crater + optional fire), then the wreck is destroyed.
     */
    public void destroyByCrash(Location impact) {
        AirshipConfig cfg = plugin.config();
        if (spawned && impact != null && impact.getWorld() != null) {
            plugin.airships().setInternalExplosion(true);
            try {
                impact.getWorld().createExplosion(impact, cfg.crashExplosionPower,
                        cfg.crashSetFire, cfg.crashBreakBlocks);
            } finally {
                plugin.airships().setInternalExplosion(false);
            }
            impact.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, impact, 4, 2.0, 1.4, 2.0, 0);
            impact.getWorld().spawnParticle(org.bukkit.Particle.LARGE_SMOKE, impact, 90, 2.4, 1.6, 2.4, 0.06);
            impact.getWorld().spawnParticle(org.bukkit.Particle.FLAME, impact, 45, 1.8, 1.2, 1.8, 0.08);
            impact.getWorld().playSound(impact, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 6f, 0.5f);
        }
        destroy(true);
    }

    private void flingDebris(Location c) {
        java.util.Random rng = new java.util.Random();
        Material mat = plugin.config().envelopeBlock;
        List<DebrisPiece> pieces = new ArrayList<>(16);
        for (int i = 0; i < 16; i++) {
            BlockDisplay chunk = world.spawn(c, BlockDisplay.class, b -> {
                b.setBlock(mat.createBlockData());
                b.setBrightness(new Display.Brightness(15, 15));
                b.setPersistent(false);
                Transformation t = b.getTransformation();
                t.getScale().set(0.7f);
                b.setTransformation(t);
            });
            Vector v = new Vector(rng.nextGaussian() * 0.5, 0.4 + rng.nextDouble() * 0.6,
                    rng.nextGaussian() * 0.5);
            pieces.add(new DebrisPiece(chunk, v));
        }
        new org.bukkit.scheduler.BukkitRunnable() {
            int life = 50;

            @Override
            public void run() {
                if (life-- <= 0 || pieces.isEmpty()) {
                    for (DebrisPiece piece : pieces) {
                        if (piece.entity().isValid()) {
                            piece.entity().remove();
                        }
                    }
                    cancel();
                    return;
                }
                for (Iterator<DebrisPiece> it = pieces.iterator(); it.hasNext(); ) {
                    DebrisPiece piece = it.next();
                    BlockDisplay chunk = piece.entity();
                    if (!chunk.isValid()) {
                        it.remove();
                        continue;
                    }
                    Vector vel = piece.velocity();
                    vel.setY(vel.getY() - 0.04);
                    chunk.teleport(chunk.getLocation().add(vel));
                }
            }
        }.runTaskTimer(plugin.bukkitPlugin(), 1, 1);
    }

    private record DebrisPiece(BlockDisplay entity, Vector velocity) {
    }

    /** Delete every entity belonging to this airship (does not touch the registry). */
    public void removeEntities() {
        preparePassengersForEject(true);
        if (core != null) {
            if (!core.getPassengers().isEmpty()) {
                core.eject();
            }
            core.remove();
        }
        for (ArmorStand seat : seats) {
            if (seat != null) {
                if (!seat.getPassengers().isEmpty()) {
                    seat.eject();
                }
                seat.remove();
            }
        }
        seats.clear();
        for (Interaction box : hitboxes) {
            if (box != null) {
                box.remove();
            }
        }
        hitboxes.clear();
        for (Display d : displays) {
            if (d != null) {
                d.remove();
            }
        }
        displays.clear();
        partDefs.clear();
        driver = null;
        spawned = false;
    }

    // ----------------------------------------------------------------- riding

    public int riderCount() {
        int n = (driver != null) ? 1 : 0;
        for (ArmorStand seat : seats) {
            if (seat != null) {
                n += seat.getPassengers().size();
            }
        }
        return n;
    }

    public boolean isFull() {
        return riderCount() >= plugin.config().seatsCapacity;
    }

    public boolean isDriver(UUID id) {
        return id != null && id.equals(driver);
    }

    public boolean hasRider(UUID id) {
        if (id == null) {
            return false;
        }
        if (id.equals(driver)) {
            return true;
        }
        for (ArmorStand seat : seats) {
            if (seat == null) {
                continue;
            }
            for (Entity p : seat.getPassengers()) {
                if (p.getUniqueId().equals(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<UUID> allRiders() {
        List<UUID> out = new ArrayList<>();
        if (driver != null) {
            out.add(driver);
        }
        for (ArmorStand seat : seats) {
            if (seat == null) {
                continue;
            }
            for (Entity p : seat.getPassengers()) {
                out.add(p.getUniqueId());
            }
        }
        return out;
    }

    /**
     * Seat a player. The first to board becomes the pilot (rides the core); the
     * rest take passenger seats. Returns false if the airship is full or it could
     * not be seated.
     */
    public boolean addRider(Player player) {
        if (riderCount() >= plugin.config().seatsCapacity) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        if (driver == null) {
            if (core == null || !core.isValid()) {
                return false;
            }
            core.addPassenger(player);
            driver = uuid;
            // fresh helm: start hovering, nose where the new pilot faces
            yaw = player.getLocation().getYaw();
            pitch = 0;
            roll = 0;
            speed = 0;
            vSpeed = 0;
            unmanned = false;
            orientDirty = true;
            weaponLock = plugin.config().mountGraceTicks; // brief "loading ammo" after boarding
            return true;
        }
        int usable = Math.min(seats.size(), Math.max(0, plugin.config().seatsCapacity - 1));
        for (int i = 0; i < usable; i++) {
            ArmorStand seat = seats.get(i);
            if (seat != null && seat.isValid() && seat.getPassengers().isEmpty()) {
                seat.addPassenger(player);
                return true;
            }
        }
        return false;
    }

    /** Remove one rider (pilot or passenger). */
    public void removeRider(UUID id) {
        if (id == null) {
            return;
        }
        if (id.equals(driver)) {
            if (core != null) {
                core.eject();
            }
            driver = null;
            persistState();
            return;
        }
        for (ArmorStand seat : seats) {
            if (seat == null) {
                continue;
            }
            for (Entity p : seat.getPassengers()) {
                if (p.getUniqueId().equals(id)) {
                    seat.eject();
                    return;
                }
            }
        }
    }

    /** Drop the pilot role (used when the tick finds the driver is gone/desynced). */
    public void clearDriver() {
        if (core != null) {
            core.eject();
            core.setVelocity(new Vector());
        }
        driver = null;
        persistState();
    }

    /**
     * After a reload/crash a player may still be a passenger of our core/seats
     * without being tracked. Re-adopt them (the core's player becomes the pilot)
     * and return all their UUIDs so the manager can re-index and re-hide them.
     */
    public List<UUID> reclaimRiders() {
        List<UUID> out = new ArrayList<>();
        if (core != null) {
            for (Entity p : core.getPassengers()) {
                if (p instanceof Player) {
                    driver = p.getUniqueId();
                    out.add(driver);
                }
            }
        }
        for (ArmorStand seat : seats) {
            if (seat == null) {
                continue;
            }
            for (Entity p : seat.getPassengers()) {
                if (p instanceof Player) {
                    out.add(p.getUniqueId());
                }
            }
        }
        return out;
    }

    /** Eject everyone (used on unload/shutdown/cleanup). */
    public void ejectAll() {
        preparePassengersForEject(true);
        if (core != null) {
            core.eject();
            core.setVelocity(new Vector());
        }
        for (ArmorStand seat : seats) {
            if (seat != null) {
                seat.eject();
            }
        }
        driver = null;
    }

    private void preparePassengersForEject(boolean slowFall) {
        int sf = plugin.config().ejectSlowFallTicks;
        if (core != null) {
            for (Entity passenger : core.getPassengers()) {
                preparePassenger(passenger, slowFall, sf);
            }
        }
        for (ArmorStand seat : seats) {
            if (seat == null) {
                continue;
            }
            for (Entity passenger : seat.getPassengers()) {
                preparePassenger(passenger, slowFall, sf);
            }
        }
    }

    private void preparePassenger(Entity passenger, boolean slowFall, int slowFallTicks) {
        passenger.setFallDistance(0f);
        if (!(passenger instanceof Player player)) {
            return;
        }
        player.setInvisible(false);
        DriverCloak.show(player);
        plugin.airships().forgetRider(player.getUniqueId());
        if (slowFall && slowFallTicks > 0) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOW_FALLING, slowFallTicks, 0, true, false, false));
        }
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

    public UUID driver() {
        return driver;
    }

    public List<Interaction> hitboxes() {
        return hitboxes;
    }

    public UUID owner() {
        return owner;
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

    public double vSpeed() {
        return vSpeed;
    }

    public void setVSpeed(double v) {
        this.vSpeed = v;
    }

    public double health() {
        return health;
    }

    public double maxHealth() {
        return plugin.config().maxHealth;
    }

    @Override
    public String type() {
        return "airship";
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
        return List.copyOf(hitboxes);
    }

    @Override
    public void applyAntiAirHit() {
        damage(plugin.config().creeperDamage);
    }

    @Override
    public void applyExplosion(Location loc, double power) {
        me.bibo.militarycraft.vehicles.airship.combat.Explosions.applyBlastTo(
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

    public int bombCooldown() {
        return bombCooldown;
    }

    public void setBombCooldown(int r) {
        this.bombCooldown = r;
    }

    public int bombAmmo() {
        return bombAmmo;
    }

    public void useBomb() {
        if (bombAmmo > 0) {
            bombAmmo--;
        }
    }

    public double burnerHeat() {
        return burnerHeat;
    }

    public void setBurnerHeat(double v) {
        this.burnerHeat = Math.max(0.0, Math.min(1.0, v));
    }

    public boolean isBurnerLocked() {
        return burnerLocked;
    }

    public void setBurnerLocked(boolean v) {
        this.burnerLocked = v;
    }

    /**
     * Replenish one bomb when its regen timer elapses (called each tick). The
     * regen timer only advances while the fire-rate cooldown has fully recovered
     * (bombCooldown == 0), so it does NOT run in parallel with the cooldown —
     * continuous bombing therefore can't be sustained: you only refill once you
     * stop dropping bombs.
     */
    public void regenAmmo(AirshipConfig cfg) {
        if (bombCooldown > 0) {
            return; // still on attack cooldown — no replenishment in parallel
        }
        if (bombAmmo < cfg.bombLoad && ++bombRegen >= cfg.bombRegenTicks) {
            bombAmmo++;
            bombRegen = 0;
        }
    }

    public boolean isUnmanned() {
        return unmanned;
    }

    public void setUnmanned(boolean v) {
        this.unmanned = v;
    }

    /** True when there is no solid block just beneath the gondola (it's aloft). */
    public boolean isAirborne() {
        if (world == null) {
            return false;
        }
        int by = (int) Math.floor(anchor.getY() + AirshipModel.GONDOLA_BOTTOM_Y - 0.5);
        return world.getBlockAt(anchor.getBlockX(), by, anchor.getBlockZ()).isPassable();
    }
}
