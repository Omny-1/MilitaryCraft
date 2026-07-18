package me.bibo.militarycraft.core.vehicle;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Live vehicle query surface. Every shipping module is an autonomous manager exposed
 * through a {@link VehicleProvider} (see {@link ManagedVehicleProvider}); this service
 * fans queries across those providers so cross-module combat/camera can treat any
 * vehicle uniformly without shared inheritance.
 */
public final class VehicleServiceImpl implements VehicleService {

    /** How deep to walk a stack of nested vehicles looking for one of ours (VehicleCameraPlugin's own constant). */
    private static final int MAX_VEHICLE_DEPTH = 4;

    private final CopyOnWriteArrayList<VehicleProvider> providers = new CopyOnWriteArrayList<>();

    @Override
    public void registerProvider(VehicleProvider provider) {
        if (provider != null) {
            providers.addIfAbsent(provider);
        }
    }

    @Override
    public void unregisterProvider(VehicleProvider provider) {
        providers.remove(provider);
    }

    @Override
    public VehicleHandle vehicleOf(Entity anyPart) {
        if (anyPart == null) {
            return null;
        }
        for (VehicleProvider provider : providers) {
            VehicleHandle vehicle = provider.vehicleOf(anyPart);
            if (vehicle != null) {
                return vehicle;
            }
        }
        return null;
    }

    @Override
    public String typeOf(Entity anyPart) {
        VehicleHandle handle = vehicleOf(anyPart);
        return handle != null ? handle.type() : null;
    }

    @Override
    public VehicleHandle riddenBy(Player player) {
        Entity vehicle = player.getVehicle();
        int depth = 0;
        while (vehicle != null && depth++ < MAX_VEHICLE_DEPTH) {
            VehicleHandle handle = vehicleOf(vehicle);
            if (handle != null) {
                return handle;
            }
            vehicle = vehicle.getVehicle();
        }
        return null;
    }

    @Override
    public Collection<VehicleHandle> all() {
        Map<UUID, VehicleHandle> unique = new LinkedHashMap<>();
        for (VehicleProvider provider : providers) {
            for (VehicleHandle handle : provider.all()) {
                if (handle != null) {
                    unique.putIfAbsent(handle.id(), handle);
                }
            }
        }
        return List.copyOf(unique.values());
    }

    @Override
    public PurgeResult purgeAll() {
        int tracked = 0;
        int strays = 0;
        for (VehicleProvider provider : providers) {
            PurgeResult removed = provider.purge();
            if (removed != null) {
                tracked += removed.tracked();
                strays += removed.strays();
            }
        }
        return new PurgeResult(tracked, strays);
    }
}
