package me.bibo.militarycraft.core.vehicle;

import org.bukkit.entity.Entity;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Builds a {@link VehicleProvider} around an original module manager. */
public final class ManagedVehicleProvider<V extends VehicleHandle> implements VehicleProvider {

    private final String type;
    private final Function<Entity, V> lookup;
    private final Supplier<? extends Collection<V>> vehicles;
    private final Supplier<VehicleService.PurgeResult> purge;

    private ManagedVehicleProvider(String type, Function<Entity, V> lookup,
                                   Supplier<? extends Collection<V>> vehicles,
                                   Supplier<VehicleService.PurgeResult> purge) {
        this.type = Objects.requireNonNull(type, "type");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.vehicles = Objects.requireNonNull(vehicles, "vehicles");
        this.purge = Objects.requireNonNull(purge, "purge");
    }

    public static <V extends VehicleHandle> ManagedVehicleProvider<V> withStraySweep(
            String type, Function<Entity, V> lookup,
            Supplier<? extends Collection<V>> vehicles, Supplier<int[]> purge) {
        Objects.requireNonNull(purge, "purge");
        return new ManagedVehicleProvider<>(type, lookup, vehicles, () -> {
            int[] result = purge.get();
            int tracked = result != null && result.length > 0 ? Math.max(0, result[0]) : 0;
            int strays = result != null && result.length > 1 ? Math.max(0, result[1]) : 0;
            return new VehicleService.PurgeResult(tracked, strays);
        });
    }

    public static <V extends VehicleHandle> ManagedVehicleProvider<V> trackedOnly(
            String type, Function<Entity, V> lookup,
            Supplier<? extends Collection<V>> vehicles, IntSupplier purge) {
        Objects.requireNonNull(purge, "purge");
        return new ManagedVehicleProvider<>(type, lookup, vehicles,
                () -> new VehicleService.PurgeResult(Math.max(0, purge.getAsInt()), 0));
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public VehicleHandle vehicleOf(Entity anyPart) {
        return anyPart == null ? null : lookup.apply(anyPart);
    }

    @Override
    public Collection<? extends VehicleHandle> all() {
        Collection<V> current = vehicles.get();
        return current == null || current.isEmpty() ? List.of() : List.copyOf(current);
    }

    @Override
    public VehicleService.PurgeResult purge() {
        return purge.get();
    }
}
