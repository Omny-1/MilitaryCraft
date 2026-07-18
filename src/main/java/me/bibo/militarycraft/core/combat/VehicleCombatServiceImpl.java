package me.bibo.militarycraft.core.combat;

import me.bibo.militarycraft.core.event.ExplosionSink;
import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import me.bibo.militarycraft.core.vehicle.VehicleService;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The single {@link ExplosionSink} registrant for vehicle blast-routing (§6/§11).
 * Deliberately the ONLY class that turns an {@code EntityExplodeEvent}/
 * {@code BlockExplodeEvent} into {@link VehicleHandle#applyExplosion} calls — if every
 * {@code VehicleManager} also implemented {@code ExplosionSink} the same blast would
 * be applied once per registered manager instead of once total, since
 * {@link #explosionDamage} already fans out across every vehicle type via
 * {@link VehicleService#all()}.
 */
public final class VehicleCombatServiceImpl implements VehicleCombatService, ExplosionSink {

    private final VehicleService vehicles;

    public VehicleCombatServiceImpl(VehicleService vehicles) {
        this.vehicles = vehicles;
    }

    @Override
    public boolean antiAirHit(Entity vehiclePart) {
        VehicleHandle handle = vehicles.vehicleOf(vehiclePart);
        if (handle == null || !handle.isActive()) {
            return false;
        }
        handle.applyAntiAirHit();
        return true;
    }

    @Override
    public boolean directDamage(Entity vehiclePart, double amount) {
        return directDamage(vehicles.vehicleOf(vehiclePart), amount);
    }

    @Override
    public boolean directDamage(VehicleHandle vehicle, double amount) {
        if (vehicle == null || !vehicle.isActive() || !Double.isFinite(amount) || amount <= 0.0) {
            return false;
        }
        vehicle.damage(amount);
        return true;
    }

    @Override
    public double repair(VehicleHandle vehicle, double amount) {
        if (vehicle == null || !vehicle.isActive() || !Double.isFinite(amount) || amount <= 0.0) {
            return 0.0;
        }
        return vehicle.repair(amount);
    }

    @Override
    public VehicleHit rayTrace(Location origin, Vector direction, double range, double pad, UUID excludedVehicle) {
        if (origin == null || origin.getWorld() == null || direction == null
                || !Double.isFinite(range) || range <= 0.0 || direction.lengthSquared() <= 1.0e-8) {
            return null;
        }
        World world = origin.getWorld();
        Vector dir = direction.clone().normalize();
        double safePad = Math.max(0.0, pad);
        VehicleHit best = null;
        double bestDistance = range;
        for (VehicleHandle handle : vehicles.all()) {
            if (!isUsableInWorld(handle, world)
                    || excludedVehicle != null && excludedVehicle.equals(handle.id())) {
                continue;
            }
            VehicleHit hit = rayTraceBody(handle, origin.toVector(), dir, range, safePad);
            if (hit == null || hit.distance() >= bestDistance) {
                continue;
            }
            if (world.rayTraceBlocks(origin, dir, Math.max(0.1, hit.distance() - 0.05),
                    FluidCollisionMode.NEVER, true) != null) {
                continue;
            }
            best = hit;
            bestDistance = hit.distance();
        }
        return best;
    }

    @Override
    public VehicleHit vehicleNear(Location center, double radius, UUID excludedVehicle) {
        List<VehicleHit> hits = vehiclesNear(center, radius, excludedVehicle);
        return hits.isEmpty() ? null : hits.get(0);
    }

    @Override
    public List<VehicleHit> vehiclesNear(Location center, double radius, UUID excludedVehicle) {
        if (center == null || center.getWorld() == null || !Double.isFinite(radius) || radius <= 0.0) {
            return List.of();
        }
        World world = center.getWorld();
        double radiusSquared = radius * radius;
        List<VehicleHit> hits = new ArrayList<>();
        for (VehicleHandle handle : vehicles.all()) {
            if (!isUsableInWorld(handle, world)
                    || excludedVehicle != null && excludedVehicle.equals(handle.id())) {
                continue;
            }
            Vector point = closestPointOnBody(handle, center.toVector(), 0.0);
            double squared = center.toVector().distanceSquared(point);
            if (squared <= radiusSquared) {
                hits.add(new VehicleHit(handle, new Location(world,
                        point.getX(), point.getY(), point.getZ()), Math.sqrt(Math.max(0.0, squared))));
            }
        }
        hits.sort(Comparator.comparingDouble(VehicleHit::distance));
        return List.copyOf(hits);
    }

    @Override
    public int radiusDamage(Location center, double radius, double maxDamage, UUID excludedVehicle, UUID excludedDirectHit) {
        if (center == null || center.getWorld() == null || !Double.isFinite(radius) || radius <= 0.0
                || !Double.isFinite(maxDamage) || maxDamage <= 0.0) {
            return 0;
        }
        int damaged = 0;
        for (VehicleHandle handle : vehicles.all()) {
            if (!isUsableInWorld(handle, center.getWorld())
                    || excludedVehicle != null && excludedVehicle.equals(handle.id())
                    || excludedDirectHit != null && excludedDirectHit.equals(handle.id())) {
                continue;
            }
            double distance = Math.sqrt(distanceSquaredToBody(handle, center.toVector(), 0.0));
            if (distance > radius) {
                continue;
            }
            double factor = 1.0 - Math.min(1.0, distance / radius);
            if (factor <= 0.0) {
                continue;
            }
            handle.damage(maxDamage * factor);
            damaged++;
        }
        return damaged;
    }

    @Override
    public void explosionDamage(Location loc, double power) {
        explosionDamage(loc, power, null);
    }

    @Override
    public void explosionDamage(Location loc, double power, UUID excludedVehicle) {
        if (loc == null || loc.getWorld() == null || !Double.isFinite(power) || power <= 0.0) {
            return;
        }
        for (VehicleHandle handle : vehicles.all()) {
            if (!isUsableInWorld(handle, loc.getWorld())
                    || excludedVehicle != null && excludedVehicle.equals(handle.id())) {
                continue;
            }
            handle.applyExplosion(loc, power);
        }
    }

    @Override
    public void onEntityExplode(EntityExplodeEvent event) {
        if (Explosions.isInternal()) {
            return; // our own shell/self-destruct — already handled directly
        }
        routeBukkitExplosion(event.getLocation(), Explosions.powerFor(event.getEntity()));
    }

    @Override
    public void onBlockExplode(BlockExplodeEvent event) {
        if (Explosions.isInternal()) {
            return;
        }
        routeBukkitExplosion(event.getBlock().getLocation().add(0.5, 0.5, 0.5), 5.0);
    }

    private void routeBukkitExplosion(Location loc, double power) {
        if (loc == null || loc.getWorld() == null || !Double.isFinite(power) || power <= 0.0) {
            return;
        }
        for (VehicleHandle handle : vehicles.all()) {
            if (isUsableInWorld(handle, loc.getWorld()) && !handle.handlesBukkitExplosionEvents()) {
                handle.applyExplosion(loc, power);
            }
        }
    }

    private static VehicleHit rayTraceBody(VehicleHandle vehicle, Vector origin, Vector direction,
                                           double reach, double pad) {
        VehicleHit best = null;
        double bestDistance = reach;
        Collection<? extends Entity> parts = vehicle.collisionEntities();
        Location vehicleLocation = vehicle.location();
        World world = vehicleLocation == null ? null : vehicleLocation.getWorld();
        if (parts == null || world == null) {
            return null;
        }
        for (Entity part : parts) {
            if (part == null || !part.isValid() || part.getWorld() != world) {
                continue;
            }
            BoundingBox box = part.getBoundingBox();
            if (pad > 0.0) {
                box.expand(pad);
            }
            RayTraceResult result = box.rayTrace(origin, direction, reach);
            if (result == null || result.getHitPosition() == null) {
                continue;
            }
            Vector point = result.getHitPosition();
            double distance = origin.distance(point);
            if (distance < bestDistance) {
                best = new VehicleHit(vehicle,
                        new Location(world, point.getX(), point.getY(), point.getZ()), distance);
                bestDistance = distance;
            }
        }
        return best;
    }

    private static double distanceSquaredToBody(VehicleHandle vehicle, Vector worldPoint, double pad) {
        return worldPoint.distanceSquared(closestPointOnBody(vehicle, worldPoint, pad));
    }

    private static Vector closestPointOnBody(VehicleHandle vehicle, Vector worldPoint, double pad) {
        Vector best = null;
        double bestSquared = Double.MAX_VALUE;
        Collection<? extends Entity> parts = vehicle.collisionEntities();
        Location vehicleLocation = vehicle.location();
        World world = vehicleLocation == null ? null : vehicleLocation.getWorld();
        if (parts != null) {
            for (Entity part : parts) {
                if (part == null || !part.isValid() || part.getWorld() != world) {
                    continue;
                }
                BoundingBox box = part.getBoundingBox();
                if (pad > 0.0) {
                    box.expand(pad);
                }
                Vector point = new Vector(
                        clamp(worldPoint.getX(), box.getMinX(), box.getMaxX()),
                        clamp(worldPoint.getY(), box.getMinY(), box.getMaxY()),
                        clamp(worldPoint.getZ(), box.getMinZ(), box.getMaxZ()));
                double squared = worldPoint.distanceSquared(point);
                if (squared < bestSquared) {
                    best = point;
                    bestSquared = squared;
                }
            }
        }
        return best != null ? best : vehicleLocation.toVector();
    }

    private static boolean isUsableInWorld(VehicleHandle handle, World world) {
        if (handle == null || !handle.isActive() || world == null) {
            return false;
        }
        Location location = handle.location();
        return location != null && location.getWorld() == world;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
