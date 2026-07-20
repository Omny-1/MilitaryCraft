package me.bibo.militarycraft.core.airsupport;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.World;

import me.bibo.militarycraft.core.util.ChunkTickets;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A moving set of plugin chunk tickets for a temporary air-support sequence (a strike's
 * jet + target window). Ticket ownership is reference-counted through {@link ChunkTickets}
 * - shared with the Drone and Train, which pin the same kind of ticket - so overlapping
 * users never unload each other's chunks. This class adds the sequence-specific sliding
 * window + a synchronous load so falling ordnance always ticks.
 */
public final class ChunkWindow {

    private final Plugin plugin;
    private final Set<Key> loaded = new LinkedHashSet<>();

    public ChunkWindow(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void update(int radius, Location... centers) {
        int safeRadius = Math.max(0, Math.min(radius, 8));
        Set<Key> wanted = new LinkedHashSet<>();
        if (centers != null) {
            for (Location center : centers) {
                if (center == null || center.getWorld() == null) {
                    continue;
                }
                World world = center.getWorld();
                int cx = center.getBlockX() >> 4;
                int cz = center.getBlockZ() >> 4;
                for (int dx = -safeRadius; dx <= safeRadius; dx++) {
                    for (int dz = -safeRadius; dz <= safeRadius; dz++) {
                        wanted.add(new Key(world, plugin, cx + dx, cz + dz));
                    }
                }
            }
        }
        applyWanted(wanted);
    }

    /**
     * Update the window to exactly the given chunks (each key packed as
     * {@code (long) chunkX << 32 | (chunkZ & 0xffffffffL)}), reference-counted exactly
     * like {@link #update}. Lets a caller with a non-square desired set - e.g. two
     * different radii around a bomber and its target - still share the global,
     * cross-instance ticket refcount instead of stomping global force-load state.
     */
    public void updateChunks(World world, Set<Long> chunkKeys) {
        Set<Key> wanted = new LinkedHashSet<>();
        if (world != null && chunkKeys != null) {
            for (long packed : chunkKeys) {
                wanted.add(new Key(world, plugin, (int) (packed >> 32), (int) packed));
            }
        }
        applyWanted(wanted);
    }

    private void applyWanted(Set<Key> wanted) {
        loaded.removeIf(key -> {
            if (wanted.contains(key)) {
                return false;
            }
            release(key);
            return true;
        });
        for (Key key : wanted) {
            if (loaded.add(key)) {
                acquire(key);
            }
        }
    }

    public void releaseAll() {
        for (Key key : Set.copyOf(loaded)) {
            release(key);
        }
        loaded.clear();
    }

    private void acquire(Key key) {
        // Shared refcount so Drone/Train/Airstrike/Nuke can all pin the same chunk safely.
        ChunkTickets.acquire(key.world(), plugin, key.x(), key.z());
        // Never generate terrain from here. getChunkAt generates a missing chunk
        // synchronously on the main thread, and an ordnance run near unexplored terrain can
        // ask for dozens of them at once, so a strike would freeze the server. Chunks that
        // already exist are fetched (the ticket then keeps them loaded); a chunk that does
        // not exist yet is simply left out of the window.
        if (key.world().isChunkGenerated(key.x(), key.z())) {
            key.world().getChunkAt(key.x(), key.z());
        }
    }

    private void release(Key key) {
        ChunkTickets.release(key.world(), plugin, key.x(), key.z());
    }

    private record Key(UUID worldId, String pluginName, World world, int x, int z) {

        private Key(World world, Plugin plugin, int x, int z) {
            this(world.getUID(), plugin.getName(), world, x, z);
        }

        private Key {
            Objects.requireNonNull(worldId, "worldId");
            Objects.requireNonNull(pluginName, "pluginName");
            Objects.requireNonNull(world, "world");
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && worldId.equals(key.worldId)
                    && pluginName.equals(key.pluginName)
                    && x == key.x
                    && z == key.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(worldId, pluginName, x, z);
        }
    }
}
