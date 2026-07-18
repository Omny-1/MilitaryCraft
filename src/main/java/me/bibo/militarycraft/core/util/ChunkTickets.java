package me.bibo.militarycraft.core.util;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Process-wide reference count for Bukkit plugin chunk tickets.
 *
 * <p>A Bukkit plugin chunk ticket is keyed only by {@code (plugin, chunk)} — there is
 * exactly one per pair, no matter how many independent subsystems want it. So Airstrike,
 * Nuke, a flying Drone and a moving Train (all owned by the same plugin) cannot each hold
 * their own ticket on the same chunk: whoever calls {@code removePluginChunkTicket} first
 * drops it for everyone. Every consumer must go through this shared counter instead, so
 * the real Bukkit ticket is added on the first acquire and removed only on the last
 * release. {@code addPluginChunkTicket == false} must never be treated as "someone else
 * owns it" inside the same plugin instance.
 *
 * <p>All calls happen on the main server thread; the map is guarded anyway for safety.
 */
public final class ChunkTickets {

    private record Key(UUID world, int x, int z) {
    }

    private static final Map<Key, Integer> COUNTS = new HashMap<>();

    private ChunkTickets() {
    }

    /** Add a reference; adds the real Bukkit ticket only when the count goes 0 → 1. */
    public static synchronized void acquire(World world, Plugin plugin, int chunkX, int chunkZ) {
        if (world == null || plugin == null) {
            return;
        }
        Key key = new Key(world.getUID(), chunkX, chunkZ);
        if (COUNTS.merge(key, 1, Integer::sum) == 1) {
            world.addPluginChunkTicket(chunkX, chunkZ, plugin);
        }
    }

    /** Drop a reference; removes the real Bukkit ticket only when the count reaches 0. */
    public static synchronized void release(World world, Plugin plugin, int chunkX, int chunkZ) {
        if (world == null || plugin == null) {
            return;
        }
        Key key = new Key(world.getUID(), chunkX, chunkZ);
        Integer count = COUNTS.get(key);
        if (count == null) {
            return;
        }
        if (count <= 1) {
            COUNTS.remove(key);
            world.removePluginChunkTicket(chunkX, chunkZ, plugin);
        } else {
            COUNTS.put(key, count - 1);
        }
    }
}
