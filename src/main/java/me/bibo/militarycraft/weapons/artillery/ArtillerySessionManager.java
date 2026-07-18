package me.bibo.militarycraft.weapons.artillery;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Top-down artillery camera sessions plus crash-safe player location/game-mode recovery. */
final class ArtillerySessionManager {

    private final ArtilleryManager manager;
    private final ArtilleryTaskTracker tasks;
    private final File file;
    private final File fallbackFile;
    private final Map<UUID, ArtillerySession> sessions = new HashMap<>();
    private final Map<UUID, SavedState> pending = new HashMap<>();
    private BukkitTask pinTask;
    private boolean writable = true;

    ArtillerySessionManager(ArtilleryManager manager, ArtilleryTaskTracker tasks) {
        this.manager = manager;
        this.tasks = tasks;
        this.file = new File(ArtilleryYamlFiles.moduleDataFolder(manager.core()), "active-sessions.yml");
        this.fallbackFile = new File(manager.core().plugin().getDataFolder(), "artillery-sessions.yml");
    }

    void start() {
        loadPending();
        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreIfPending(player);
        }
        pinTask = tasks.repeating(this::tick, 1L, 1L);
    }

    boolean open(Player player, Artillery artillery) {
        UUID playerId = player.getUniqueId();
        if (sessions.containsKey(playerId)) {
            ArtilleryMessages.action(player, "You are already operating artillery.");
            return false;
        }
        if (pending.containsKey(playerId) && !restoreIfPending(player)) {
            ArtilleryMessages.send(player, "&cYour interrupted session could not be restored yet.");
            return false;
        }
        if (!manager.operational(artillery)) {
            ArtilleryMessages.action(player, "This artillery is unavailable.");
            return false;
        }
        if (artillery.operator() != null && !artillery.operator().equals(playerId)) {
            ArtilleryMessages.action(player, "This artillery is already in use.");
            return false;
        }

        SavedState saved = SavedState.from(player);
        pending.put(playerId, saved);
        if (!savePending()) {
            pending.remove(playerId);
            ArtilleryMessages.send(player, "&cThe recovery state could not be saved; the camera was not opened.");
            return false;
        }
        artillery.setOperator(playerId);
        sessions.put(playerId, new ArtillerySession(playerId, artillery.id()));
        try {
            player.setGameMode(GameMode.SPECTATOR);
            if (!player.teleport(camera(artillery))) {
                throw new IllegalStateException("camera teleport was rejected");
            }
        } catch (RuntimeException ex) {
            sessions.remove(playerId);
            artillery.setOperator(null);
            restoreIfPending(player);
            manager.core().logger().warning("Could not open artillery session for "
                    + player.getName() + ": " + ex.getMessage());
            return false;
        }
        ArtilleryMessages.send(player, "&aOperating " + ArtilleryMessages.NAME
                + ". Use &e/mc artillery fire <x> <z>&a or &e/mc artillery exit&a.");
        return true;
    }

    boolean close(Player player) {
        ArtillerySession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            releaseOperator(session);
        }
        return restoreIfPending(player);
    }

    void closeByArtillery(Artillery artillery) {
        for (ArtillerySession session : new ArrayList<>(sessions.values())) {
            if (!session.artilleryId().equals(artillery.id())) {
                continue;
            }
            Player player = Bukkit.getPlayer(session.playerId());
            if (player != null && player.isOnline()) {
                close(player);
            } else {
                abandon(session);
            }
        }
    }

    void onQuit(Player player) {
        if (sessions.containsKey(player.getUniqueId())) {
            close(player);
        }
    }

    void onDeath(Player player) {
        ArtillerySession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        releaseOperator(session);
        SavedState state = pending.get(player.getUniqueId());
        if (state != null) {
            player.setGameMode(state.gameMode());
        }
    }

    void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        SavedState state = pending.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        Location saved = state.location();
        if (saved != null) {
            event.setRespawnLocation(saved);
        }
        tasks.later(() -> restoreIfPending(player), 1L);
    }

    boolean restoreIfPending(Player player) {
        SavedState state = pending.get(player.getUniqueId());
        if (state == null) {
            return true;
        }
        Location location = state.location();
        if (location == null) {
            return false;
        }
        try {
            player.setGameMode(state.gameMode());
            if (!player.teleport(location)) {
                return false;
            }
        } catch (RuntimeException ex) {
            manager.core().logger().warning("Could not restore artillery operator "
                    + player.getName() + ": " + ex.getMessage());
            return false;
        }
        pending.remove(player.getUniqueId());
        if (savePending()) {
            return true;
        }
        pending.put(player.getUniqueId(), state);
        return false;
    }

    void shutdown() {
        tasks.cancel(pinTask);
        pinTask = null;
        for (ArtillerySession session : new ArrayList<>(sessions.values())) {
            Player player = Bukkit.getPlayer(session.playerId());
            if (player != null && player.isOnline()) {
                close(player);
            } else {
                abandon(session);
            }
        }
        sessions.clear();
        manager.clearOperators();
        savePending();
    }

    ArtillerySession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    Artillery selected(Player player) {
        ArtillerySession session = session(player);
        return session == null ? null : manager.byId(session.artilleryId());
    }

    boolean active(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    boolean hasPending(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    private void tick() {
        for (ArtillerySession session : new ArrayList<>(sessions.values())) {
            Artillery artillery = manager.byId(session.artilleryId());
            Player player = Bukkit.getPlayer(session.playerId());
            if (player == null || !player.isOnline()) {
                abandon(session);
                continue;
            }
            if (artillery == null || !manager.operational(artillery)) {
                close(player);
                continue;
            }
            try {
                if (player.getGameMode() != GameMode.SPECTATOR) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
                if (!player.teleport(camera(artillery))) {
                    close(player);
                }
            } catch (RuntimeException ex) {
                close(player);
            }
        }
    }

    private Location camera(Artillery artillery) {
        World world = artillery.world();
        return new Location(world, artillery.x() + 0.5,
                artillery.y() + manager.settings().cameraHeight,
                artillery.z() + 0.5, 0.0f, 90.0f);
    }

    private void abandon(ArtillerySession session) {
        sessions.remove(session.playerId());
        releaseOperator(session);
    }

    private void releaseOperator(ArtillerySession session) {
        Artillery artillery = manager.byId(session.artilleryId());
        if (artillery != null && session.playerId().equals(artillery.operator())) {
            artillery.setOperator(null);
        }
    }

    private void loadPending() {
        pending.clear();
        writable = true;
        File source = file.isFile() ? file : fallbackFile;
        if (!source.isFile()) {
            return;
        }
        YamlConfiguration yaml;
        try {
            yaml = ArtilleryYamlFiles.load(source);
        } catch (IOException | InvalidConfigurationException ex) {
            writable = false;
            manager.core().logger().severe("Could not read " + source.getPath()
                    + "; camera sessions are disabled and the original file will not be overwritten: "
                    + ex.getMessage());
            return;
        }
        ConfigurationSection root = yaml.getConfigurationSection("active");
        if (root == null) {
            if (!yaml.getKeys(false).isEmpty()) {
                writable = false;
                manager.core().logger().severe(
                        "active-sessions.yml has no 'active' section; refusing to overwrite it.");
            }
            return;
        }
        boolean malformed = false;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                UUID playerId = UUID.fromString(key);
                String worldName = section.getString("world", "");
                if (worldName.isBlank()) {
                    throw new IllegalArgumentException("world is missing");
                }
                UUID worldId = parseUuid(section.getString("world-uuid", ""));
                if (worldId == null) {
                    worldId = worldId(worldName);
                }
                GameMode mode = GameMode.valueOf(section.getString("gamemode", "SURVIVAL"));
                double x = section.getDouble("x");
                double y = section.getDouble("y");
                double z = section.getDouble("z");
                double yaw = section.getDouble("yaw");
                double pitch = section.getDouble("pitch");
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                        || !Double.isFinite(yaw) || !Double.isFinite(pitch)
                        || yaw < -Float.MAX_VALUE || yaw > Float.MAX_VALUE
                        || pitch < -Float.MAX_VALUE || pitch > Float.MAX_VALUE) {
                    throw new IllegalArgumentException("location contains a non-finite value");
                }
                pending.put(playerId, new SavedState(worldId, worldName, x, y, z,
                        (float) yaw, (float) pitch, mode));
            } catch (RuntimeException ex) {
                malformed = true;
                manager.core().logger().warning("Skipping malformed artillery session '" + key + "'.");
            }
        }
        if (malformed) {
            writable = false;
            manager.core().logger().severe("active-sessions.yml contains malformed entries; "
                    + "valid recovery entries were loaded read-only so the damaged data is preserved.");
        }
        if (!pending.isEmpty()) {
            manager.core().logger().info("Recovering " + pending.size()
                    + " interrupted artillery session(s).");
        }
    }

    private boolean savePending() {
        if (!writable) {
            manager.core().logger().severe("Refusing to overwrite an unreadable or malformed active-sessions.yml.");
            return false;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, SavedState> entry : pending.entrySet()) {
            String base = "active." + entry.getKey();
            SavedState state = entry.getValue();
            yaml.set(base + ".world", state.worldName());
            yaml.set(base + ".x", state.x());
            yaml.set(base + ".y", state.y());
            yaml.set(base + ".z", state.z());
            yaml.set(base + ".yaw", state.yaw());
            yaml.set(base + ".pitch", state.pitch());
            yaml.set(base + ".gamemode", state.gameMode().name());
        }
        try {
            ArtilleryYamlFiles.saveAtomically(yaml, file);
            return true;
        } catch (IOException ex) {
            manager.core().logger().warning("Could not save active-sessions.yml: " + ex.getMessage());
            return false;
        }
    }

    private record SavedState(UUID worldId, String worldName, double x, double y, double z,
                              float yaw, float pitch, GameMode gameMode) {

        static SavedState from(Player player) {
            Location location = player.getLocation();
            return new SavedState(location.getWorld().getUID(), location.getWorld().getName(),
                    location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch(), player.getGameMode());
        }

        Location location() {
            World world = Bukkit.getWorld(worldId);
            if (world == null) {
                world = Bukkit.getWorld(worldName);
            }
            return world == null ? null : new Location(world, x, y, z, yaw, pitch);
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static UUID worldId(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return world.getUID();
        }
        return UUID.nameUUIDFromBytes(("world:" + worldName).getBytes(StandardCharsets.UTF_8));
    }
}
