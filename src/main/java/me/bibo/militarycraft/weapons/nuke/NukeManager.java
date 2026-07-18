package me.bibo.militarycraft.weapons.nuke;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.text.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NukeManager {

    public static final String MODULE_ID = "nuke";
    private static final double MIN_TARGET_SEPARATION_SQ = 24.0 * 24.0;

    private final Core core;
    private final Set<NukeSequence> activeSequences = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final RadiationManager radiation;
    private NukeSettings settings;

    NukeManager(Core core, NukeSettings settings) {
        this.core = core;
        this.settings = settings;
        this.radiation = new RadiationManager(this);
        this.radiation.start();
    }

    Core core() {
        return core;
    }

    NukeSettings settings() {
        return settings;
    }

    RadiationManager radiation() {
        return radiation;
    }

    void setSettings(NukeSettings settings) {
        this.settings = settings;
    }

    Component message(String key, Object... replacements) {
        String raw = settings.message(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace("{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
        }
        return Text.of(raw);
    }

    boolean callNuke(Player caller, Location target) {
        if (target == null || target.getWorld() == null) {
            return false;
        }

        if (caller != null) {
            long remaining = getRemainingCooldown(caller);
            if (remaining > 0) {
                caller.sendMessage(message("on-cooldown", "time", remaining));
                return false;
            }
        }
        if (isAreaActive(target)) {
            if (caller != null) {
                caller.sendMessage(message("already-active"));
            }
            return false;
        }
        int maxActive = settings.getInt("max-active-strikes", 2);
        if (maxActive > 0 && activeSequences.size() >= maxActive) {
            if (caller != null) {
                caller.sendMessage(message("too-many-active"));
            }
            return false;
        }

        if (caller != null && !caller.hasPermission("nuke.bypass-cooldown")) {
            cooldowns.put(caller.getUniqueId(), System.currentTimeMillis());
        }

        World world = target.getWorld();
        int startDist = Math.max(1, settings.getInt("bomber-start-distance", 260));
        int altitude = Math.max(1, settings.getInt("bomber-altitude", 90));

        double bearing = Math.random() * Math.PI * 2.0;
        double startX = target.getX() - Math.sin(bearing) * startDist;
        double startZ = target.getZ() - Math.cos(bearing) * startDist;
        double startY = target.getY() + altitude;
        Location bomberStart = new Location(world, startX, startY, startZ);

        Vector dir = new Vector(target.getX() - startX, 0.0, target.getZ() - startZ).normalize();

        NukeSequence sequence = new NukeSequence(this, target, bomberStart, dir.getX(), dir.getZ());
        activeSequences.add(sequence);
        sequence.spawnBomber();
        sequence.runTaskTimer(core.plugin(), 0L, 1L);

        if (caller != null) {
            caller.sendMessage(message("strike-called",
                    "x", target.getBlockX(), "y", target.getBlockY(), "z", target.getBlockZ()));
        }
        core.logger().info((caller != null ? caller.getName() : "console") + " called a NUCLEAR strike at "
                + target.getBlockX() + " " + target.getBlockY() + " " + target.getBlockZ());
        return true;
    }

    private boolean isAreaActive(Location target) {
        for (NukeSequence sequence : activeSequences) {
            Location other = sequence.getTarget();
            if (other.getWorld() == target.getWorld()
                    && other.distanceSquared(target) < MIN_TARGET_SEPARATION_SQ) {
                return true;
            }
        }
        return false;
    }

    void onSequenceFinished(NukeSequence sequence) {
        activeSequences.remove(sequence);
    }

    long getRemainingCooldown(Player player) {
        if (player.hasPermission("nuke.bypass-cooldown")) {
            return 0L;
        }
        int cooldownSeconds = settings.getInt("cooldown-seconds", 120);
        long lastStrike = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long elapsed = (System.currentTimeMillis() - lastStrike) / 1000L;
        return Math.max(0L, cooldownSeconds - elapsed);
    }

    void reload() {
        setSettings(new NukeSettings(core.config().section(MODULE_ID)));
    }

    void cleanup() {
        for (NukeSequence sequence : activeSequences) {
            try {
                sequence.shutdown();
            } catch (Exception ignored) {
                // Original plugin used best-effort teardown on disable.
            }
        }
        activeSequences.clear();
        cooldowns.clear();
    }

    Set<NukeSequence> active() {
        return Set.copyOf(activeSequences);
    }

    void shutdown() {
        cleanup();
        radiation.stop();
    }
}
