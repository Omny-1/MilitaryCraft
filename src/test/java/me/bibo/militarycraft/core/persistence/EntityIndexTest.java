package me.bibo.militarycraft.core.persistence;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityIndexTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void activeRecordsAndCooldownSurviveReopen() {
        Plugin plugin = plugin(temporaryDirectory);
        UUID worldId = UUID.randomUUID();
        World world = world(worldId);
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        EntityIndex first = index(plugin, "motorcycles.yml");
        first.record(id, owner, new Location(world, 10.5, 64.0, -3.25));
        first.recordSpawn(player, 123_456L);
        first.close();

        EntityIndex reopened = index(plugin, "motorcycles.yml");
        assertEquals(1, reopened.countActive());
        assertEquals(1, reopened.countOwned(owner));
        assertEquals(1, reopened.countInChunk(worldId, 0, -1));
        assertEquals(123_456L, reopened.lastSpawn(player));
        assertTrue(reopened.tooClose(worldId, 11.0, 64.0, -3.0, 2.0));
        assertFalse(reopened.tooClose(worldId, 30.0, 64.0, -3.0, 2.0));
        reopened.close();
    }

    @Test
    void moduleFilesRemainIndependent() {
        Plugin plugin = plugin(temporaryDirectory);
        EntityIndex motorcycles = new EntityIndex(plugin, "moto", "motorcycles.yml");
        motorcycles.record(UUID.randomUUID(), UUID.randomUUID(),
                new Location(world(UUID.randomUUID()), 2.0, 64.0, 3.0));
        motorcycles.close();

        EntityIndex pickups = new EntityIndex(plugin, "pickup", "pickups.yml");
        assertEquals(0, pickups.countActive());
        pickups.recordSpawn(UUID.randomUUID(), 42L);
        pickups.close();

        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("motorcycles.yml")));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("pickups.yml")));
    }

    @Test
    void tombstoneSurvivesAndCannotBeResurrected() {
        Plugin plugin = plugin(temporaryDirectory);
        World world = world(UUID.randomUUID());
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        Location location = new Location(world, 0.0, 70.0, 0.0);

        EntityIndex index = index(plugin, "motorcycles.yml");
        index.record(id, owner, location);
        assertTrue(index.remove(id));
        assertEquals(0, index.countActive());
        assertTrue(index.isDeleted(id));
        assertThrows(IllegalStateException.class, () -> index.record(id, owner, location));
        index.close();

        EntityIndex reopened = index(plugin, "motorcycles.yml");
        assertTrue(reopened.isDeleted(id));
        assertEquals(0, reopened.countActive());
        reopened.close();
    }

    @Test
    void updateMovesSpacingCoordinate() {
        Plugin plugin = plugin(temporaryDirectory);
        UUID worldId = UUID.randomUUID();
        World world = world(worldId);
        UUID id = UUID.randomUUID();

        EntityIndex index = index(plugin, "motorcycles.yml");
        index.record(id, UUID.randomUUID(), new Location(world, 1.0, 64.0, 1.0));
        assertTrue(index.updatePosition(id, new Location(world, 100.0, 70.0, 100.0)));
        assertFalse(index.tooClose(worldId, 1.0, 64.0, 1.0, 5.0));
        assertTrue(index.tooClose(worldId, 100.0, 70.0, 100.0, 0.1));
        index.close();
    }

    @Test
    void rejectsNonFiniteCoordinatesAndUnsafeFileNames() {
        EntityIndex index = index(plugin(temporaryDirectory), "motorcycles.yml");
        World world = world(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> index.record(
                UUID.randomUUID(), UUID.randomUUID(),
                new Location(world, Double.NaN, 64.0, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> index.tooClose(
                world.getUID(), 0.0, 64.0, 0.0, Double.POSITIVE_INFINITY));
        index.close();

        assertThrows(IllegalArgumentException.class,
                () -> new EntityIndex(plugin(temporaryDirectory), "moto", "../motorcycles.yml"));
    }

    @Test
    void ownerThreadGuardPreventsAsyncAccess() throws InterruptedException {
        EntityIndex index = index(plugin(temporaryDirectory), "motorcycles.yml");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                index.countActive();
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        thread.start();
        thread.join();
        assertTrue(failure.get() instanceof IllegalStateException);
        index.close();
    }

    @Test
    void flushUsesCommittedUtf8FileAndLeavesNoTemp() {
        EntityIndex index = index(plugin(temporaryDirectory), "motorcycles.yml");
        index.record(UUID.randomUUID(), UUID.randomUUID(),
                new Location(world(UUID.randomUUID()), 1.25, 65.5, -2.75));
        index.flush();
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("motorcycles.yml")));
        assertFalse(Files.exists(temporaryDirectory.resolve("motorcycles.yml.tmp")));
        index.close();
    }

    private static EntityIndex index(Plugin plugin, String fileName) {
        return new EntityIndex(plugin, "moto", fileName);
    }

    private static Plugin plugin(Path dataDirectory) {
        Logger logger = Logger.getLogger("EntityIndexTest");
        return proxy(Plugin.class, (method, args) -> switch (method.getName()) {
            case "getDataFolder" -> dataDirectory.toFile();
            case "getLogger" -> logger;
            case "getName" -> "MilitaryCraftTest";
            default -> defaultValue(method.getReturnType());
        });
    }

    private static World world(UUID id) {
        return proxy(World.class, (method, args) -> switch (method.getName()) {
            case "getUID" -> id;
            case "getName" -> "test-world";
            default -> defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> type.getSimpleName() + "TestProxy";
                            case "hashCode" -> System.identityHashCode(instance);
                            case "equals" -> instance == args[0];
                            default -> null;
                        };
                    }
                    return invocation.invoke(method, args);
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0d;
        if (type == char.class) return '\0';
        return null;
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }
}
