package me.bibo.militarycraft.vehicles.moto.motorcycle;

import io.papermc.paper.entity.TeleportFlag;
import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import me.bibo.militarycraft.vehicles.moto.MotoRuntime;
import me.bibo.militarycraft.vehicles.moto.config.MotoConfig;
import me.bibo.militarycraft.vehicles.moto.items.MotorcycleItem;
import me.bibo.militarycraft.vehicles.moto.model.MotorcycleModel;
import me.bibo.militarycraft.vehicles.moto.model.MotorcyclePart;
import me.bibo.militarycraft.vehicles.moto.model.Transforms;
import me.bibo.militarycraft.vehicles.moto.util.Keys;
import me.bibo.militarycraft.vehicles.moto.util.MathUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One loaded motorcycle aggregate.
 *
 * <p>Only {@link #anchorEntity} is persistent. It is the canonical ground anchor
 * and carries all durable state. Driver/passenger seats, interaction hitboxes and
 * display parts are derived, transient entities rebuilt whenever the anchor is
 * adopted. This makes chunk boundaries, restarts and model upgrades independent
 * of display count or load order.</p>
 */
public final class Motorcycle implements VehicleHandle {

    public static final int SCHEMA_VERSION = 1;

    /** Visual wheel-spin damping, matching the Kamaz: a slower spin reads smooth and
     *  keeps the crossed tyre tiles from strobing/"flying out" at speed. */
    private static final double WHEEL_SPIN_VISUAL_SCALE = 0.35;

    private static final float[][] HITBOX_OFFSETS = {
            {0.0f, -0.90f, 1.75f},
            {0.0f, 0.90f, 1.75f},
            {MotorcycleModel.sidecarMountOffset().x, 0.0f, 1.70f}
    };
    private static final int MOTORCYCLE_HITBOX_COUNT = 2;
    private static final int CENTER_HITBOX_INDEX = 0;

    private final MotoRuntime plugin;
    private final UUID id;
    private final UUID ownerId;
    private final World world;
    private final Location anchor;
    private final boolean withSidecar;

    private double hullYaw;
    private double speed;
    private double wheelSpin;
    private double wheelSteer;
    private double verticalVelocity;
    private double health;
    private double maxHealth;

    private ArmorStand anchorEntity;
    private ArmorStand driverSeat;
    private final List<Interaction> hitboxes = new ArrayList<>();
    private final List<Display> displays = new ArrayList<>();
    private final List<MotorcyclePart> partDefs = new ArrayList<>();
    private final Map<Integer, ArmorStand> passengerSeats = new HashMap<>();

    private UUID driver;
    private boolean active;
    private boolean destroying;
    private boolean submerged;
    private int submergedTicks;

    private final Map<UUID, Long> impactCooldown = new HashMap<>();
    private long lastImpactCleanupMs;

    private double lastX = Double.NaN;
    private double lastY;
    private double lastZ;
    private double lastHull;
    private double lastWheelSpin;
    private double lastWheelSteer;

    private Motorcycle(MotoRuntime plugin, UUID id, UUID ownerId, World world,
                       double x, double y, double z, double hullYaw,
                       double health, double maxHealth, boolean withSidecar) {
        this.plugin = plugin;
        this.id = id;
        this.ownerId = ownerId;
        this.world = world;
        this.anchor = new Location(world, x, y, z);
        this.withSidecar = withSidecar;
        this.hullYaw = MathUtil.wrapDegrees(hullYaw);
        this.maxHealth = maxHealth;
        this.health = MathUtil.clamp(health, 0.0, maxHealth);
    }

    // ---------------------------------------------------------------- factories

    public static Motorcycle create(MotoRuntime plugin, Location at, double yaw, UUID ownerId,
                                    boolean withSidecar) {
        requireFiniteLocation(at);
        MotoConfig config = plugin.config();
        Motorcycle motorcycle = new Motorcycle(plugin, UUID.randomUUID(), ownerId,
                at.getWorld(), at.getX(), at.getY(), at.getZ(), yaw,
                config.maxHealth, config.maxHealth, withSidecar);
        try {
            motorcycle.spawnAnchor();
            motorcycle.active = true;
            motorcycle.rebuildDerivedEntities();
            motorcycle.persistState();
            return motorcycle;
        } catch (RuntimeException | Error failure) {
            motorcycle.removeEntities();
            throw failure;
        }
    }

    /** Adopt one canonical persistent anchor and rebuild every derived entity. */
    public static Motorcycle rehydrate(MotoRuntime plugin, UUID id, ArmorStand canonicalAnchor) {
        if (canonicalAnchor == null || !canonicalAnchor.isValid()) {
            return null;
        }
        PersistentDataContainer pdc = canonicalAnchor.getPersistentDataContainer();
        String role = pdc.get(Keys.ENTITY_ROLE, PersistentDataType.STRING);
        if (!"anchor".equals(role)) {
            return null;
        }
        Location at = canonicalAnchor.getLocation();
        if (!finite(at.getX()) || !finite(at.getY()) || !finite(at.getZ())) {
            return null;
        }

        MotoConfig config = plugin.config();
        double yaw = finiteOr(pdc.get(Keys.STATE_YAW, PersistentDataType.DOUBLE), 0.0);
        double hp = finiteOr(pdc.get(Keys.STATE_HEALTH, PersistentDataType.DOUBLE), config.maxHealth);
        hp = MathUtil.clamp(hp, 0.01, config.maxHealth);
        UUID ownerId = parseUuid(pdc.get(Keys.MOTORCYCLE_OWNER, PersistentDataType.STRING));
        Byte sidecarTag = pdc.get(Keys.STATE_SIDECAR, PersistentDataType.BYTE);
        boolean withSidecar = sidecarTag == null || sidecarTag != (byte) 0; // legacy = with sidecar

        Motorcycle motorcycle = new Motorcycle(plugin, id, ownerId,
                canonicalAnchor.getWorld(), at.getX(), at.getY(), at.getZ(),
                yaw, hp, config.maxHealth, withSidecar);
        motorcycle.anchorEntity = canonicalAnchor;
        motorcycle.active = true;
        motorcycle.persistState(); // upgrades schema/model metadata in place
        try {
            motorcycle.rebuildDerivedEntities();
            return motorcycle;
        } catch (RuntimeException failure) {
            motorcycle.removeDerivedEntities();
            throw failure;
        }
    }

    private static void requireFiniteLocation(Location at) {
        if (at == null || at.getWorld() == null
                || !finite(at.getX()) || !finite(at.getY()) || !finite(at.getZ())) {
            throw new IllegalArgumentException("Motorcycle location and world must be finite");
        }
    }

    private void spawnAnchor() {
        anchorEntity = world.spawn(anchor, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setSilent(true);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setPersistent(true);
            tagEntity(stand, "anchor", -1);
        });
        requireSpawned(anchorEntity, "persistent anchor");
    }

    // ---------------------------------------------------------- derived entities

    public void rebuildDerivedEntities() {
        removeDerivedEntities();
        driverSeat = spawnSeat(driverSeatLocation(), "driver_seat", -1);
        buildHitboxes();
        List<MotorcyclePart> parts = MotorcycleModel.parts(withSidecar);
        for (int index = 0; index < parts.size(); index++) {
            MotorcyclePart part = parts.get(index);
            displays.add(spawnDisplay(part, index));
            partDefs.add(part);
        }
        forceRefresh();
    }

    /** Repairs missing transient pieces without touching a healthy model. */
    public void repairDerivedEntities() {
        if (!active) {
            return;
        }
        boolean repaired = false;
        if (driverSeat == null || !driverSeat.isValid()) {
            driverSeat = spawnSeat(driverSeatLocation(), "driver_seat", -1);
            repaired = true;
        }
        if (hitboxes.size() != hitboxCount()
                || hitboxes.stream().anyMatch(box -> box == null || !box.isValid())) {
            removeHitboxes();
            buildHitboxes();
            repaired = true;
        }
        List<MotorcyclePart> expected = MotorcycleModel.parts(withSidecar);
        boolean displayBroken = displays.size() != expected.size();
        if (!displayBroken) {
            for (Display display : displays) {
                if (display == null || !display.isValid()) {
                    displayBroken = true;
                    break;
                }
            }
        }
        if (displayBroken) {
            removeDisplays();
            for (int index = 0; index < expected.size(); index++) {
                MotorcyclePart part = expected.get(index);
                displays.add(spawnDisplay(part, index));
                partDefs.add(part);
            }
            repaired = true;
        }
        if (repaired) {
            forceRefresh();
        }
    }

    private Display spawnDisplay(MotorcyclePart part, int index) {
        Display display;
        if (part.isText()) {
            display = world.spawn(anchor, TextDisplay.class, text -> {
                text.text(Component.text(part.text)
                        .color(TextColor.color(part.textArgb & 0x00FF_FFFF)));
                text.setBillboard(part.billboardCenter
                        ? Display.Billboard.CENTER : Display.Billboard.FIXED);
                text.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                text.setSeeThrough(false);
                text.setShadowed(false);
                text.setAlignment(TextDisplay.TextAlignment.CENTER);
                configureDisplay(text, part, index);
            });
        } else {
            Material material = part.material(plugin.config());
            display = world.spawn(anchor, BlockDisplay.class, block -> {
                block.setBlock(material.createBlockData());
                configureDisplay(block, part, index);
            });
        }
        return requireSpawned(display, "display part " + index);
    }

    private void configureDisplay(Display display, MotorcyclePart part, int index) {
        // Full brightness on EVERY part (like the Kamaz): a display otherwise samples
        // the light level at its position and renders pitch-black the moment the anchor
        // dips into a block or a dark spot while driving.
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(4.0f); // render far enough for the pulled-back F5 camera
        display.setTeleportDuration(2);
        display.setInterpolationDuration(2);
        display.setPersistent(false);
        tagEntity(display, "display", index);
    }

    /** Solo uses two body hitboxes; the sidecar variant adds one sidecar hitbox. */
    private int hitboxCount() {
        return withSidecar ? HITBOX_OFFSETS.length : MOTORCYCLE_HITBOX_COUNT;
    }

    private void buildHitboxes() {
        for (int index = 0; index < hitboxCount(); index++) {
            final int partIndex = index;
            Interaction interaction = world.spawn(hitboxLocation(index), Interaction.class, hitbox -> {
                hitbox.setInteractionWidth(HITBOX_OFFSETS[partIndex][2]);
                hitbox.setInteractionHeight(MotorcycleModel.HEIGHT);
                hitbox.setResponsive(true);
                hitbox.setPersistent(false);
                tagEntity(hitbox, "hitbox", partIndex);
            });
            requireSpawned(interaction, "interaction hitbox " + partIndex);
            hitboxes.add(interaction);
        }
    }

    private Location hitboxLocation(int index) {
        float[] local = HITBOX_OFFSETS[index];
        Vector3f offset = Transforms.localPointToWorld(
                new Vector3f(local[0], 0.0f, local[1]), hullYaw);
        return new Location(world, anchor.getX() + offset.x, anchor.getY(), anchor.getZ() + offset.z);
    }

    private ArmorStand spawnSeat(Location at, String role, int slot) {
        ArmorStand seat = world.spawn(at, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setMarker(false);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setSilent(true);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setPersistent(false);
            tagEntity(stand, role, slot);
        });
        return requireSpawned(seat, role);
    }

    private void tagEntity(Entity entity, String role, int index) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(Keys.MOTORCYCLE_ID, PersistentDataType.STRING, id.toString());
        pdc.set(Keys.ENTITY_ROLE, PersistentDataType.STRING, role);
        if (index >= 0) {
            pdc.set(Keys.PART_INDEX, PersistentDataType.INTEGER, index);
        }
        entity.addScoreboardTag(Keys.SCOREBOARD_TAG);
    }

    // --------------------------------------------------------------- persistence

    public void persistState() {
        if (anchorEntity == null || !anchorEntity.isValid()) {
            return;
        }
        PersistentDataContainer pdc = anchorEntity.getPersistentDataContainer();
        pdc.set(Keys.MOTORCYCLE_ID, PersistentDataType.STRING, id.toString());
        pdc.set(Keys.ENTITY_ROLE, PersistentDataType.STRING, "anchor");
        pdc.set(Keys.MODEL_VERSION, PersistentDataType.INTEGER, SCHEMA_VERSION);
        pdc.set(Keys.STATE_YAW, PersistentDataType.DOUBLE, hullYaw);
        pdc.set(Keys.STATE_HEALTH, PersistentDataType.DOUBLE, health);
        pdc.set(Keys.STATE_SIDECAR, PersistentDataType.BYTE, (byte) (withSidecar ? 1 : 0));
        if (ownerId != null) {
            pdc.set(Keys.MOTORCYCLE_OWNER, PersistentDataType.STRING, ownerId.toString());
        }
    }

    /** Keep the anchor but remove all transient runtime entities before unload/disable. */
    public void detach() {
        if (!active) {
            return;
        }
        persistState();
        active = false;
        removeDerivedEntities();
    }

    // ---------------------------------------------------------------- rendering

    public void refreshModel() {
        if (!active || anchorEntity == null || !anchorEntity.isValid()) {
            return;
        }
        if (!finite(anchor.getX()) || !finite(anchor.getY()) || !finite(anchor.getZ())) {
            Location safe = anchorEntity.getLocation();
            anchor.setX(safe.getX());
            anchor.setY(safe.getY());
            anchor.setZ(safe.getZ());
            speed = 0.0;
            verticalVelocity = 0.0;
        }

        boolean first = Double.isNaN(lastX);
        boolean moved = first
                || Math.abs(lastX - anchor.getX()) > 1.0e-5
                || Math.abs(lastY - anchor.getY()) > 1.0e-5
                || Math.abs(lastZ - anchor.getZ()) > 1.0e-5;
        boolean hullChanged = first || Math.abs(lastHull - hullYaw) > 1.0e-4;
        boolean wheelChanged = first || Math.abs(lastWheelSpin - wheelSpin) > 0.1;
        boolean steerChanged = first || Math.abs(lastWheelSteer - wheelSteer) > 0.1;
        if (!moved && !hullChanged && !wheelChanged && !steerChanged) {
            return;
        }

        Location base = anchor.clone();
        if (moved) {
            anchorEntity.teleport(base);
        }
        if (moved || hullChanged) {
            teleportSeatRetainingRider(driverSeat, driverSeatLocation());
            for (int index = 0; index < hitboxes.size(); index++) {
                Interaction box = hitboxes.get(index);
                if (box != null && box.isValid()) {
                    box.teleport(hitboxLocation(index));
                }
            }
            for (Map.Entry<Integer, ArmorStand> entry : passengerSeats.entrySet()) {
                teleportSeatRetainingRider(entry.getValue(), passengerSeatLocation(entry.getKey()));
            }
        }

        for (int index = 0; index < displays.size(); index++) {
            Display display = displays.get(index);
            if (display == null || !display.isValid()) {
                continue;
            }
            if (moved) {
                display.teleport(base);
            }
            MotorcyclePart part = partDefs.get(index);
            if (hullChanged
                    || (wheelChanged && part.rollsWithWheel)
                    || (steerChanged && part.steersWithWheel)) {
                Transformation transform = Transforms.forPart(part, hullYaw, wheelSpin, wheelSteer);
                // A wheel updating ONLY because it spun (hull not turning, bars not
                // moving) is snapped, not interpolated. A BlockDisplay rotates about a
                // corner, so its centre is held by a half-extent shift that depends on
                // the spin angle; interpolating a big per-tick spin makes that centre
                // orbit — the tyre "flies out". Snapping pins the spin axis exactly.
                // When the hull/steer DO move, every part interpolates together so the
                // wheel never desyncs from the fork/body.
                boolean spinOnlyUpdate = part.rollsWithWheel && !hullChanged
                        && !(steerChanged && part.steersWithWheel);
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(spinOnlyUpdate ? 0 : 2);
                display.setTransformation(transform);
            }
        }
        markClean();
    }

    private static void teleportSeatRetainingRider(ArmorStand seat, Location destination) {
        if (seat == null || !seat.isValid()) {
            return;
        }
        seat.teleport(destination, TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    public void forceRefresh() {
        lastX = Double.NaN;
        refreshModel();
    }

    private void markClean() {
        lastX = anchor.getX();
        lastY = anchor.getY();
        lastZ = anchor.getZ();
        lastHull = hullYaw;
        lastWheelSpin = wheelSpin;
        lastWheelSteer = wheelSteer;
    }

    public void advanceWheelSpin(double signedDistance) {
        if (!finite(signedDistance) || Math.abs(signedDistance) < 1.0e-8) {
            return;
        }
        wheelSpin += Math.toDegrees(signedDistance / MotorcycleModel.WHEEL_RADIUS)
                * WHEEL_SPIN_VISUAL_SCALE;
        if (Math.abs(wheelSpin) > 3600.0) {
            wheelSpin %= 360.0;
        }
    }

    public void reapplyAppearance(MotoConfig config) {
        for (int index = 0; index < displays.size() && index < partDefs.size(); index++) {
            Display display = displays.get(index);
            MotorcyclePart part = partDefs.get(index);
            if (display == null || !display.isValid()) {
                continue;
            }
            if (part.isText() && display instanceof TextDisplay text) {
                text.text(Component.text(part.text)
                        .color(TextColor.color(part.textArgb & 0x00FF_FFFF)));
            } else if (!part.isText() && display instanceof BlockDisplay block) {
                block.setBlock(part.material(config).createBlockData());
            }
        }
    }

    public void onConfigReload(MotoConfig config) {
        double fraction = maxHealth <= 0.0 ? 1.0 : MathUtil.clamp(health / maxHealth, 0.0, 1.0);
        maxHealth = config.maxHealth;
        health = Math.max(0.01, fraction * maxHealth);
        if (!config.drownEnabled) {
            submerged = false;
            submergedTicks = 0;
        }
        reapplyAppearance(config);
        forceRefresh();
        persistState();
    }

    // -------------------------------------------------------------------- riders

    private Location driverSeatLocation() {
        MotoConfig config = plugin.config();
        Vector3f model = MotorcycleModel.driverMountOffset();
        return localSeatLocation(model.x, config.driverSeatHeight, config.driverSeatZ);
    }

    private Location passengerSeatLocation(int slot) {
        MotoConfig config = plugin.config();
        return switch (slot) {
            case 0 -> {
                Vector3f model = MotorcycleModel.pillionMountOffset();
                yield localSeatLocation(model.x, config.pillionSeatHeight, model.z);
            }
            case 1 -> {
                Vector3f model = MotorcycleModel.sidecarMountOffset();
                yield localSeatLocation(model.x, config.sidecarSeatHeight, model.z);
            }
            default -> throw new IllegalArgumentException("Unknown passenger slot " + slot);
        };
    }

    private Location localSeatLocation(double x, double y, double z) {
        Vector3f offset = Transforms.localPointToWorld(
                new Vector3f((float) x, 0.0f, (float) z), hullYaw);
        return new Location(world, anchor.getX() + offset.x,
                anchor.getY() + y, anchor.getZ() + offset.z, (float) hullYaw, 0.0f);
    }

    public boolean mount(Player player) {
        if (driver != null || player == null || player.getVehicle() != null) {
            return false;
        }
        repairDerivedEntities();
        if (driverSeat == null || !driverSeat.isValid() || !driverSeat.addPassenger(player)) {
            return false;
        }
        driver = player.getUniqueId();
        speed = 0.0;
        return true;
    }

    public int boardPassenger(Player player) {
        if (player == null || player.getVehicle() != null) {
            return -1;
        }
        for (int slot = 0; slot < passengerCapacity(); slot++) {
            ArmorStand existing = passengerSeats.get(slot);
            if (existing != null && existing.isValid() && !existing.getPassengers().isEmpty()) {
                continue;
            }
            if (existing != null) {
                existing.remove();
            }
            ArmorStand seat = spawnSeat(passengerSeatLocation(slot), "passenger_seat", slot);
            if (!seat.addPassenger(player)) {
                seat.remove();
                return -1;
            }
            passengerSeats.put(slot, seat);
            return slot;
        }
        return -1;
    }

    public boolean isDriverMounted(Player player) {
        return player != null && driver != null && driver.equals(player.getUniqueId())
                && driverSeat != null && driverSeat.isValid() && player.getVehicle() == driverSeat;
    }

    public boolean isPassengerMounted(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        for (ArmorStand seat : passengerSeats.values()) {
            if (seat != null && seat.isValid() && seat.getPassengers().stream()
                    .anyMatch(entity -> entity.getUniqueId().equals(playerId))) {
                return true;
            }
        }
        return false;
    }

    public void clearDriver() {
        driver = null;
        speed = 0.0;
    }

    public void ejectDriver() {
        driver = null;
        speed = 0.0;
        if (driverSeat != null && driverSeat.isValid()) {
            driverSeat.eject();
        }
    }

    public void removePassenger(Player player) {
        if (player == null) {
            return;
        }
        for (var iterator = passengerSeats.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<Integer, ArmorStand> entry = iterator.next();
            ArmorStand seat = entry.getValue();
            if (seat == null || !seat.isValid()) {
                iterator.remove();
                continue;
            }
            boolean carriesPlayer = seat.getPassengers().stream()
                    .anyMatch(entity -> entity.getUniqueId().equals(player.getUniqueId()));
            if (carriesPlayer) {
                seat.eject();
                seat.remove();
                iterator.remove();
                return;
            }
        }
    }

    public void pruneEmptyPassengerSeats() {
        passengerSeats.values().removeIf(seat -> {
            if (seat == null || !seat.isValid()) {
                return true;
            }
            if (seat.getPassengers().isEmpty()) {
                seat.remove();
                return true;
            }
            return false;
        });
    }

    public void ejectAllRiders() {
        ejectDriver();
        removeAllPassengerSeats();
    }

    public void removeAllPassengerSeats() {
        for (ArmorStand seat : passengerSeats.values()) {
            if (seat != null) {
                if (seat.isValid()) {
                    seat.eject();
                }
                seat.remove();
            }
        }
        passengerSeats.clear();
    }

    public int passengerCapacity() {
        return withSidecar ? 2 : 1; // solo has only the pillion seat
    }

    // --------------------------------------------------------------------- water

    public boolean refreshSubmerged() {
        submerged = active && fullyUnderWater();
        if (!submerged) {
            submergedTicks = 0;
        }
        return submerged;
    }

    private boolean fullyUnderWater() {
        Vector3f sidecar = MotorcycleModel.sidecarMountOffset();
        double[][] samples = withSidecar
                ? new double[][]{
                {0.0, 0.25, -0.8}, {0.0, 1.05, 0.2}, {0.0, 1.9, 0.9},
                {sidecar.x, 0.25, sidecar.z}, {sidecar.x, 0.85, sidecar.z}}
                : new double[][]{
                {0.0, 0.25, -0.8}, {0.0, 1.05, 0.2}, {0.0, 1.9, 0.9}};
        for (double[] sample : samples) {
            Vector3f offset = Transforms.localPointToWorld(
                    new Vector3f((float) sample[0], 0.0f, (float) sample[2]), hullYaw);
            org.bukkit.block.Block block = world.getBlockAt(
                    (int) Math.floor(anchor.getX() + offset.x),
                    (int) Math.floor(anchor.getY() + sample[1]),
                    (int) Math.floor(anchor.getZ() + offset.z));
            if (!isWater(block)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWater(org.bukkit.block.Block block) {
        return block.getType() == Material.WATER
                || block.getBlockData() instanceof org.bukkit.block.data.Waterlogged waterlogged
                && waterlogged.isWaterlogged();
    }

    public void tickWater(MotoConfig config) {
        submergedTicks++;
        if (submergedTicks % 8 == 0) {
            world.spawnParticle(Particle.BUBBLE_COLUMN_UP,
                    anchor.clone().add(0, MotorcycleModel.HEIGHT * 0.5, 0),
                    8, 0.8, 0.7, 0.8, 0.02);
        }
        if (submergedTicks % 20 == 0) {
            world.playSound(anchor, Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.65f);
            damage(maxHealth * config.drownDamagePercent / 100.0);
        }
    }

    // --------------------------------------------------------------- impact CD

    public boolean tryImpact(UUID victim, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long last = impactCooldown.get(victim);
        if (last != null && now - last < cooldownMs) {
            return false;
        }
        impactCooldown.put(victim, now);
        if (impactCooldown.size() > 128 || now - lastImpactCleanupMs > 1000L) {
            long expiry = Math.max(50L, cooldownMs);
            impactCooldown.entrySet().removeIf(entry -> now - entry.getValue() > expiry);
            lastImpactCleanupMs = now;
        }
        return true;
    }

    // --------------------------------------------------------- damage/destruction

    public boolean damage(double amount) {
        if (!active || destroying || !finite(amount) || amount <= 0.0) {
            return false;
        }
        health = Math.max(0.0, health - amount);
        persistState();
        if (plugin.config().debug) {
            plugin.getLogger().info("[debug] Motorcycle -" + String.format(java.util.Locale.ROOT,
                    "%.2f", amount) + " HP -> " + String.format(java.util.Locale.ROOT, "%.2f", health));
        }
        if (health <= 0.0) {
            destroy(true);
            return true;
        }
        world.spawnParticle(Particle.CRIT, anchor.clone().add(0, 1.0, 0),
                8, 0.7, 0.5, 0.7, 0.02);
        return false;
    }

    @Override
    public double repair(double amount) {
        if (!finite(amount) || amount <= 0.0 || !isActive()) {
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
        if (destroying) {
            return;
        }
        destroying = true;
        boolean wasActive = active;
        active = false; // nested explosion events must not damage this vehicle again
        Location centre = anchor.clone().add(0, 0.9, 0);
        removeDerivedEntities();
        if (anchorEntity != null) {
            anchorEntity.remove();
            anchorEntity = null;
        }
        if (effects && wasActive) {
            plugin.motorcycles().createDestructionExplosion(centre, 1.8f);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, centre, 1);
            world.spawnParticle(Particle.LARGE_SMOKE, centre, 28, 0.9, 0.7, 0.9, 0.04);
            world.playSound(centre, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.9f);
            if (plugin.config().debris) {
                world.spawnParticle(Particle.BLOCK, centre, 36, 1.0, 0.8, 1.0, 0.14,
                        plugin.config().bodyBlock.createBlockData());
            }
            if (plugin.config().dropItemOnDestroy) {
                world.dropItemNaturally(centre, MotorcycleItem.create(plugin, withSidecar));
            }
        }
    }

    public void removeEntities() {
        active = false;
        destroying = true;
        removeDerivedEntities();
        if (anchorEntity != null) {
            anchorEntity.remove();
            anchorEntity = null;
        }
    }

    private void removeDerivedEntities() {
        removeAllPassengerSeats();
        if (driverSeat != null) {
            if (driverSeat.isValid()) {
                driverSeat.eject();
            }
            driverSeat.remove();
            driverSeat = null;
        }
        driver = null;
        removeHitboxes();
        removeDisplays();
    }

    private void removeHitboxes() {
        for (Interaction hitbox : hitboxes) {
            if (hitbox != null) {
                hitbox.remove();
            }
        }
        hitboxes.clear();
    }

    private void removeDisplays() {
        for (Display display : displays) {
            if (display != null) {
                display.remove();
            }
        }
        displays.clear();
        partDefs.clear();
    }

    // ------------------------------------------------------------------- getters

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

    public ArmorStand anchorEntity() {
        return anchorEntity;
    }

    public ArmorStand seat() {
        return driverSeat;
    }

    public Interaction hitbox() {
        return hitboxes.size() > CENTER_HITBOX_INDEX ? hitboxes.get(CENTER_HITBOX_INDEX) : null;
    }

    public List<Interaction> hitboxes() {
        return List.copyOf(hitboxes);
    }

    public UUID driver() {
        return driver;
    }

    public boolean isOccupied() {
        return driver != null;
    }

    public boolean isActive() {
        return active && anchorEntity != null && anchorEntity.isValid();
    }

    public boolean isSubmerged() {
        return submerged;
    }

    public boolean hasSidecar() {
        return withSidecar;
    }

    public double hullYaw() {
        return hullYaw;
    }

    public void setHullYaw(double value) {
        if (finite(value)) {
            hullYaw = MathUtil.wrapDegrees(value);
        }
    }

    public double wheelSteer() {
        return wheelSteer;
    }

    public void setWheelSteer(double value) {
        wheelSteer = finite(value) ? value : 0.0;
    }

    public double speed() {
        return speed;
    }

    public void setSpeed(double value) {
        speed = finite(value) ? value : 0.0;
    }

    public double verticalVelocity() {
        return verticalVelocity;
    }

    public void setVerticalVelocity(double value) {
        verticalVelocity = finite(value) ? value : 0.0;
    }

    public double health() {
        return health;
    }

    public double maxHealth() {
        return maxHealth;
    }

    @Override
    public String type() {
        return "moto";
    }

    @Override
    public Entity coreEntity() {
        return driverSeat != null ? driverSeat : anchorEntity;
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
        plugin.motorcycles().applyAntiAirHit(this);
    }

    @Override
    public void applyExplosion(Location loc, double power) {
        plugin.motorcycles().applyExplosionTo(this, loc, power);
    }

    @Override
    public boolean handlesBukkitExplosionEvents() {
        return true;
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static double finiteOr(Double value, double fallback) {
        return value != null && finite(value) ? value : fallback;
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    private static <T extends Entity> T requireSpawned(T entity, String description) {
        if (entity == null || !entity.isValid()) {
            if (entity != null) {
                entity.remove();
            }
            throw new IllegalStateException("Paper rejected MotoCraft " + description + " spawn");
        }
        return entity;
    }
}
