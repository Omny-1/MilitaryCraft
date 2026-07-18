/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.papermc.paper.entity.TeleportFlag
 *  io.papermc.paper.entity.TeleportFlag$EntityState
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.Particle
 *  org.bukkit.Sound
 *  org.bukkit.World
 *  org.bukkit.attribute.Attribute
 *  org.bukkit.attribute.AttributeInstance
 *  org.bukkit.block.Block
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.block.data.Waterlogged
 *  org.bukkit.entity.ArmorStand
 *  org.bukkit.entity.BlockDisplay
 *  org.bukkit.entity.Display
 *  org.bukkit.entity.Display$Brightness
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Interaction
 *  org.bukkit.entity.Player
 *  org.bukkit.persistence.PersistentDataContainer
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitRunnable
 *  org.bukkit.util.Transformation
 *  org.bukkit.util.Vector
 *  org.joml.Vector3f
 */
package me.bibo.militarycraft.vehicles.pickup.vehicle;

import io.papermc.paper.entity.TeleportFlag;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import me.bibo.militarycraft.vehicles.pickup.PickupRuntime;
import me.bibo.militarycraft.vehicles.pickup.combat.Explosions;
import me.bibo.militarycraft.vehicles.pickup.config.PickupConfig;
import me.bibo.militarycraft.vehicles.pickup.items.PickupItem;
import me.bibo.militarycraft.vehicles.pickup.model.PartGroup;
import me.bibo.militarycraft.vehicles.pickup.model.PickupModel;
import me.bibo.militarycraft.vehicles.pickup.model.PickupPart;
import me.bibo.militarycraft.vehicles.pickup.model.Transforms;
import me.bibo.militarycraft.vehicles.pickup.util.Keys;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

public final class Pickup implements VehicleHandle {
    private static final double WHEEL_SPIN_VISUAL_SCALE = 0.35;
    private static final double SEAT_MOTION_LEAD_TICKS = 1.15;
    private static final double MAX_SEAT_MOTION_LEAD = 0.52;
    private final PickupRuntime plugin;
    private final UUID id;
    private final World world;
    private final Location anchor;
    private double hullYaw;
    private double gunYaw;
    private double gunPitch;
    private double speed;
    private int forwardHoldTicks;
    private int backwardHoldTicks;
    private double wheelSpin;
    private double wheelSteer;
    private double verticalVelocity;
    private double health;
    private ArmorStand driverSeat;
    private ArmorStand passengerSeat;
    private ArmorStand gunnerSeat;
    private final List<Interaction> hitboxes = new ArrayList<Interaction>();
    private final List<Display> displays = new ArrayList<Display>();
    private final List<PickupPart> partDefs = new ArrayList<PickupPart>();
    private boolean spawned;
    private UUID driver;
    private UUID passenger;
    private UUID gunner;
    private int gunLock;
    private int gunCooldown;
    private int overheatTicks;
    private final ArrayDeque<Long> recentShotTicks = new ArrayDeque<>();
    private boolean submerged;
    private int submergedTicks;
    private boolean stateDirty;
    private int persistCooldown;
    private final Map<UUID, Long> ramCooldown = new HashMap<UUID, Long>();
    private double lastX = Double.NaN;
    private double lastY;
    private double lastZ;
    private double lastHull;
    private double lastGunYaw;
    private double lastGunPitch;
    private double lastWheelSpin;
    private double lastWheelSteer;
    private double lastSeatLead;

    private Pickup(PickupRuntime plugin, UUID id, World world, double x, double y, double z, double hullYaw, double gunYaw, double gunPitch, double health) {
        this.plugin = plugin;
        this.id = id;
        this.world = world;
        this.anchor = new Location(world, x, y, z);
        this.hullYaw = hullYaw;
        this.gunYaw = gunYaw;
        this.gunPitch = gunPitch;
        this.health = health;
    }

    public static Pickup create(PickupRuntime plugin, Location at, double yaw) {
        Pickup pickup = new Pickup(plugin, UUID.randomUUID(), at.getWorld(), at.getX(), at.getY(), at.getZ(), yaw, yaw, 0.0, plugin.config().maxHealth);
        pickup.spawnEntities();
        return pickup;
    }

    public static Pickup rehydrate(PickupRuntime plugin, UUID id, List<Entity> entities) {
        PickupConfig cfg = plugin.config();
        List<PickupPart> parts = PickupModel.parts();
        ArmorStand driverSeat = null;
        ArmorStand passengerSeat = null;
        ArmorStand gunnerSeat = null;
        ArrayList<Interaction> allHitboxes = new ArrayList<Interaction>();
        Interaction[] hitboxArr = new Interaction[PickupModel.HITBOX_LOCAL.length];
        Display[] arr = new Display[parts.size()];
        block14: for (Entity entity : entities) {
            String role = (String)entity.getPersistentDataContainer().get(Keys.PICKUP_PART, PersistentDataType.STRING);
            if (role == null) continue;
            switch (role) {
                case "driver_seat": {
                    ArmorStand a;
                    if (!(entity instanceof ArmorStand)) continue block14;
                    driverSeat = a = (ArmorStand)entity;
                    break;
                }
                case "passenger_seat": {
                    ArmorStand a;
                    if (!(entity instanceof ArmorStand)) continue block14;
                    passengerSeat = a = (ArmorStand)entity;
                    break;
                }
                case "gunner_seat": {
                    ArmorStand a;
                    if (!(entity instanceof ArmorStand)) continue block14;
                    gunnerSeat = a = (ArmorStand)entity;
                    break;
                }
                case "hitbox": {
                    if (!(entity instanceof Interaction)) continue block14;
                    Interaction i = (Interaction)entity;
                    allHitboxes.add(i);
                    Integer idx = (Integer)entity.getPersistentDataContainer().get(Keys.PART_INDEX, PersistentDataType.INTEGER);
                    if (idx == null || idx < 0 || idx >= hitboxArr.length) continue block14;
                    hitboxArr[idx.intValue()] = i;
                    break;
                }
                case "part": {
                    Integer idx = (Integer)entity.getPersistentDataContainer().get(Keys.PART_INDEX, PersistentDataType.INTEGER);
                    if (!(entity instanceof Display)) continue block14;
                    Display d = (Display)entity;
                    if (idx == null || idx < 0 || idx >= arr.length) continue block14;
                    arr[idx.intValue()] = d;
                    break;
                }
            }
        }
        Interaction anchorHitbox = hitboxArr[0] != null ? hitboxArr[0] : (!allHitboxes.isEmpty() ? allHitboxes.get(0) : null);
        if (driverSeat == null || passengerSeat == null || gunnerSeat == null || anchorHitbox == null) {
            return null;
        }
        for (Display d : arr) {
            if (d != null) continue;
            return null;
        }
        PersistentDataContainer persistentDataContainer = driverSeat.getPersistentDataContainer();
        double hull = persistentDataContainer.getOrDefault(Keys.STATE_HULL_YAW, PersistentDataType.DOUBLE, 0.0);
        double gYaw = persistentDataContainer.getOrDefault(Keys.STATE_GUN_YAW, PersistentDataType.DOUBLE, hull);
        double gPitch = persistentDataContainer.getOrDefault(Keys.STATE_GUN_PITCH, PersistentDataType.DOUBLE, 0.0);
        double hp = persistentDataContainer.getOrDefault(Keys.STATE_HEALTH, PersistentDataType.DOUBLE, plugin.config().maxHealth);
        Location loc = Pickup.restoredAnchorLocation(driverSeat, anchorHitbox, hitboxArr, allHitboxes, hull, gYaw, gPitch);
        Pickup pickup = new Pickup(plugin, id, anchorHitbox.getWorld(), loc.getX(), loc.getY(), loc.getZ(), hull, gYaw, gPitch, hp);
        pickup.driverSeat = driverSeat;
        pickup.passengerSeat = passengerSeat;
        pickup.gunnerSeat = gunnerSeat;
        boolean completeHitboxes = true;
        for (Interaction box : hitboxArr) {
            if (box != null) continue;
            completeHitboxes = false;
            break;
        }
        if (!completeHitboxes) {
            return null;
        }
        pickup.hitboxes.addAll(List.of(hitboxArr));
        for (int i = 0; i < arr.length; ++i) {
            pickup.displays.add(arr[i]);
            pickup.partDefs.add(parts.get(i));
        }
        pickup.spawned = true;
        pickup.markClean();
        pickup.reapplyAppearance(cfg);
        pickup.forceRefresh();
        pickup.persistState();
        return pickup;
    }

    private static Location restoredAnchorLocation(ArmorStand stateHolder, Interaction anchorHitbox, Interaction[] indexedHitboxes, List<Interaction> allHitboxes, double hullYaw, double gunYaw, double gunPitch) {
        PersistentDataContainer pdc = stateHolder.getPersistentDataContainer();
        Double x = (Double)pdc.get(Keys.STATE_ANCHOR_X, PersistentDataType.DOUBLE);
        Double y = (Double)pdc.get(Keys.STATE_ANCHOR_Y, PersistentDataType.DOUBLE);
        Double z = (Double)pdc.get(Keys.STATE_ANCHOR_Z, PersistentDataType.DOUBLE);
        if (x != null && y != null && z != null) {
            return new Location(stateHolder.getWorld(), x.doubleValue(), y.doubleValue(), z.doubleValue());
        }
        for (int i = 0; i < indexedHitboxes.length; ++i) {
            Interaction box = indexedHitboxes[i];
            if (box == null) continue;
            return Pickup.anchorFromHitbox(box, i, hullYaw, gunYaw, gunPitch);
        }
        for (Interaction box : allHitboxes) {
            Integer idx = (Integer)box.getPersistentDataContainer().get(Keys.PART_INDEX, PersistentDataType.INTEGER);
            if (idx == null || idx < 0 || idx >= PickupModel.HITBOX_LOCAL.length) continue;
            return Pickup.anchorFromHitbox(box, idx, hullYaw, gunYaw, gunPitch);
        }
        return anchorHitbox.getLocation();
    }

    private static Location anchorFromHitbox(Interaction box, int index, double hullYaw, double gunYaw, double gunPitch) {
        float[] local = PickupModel.HITBOX_LOCAL[index];
        Vector3f off = Transforms.localPointToWorld(new Vector3f(local[0], 0.0f, local[1]), PartGroup.HULL, hullYaw, gunYaw, gunPitch);
        Location loc = box.getLocation();
        return new Location(box.getWorld(), loc.getX() - (double)off.x, loc.getY() - (double)off.y, loc.getZ() - (double)off.z);
    }

    private void spawnEntities() {
        PickupConfig cfg = this.plugin.config();
        Location base = this.anchor.clone();
        this.driverSeat = this.spawnSeat(base, PickupModel.DRIVER_SEAT_XZ, cfg.driverSeatHeight, PartGroup.HULL, this.hullYaw, "driver_seat", 0);
        this.passengerSeat = this.spawnSeat(base, PickupModel.PASSENGER_SEAT_XZ, cfg.driverSeatHeight, PartGroup.HULL, this.hullYaw, "passenger_seat", 2);
        this.gunnerSeat = this.spawnSeat(base, PickupModel.GUNNER_SEAT_XZ, cfg.gunnerSeatHeight, PartGroup.MOUNT, this.gunYaw, "gunner_seat", 1);
        this.buildHitboxes(base);
        List<PickupPart> parts = PickupModel.parts();
        int index = 0;
        while (index < parts.size()) {
            PickupPart part = parts.get(index);
            int idx = index++;
            Material mat = part.material(cfg);
            Display d = (Display)this.world.spawn(base, BlockDisplay.class, b -> {
                b.setBlock(mat.createBlockData());
                this.configureDisplay((Display)b, idx);
            });
            this.displays.add(d);
            this.partDefs.add(part);
        }
        this.spawned = true;
        this.forceRefresh();
        this.persistState();
    }

    private ArmorStand spawnSeat(Location base, Vector3f localXZ, double seatHeight, PartGroup group, double seatYaw, String role, int zoneIndex) {
        Vector3f local = new Vector3f(localXZ.x, (float)seatHeight, localXZ.z);
        Vector3f off = Transforms.localPointToWorld(local, group, this.hullYaw, this.gunYaw, this.gunPitch);
        Location loc = new Location(this.world, base.getX() + (double)off.x, base.getY() + (double)off.y, base.getZ() + (double)off.z);
        loc.setYaw((float)seatYaw);
        return (ArmorStand)this.world.spawn(loc, ArmorStand.class, a -> {
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
            AttributeInstance scaleAttr = a.getAttribute(Attribute.SCALE);
            if (scaleAttr != null) {
                scaleAttr.setBaseValue(this.plugin.config().seatScale);
            }
            this.tagEntity((Entity)a, role, zoneIndex);
        });
    }

    private void buildHitboxes(Location base) {
        this.hitboxes.clear();
        int i = 0;
        while (i < PickupModel.HITBOX_LOCAL.length) {
            int idx = i++;
            Interaction box = (Interaction)this.world.spawn(this.hitboxLocation(base, idx), Interaction.class, in -> {
                in.setInteractionWidth(PickupModel.HITBOX_LOCAL[idx][2]);
                in.setInteractionHeight(3.9f);
                in.setResponsive(true);
                in.setPersistent(true);
                this.tagEntity((Entity)in, "hitbox", idx);
            });
            this.hitboxes.add(box);
        }
    }

    private Location hitboxLocation(Location base, int index) {
        float[] xz = PickupModel.HITBOX_LOCAL[index];
        Vector3f off = Transforms.localPointToWorld(new Vector3f(xz[0], 0.0f, xz[1]), PartGroup.HULL, this.hullYaw, this.gunYaw, this.gunPitch);
        return new Location(this.world, base.getX() + (double)off.x, base.getY() + (double)off.y, base.getZ() + (double)off.z);
    }

    private void configureDisplay(Display d, int index) {
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(2.0f);
        d.setTeleportDuration(2);
        d.setInterpolationDuration(2);
        d.setPersistent(true);
        this.tagEntity((Entity)d, "part", index);
    }

    private void tagEntity(Entity e, String role, int index) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        pdc.set(Keys.PICKUP_ID, PersistentDataType.STRING, this.id.toString());
        pdc.set(Keys.PICKUP_PART, PersistentDataType.STRING, role);
        if (index >= 0) {
            pdc.set(Keys.PART_INDEX, PersistentDataType.INTEGER, index);
        }
        e.addScoreboardTag("pickupcraft_entity");
    }

    public void persistState() {
        if (this.driverSeat == null || !this.driverSeat.isValid()) {
            return;
        }
        PersistentDataContainer pdc = this.driverSeat.getPersistentDataContainer();
        pdc.set(Keys.STATE_HULL_YAW, PersistentDataType.DOUBLE, this.hullYaw);
        pdc.set(Keys.STATE_GUN_YAW, PersistentDataType.DOUBLE, this.gunYaw);
        pdc.set(Keys.STATE_GUN_PITCH, PersistentDataType.DOUBLE, this.gunPitch);
        pdc.set(Keys.STATE_HEALTH, PersistentDataType.DOUBLE, this.health);
        pdc.set(Keys.STATE_ANCHOR_X, PersistentDataType.DOUBLE, this.anchor.getX());
        pdc.set(Keys.STATE_ANCHOR_Y, PersistentDataType.DOUBLE, this.anchor.getY());
        pdc.set(Keys.STATE_ANCHOR_Z, PersistentDataType.DOUBLE, this.anchor.getZ());
        this.stateDirty = false;
        this.persistCooldown = 0;
    }

    public void tickPersist() {
        if (!this.stateDirty || !this.isActive()) {
            return;
        }
        if (++this.persistCooldown >= 20) {
            this.persistState();
        }
    }

    private void markStateDirty() {
        this.stateDirty = true;
    }

    private void markClean() {
        this.lastX = this.anchor.getX();
        this.lastY = this.anchor.getY();
        this.lastZ = this.anchor.getZ();
        this.lastHull = this.hullYaw;
        this.lastGunYaw = this.gunYaw;
        this.lastGunPitch = this.gunPitch;
        this.lastWheelSpin = this.wheelSpin;
        this.lastWheelSteer = this.wheelSteer;
        this.lastSeatLead = this.seatMotionLead();
    }

    public void forceRefresh() {
        this.lastX = Double.NaN;
        this.refreshModel();
    }

    public void refreshModel() {
        boolean seatLeadChanged;
        if (!this.spawned || this.driverSeat == null) {
            return;
        }
        boolean first = Double.isNaN(this.lastX);
        boolean moved = first || Math.abs(this.lastX - this.anchor.getX()) > 1.0E-5 || Math.abs(this.lastY - this.anchor.getY()) > 1.0E-5 || Math.abs(this.lastZ - this.anchor.getZ()) > 1.0E-5;
        boolean hullChanged = first || Math.abs(this.lastHull - this.hullYaw) > 1.0E-4;
        boolean gunYawChanged = first || Math.abs(this.lastGunYaw - this.gunYaw) > 1.0E-4;
        boolean gunPitchChanged = first || Math.abs(this.lastGunPitch - this.gunPitch) > 1.0E-4;
        boolean wheelChanged = first || Math.abs(this.lastWheelSpin - this.wheelSpin) > 0.1;
        boolean steerChanged = first || Math.abs(this.lastWheelSteer - this.wheelSteer) > 0.1;
        boolean bl = seatLeadChanged = first || Math.abs(this.lastSeatLead - this.seatMotionLead()) > 0.01;
        if (!(moved || hullChanged || gunYawChanged || gunPitchChanged || wheelChanged || steerChanged || seatLeadChanged)) {
            return;
        }
        Location base = new Location(this.world, this.anchor.getX(), this.anchor.getY(), this.anchor.getZ());
        if (moved || hullChanged || gunYawChanged || seatLeadChanged) {
            PickupConfig cfg = this.plugin.config();
            if (moved || hullChanged || seatLeadChanged) {
                this.teleportSeat(this.driverSeat, base, PickupModel.DRIVER_SEAT_XZ, cfg.driverSeatHeight, PartGroup.HULL, this.hullYaw);
                this.teleportSeat(this.passengerSeat, base, PickupModel.PASSENGER_SEAT_XZ, cfg.driverSeatHeight, PartGroup.HULL, this.hullYaw);
            }
            this.teleportSeat(this.gunnerSeat, base, PickupModel.GUNNER_SEAT_XZ, cfg.gunnerSeatHeight, PartGroup.MOUNT, this.gunYaw);
            if (moved || hullChanged) {
                for (int i = 0; i < this.hitboxes.size(); ++i) {
                    Interaction box = this.hitboxes.get(i);
                    if (box == null || !box.isValid()) continue;
                    box.teleport(this.hitboxLocation(base, i));
                }
            }
        }
        for (int i = 0; i < this.displays.size(); ++i) {
            boolean needTransform;
            Display d = this.displays.get(i);
            if (d == null || !d.isValid()) continue;
            if (moved) {
                d.teleport(base);
            }
            PickupPart part = this.partDefs.get(i);
            switch (part.group) {
                case HULL: {
                    needTransform = hullChanged || wheelChanged && part.rollsWithWheel || steerChanged && part.steersWithWheel;
                    break;
                }
                case MOUNT: {
                    needTransform = hullChanged || gunYawChanged;
                    break;
                }
                case BARREL: {
                    needTransform = hullChanged || gunYawChanged || gunPitchChanged;
                    break;
                }
                default: {
                    throw new MatchException(null, null);
                }
            }
            if (!needTransform) continue;
            Transformation t = Transforms.forPart(part, this.hullYaw, this.gunYaw, this.gunPitch, this.wheelSpin, this.wheelSteer);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(2);
            d.setTransformation(t);
        }
        this.markClean();
    }

    private void teleportSeat(ArmorStand seat, Location base, Vector3f localXZ, double seatHeight, PartGroup group, double seatYaw) {
        if (seat == null || !seat.isValid()) {
            return;
        }
        Vector3f local = new Vector3f(localXZ.x, (float)seatHeight, localXZ.z);
        Vector3f off = Transforms.localPointToWorld(local, group, this.hullYaw, this.gunYaw, this.gunPitch);
        Location loc = new Location(this.world, base.getX() + (double)off.x, base.getY() + (double)off.y, base.getZ() + (double)off.z);
        this.addSeatMotionLead(loc);
        loc.setYaw((float)seatYaw);
        try {
            seat.teleport(loc, new TeleportFlag[]{TeleportFlag.EntityState.RETAIN_PASSENGERS});
            seat.setRotation((float)seatYaw, 0.0f);
        }
        catch (Exception ex) {
            this.plugin.getLogger().fine("Pickup seat teleport failed: " + ex.getMessage());
        }
    }

    private double seatMotionLead() {
        if (Math.abs(this.speed) < 0.02) {
            return 0.0;
        }
        double lead = this.speed * 1.15;
        return Math.max(-0.52, Math.min(0.52, lead));
    }

    private void addSeatMotionLead(Location loc) {
        double lead = this.seatMotionLead();
        if (Math.abs(lead) < 1.0E-6) {
            return;
        }
        double yawRad = Math.toRadians(this.hullYaw);
        loc.add(-Math.sin(yawRad) * lead, 0.0, Math.cos(yawRad) * lead);
    }

    public void reapplyAppearance(PickupConfig cfg) {
        for (int i = 0; i < this.displays.size(); ++i) {
            Display d = this.displays.get(i);
            if (d == null || !d.isValid()) continue;
            PickupPart part = this.partDefs.get(i);
            if (!(d instanceof BlockDisplay)) continue;
            BlockDisplay bd = (BlockDisplay)d;
            bd.setBlock(part.material(cfg).createBlockData());
        }
    }

    public boolean refreshSubmerged() {
        boolean bl = this.submerged = this.spawned && this.fullyUnderWater();
        if (!this.submerged) {
            this.submergedTicks = 0;
        }
        return this.submerged;
    }

    public boolean isSubmerged() {
        return this.submerged;
    }

    private boolean fullyUnderWater() {
        int bx = this.anchor.getBlockX();
        int bz = this.anchor.getBlockZ();
        for (double dy : new double[]{0.3, 1.0, 1.8}) {
            Block b = this.world.getBlockAt(bx, (int)Math.floor(this.anchor.getY() + dy), bz);
            if (Pickup.isWater(b)) continue;
            return false;
        }
        return true;
    }

    private static boolean isWater(Block b) {
        Waterlogged w;
        if (b.getType() == Material.WATER) {
            return true;
        }
        BlockData blockData = b.getBlockData();
        return blockData instanceof Waterlogged && (w = (Waterlogged)blockData).isWaterlogged();
    }

    public void tickWater(PickupConfig cfg) {
        ++this.submergedTicks;
        if (this.submergedTicks % 6 == 0) {
            this.world.spawnParticle(Particle.BUBBLE_COLUMN_UP, this.anchor.clone().add(0.0, 1.2, 0.0), 10, 1.0, 1.0, 1.0, 0.02);
        }
        if (this.submergedTicks % 20 == 0) {
            this.world.playSound(this.anchor, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.6f);
            double dps = this.maxHealth() * cfg.drownDamagePercent / 100.0;
            if (dps > 0.0) {
                this.damage(dps);
            }
        }
    }

    public Location muzzleLocation() {
        Vector3f off = Transforms.localPointToWorld(PickupModel.MUZZLE_TIP, PartGroup.BARREL, this.hullYaw, this.gunYaw, this.gunPitch);
        return new Location(this.world, this.anchor.getX() + (double)off.x, this.anchor.getY() + (double)off.y, this.anchor.getZ() + (double)off.z);
    }

    public boolean damage(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0 || !this.isActive()) {
            return false;
        }
        this.health = Math.max(0.0, this.health - amount);
        this.markStateDirty();
        if (this.plugin.config().debug) {
            this.plugin.getLogger().info("[debug] pickup -" + String.format(Locale.US, "%.1f", amount) + " HP -> " + String.format(Locale.US, "%.1f", this.health));
        }
        if (this.health <= 0.0) {
            this.destroy(true);
            return true;
        }
        this.world.spawnParticle(Particle.CRIT, this.anchor.clone().add(0.0, 1.0, 0.0), 6, 0.7, 0.5, 0.7, 0.0);
        return false;
    }

    @Override
    public double repair(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0 || !this.isActive()) {
            return 0.0;
        }
        double before = this.health;
        this.health = Math.min(this.maxHealth(), this.health + amount);
        double restored = this.health - before;
        if (restored > 0.0) {
            this.markStateDirty();
        }
        return restored;
    }

    public void tickDamageEffects(long tickCounter, PickupConfig cfg) {
        int period;
        if (!cfg.damageSmoke || !this.isActive() || this.maxHealth() <= 0.0) {
            return;
        }
        double ratio = this.health / this.maxHealth();
        if (ratio > 0.5) {
            return;
        }
        int n = period = ratio > 0.25 ? 30 : 12;
        if (tickCounter % (long)period != 0L) {
            return;
        }
        Location vent = this.anchor.clone().add(0.0, 1.3, 0.0);
        this.world.spawnParticle(Particle.LARGE_SMOKE, vent, ratio > 0.25 ? 2 : 4, 0.4, 0.2, 0.4, 0.015);
        if (ratio <= 0.25) {
            this.world.spawnParticle(Particle.SMOKE, vent, 4, 0.4, 0.2, 0.4, 0.02);
            this.world.spawnParticle(Particle.ELECTRIC_SPARK, vent, 2, 0.25, 0.15, 0.25, 0.01);
        }
        if (ratio <= 0.1 && tickCounter % 60L == 0L) {
            this.world.playSound(vent, Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.8f);
        }
    }

    public void destroy(boolean effects) {
        if (effects && this.spawned) {
            Location c = this.anchor.clone().add(0.0, 0.8, 0.0);
            this.plugin.pickups().damagePickupsFromExplosion(c, this.plugin.config().explosionPower, this.id);
            this.plugin.pickups().setInternalExplosion(true);
            try {
                this.world.createExplosion(c, this.plugin.config().explosionPower, this.plugin.config().setFire, this.plugin.config().breakBlocks);
            }
            finally {
                this.plugin.pickups().setInternalExplosion(false);
            }
            this.world.spawnParticle(Particle.EXPLOSION_EMITTER, c, 2, 1.0, 0.6, 1.0, 0.0);
            this.world.spawnParticle(Particle.LARGE_SMOKE, c, 45, 1.1, 0.8, 1.1, 0.05);
            this.world.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 3.5f, 0.85f);
            if (this.plugin.config().debris) {
                this.flingDebris(c);
            }
            if (this.plugin.config().dropItemOnDestroy) {
                this.world.dropItemNaturally(c, PickupItem.create(this.plugin));
            }
            Explosions.impactAfterglow(this.plugin, c.clone(), this.plugin.config());
        }
        this.removeEntities();
    }

    private void flingDebris(Location c) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Material mat = this.plugin.config().hullBlock;
        final ArrayList<BlockDisplay> chunks = new ArrayList<BlockDisplay>();
        final ArrayList<Vector> velocities = new ArrayList<Vector>();
        for (int i = 0; i < 6; ++i) {
            BlockDisplay chunk = (BlockDisplay)this.world.spawn(c, BlockDisplay.class, b -> {
                b.setBlock(mat.createBlockData());
                b.setBrightness(new Display.Brightness(15, 15));
                b.setPersistent(false);
                Transformation t = b.getTransformation();
                t.getScale().set(0.35f);
                b.setTransformation(t);
            });
            chunks.add(chunk);
            velocities.add(new Vector(rng.nextGaussian() * 0.3, 0.35 + rng.nextDouble() * 0.4, rng.nextGaussian() * 0.3));
        }
        new BukkitRunnable(){
            int life = 28;

            public void run() {
                if (this.life-- <= 0) {
                    chunks.forEach(Entity::remove);
                    this.cancel();
                    return;
                }
                for (int i = 0; i < chunks.size(); ++i) {
                    BlockDisplay chunk = (BlockDisplay)chunks.get(i);
                    if (!chunk.isValid()) continue;
                    Vector vel = (Vector)velocities.get(i);
                    vel.setY(vel.getY() - 0.04);
                    chunk.teleport(chunk.getLocation().add(vel));
                }
            }
        }.runTaskTimer(this.plugin.bukkitPlugin(), 1L, 1L);
    }

    public void removeEntities() {
        if (this.driverSeat != null) {
            if (!this.driverSeat.getPassengers().isEmpty()) {
                this.driverSeat.eject();
            }
            this.driverSeat.remove();
            this.driverSeat = null;
        }
        if (this.passengerSeat != null) {
            if (!this.passengerSeat.getPassengers().isEmpty()) {
                this.passengerSeat.eject();
            }
            this.passengerSeat.remove();
            this.passengerSeat = null;
        }
        if (this.gunnerSeat != null) {
            if (!this.gunnerSeat.getPassengers().isEmpty()) {
                this.gunnerSeat.eject();
            }
            this.gunnerSeat.remove();
            this.gunnerSeat = null;
        }
        for (Interaction box : this.hitboxes) {
            if (box == null) continue;
            box.remove();
        }
        this.hitboxes.clear();
        for (Display d : this.displays) {
            if (d == null) continue;
            d.remove();
        }
        this.displays.clear();
        this.partDefs.clear();
        this.spawned = false;
    }

    public boolean isDriverSeatOccupied() {
        return this.driver != null;
    }

    public boolean isPassengerSeatOccupied() {
        return this.passenger != null;
    }

    public boolean isGunnerSeatOccupied() {
        return this.gunner != null;
    }

    public boolean mountDriver(Player player) {
        if (this.driverSeat == null || !this.driverSeat.isValid() || !this.driverSeat.addPassenger((Entity)player)) {
            return false;
        }
        this.driver = player.getUniqueId();
        this.markStateDirty();
        return true;
    }

    public boolean mountPassenger(Player player) {
        if (this.passengerSeat == null || !this.passengerSeat.isValid() || !this.passengerSeat.addPassenger((Entity)player)) {
            return false;
        }
        this.passenger = player.getUniqueId();
        return true;
    }

    public boolean mountGunner(Player player) {
        if (this.gunnerSeat == null || !this.gunnerSeat.isValid() || !this.gunnerSeat.addPassenger((Entity)player)) {
            return false;
        }
        this.gunner = player.getUniqueId();
        this.gunYaw = player.getLocation().getYaw();
        this.gunLock = this.plugin.config().mountGraceTicks;
        this.markStateDirty();
        return true;
    }

    public void clearDriver() {
        this.driver = null;
        this.speed = 0.0;
        this.wheelSteer = 0.0;
        this.refreshModel();
        this.persistState();
    }

    public void clearPassenger() {
        this.passenger = null;
    }

    public void clearGunner() {
        this.gunner = null;
        this.persistState();
    }

    public void ejectDriver() {
        if (this.driverSeat != null) {
            for (Entity p : new ArrayList<Entity>(this.driverSeat.getPassengers())) {
                this.driverSeat.removePassenger(p);
            }
        }
        this.driver = null;
    }

    public void ejectPassenger() {
        if (this.passengerSeat != null) {
            for (Entity p : new ArrayList<Entity>(this.passengerSeat.getPassengers())) {
                this.passengerSeat.removePassenger(p);
            }
        }
        this.passenger = null;
    }

    public void ejectGunner() {
        if (this.gunnerSeat != null) {
            for (Entity p : new ArrayList<Entity>(this.gunnerSeat.getPassengers())) {
                this.gunnerSeat.removePassenger(p);
            }
        }
        this.gunner = null;
    }

    public boolean tryRam(UUID victim, long nowMs, long cooldownMs) {
        Long last = this.ramCooldown.get(victim);
        if (last != null && nowMs - last < cooldownMs) {
            return false;
        }
        this.ramCooldown.put(victim, nowMs);
        if (this.ramCooldown.size() > 64) {
            this.ramCooldown.entrySet().removeIf(e -> nowMs - (Long)e.getValue() > cooldownMs * 4L);
        }
        return true;
    }

    public UUID id() {
        return this.id;
    }

    public World world() {
        return this.world;
    }

    public Location anchor() {
        return this.anchor;
    }

    public boolean isSpawned() {
        return this.spawned;
    }

    public boolean isActive() {
        return this.spawned && this.driverSeat != null && this.driverSeat.isValid();
    }

    public boolean validateEntities() {
        if (!(this.driverSeat != null && this.driverSeat.isValid() && this.passengerSeat != null && this.passengerSeat.isValid() && this.gunnerSeat != null && this.gunnerSeat.isValid())) {
            return false;
        }
        if (this.hitboxes.size() != PickupModel.HITBOX_LOCAL.length || this.displays.size() != this.partDefs.size() || this.partDefs.size() != PickupModel.parts().size()) {
            return false;
        }
        for (Interaction hitbox : this.hitboxes) {
            if (hitbox != null && hitbox.isValid()) continue;
            return false;
        }
        for (Display display : this.displays) {
            if (display != null && display.isValid()) continue;
            return false;
        }
        return true;
    }

    public ArmorStand driverSeat() {
        return this.driverSeat;
    }

    public ArmorStand passengerSeat() {
        return this.passengerSeat;
    }

    public ArmorStand gunnerSeat() {
        return this.gunnerSeat;
    }

    public List<Interaction> hitboxes() {
        return this.hitboxes;
    }

    public UUID driver() {
        return this.driver;
    }

    public UUID passenger() {
        return this.passenger;
    }

    public UUID gunner() {
        return this.gunner;
    }

    public double hullYaw() {
        return this.hullYaw;
    }

    public void setHullYaw(double v) {
        if (Math.abs(this.hullYaw - v) > 1.0E-4) {
            this.markStateDirty();
        }
        this.hullYaw = v;
    }

    public double gunYaw() {
        return this.gunYaw;
    }

    public void setGunYaw(double v) {
        if (Math.abs(this.gunYaw - v) > 1.0E-4) {
            this.markStateDirty();
        }
        this.gunYaw = v;
    }

    public double gunPitch() {
        return this.gunPitch;
    }

    public void setGunPitch(double v) {
        if (Math.abs(this.gunPitch - v) > 1.0E-4) {
            this.markStateDirty();
        }
        this.gunPitch = v;
    }

    public double speed() {
        return this.speed;
    }

    public void setSpeed(double v) {
        this.speed = v;
    }

    public int forwardHoldTicks() {
        return this.forwardHoldTicks;
    }

    public void setForwardHoldTicks(int t) {
        this.forwardHoldTicks = t;
    }

    public int backwardHoldTicks() {
        return this.backwardHoldTicks;
    }

    public void setBackwardHoldTicks(int t) {
        this.backwardHoldTicks = t;
    }

    public double wheelSteer() {
        return this.wheelSteer;
    }

    public void setWheelSteer(double degrees) {
        this.wheelSteer = degrees;
    }

    public void advanceWheelSpin(double signedDistance) {
        if (Math.abs(signedDistance) < 1.0E-6) {
            return;
        }
        double degrees = Math.toDegrees(signedDistance / (double)0.675f);
        this.wheelSpin += degrees * 0.35;
        if (Math.abs(this.wheelSpin) > 3600.0) {
            this.wheelSpin %= 360.0;
        }
    }

    public double verticalVelocity() {
        return this.verticalVelocity;
    }

    public void setVerticalVelocity(double v) {
        this.verticalVelocity = v;
    }

    public double health() {
        return this.health;
    }

    public double maxHealth() {
        return this.plugin.config().maxHealth;
    }

    @Override
    public String type() {
        return "pickup";
    }

    @Override
    public Entity coreEntity() {
        return this.driverSeat;
    }

    @Override
    public Location location() {
        return this.anchor.clone();
    }

    @Override
    public List<Interaction> collisionEntities() {
        return List.copyOf(this.hitboxes);
    }

    @Override
    public void applyAntiAirHit() {
        this.damage(this.plugin.config().creeperDamage);
    }

    @Override
    public void applyExplosion(Location loc, double power) {
        Explosions.applyBlastTo(this, loc, power, this.plugin.config());
    }

    @Override
    public boolean handlesBukkitExplosionEvents() {
        return true;
    }

    public int gunLock() {
        return this.gunLock;
    }

    public void setGunLock(int t) {
        this.gunLock = t;
    }

    public int gunCooldown() {
        return this.gunCooldown;
    }

    public void setGunCooldown(int t) {
        this.gunCooldown = t;
    }

    public int overheatTicks() {
        return this.overheatTicks;
    }

    public void setOverheatTicks(int t) {
        this.overheatTicks = t;
    }

    public boolean isOverheated() {
        return this.overheatTicks > 0;
    }

    public boolean recordShotAndCheckOverheat(PickupConfig cfg) {
        long now = this.world.getFullTime();
        this.recentShotTicks.addLast(now);
        while (!this.recentShotTicks.isEmpty() && now - this.recentShotTicks.peekFirst() > (long)cfg.overheatWindowTicks) {
            this.recentShotTicks.pollFirst();
        }
        if (this.recentShotTicks.size() > cfg.overheatShotLimit) {
            this.recentShotTicks.clear();
            this.overheatTicks = cfg.overheatDurationTicks;
            return true;
        }
        return false;
    }
}
