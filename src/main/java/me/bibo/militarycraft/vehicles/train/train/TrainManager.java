package me.bibo.militarycraft.vehicles.train.train;

import me.bibo.militarycraft.vehicles.train.TrainRuntime;
import me.bibo.militarycraft.vehicles.train.model.TrainModel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import me.bibo.militarycraft.vehicles.train.util.Keys;

/** Registry of live trains plus the single global tick task. */
public final class TrainManager {

    private final TrainRuntime plugin;
    private final Map<UUID, Train> trains = new LinkedHashMap<>();
    private final Map<UUID, Long> fallProtectedUntil = new HashMap<>();
    /** Per-train consecutive tick-failure count; a persistently poison train is quarantined. */
    private final java.util.Map<UUID, Integer> tickFailures = new java.util.HashMap<>();
    private static final int MAX_TICK_FAILURES = 3;
    private BukkitTask task;

    public TrainManager(TrainRuntime plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin.bukkitPlugin(), this::tickAll, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        removeAll();
    }

    private void tickAll() {
        if (trains.isEmpty()) {
            return;
        }
        for (Iterator<Train> it = new ArrayList<>(trains.values()).iterator(); it.hasNext(); ) {
            Train t = it.next();
            try {
                t.tick();
            } catch (Exception ex) {
                // Isolate a faulting train so one bad object cannot cancel the shared tick
                // task and freeze EVERY train. Tolerate a couple of transient failures, but
                // QUARANTINE a persistently poison train - otherwise it throws 20x/sec
                // forever, silently, while holding tickets/displays and a maxTrains slot.
                int fails = tickFailures.merge(t.id(), 1, Integer::sum);
                if (fails == 1 || fails == MAX_TICK_FAILURES) {
                    plugin.bukkitPlugin().getLogger().log(java.util.logging.Level.WARNING,
                            "Train " + t.id() + " tick failed (" + fails + "/" + MAX_TICK_FAILURES + ")", ex);
                }
                if (fails >= MAX_TICK_FAILURES) {
                    try {
                        t.remove();
                    } catch (Exception cleanup) {
                        plugin.bukkitPlugin().getLogger().log(java.util.logging.Level.WARNING,
                                "Cleanup of poison train " + t.id() + " also failed", cleanup);
                    }
                    trains.remove(t.id());
                    tickFailures.remove(t.id());
                }
                continue;
            }
            tickFailures.remove(t.id());
            if (t.isRemoved()) {
                trains.remove(t.id());
            }
        }
    }

    // -------------------------------------------------------------- registry

    public int count() {
        return trains.size();
    }

    public Train spawn(Block rail, Player placer) {
        if (trains.size() >= plugin.cfg().maxTrains) {
            placer.sendActionBar(Component.text(
                    "Too many trains (limit " + plugin.cfg().maxTrains + ")", NamedTextColor.RED));
            return null;
        }
        Train t = Train.spawn(plugin, rail, placer);
        if (t != null) {
            trains.put(t.id(), t);
        }
        return t;
    }

    public Train spawn(Block rail, CommandSender sender, double yawDegrees) {
        if (trains.size() >= plugin.cfg().maxTrains) {
            sender.sendMessage(Component.text("Too many trains (limit " + plugin.cfg().maxTrains + ")",
                    NamedTextColor.RED));
            return null;
        }
        Train t = Train.spawn(plugin, rail, yawDegrees);
        if (t != null) {
            trains.put(t.id(), t);
        }
        return t;
    }

    public Train byId(UUID id) {
        return trains.get(id);
    }

    public Train byEntity(Entity e) {
        String raw = e.getPersistentDataContainer().get(Keys.TRAIN_ID, PersistentDataType.STRING);
        if (raw == null) {
            for (Train train : trains.values()) {
                for (TrainCar car : train.cars()) {
                    if (car.ownsSeat(e)) {
                        return train;
                    }
                }
            }
            return null;
        }
        try {
            return trains.get(UUID.fromString(raw));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public Collection<Train> all() {
        return trains.values();
    }

    /** The train and car that own this seat armour stand, or null. */
    public TrainCar carOfSeat(Entity seat) {
        if (!(seat instanceof ArmorStand)) {
            return null;
        }
        for (Train t : trains.values()) {
            for (TrainCar car : t.cars()) {
                if (car.ownsSeat(seat)) {
                    return car;
                }
            }
        }
        return null;
    }

    public void removeTrain(Train t) {
        t.remove(); // grants fall protection to riders itself
        trains.remove(t.id());
    }

    public Train nearest(Location loc, double radius) {
        Train best = null;
        double bestSq = radius * radius;
        for (Train t : trains.values()) {
            if (!t.world().equals(loc.getWorld())) {
                continue;
            }
            Location c = t.locoCenter();
            if (c == null) {
                continue;
            }
            double d = c.distanceSquared(loc);
            if (d <= bestSq) {
                bestSq = d;
                best = t;
            }
        }
        return best;
    }

    public int removeAll() {
        int n = trains.size();
        for (Train t : new ArrayList<>(trains.values())) {
            removeTrain(t);
        }
        trains.clear();
        return n;
    }

    public void removeInWorld(World w) {
        for (Train t : new ArrayList<>(trains.values())) {
            if (t.world().equals(w)) {
                removeTrain(t);
            }
        }
    }

    // ------------------------------------------------------------ dismounting

    public void grantFallProtection(Player p) {
        fallProtectedUntil.put(p.getUniqueId(), System.currentTimeMillis() + 5000L);
    }

    public boolean isFallProtected(Player p) {
        Long until = fallProtectedUntil.get(p.getUniqueId());
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() > until) {
            fallProtectedUntil.remove(p.getUniqueId());
            return false;
        }
        return true;
    }

    /**
     * After a rider leaves a seat: shield them from the landing and, a tick
     * later, set them down on safe ground beside the track (so they don't pop
     * out inside the moving model) and sweep the now-empty seat. Everything
     * mutating is deferred - this runs inside the dismount event, possibly
     * while the car is iterating its own seats during removal.
     */
    public void handleSeatDismount(Player player, Entity seat) {
        grantFallProtection(player);
        if (!plugin.isEnabled()) {
            return; // server shutting down: eject fired from our own cleanup
        }
        TrainCar car = carOfSeat(seat);
        Location spot = car == null ? null : findDismountSpot(car);
        plugin.getServer().getScheduler().runTask(plugin.bukkitPlugin(), () -> {
            if (car != null) {
                car.pruneSeats();
            }
            if (!player.isOnline() || player.getVehicle() != null) {
                return;
            }
            if (spot != null) {
                spot.setYaw(player.getLocation().getYaw());
                spot.setPitch(player.getLocation().getPitch());
                player.teleport(spot);
            }
        });
    }

    public void handleQuit(Player player) {
        fallProtectedUntil.remove(player.getUniqueId());
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin.bukkitPlugin(), () -> {
            for (Train t : trains.values()) {
                for (TrainCar car : t.cars()) {
                    car.pruneSeats();
                }
            }
        });
    }

    /** Ground beside the car: right, then left, then behind; null = stay put. */
    private Location findDismountSpot(TrainCar car) {
        Location c = car.worldCenter();
        if (c == null) {
            return null;
        }
        World w = c.getWorld();
        double yawRad = Math.toRadians(car.yaw());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double lat = TrainModel.WIDTH / 2.0 + 1.3;
        double back = car.length() / 2.0 + 2.0;
        List<Vector> candidates = List.of(
                new Vector(-fz * lat, 0, fx * lat),
                new Vector(fz * lat, 0, -fx * lat),
                new Vector(-fx * back, 0, -fz * back));
        for (Vector off : candidates) {
            int bx = (int) Math.floor(c.getX() + off.getX());
            int bz = (int) Math.floor(c.getZ() + off.getZ());
            for (int dy = 1; dy >= -3; dy--) {
                int by = (int) Math.floor(c.getY()) + dy;
                Block feet = w.getBlockAt(bx, by, bz);
                if (feet.isPassable()
                        && feet.getRelative(0, 1, 0).isPassable()
                        && !feet.getRelative(0, -1, 0).isPassable()) {
                    return new Location(w, bx + 0.5, by, bz + 0.5);
                }
            }
        }
        return null;
    }
}
