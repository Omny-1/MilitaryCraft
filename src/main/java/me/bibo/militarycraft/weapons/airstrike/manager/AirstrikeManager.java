package me.bibo.militarycraft.weapons.airstrike.manager;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import me.bibo.militarycraft.weapons.airstrike.AirstrikeRuntime;
import me.bibo.militarycraft.weapons.airstrike.task.AirstrikeSequence;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AirstrikeManager {

    /** Reject a new strike whose target is within this many blocks of an active one. */
    private static final double MIN_TARGET_SEPARATION_SQ = 10.0 * 10.0;

    private final AirstrikeRuntime plugin;
    private final Set<AirstrikeSequence> activeSequences = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public AirstrikeManager(AirstrikeRuntime plugin) {
        this.plugin = plugin;
    }

    public boolean callAirstrike(Player player, Location target) {
        if (target == null || target.getWorld() == null) {
            return false;
        }

        long remaining = getRemainingCooldown(player);
        if (remaining > 0) {
            player.sendMessage(plugin.message("on-cooldown", "time", remaining));
            return false;
        }
        if (isAreaActive(target)) {
            player.sendMessage(plugin.message("already-active"));
            return false;
        }
        int maxActive = plugin.getConfig().getInt("max-active-strikes", 5);
        if (maxActive > 0 && activeSequences.size() >= maxActive) {
            player.sendMessage(plugin.message("too-many-active"));
            return false;
        }

        if (!player.hasPermission("airstrike.bypass-cooldown")) {
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }

        World world = target.getWorld();
        // Clamp to safe minimums: a zero start distance would make the heading
        // vector zero-length, and normalize() would yield NaN coordinates.
        int startDist = Math.max(1, plugin.getConfig().getInt("jet-start-distance", 220));
        int altitude = Math.max(1, plugin.getConfig().getInt("jet-altitude", 75));

        // Approach from a random horizontal bearing, flying level at target.y + altitude.
        double bearing = Math.random() * Math.PI * 2.0;
        double startX = target.getX() - Math.sin(bearing) * startDist;
        double startZ = target.getZ() - Math.cos(bearing) * startDist;
        double startY = target.getY() + altitude;
        Location jetStart = new Location(world, startX, startY, startZ);

        Vector dir = new Vector(target.getX() - startX, 0.0, target.getZ() - startZ).normalize();

        AirstrikeSequence seq = new AirstrikeSequence(plugin, target, jetStart, dir.getX(), dir.getZ());
        activeSequences.add(seq);
        seq.spawnJet();
        seq.runTaskTimer(plugin.bukkitPlugin(), 0L, 1L);

        player.sendMessage(plugin.message("strike-called",
                "x", target.getBlockX(), "y", target.getBlockY(), "z", target.getBlockZ()));
        plugin.getLogger().info(player.getName() + " called airstrike at "
                + target.getBlockX() + " " + target.getBlockY() + " " + target.getBlockZ());
        return true;
    }

    private boolean isAreaActive(Location target) {
        for (AirstrikeSequence seq : activeSequences) {
            Location other = seq.getTarget();
            if (other.getWorld() == target.getWorld()
                    && other.distanceSquared(target) < MIN_TARGET_SEPARATION_SQ) {
                return true;
            }
        }
        return false;
    }

    public void onSequenceFinished(AirstrikeSequence seq) {
        activeSequences.remove(seq);
    }

    public long getRemainingCooldown(Player player) {
        if (player.hasPermission("airstrike.bypass-cooldown")) {
            return 0L;
        }
        int cooldownSec = plugin.getConfig().getInt("cooldown-seconds", 45);
        long lastStrike = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long elapsed = (System.currentTimeMillis() - lastStrike) / 1000L;
        return Math.max(0L, cooldownSec - elapsed);
    }

    public void cleanup() {
        for (AirstrikeSequence seq : activeSequences) {
            try {
                seq.shutdown();
            } catch (Exception ignored) {
                // best-effort teardown on disable
            }
        }
        activeSequences.clear();
        cooldowns.clear();
    }
}
