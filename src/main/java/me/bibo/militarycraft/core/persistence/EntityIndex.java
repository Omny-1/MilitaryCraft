package me.bibo.militarycraft.core.persistence;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.Reader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Optional durable index for module-owned entities, including unloaded records,
 * deletion tombstones and per-player spawn cooldowns. Instances are main-thread-only.
 */
public final class EntityIndex implements AutoCloseable {

    private final Plugin plugin;
    private final String moduleId;
    private final String fileName;
    private final Path dataDirectory;
    private final Path dataFile;
    private final Path temporaryFile;
    private final Thread ownerThread;
    private final Map<UUID, Entry> entities = new LinkedHashMap<>();
    private final Map<UUID, Long> spawnCooldowns = new LinkedHashMap<>();

    private boolean dirty;
    private boolean closed;

    /**
     * Creates and loads a module-specific index from the owning plugin's data folder.
     * The module id is used only for diagnostics/header text; the file name is explicit
     * so independent modules never share an index accidentally.
     */
    public EntityIndex(Plugin plugin, String moduleId, String fileName) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.moduleId = requireModuleId(moduleId);
        this.fileName = requireFileName(fileName);
        this.dataDirectory = plugin.getDataFolder().toPath();
        this.dataFile = dataDirectory.resolve(this.fileName);
        this.temporaryFile = dataDirectory.resolve(this.fileName + ".tmp");
        this.ownerThread = Thread.currentThread();

        try {
            Files.createDirectories(dataDirectory);
            recoverInterruptedFirstWrite();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot prepare data directory for " + moduleId, ex);
        }
        load();
    }

    public int countActive() {
        requireOpenOnOwnerThread();
        int count = 0;
        for (Entry entry : entities.values()) {
            if (!entry.deleted()) {
                count++;
            }
        }
        return count;
    }

    public int countOwned(UUID owner) {
        requireOpenOnOwnerThread();
        Objects.requireNonNull(owner, "owner");
        int count = 0;
        for (Entry entry : entities.values()) {
            if (!entry.deleted() && entry.owner().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    public int countInChunk(UUID world, int chunkX, int chunkZ) {
        requireOpenOnOwnerThread();
        Objects.requireNonNull(world, "world");
        int count = 0;
        for (Entry entry : entities.values()) {
            if (!entry.deleted() && entry.world().equals(world)
                    && (((int) Math.floor(entry.x())) >> 4) == chunkX
                    && (((int) Math.floor(entry.z())) >> 4) == chunkZ) {
                count++;
            }
        }
        return count;
    }

    public boolean tooClose(UUID world, double x, double y, double z, double minDistance) {
        requireOpenOnOwnerThread();
        Objects.requireNonNull(world, "world");
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        requireFinite(minDistance, "minDistance");
        if (minDistance < 0.0) {
            throw new IllegalArgumentException("minDistance must be >= 0");
        }
        for (Entry entry : entities.values()) {
            if (entry.deleted() || !entry.world().equals(world)) {
                continue;
            }
            double horizontal = Math.hypot(entry.x() - x, entry.z() - z);
            if (Math.hypot(horizontal, entry.y() - y) < minDistance) {
                return true;
            }
        }
        return false;
    }

    /** Records an active entity. Tombstoned ids cannot be reused. */
    public void record(UUID id, UUID owner, Location location) {
        requireOpenOnOwnerThread();
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Position position = positionOf(location);
        Entry previous = entities.get(id);
        if (previous != null) {
            if (previous.deleted()) {
                throw new IllegalStateException("Cannot resurrect tombstoned entity " + id);
            }
            if (!previous.owner().equals(owner)) {
                throw new IllegalStateException("Cannot change owner of entity " + id);
            }
        }
        long createdAt = previous == null ? System.currentTimeMillis() : previous.createdAt();
        Entry replacement = new Entry(id, owner, position.world(), position.x(), position.y(),
                position.z(), createdAt, false);
        if (!replacement.equals(previous)) {
            entities.put(id, replacement);
            dirty = true;
        }
    }

    public boolean updatePosition(UUID id, Location location) {
        requireOpenOnOwnerThread();
        Objects.requireNonNull(id, "id");
        Position position = positionOf(location);
        Entry previous = entities.get(id);
        if (previous == null || previous.deleted()) {
            return false;
        }
        Entry replacement = new Entry(previous.id(), previous.owner(), position.world(),
                position.x(), position.y(), position.z(), previous.createdAt(), false);
        if (!replacement.equals(previous)) {
            entities.put(id, replacement);
            dirty = true;
        }
        return true;
    }

    public boolean remove(UUID id) {
        requireOpenOnOwnerThread();
        Objects.requireNonNull(id, "id");
        Entry previous = entities.get(id);
        if (previous == null || previous.deleted()) {
            return false;
        }
        entities.put(id, previous.asDeleted());
        dirty = true;
        return true;
    }

    public int markAllDeleted() {
        requireOpenOnOwnerThread();
        int changed = 0;
        for (Map.Entry<UUID, Entry> mapEntry : entities.entrySet()) {
            if (!mapEntry.getValue().deleted()) {
                mapEntry.setValue(mapEntry.getValue().asDeleted());
                changed++;
            }
        }
        if (changed > 0) {
            dirty = true;
        }
        return changed;
    }

    public boolean isDeleted(UUID id) {
        requireOpenOnOwnerThread();
        Objects.requireNonNull(id, "id");
        Entry entry = entities.get(id);
        return entry != null && entry.deleted();
    }

    public void recordSpawn(UUID player, long epochMillis) {
        requireOpenOnOwnerThread();
        Objects.requireNonNull(player, "player");
        if (epochMillis < 0L) {
            throw new IllegalArgumentException("epochMillis must be >= 0");
        }
        Long previous = spawnCooldowns.put(player, epochMillis);
        if (previous == null || previous.longValue() != epochMillis) {
            dirty = true;
        }
    }

    public long lastSpawn(UUID player) {
        requireOpenOnOwnerThread();
        Objects.requireNonNull(player, "player");
        return spawnCooldowns.getOrDefault(player, 0L);
    }

    public void flush() {
        requireOpenOnOwnerThread();
        flushInternal();
    }

    @Override
    public void close() {
        requireOwnerThread();
        if (closed) {
            return;
        }
        flushInternal();
        closed = true;
    }

    private void load() {
        if (!Files.exists(dataFile)) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try (Reader reader = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
            yaml.load(reader);
        } catch (InvalidConfigurationException ex) {
            preserveMalformedFile(ex);
            dirty = true;
            return;
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read " + dataFile, ex);
        }
        loadEntities(yaml);
        loadSpawnCooldowns(yaml);
    }

    private void loadEntities(YamlConfiguration yaml) {
        ConfigurationSection root = yaml.getConfigurationSection("entities");
        if (root == null) {
            if (yaml.contains("entities")) {
                warnMalformed("entities", "root value is not a YAML section");
                dirty = true;
            }
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                warnMalformed(key, "record is not a YAML section");
                dirty = true;
                continue;
            }
            try {
                UUID keyId = parseUuid(key, "record key");
                UUID id = readUuid(section, "id");
                if (!keyId.equals(id)) {
                    throw new IllegalArgumentException("id does not match record key");
                }
                UUID owner = readUuid(section, "owner");
                UUID world = readUuid(section, "world");
                double x = readFiniteDouble(section, "x");
                double y = readFiniteDouble(section, "y");
                double z = readFiniteDouble(section, "z");
                long createdAt = readNonNegativeLong(section.get("createdAt"), "createdAt");
                Object deletedValue = section.get("deleted");
                if (!(deletedValue instanceof Boolean deleted)) {
                    throw new IllegalArgumentException("deleted must be a boolean");
                }
                entities.put(id, new Entry(id, owner, world, x, y, z, createdAt, deleted));
            } catch (IllegalArgumentException ex) {
                warnMalformed(key, ex.getMessage());
                dirty = true;
            }
        }
    }

    private void loadSpawnCooldowns(YamlConfiguration yaml) {
        ConfigurationSection root = yaml.getConfigurationSection("spawn-cooldowns");
        if (root == null) {
            if (yaml.contains("spawn-cooldowns")) {
                warnMalformed("spawn-cooldowns", "root value is not a YAML section");
                dirty = true;
            }
            return;
        }
        for (String key : root.getKeys(false)) {
            try {
                UUID player = parseUuid(key, "player UUID");
                spawnCooldowns.put(player,
                        readNonNegativeLong(root.get(key), "spawn timestamp"));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning(prefix() + "Skipping malformed spawn cooldown '"
                        + key + "': " + ex.getMessage());
                dirty = true;
            }
        }
    }

    private boolean flushInternal() {
        if (!dirty) {
            return true;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("MilitaryCraft entity index for module '"
                + moduleId + "'. Edit only while the server is stopped."));
        ConfigurationSection entityRoot = yaml.createSection("entities");
        List<UUID> ids = new ArrayList<>(entities.keySet());
        ids.sort(Comparator.comparing(UUID::toString));
        for (UUID id : ids) {
            Entry entry = entities.get(id);
            ConfigurationSection section = entityRoot.createSection(id.toString());
            section.set("id", entry.id().toString());
            section.set("owner", entry.owner().toString());
            section.set("world", entry.world().toString());
            section.set("x", entry.x());
            section.set("y", entry.y());
            section.set("z", entry.z());
            section.set("createdAt", entry.createdAt());
            section.set("deleted", entry.deleted());
        }
        ConfigurationSection cooldownRoot = yaml.createSection("spawn-cooldowns");
        List<UUID> players = new ArrayList<>(spawnCooldowns.keySet());
        players.sort(Comparator.comparing(UUID::toString));
        for (UUID player : players) {
            cooldownRoot.set(player.toString(), spawnCooldowns.get(player));
        }

        try {
            Files.createDirectories(dataDirectory);
            Files.writeString(temporaryFile, yaml.saveToString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try (FileChannel channel = FileChannel.open(temporaryFile, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            moveReplacing(temporaryFile, dataFile);
            dirty = false;
            return true;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, prefix() + "Could not flush index to "
                    + dataFile + "; the previous file was left intact", ex);
            return false;
        }
    }

    private void recoverInterruptedFirstWrite() throws IOException {
        if (!Files.exists(dataFile) && Files.exists(temporaryFile)) {
            moveReplacing(temporaryFile, dataFile);
            plugin.getLogger().warning(prefix() + "Recovered interrupted index write from "
                    + temporaryFile.getFileName());
        }
    }

    private void preserveMalformedFile(InvalidConfigurationException cause) {
        plugin.getLogger().log(Level.SEVERE,
                prefix() + "Cannot parse " + dataFile + "; malformed content will not be loaded", cause);
        Path backup = uniqueMalformedBackupPath();
        try {
            Files.copy(dataFile, backup);
            plugin.getLogger().severe(prefix() + "Preserved malformed index as " + backup);
        } catch (IOException backupFailure) {
            backupFailure.addSuppressed(cause);
            throw new IllegalStateException("Cannot preserve malformed index before recovery", backupFailure);
        }
    }

    private Path uniqueMalformedBackupPath() {
        long timestamp = System.currentTimeMillis();
        Path candidate = dataDirectory.resolve(fileName + ".malformed-" + timestamp);
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = dataDirectory.resolve(fileName + ".malformed-" + timestamp + "-" + suffix++);
        }
        return candidate;
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailure) {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException fallbackFailure) {
                fallbackFailure.addSuppressed(atomicFailure);
                throw fallbackFailure;
            }
        }
    }

    private static Position positionOf(Location location) {
        Objects.requireNonNull(location, "location");
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("location must have a world");
        }
        requireFinite(location.getX(), "location.x");
        requireFinite(location.getY(), "location.y");
        requireFinite(location.getZ(), "location.z");
        return new Position(world.getUID(), location.getX(), location.getY(), location.getZ());
    }

    private static UUID readUuid(ConfigurationSection section, String path) {
        Object raw = section.get(path);
        if (!(raw instanceof String value)) {
            throw new IllegalArgumentException(path + " must be a UUID string");
        }
        return parseUuid(value, path);
    }

    private static UUID parseUuid(String value, String description) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(description + " is not a valid UUID");
        }
    }

    private static double readFiniteDouble(ConfigurationSection section, String path) {
        Object raw = section.get(path);
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException(path + " must be numeric");
        }
        double value = number.doubleValue();
        requireFinite(value, path);
        return value;
    }

    private static long readNonNegativeLong(Object raw, String description) {
        long value;
        if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long) {
            value = ((Number) raw).longValue();
        } else if (raw instanceof Number number) {
            double decimal = number.doubleValue();
            if (!Double.isFinite(decimal) || decimal != Math.rint(decimal)
                    || decimal < 0.0 || decimal > Long.MAX_VALUE) {
                throw new IllegalArgumentException(description + " must be a non-negative integer");
            }
            value = (long) decimal;
        } else {
            throw new IllegalArgumentException(description + " must be a non-negative integer");
        }
        if (value < 0L) {
            throw new IllegalArgumentException(description + " must be >= 0");
        }
        return value;
    }

    private static void requireFinite(double value, String description) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(description + " must be finite");
        }
    }

    private static String requireModuleId(String value) {
        Objects.requireNonNull(value, "moduleId");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("moduleId must not be blank");
        }
        return normalized;
    }

    private static String requireFileName(String value) {
        Objects.requireNonNull(value, "fileName");
        String normalized = value.trim();
        if (normalized.isEmpty() || !Path.of(normalized).getFileName().toString().equals(normalized)) {
            throw new IllegalArgumentException("fileName must be one local file name");
        }
        return normalized;
    }

    private void warnMalformed(String id, String reason) {
        plugin.getLogger().warning(prefix() + "Skipping malformed entity record '"
                + id + "': " + reason);
    }

    private String prefix() {
        return "[EntityIndex:" + moduleId + "] ";
    }

    private void requireOpenOnOwnerThread() {
        requireOwnerThread();
        if (closed) {
            throw new IllegalStateException("EntityIndex for " + moduleId + " is closed");
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("EntityIndex may only be used from its creating server thread");
        }
    }

    private record Position(UUID world, double x, double y, double z) {
    }

    private record Entry(UUID id, UUID owner, UUID world, double x, double y, double z,
                         long createdAt, boolean deleted) {
        private Entry asDeleted() {
            return new Entry(id, owner, world, x, y, z, createdAt, true);
        }
    }
}
