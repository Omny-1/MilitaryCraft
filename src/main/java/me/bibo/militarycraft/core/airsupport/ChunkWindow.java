package me.bibo.militarycraft.core.airsupport;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.World;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Owns the small moving set of plugin chunk tickets used by temporary air support
 * sequences. Tickets are reference-counted across all ChunkWindow instances so two
 * active strikes cannot accidentally unload each other's chunks.
 */
public final class ChunkWindow {

    private static final Map<Key, Reference> REFERENCES = new HashMap<>();

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
        synchronized (REFERENCES) {
            Reference existing = REFERENCES.get(key);
            if (existing != null) {
                existing.count++;
                return;
            }
            boolean ticketOwned = key.world().addPluginChunkTicket(key.x(), key.z(), plugin);
            Chunk chunk = key.world().getChunkAt(key.x(), key.z());
            if (!chunk.isLoaded()) {
                chunk.load();
            }
            REFERENCES.put(key, new Reference(key.world(), plugin, ticketOwned));
        }
    }

    private void release(Key key) {
        synchronized (REFERENCES) {
            Reference reference = REFERENCES.get(key);
            if (reference == null) {
                return;
            }
            reference.count--;
            if (reference.count > 0) {
                return;
            }
            REFERENCES.remove(key);
            if (reference.ticketOwned) {
                try {
                    reference.world.removePluginChunkTicket(key.x(), key.z(), reference.plugin);
                } catch (IllegalArgumentException exception) {
                    reference.plugin.getLogger().log(Level.WARNING,
                            "Could not release air support chunk ticket at "
                                    + key.x() + "," + key.z() + " in " + reference.world.getName(), exception);
                }
            }
        }
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

    private static final class Reference {
        private final World world;
        private final Plugin plugin;
        private final boolean ticketOwned;
        private int count = 1;

        private Reference(World world, Plugin plugin, boolean ticketOwned) {
            this.world = world;
            this.plugin = plugin;
            this.ticketOwned = ticketOwned;
        }
    }
}
