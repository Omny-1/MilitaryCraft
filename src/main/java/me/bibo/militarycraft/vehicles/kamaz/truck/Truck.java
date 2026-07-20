package me.bibo.militarycraft.vehicles.kamaz.truck;

import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import me.bibo.militarycraft.vehicles.kamaz.KamazRuntime;
import me.bibo.militarycraft.vehicles.kamaz.config.KamazConfig;
import me.bibo.militarycraft.vehicles.kamaz.model.Transforms;
import me.bibo.militarycraft.vehicles.kamaz.model.TruckModel;
import me.bibo.militarycraft.vehicles.kamaz.model.TruckPart;
import me.bibo.militarycraft.vehicles.kamaz.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
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

/**
 * A single Kamaz "Pushinka": a cluster of entities (an invisible seat the driver
 * rides, a row of Interaction hitboxes, and a set of display model parts) plus
 * its current heading, speed and health. Its full state is mirrored onto the
 * seat's persistent data so the truck can be rebuilt after a chunk reload or
 * restart.
 */
public final class Truck implements VehicleHandle {

    // Five hitboxes spaced along the long body. The CENTER one sits at the anchor
    // (local Z=0) so rehydration can read the true ground point from it.
    private static final int CENTER_HITBOX_INDEX = 2;
    // Fractions of the half-length, so the five hitboxes always span the body no
    // matter the model SCALE. The centre one (index 2) sits at local Z=0 = anchor.
    private static final float[] HITBOX_Z_FRACTIONS = {-0.75f, -0.375f, 0f, 0.375f, 0.75f};
    private static final double WHEEL_SPIN_VISUAL_SCALE = 0.35;

    // Passenger seats inside the troop compartment (truck-space x,z). Players ride
    // these but don't steer. Created lazily and NOT persisted (transient), so they
    // never affect model rehydration.
    private static final double[][] PASSENGER_OFFSETS = {
            {-1.4, 0.3}, {1.4, 0.3}, {-1.4, -2.4}, {1.4, -2.4}, {-1.4, -5.0}, {1.4, -5.0}
    };

    private final KamazRuntime plugin;
    private final UUID id;
    private final UUID ownerId;
    private final World world;
    private final Location anchor; // x/y/z only; yaw/pitch unused

    private double hullYaw;
    private double speed;
    private double wheelSpin;
    private double wheelSteer;
    private double verticalVelocity;
    private double health;

    private ArmorStand seat;
    private Interaction hitbox;
    private final List<Interaction> hitboxes = new ArrayList<>();
    private final List<Display> displays = new ArrayList<>();
    private final List<TruckPart> partDefs = new ArrayList<>();
    private boolean spawned;

    private UUID driver;
    private boolean submerged; // whole body underwater -> stalled + slowly flooding
    private int submergedTicks;

    /** Per-victim run-over cooldown (victim UUID -> last-hit epoch millis). */
    private final Map<UUID, Long> hitCd = new HashMap<>();
    private long lastHitCdCleanupMs;

    /** Lazily-spawned passenger seats: slot index -> seat ArmorStand. */
    private final Map<Integer, ArmorStand> passengerSeats = new HashMap<>();

    // last-pushed transform state, for cheap dirty checking
    private double lastX = Double.NaN, lastY, lastZ, lastHull, lastWheelSpin, lastWheelSteer;

    private Truck(KamazRuntime plugin, UUID id, UUID ownerId, World world, double x, double y, double z,
                  double hullYaw, double health) {
        this.plugin = plugin;
        this.id = id;
        this.ownerId = ownerId;
        this.world = world;
        this.anchor = new Location(world, x, y, z);
        this.hullYaw = hullYaw;
        this.health = health;
    }

    // ----------------------------------------------------------------- factories

    public static Truck create(KamazRuntime plugin, Location at, double yaw) {
        return create(plugin, at, yaw, null);
    }

    public static Truck create(KamazRuntime plugin, Location at, double yaw, UUID ownerId) {
        Truck truck = new Truck(plugin, UUID.randomUUID(), ownerId, at.getWorld(),
                at.getX(), at.getY(), at.getZ(), yaw, plugin.config().maxHealth);
        truck.spawnEntities();
        return truck;
    }

    /**
     * Rebuild a truck from its existing (persistent) entities found in a chunk.
     * Returns null if the group is incomplete/mismatched (caller should clean up).
     */
    public static Truck rehydrate(KamazRuntime plugin, UUID id, List<Entity> entities) {
        KamazConfig cfg = plugin.config();
        List<TruckPart> parts = TruckModel.parts();
        ArmorStand seat = null;
        List<Interaction> allHitboxes = new ArrayList<>();
        Interaction[] hitboxArr = new Interaction[HITBOX_Z_FRACTIONS.length];
        Display[] arr = new Display[parts.size()];

        for (Entity e : entities) {
            String role = e.getPersistentDataContainer().get(Keys.TRUCK_PART, PersistentDataType.STRING);
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
        double hp = pdc.getOrDefault(Keys.STATE_HEALTH, PersistentDataType.DOUBLE, cfg.maxHealth);
        UUID ownerId = null;
        String ownerStr = pdc.get(Keys.TRUCK_OWNER, PersistentDataType.STRING);
        if (ownerStr != null) {
            try {
                ownerId = UUID.fromString(ownerStr);
            } catch (IllegalArgumentException ignored) {
            }
        }
        // The anchor (ground point) comes from the centre hitbox, which sits at the
        // anchor; the seat is offset upward by seat-height, so it must not define it.
        Location loc = anchorHitbox.getLocation();

        Truck truck = new Truck(plugin, id, ownerId, anchorHitbox.getWorld(),
                loc.getX(), loc.getY(), loc.getZ(), hull, hp);
        truck.seat = seat;
        boolean completeHitboxes = true;
        for (Interaction box : hitboxArr) {
            if (box == null) {
                completeHitboxes = false;
                break;
            }
        }
        if (completeHitboxes) {
            for (Interaction box : hitboxArr) {
                truck.hitboxes.add(box);
            }
            truck.hitbox = hitboxArr[CENTER_HITBOX_INDEX];
        } else {
            for (Interaction box : allHitboxes) {
                if (box != null) {
                    box.remove();
                }
            }
            truck.buildHitboxes(loc);
        }
        for (int i = 0; i < arr.length; i++) {
            truck.displays.add(arr[i]);
            truck.partDefs.add(parts.get(i));
        }
        truck.spawned = true;
        truck.forceRefresh();
        truck.reapplyAppearance(cfg); // pull colours/number/name from the current config
        return truck;
    }

    // ----------------------------------------------------------------- spawning

    private void spawnEntities() {
        KamazConfig cfg = plugin.config();
        Location base = anchor.clone();

        seat = world.spawn(seatLocation(), ArmorStand.class, a -> {
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
            if (ownerId != null) {
                a.getPersistentDataContainer().set(Keys.TRUCK_OWNER, PersistentDataType.STRING, ownerId.toString());
            }
        });

        buildHitboxes(base);

        List<TruckPart> parts = TruckModel.parts();
        for (int index = 0; index < parts.size(); index++) {
            TruckPart part = parts.get(index);
            final int idx = index;
            Display d;
            if (part.isText()) {
                d = world.spawn(base, TextDisplay.class, t -> {
                    t.text(Component.text(part.text).color(TextColor.color(part.textArgb & 0xFFFFFF)));
                    t.setBillboard(part.billboardCenter ? Display.Billboard.CENTER : Display.Billboard.FIXED);
                    t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                    t.setSeeThrough(false);
                    t.setShadowed(false);
                    t.setAlignment(TextDisplay.TextAlignment.CENTER);
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
        for (int i = 0; i < HITBOX_Z_FRACTIONS.length; i++) {
            final int idx = i;
            Interaction box = world.spawn(hitboxLocation(base, idx), Interaction.class, in -> {
                in.setInteractionWidth(TruckModel.WIDTH);
                in.setInteractionHeight(TruckModel.HEIGHT);
                in.setResponsive(true);
                in.setPersistent(true);
                tagEntity(in, "hitbox", idx);
            });
            hitboxes.add(box);
        }
        hitbox = hitboxes.get(CENTER_HITBOX_INDEX);
    }

    private Location hitboxLocation(Location base, int index) {
        float z = HITBOX_Z_FRACTIONS[index] * (TruckModel.LENGTH / 2f);
        Vector3f off = Transforms.localPointToWorld(new Vector3f(0f, 0f, z), hullYaw);
        return new Location(world, base.getX() + off.x, base.getY() + off.y, base.getZ() + off.z);
    }

    /**
     * Where the driver's seat (and thus the camera) sits: raised above the hull and
     * pushed a little toward the rear so the view clears the tall cab - even in F5.
     * The back offset rotates with the hull, so it stays "behind" as the truck turns.
     */
    private Location seatLocation() {
        KamazConfig cfg = plugin.config();
        Vector3f off = Transforms.localPointToWorld(
                new Vector3f(0f, 0f, (float) -cfg.seatBackOffset), hullYaw);
        return new Location(world, anchor.getX() + off.x,
                anchor.getY() + cfg.seatHeight, anchor.getZ() + off.z);
    }

    private void configureDisplay(Display d, int index) {
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(3.0f);
        d.setTeleportDuration(2);
        d.setInterpolationDuration(2);
        d.setPersistent(true);
        tagEntity(d, "part", index);
    }

    private void tagEntity(Entity e, String role, int index) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        pdc.set(Keys.TRUCK_ID, PersistentDataType.STRING, id.toString());
        pdc.set(Keys.TRUCK_PART, PersistentDataType.STRING, role);
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
        pdc.set(Keys.STATE_HEALTH, PersistentDataType.DOUBLE, health);
        if (ownerId != null) {
            pdc.set(Keys.TRUCK_OWNER, PersistentDataType.STRING, ownerId.toString());
        }
    }

    // ----------------------------------------------------------------- rendering

    private void markClean() {
        lastX = anchor.getX();
        lastY = anchor.getY();
        lastZ = anchor.getZ();
        lastHull = hullYaw;
        lastWheelSpin = wheelSpin;
        lastWheelSteer = wheelSteer;
    }

    private void forceRefresh() {
        lastX = Double.NaN;
        refreshModel();
    }

    public void advanceWheelSpin(double signedDistance) {
        if (Math.abs(signedDistance) < 1e-6) {
            return;
        }
        double degrees = Math.toDegrees(signedDistance / TruckModel.WHEEL_RADIUS);
        wheelSpin += degrees * WHEEL_SPIN_VISUAL_SCALE;
        if (Math.abs(wheelSpin) > 3600.0) {
            wheelSpin %= 360.0;
        }
    }

    public void setWheelSteer(double degrees) {
        this.wheelSteer = degrees;
    }

    /**
     * Push only what changed: teleport the entities when the anchor moved, and
     * recompute transformations only when the hull yaw or wheel spin changed.
     * While the truck drives in a straight line, non-wheel parts only teleport;
     * rolling wheel parts get their own cheap transform update.
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
        boolean wheelChanged = first || Math.abs(wheelSpin - lastWheelSpin) > 0.1;
        boolean steerChanged = first || Math.abs(wheelSteer - lastWheelSteer) > 0.1;
        if (!moved && !hullChanged && !wheelChanged && !steerChanged) {
            return;
        }

        Location base = new Location(world, anchor.getX(), anchor.getY(), anchor.getZ());
        if (moved || hullChanged) {
            // The seat (camera) rides above and behind the hull; its offset rotates
            // with the truck, so it must be re-placed on a turn as well as a move.
            try {
                seat.teleport(seatLocation(),
                        io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
            } catch (Exception ignored) {
            }
            for (int i = 0; i < hitboxes.size(); i++) {
                Interaction box = hitboxes.get(i);
                if (box == null || !box.isValid()) {
                    continue;
                }
                box.teleport(hitboxLocation(base, i));
            }
            if (!passengerSeats.isEmpty()) {
                double psh = plugin.config().passengerSeatHeight;
                for (Map.Entry<Integer, ArmorStand> e : passengerSeats.entrySet()) {
                    ArmorStand s = e.getValue();
                    if (s == null || !s.isValid()) {
                        continue;
                    }
                    try {
                        s.teleport(passengerSeatLocation(e.getKey(), psh),
                                io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
                    } catch (Exception ignored) {
                    }
                }
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
            TruckPart part = partDefs.get(i);
            if (hullChanged
                    || (wheelChanged && part.rollsWithWheel)
                    || (steerChanged && part.steersWithWheel)) {
                Transformation t = Transforms.forPart(part, hullYaw, wheelSpin, wheelSteer);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(2);
                d.setTransformation(t);
            }
        }
        markClean();
    }

    /**
     * Re-apply the configured block materials (and side number / name) to every
     * part from the current config. Block data is baked into each display at spawn,
     * so this lets a config recolour reach trucks rebuilt from saved entities and
     * trucks already loaded on a /kamaz reload.
     */
    public void reapplyAppearance(KamazConfig cfg) {
        for (int i = 0; i < displays.size(); i++) {
            Display d = displays.get(i);
            if (d == null || !d.isValid()) {
                continue;
            }
            TruckPart part = partDefs.get(i);
            if (part.isText()) {
                if (d instanceof TextDisplay td) {
                    td.text(Component.text(part.text).color(TextColor.color(part.textArgb & 0xFFFFFF)));
                }
            } else if (d instanceof BlockDisplay bd) {
                bd.setBlock(part.material(cfg).createBlockData());
            }
        }
    }

    // ----------------------------------------------------------------- water

    /**
     * Recompute whether the whole truck is under water (the centre column is water
     * from the body up past the roof). Resets the flood timer when it surfaces.
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
        double[] heights = {0.8, TruckModel.HEIGHT * 0.45, TruckModel.HEIGHT * 0.85};
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
    public void tickWater(KamazConfig cfg) {
        submergedTicks++;
        if (submergedTicks % 6 == 0) {
            world.spawnParticle(org.bukkit.Particle.BUBBLE_COLUMN_UP,
                    anchor.clone().add(0, TruckModel.HEIGHT * 0.5, 0),
                    14, 1.8, 2.2, 1.8, 0.02);
        }
        if (submergedTicks % 20 == 0) {
            world.playSound(anchor, org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.5f);
            double dps = maxHealth() * cfg.drownDamagePercent / 100.0;
            if (dps > 0) {
                damage(dps); // ~once per second
            }
        }
    }

    // ----------------------------------------------------------------- run-over cooldown

    /**
     * @return true if this victim may be run over right now (and records the hit),
     * false if it is still on cooldown.
     */
    public boolean tryRunOver(UUID victim, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long last = hitCd.get(victim);
        if (last != null && now - last < cooldownMs) {
            return false;
        }
        hitCd.put(victim, now);
        if (hitCd.size() > 128 || now - lastHitCdCleanupMs > 1000L) {
            long expiryMs = Math.max(50L, cooldownMs);
            hitCd.entrySet().removeIf(en -> now - en.getValue() > expiryMs);
            lastHitCdCleanupMs = now;
        }
        return true;
    }

    // ----------------------------------------------------------------- damage / destruction

    /** @return true if the truck was destroyed by this hit. */
    public boolean damage(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0 || !isActive()) {
            return false;
        }
        health -= amount;
        persistState();
        if (plugin.config().debug) {
            plugin.getLogger().info("[debug] Kamaz -"
                    + String.format(java.util.Locale.US, "%.1f", amount) + " HP -> "
                    + String.format(java.util.Locale.US, "%.1f", health));
        }
        if (health <= 0) {
            destroy(true);
            return true;
        }
        world.spawnParticle(org.bukkit.Particle.CRIT, anchor.clone().add(0, 2.0, 0),
                10, 1.4, 1.0, 1.4, 0.0);
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
            Location c = anchor.clone().add(0, 1.6, 0);
            plugin.trucks().setInternalExplosion(true);
            try {
                world.createExplosion(c, 2.8f, false, false);
            } finally {
                plugin.trucks().setInternalExplosion(false);
            }
            world.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, c, 4, 1.6, 1.0, 1.6, 0);
            world.spawnParticle(org.bukkit.Particle.LARGE_SMOKE, c, 80, 1.8, 1.2, 1.8, 0.05);
            world.playSound(c, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.6f);
            if (plugin.config().debris) {
                flingDebris(c);
            }
            if (plugin.config().dropItemOnDestroy) {
                world.dropItemNaturally(c, me.bibo.militarycraft.vehicles.kamaz.items.TruckItem.create(plugin));
            }
        }
        removeEntities();
    }

    private void flingDebris(Location c) {
        java.util.Random rng = new java.util.Random();
        Material mat = plugin.config().hullBlock;
        for (int i = 0; i < 10; i++) {
            BlockDisplay chunk = world.spawn(c, BlockDisplay.class, b -> {
                b.setBlock(mat.createBlockData());
                b.setBrightness(new Display.Brightness(15, 15));
                b.setPersistent(false);
                Transformation t = b.getTransformation();
                t.getScale().set(0.6f);
                b.setTransformation(t);
            });
            Vector vel0 = new Vector(rng.nextGaussian() * 0.35, 0.45 + rng.nextDouble() * 0.5,
                    rng.nextGaussian() * 0.35);
            new org.bukkit.scheduler.BukkitRunnable() {
                int life = 30;
                final Vector vel = vel0;

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

    /** Delete every entity belonging to this truck (does not touch the registry). */
    public void removeEntities() {
        removeAllPassengerSeats();
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

    public void mount(Player player) {
        seat.addPassenger(player);
        driver = player.getUniqueId();
        hullYaw = player.getLocation().getYaw();
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

    // ----------------------------------------------------------------- passengers

    public int passengerCapacity() {
        return PASSENGER_OFFSETS.length;
    }

    private Location passengerSeatLocation(int slot, double seatHeight) {
        double[] o = PASSENGER_OFFSETS[slot];
        Vector3f off = Transforms.localPointToWorld(new Vector3f(
                (float) (o[0] * TruckModel.SCALE), 0f, (float) (o[1] * TruckModel.SCALE)), hullYaw);
        return new Location(world, anchor.getX() + off.x,
                anchor.getY() + seatHeight + off.y, anchor.getZ() + off.z);
    }

    /**
     * Seat a passenger in the first free slot. The seat ArmorStand is spawned on
     * demand and is transient (not persisted), so it never affects rehydration.
     *
     * @return the slot used, or -1 if the truck is full.
     */
    public int boardPassenger(Player player, double seatHeight) {
        for (int slot = 0; slot < PASSENGER_OFFSETS.length; slot++) {
            ArmorStand s = passengerSeats.get(slot);
            if (s != null && s.isValid()) {
                if (!s.getPassengers().isEmpty()) {
                    continue; // taken
                }
                s.addPassenger(player); // reuse a stray empty seat
                return slot;
            }
            ArmorStand seatAs = spawnPassengerSeat(slot, seatHeight);
            seatAs.addPassenger(player);
            passengerSeats.put(slot, seatAs);
            return slot;
        }
        return -1;
    }

    private ArmorStand spawnPassengerSeat(int slot, double seatHeight) {
        Location at = passengerSeatLocation(slot, seatHeight);
        return world.spawn(at, ArmorStand.class, a -> {
            a.setInvisible(true);
            a.setGravity(false);
            a.setMarker(false);
            a.setSmall(true);
            a.setBasePlate(false);
            a.setArms(false);
            a.setSilent(true);
            a.setInvulnerable(true);
            a.setCollidable(false);
            a.setPersistent(false);
            // Tagged (so a purge sweeps any orphan) but NO truck-id, so the
            // entity-load adopter never tries to fold a stray seat into the model.
            a.addScoreboardTag(Keys.SCOREBOARD_TAG);
        });
    }

    /** Remove the seat carrying this player (called when a passenger dismounts). */
    public void removePassenger(Player player) {
        for (java.util.Iterator<Map.Entry<Integer, ArmorStand>> it = passengerSeats.entrySet().iterator();
             it.hasNext(); ) {
            ArmorStand s = it.next().getValue();
            if (s == null || !s.isValid()) {
                it.remove();
                continue;
            }
            boolean has = s.getPassengers().stream()
                    .anyMatch(p -> p.getUniqueId().equals(player.getUniqueId()));
            if (has) {
                s.eject();
                s.remove();
                it.remove();
                return;
            }
        }
    }

    /** Drop seats whose rider vanished without firing a dismount (death, kick…). */
    public void pruneEmptyPassengerSeats() {
        passengerSeats.values().removeIf(s -> {
            if (s == null || !s.isValid()) {
                return true;
            }
            if (s.getPassengers().isEmpty()) {
                s.remove();
                return true;
            }
            return false;
        });
    }

    public void removeAllPassengerSeats() {
        for (ArmorStand s : passengerSeats.values()) {
            if (s != null) {
                if (!s.getPassengers().isEmpty()) {
                    s.eject();
                }
                s.remove();
            }
        }
        passengerSeats.clear();
    }

    // ----------------------------------------------------------------- getters

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
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
        this.hullYaw = v;
    }

    public double wheelSteer() {
        return wheelSteer;
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
        return "kamaz";
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
        plugin.trucks().applyExplosionTo(this, loc, power);
    }

    @Override
    public boolean handlesBukkitExplosionEvents() {
        return true;
    }
}
