package me.bibo.militarycraft.vehicles.train.train;

import io.papermc.paper.entity.TeleportFlag;
import me.bibo.militarycraft.vehicles.train.TrainRuntime;
import me.bibo.militarycraft.vehicles.train.model.CarTransforms;
import me.bibo.militarycraft.vehicles.train.model.TrainModel;
import me.bibo.militarycraft.vehicles.train.model.TrainPart;
import me.bibo.militarycraft.vehicles.train.util.Keys;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One car of the train: the locomotive (index 0) or a passenger wagon. Owns
 * its block-display model parts, the Interaction hitboxes players right-click
 * to hop on, and lazily-spawned invisible seats. The whole car is posed by a
 * single (centre, yaw, pitch) each tick. Static BlockDisplay parts use normal
 * smoothing; spinning wheels are ItemDisplays because item models are centered,
 * giving them a real pivot without a translation/rotation compensation fight.
 */
public final class TrainCar {

    /** Bogie anchors sit this fraction of the car length in from each end. */
    public static final double BOGIE_INSET = 0.2;
    private static final int TELEPORT_SMOOTH_TICKS = 2;
    private static final int STATIC_TRANSFORM_TICKS = 2;
    private static final int ANIMATED_TRANSFORM_TICKS = 2;

    private final TrainRuntime plugin;
    private final UUID trainId;
    private final int index;
    private final float length;
    private final List<TrainPart> parts;
    private final float[] hitboxZ;
    private final float[][] seatSpots;

    private final World world;
    private final List<Display> displays = new ArrayList<>();
    private final List<Interaction> interactions = new ArrayList<>();
    private final Map<Integer, ArmorStand> seats = new HashMap<>();

    private Location center;
    private double yaw;
    private double pitch;
    private double lastYaw = Double.NaN;
    private double lastPitch = Double.NaN;
    private boolean spawned;
    private CarTransforms.WheelPhases phases = CarTransforms.WheelPhases.ZERO;

    public TrainCar(TrainRuntime plugin, UUID trainId, World world, int index) {
        this.plugin = plugin;
        this.trainId = trainId;
        this.world = world;
        this.index = index;
        if (index == 0) {
            this.parts = TrainModel.locomotive();
            this.length = TrainModel.LOCO_LENGTH;
            this.hitboxZ = TrainModel.LOCO_HITBOX_Z;
            this.seatSpots = TrainModel.LOCO_SEATS;
        } else {
            this.parts = TrainModel.wagon(index - 1);
            this.length = TrainModel.WAGON_LENGTH;
            this.hitboxZ = TrainModel.WAGON_HITBOX_Z;
            this.seatSpots = TrainModel.WAGON_SEATS;
        }
    }

    public float length() {
        return length;
    }

    public int index() {
        return index;
    }

    public double yaw() {
        return yaw;
    }

    public Location worldCenter() {
        return center == null ? null : center.clone();
    }

    public boolean isValid() {
        return modelEntitiesValid();
    }

    public List<Interaction> interactions() {
        return List.copyOf(interactions);
    }

    /** Car-space point to a world location at the car's current pose. */
    public Location worldPoint(Vector3f local) {
        Vector3f off = CarTransforms.localPointToWorld(local, yaw, pitch);
        return new Location(world, center.getX() + off.x, center.getY() + off.y, center.getZ() + off.z);
    }

    // ------------------------------------------------------------------ pose

    /**
     * Move the car to a new pose. First call spawns the entities in place;
     * afterwards displays are teleported every tick (2-tick client
     * interpolation) and transformations are re-pushed when the car has
     * rotated OR the part animates on its own (wheels/rods spin continuously
     * even on a dead-straight track), so cruising still costs no transform
     * packets for the ~90% of parts that never move relative to the car.
     */
    public void refresh(Location newCenter, double newYaw, double newPitch, CarTransforms.WheelPhases phases) {
        this.center = newCenter;
        this.yaw = newYaw;
        this.pitch = newPitch;
        this.phases = phases;
        if (!spawned) {
            spawnEntities();
            spawned = true;
            lastYaw = newYaw;
            lastPitch = newPitch;
            return;
        }
        if (!modelEntitiesValid()) {
            respawnModelEntities();
            lastYaw = newYaw;
            lastPitch = newPitch;
        }
        boolean rotated = Math.abs(angleDiff(newYaw, lastYaw)) > 0.02
                || Math.abs(newPitch - lastPitch) > 0.02;
        for (int i = 0; i < displays.size(); i++) {
            Display d = displays.get(i);
            if (d == null || !d.isValid()) {
                continue;
            }
            TrainPart part = parts.get(i);
            d.teleport(center);
            if (rotated || part.animated()) {
                d.setInterpolationDelay(0);
                d.setTransformation(transformationFor(d, part));
            }
        }
        if (rotated) {
            lastYaw = newYaw;
            lastPitch = newPitch;
        }
        for (int i = 0; i < interactions.size(); i++) {
            Interaction box = interactions.get(i);
            if (box != null && box.isValid()) {
                box.teleport(hitboxLocation(i));
            }
        }
        for (Map.Entry<Integer, ArmorStand> e : seats.entrySet()) {
            ArmorStand s = e.getValue();
            if (s != null && s.isValid()) {
                try {
                    s.teleport(seatLocation(e.getKey()), TeleportFlag.EntityState.RETAIN_PASSENGERS);
                } catch (Exception ex) {
                    plugin.getLogger().fine("Seat teleport failed: " + ex.getMessage());
                }
            }
        }
    }

    /** Restore transient display/hitbox entities after a chunk unload or external cleanup. */
    public void repairModelIfNeeded() {
        if (center == null || modelEntitiesValid()) {
            return;
        }
        respawnModelEntities();
        lastYaw = yaw;
        lastPitch = pitch;
    }

    private static double angleDiff(double a, double b) {
        double d = (a - b) % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        } else if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }

    private void spawnEntities() {
        float viewRange = plugin.cfg().viewRange;
        for (int i = 0; i < parts.size(); i++) {
            TrainPart part = parts.get(i);
            final int idx = i;
            Display d;
            if (usesCenteredWheelDisplay(part)) {
                d = world.spawn(center, ItemDisplay.class, item -> {
                    configureDisplay(item, part, viewRange, idx);
                    item.setItemStack(new ItemStack(part.material));
                    item.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    item.setTransformation(CarTransforms.forCenteredPart(part, yaw, pitch, phases));
                });
            } else {
                d = world.spawn(center, BlockDisplay.class, block -> {
                    configureDisplay(block, part, viewRange, idx);
                    block.setBlock(part.material.createBlockData());
                    block.setTransformation(CarTransforms.forBlockPart(part, yaw, pitch, phases));
                });
            }
            displays.add(d);
        }
        for (int i = 0; i < hitboxZ.length; i++) {
            final int idx = i;
            Interaction box = world.spawn(hitboxLocation(i), Interaction.class, in -> {
                in.setInteractionWidth(TrainModel.HITBOX_WIDTH);
                in.setInteractionHeight(TrainModel.HITBOX_HEIGHT);
                in.setResponsive(true);
                in.setPersistent(false);
                tag(in, "hitbox", idx);
            });
            interactions.add(box);
        }
    }

    private boolean modelEntitiesValid() {
        if (!spawned || displays.size() != parts.size() || interactions.size() != hitboxZ.length) {
            return false;
        }
        for (Display d : displays) {
            if (d == null || !d.isValid()) {
                return false;
            }
        }
        for (Interaction box : interactions) {
            if (box == null || !box.isValid()) {
                return false;
            }
        }
        return true;
    }

    private void respawnModelEntities() {
        removeModelEntities();
        spawnEntities();
        spawned = true;
    }

    private void removeModelEntities() {
        for (Interaction box : interactions) {
            if (box != null) {
                box.remove();
            }
        }
        interactions.clear();
        for (Display d : displays) {
            if (d != null) {
                d.remove();
            }
        }
        displays.clear();
        spawned = false;
    }

    private Location hitboxLocation(int i) {
        return worldPoint(new Vector3f(0f, 0f, hitboxZ[i]));
    }

    private void configureDisplay(Display d, TrainPart part, float viewRange, int idx) {
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(viewRange);
        d.setTeleportDuration(TELEPORT_SMOOTH_TICKS);
        d.setInterpolationDuration(transformDuration(part));
        d.setInterpolationDelay(0);
        d.setPersistent(false);
        tag(d, "part", idx);
    }

    private org.bukkit.util.Transformation transformationFor(Display d, TrainPart part) {
        if (d instanceof ItemDisplay && usesCenteredWheelDisplay(part)) {
            return CarTransforms.forCenteredPart(part, yaw, pitch, phases);
        }
        return CarTransforms.forBlockPart(part, yaw, pitch, phases);
    }

    private static boolean usesCenteredWheelDisplay(TrainPart part) {
        return part.anim == TrainPart.Anim.WHEEL && part.material.isItem();
    }

    private static int transformDuration(TrainPart part) {
        return part.animated() ? ANIMATED_TRANSFORM_TICKS : STATIC_TRANSFORM_TICKS;
    }

    private void tag(Entity e, String role, int idx) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        pdc.set(Keys.TRAIN_ID, PersistentDataType.STRING, trainId.toString());
        pdc.set(Keys.ROLE, PersistentDataType.STRING, role);
        pdc.set(Keys.CAR_INDEX, PersistentDataType.INTEGER, index);
        e.addScoreboardTag(Keys.SCOREBOARD_TAG);
    }

    // ----------------------------------------------------------------- seats

    private Location seatLocation(int slot) {
        float[] spot = seatSpots[slot];
        double h = index == 0 ? plugin.cfg().seatHeightLocomotive : plugin.cfg().seatHeightWagon;
        Location loc = worldPoint(new Vector3f(spot[0], (float) h, spot[1]));
        loc.setYaw((float) yaw);
        return loc;
    }

    public int capacity() {
        int configured = index == 0 ? plugin.cfg().seatsLocomotive : plugin.cfg().seatsWagon;
        return Math.min(configured, seatSpots.length);
    }

    /** Seat the player in the first free slot; false if the car is full. */
    public boolean board(Player player) {
        for (int slot = 0; slot < capacity(); slot++) {
            ArmorStand s = seats.get(slot);
            if (s != null && s.isValid()) {
                if (!s.getPassengers().isEmpty()) {
                    continue;
                }
                return s.addPassenger(player);
            }
            ArmorStand seat = spawnSeat(slot);
            seats.put(slot, seat);
            return seat.addPassenger(player);
        }
        return false;
    }

    private ArmorStand spawnSeat(int slot) {
        return world.spawn(seatLocation(slot), ArmorStand.class, a -> {
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
            // Tagged so a purge sweeps strays, but transient: never persisted.
            a.addScoreboardTag(Keys.SCOREBOARD_TAG);
        });
    }

    /** @return true if this car owned that seat entity. */
    public boolean ownsSeat(Entity seat) {
        for (ArmorStand s : seats.values()) {
            if (s != null && s.getUniqueId().equals(seat.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    /** Drop seats whose rider vanished without a dismount (death, kick…). */
    public void pruneSeats() {
        seats.values().removeIf(s -> {
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

    public List<Player> passengers() {
        List<Player> out = new ArrayList<>();
        for (ArmorStand s : seats.values()) {
            if (s == null || !s.isValid()) {
                continue;
            }
            for (Entity e : s.getPassengers()) {
                if (e instanceof Player p) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- remove

    public void remove() {
        for (ArmorStand s : seats.values()) {
            if (s != null) {
                if (!s.getPassengers().isEmpty()) {
                    s.eject();
                }
                s.remove();
            }
        }
        seats.clear();
        removeModelEntities();
    }
}
