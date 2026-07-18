package me.bibo.militarycraft.core.vehicle;

import me.bibo.militarycraft.core.key.EntityTag;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class VehicleServiceImpl implements VehicleService {

    /** How deep to walk a stack of nested vehicles looking for one of ours (VehicleCameraPlugin's own constant). */
    private static final int MAX_VEHICLE_DEPTH = 4;

    private final CopyOnWriteArrayList<VehicleManager<?>> managers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<VehicleProvider> providers = new CopyOnWriteArrayList<>();

    @Override
    public void registerManager(VehicleManager<?> manager) {
        managers.addIfAbsent(manager);
    }

    @Override
    public void unregisterManager(VehicleManager<?> manager) {
        managers.remove(manager);
    }

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
        String moduleId = EntityTag.moduleOf(anyPart);
        if (moduleId != null) {
            for (VehicleManager<?> manager : managers) {
                if (manager.moduleId().equals(moduleId)) {
                    DisplayVehicle v = manager.byEntity(anyPart);
                    if (v != null) {
                        return v;
                    }
                }
            }
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
        for (VehicleManager<?> manager : managers) {
            for (VehicleHandle handle : manager.all()) {
                if (handle != null) {
                    unique.putIfAbsent(handle.id(), handle);
                }
            }
        }
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
        for (VehicleManager<?> manager : managers) {
            int[] removed = manager.purgeAll();
            tracked += removed[0];
            strays += removed[1];
        }
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
