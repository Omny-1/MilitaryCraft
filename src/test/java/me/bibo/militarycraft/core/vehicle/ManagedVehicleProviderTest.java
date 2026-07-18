package me.bibo.militarycraft.core.vehicle;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManagedVehicleProviderTest {

    @Test
    void exposesFreshImmutableSnapshots() {
        StubVehicle firstVehicle = new StubVehicle(UUID.randomUUID(), "tank");
        StubVehicle secondVehicle = new StubVehicle(UUID.randomUUID(), "tank");
        List<StubVehicle> live = new ArrayList<>();
        live.add(firstVehicle);
        ManagedVehicleProvider<StubVehicle> provider = ManagedVehicleProvider.trackedOnly(
                "tank", entity -> null, () -> live, () -> 0);

        Collection<? extends VehicleHandle> firstSnapshot = provider.all();
        assertEquals(List.of(firstVehicle), firstSnapshot);
        assertThrows(UnsupportedOperationException.class, () -> firstSnapshot.remove(firstVehicle));

        live.add(secondVehicle);
        assertEquals(1, firstSnapshot.size());
        assertEquals(List.of(firstVehicle, secondVehicle), provider.all());
    }

    @Test
    void normalizesLegacyPurgeResults() {
        ManagedVehicleProvider<StubVehicle> provider = ManagedVehicleProvider.withStraySweep(
                "pickup", entity -> null, List::of, () -> new int[]{-3, 4});

        assertEquals(new VehicleService.PurgeResult(0, 4), provider.purge());
    }

    @Test
    void toleratesMissingLegacyPurgeCounts() {
        ManagedVehicleProvider<StubVehicle> provider = ManagedVehicleProvider.withStraySweep(
                "pickup", entity -> null, List::of, () -> null);

        assertEquals(new VehicleService.PurgeResult(0, 0), provider.purge());
    }

    @Test
    void trackedOnlyProviderClampsNegativeCounts() {
        ManagedVehicleProvider<StubVehicle> provider = ManagedVehicleProvider.trackedOnly(
                "train", entity -> null, List::of, () -> -1);

        assertEquals(new VehicleService.PurgeResult(0, 0), provider.purge());
    }

    @Test
    void serviceDeduplicatesVehicleIdsAndAggregatesPurgeCounts() {
        UUID sharedId = UUID.randomUUID();
        StubVehicle preferred = new StubVehicle(sharedId, "tank");
        StubVehicle duplicate = new StubVehicle(sharedId, "jet");
        VehicleServiceImpl service = new VehicleServiceImpl();
        ManagedVehicleProvider<StubVehicle> first = ManagedVehicleProvider.trackedOnly(
                "tank", entity -> null, () -> List.of(preferred), () -> 2);
        ManagedVehicleProvider<StubVehicle> second = ManagedVehicleProvider.withStraySweep(
                "jet", entity -> null, () -> List.of(duplicate), () -> new int[]{3, 4});

        service.registerProvider(first);
        service.registerProvider(second);

        assertEquals(1, service.all().size());
        assertSame(preferred, service.all().iterator().next());
        assertEquals(new VehicleService.PurgeResult(5, 4), service.purgeAll());

        service.unregisterProvider(first);
        assertSame(duplicate, service.all().iterator().next());
    }

    private record StubVehicle(UUID id, String type) implements VehicleHandle {

        @Override
        public Entity coreEntity() {
            return null;
        }

        @Override
        public Location location() {
            return null;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public double health() {
            return 1.0;
        }

        @Override
        public double maxHealth() {
            return 1.0;
        }

        @Override
        public boolean damage(double amount) {
            return false;
        }

        @Override
        public double repair(double amount) {
            return 0.0;
        }

        @Override
        public void applyAntiAirHit() {
        }

        @Override
        public void applyExplosion(Location loc, double power) {
        }
    }
}
