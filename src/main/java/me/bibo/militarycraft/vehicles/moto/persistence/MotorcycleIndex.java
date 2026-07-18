package me.bibo.militarycraft.vehicles.moto.persistence;

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
 * Durable, main-thread-only index of all known MotoCraft motorcycles.
 *
 * <p>The index deliberately includes unloaded motorcycles. A deleted motorcycle is
 * retained as a tombstone so a persistent anchor that loads later can be removed
 * instead of accidentally being adopted again. Player spawn timestamps are stored
 * in the same file so cooldowns survive restarts.</p>
 *
 * <p>Construct this class on the Bukkit server thread and invoke every public
 * method from that same thread. The class performs no asynchronous work.</p>
 */
public final class MotorcycleIndex implements AutoCloseable {

    private static final String FILE_NAME = "motorcycles.yml";
    private static final String TEMP_FILE_NAME = "motorcycles.yml.tmp";

    private final Plugin plugin;
    private final Path dataDirectory;
    private final Path dataFile;
    private final Path temporaryFile;
    private final Thread ownerThread;
    private final Map<UUID, Entry> motorcycles = new LinkedHashMap<>();
    private final Map<UUID, Long> spawnCooldowns = new LinkedHashMap<>();

    private boolean dirty;
    private boolean closed;

    /**
     * Creates and immediately loads {@code motorcycles.yml} from the plugin data
     * folder. Malformed individual records are logged and omitted.
     *
     * @param plugin owning plugin, used for the data directory and logging
     * @throws IllegalStateException if the data directory/file cannot be read safely
     */
    public MotorcycleIndex(Plugin plugin) {
        this(plugin, Objects.requireNonNull(plugin, "plugin").getDataFolder().toPath());
    }

    public MotorcycleIndex(Plugin plugin, Path dataDirectory) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.dataFile = dataDirectory.resolve(FILE_NAME);
        this.temporaryFile = dataDirectory.resolve(TEMP_FILE_NAME);
        this.ownerThread = Thread.currentThread();

        try {
            Files.createDirectories(dataDirectory);
            recoverInterruptedFirstWrite();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot prepare MotoCraft data directory " + dataDirectory, ex);
        }
        load();
    }

    /** Returns the number of non-deleted motorcycles, loaded or unloaded. */
    public int countActive() {
        requireOpenOnOwnerThread();
        int count = 0;
        for (Entry entry : motorcycles.values()) {
            if (!entry.deleted()) {
                count++;
            }
        }
        return count;
    }

    /** Returns the number of non-deleted motorcycles owned by {@code owner}. */
    public int countOwned(UUID owner) {
        requireOpenOnOwnerThread();
        Objects.requireNonNull(owner, "owner");
        int count = 0;
        for (Entry entry : motorcycles.values()) {
            if (!entry.deleted() && entry.owner().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    /** Counts active records whose last known anchor lies in one exact chunk. */
    public int countInChunk(UUID world, int chunkX, int chunkZ) {
        requireOpenOnOwnerThread();
        Objects.requireNonNull(world, "world");
        int count = 0;
        for (Entry entry : motorcycles.values()) {
            if (!entry.deleted() && entry.world().equals(world)
                    && (((int) Math.floor(entry.x())) >> 4) == chunkX
                    && (((int) Math.floor(entry.z())) >> 4) == chunkZ) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns whether an active motorcycle in {@code world} is strictly closer
     * than {@code minDistance} to the supplied three-dimensional point.
     *
     * @throws IllegalArgumentException if a coordinate or distance is non-finite,
     *                                  or the distance is negative
     */
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

        for (Entry entry : motorcycles.values()) {
            if (entry.deleted() || !entry.world().equals(world)) {
                continue;
            }
            double horizontal = Math.hypot(entry.x() - x, entry.z() - z);
            double distance = Math.hypot(horizontal, entry.y() - y);
            if (distance < minDistance) {
                return true;
            }
        }
        return false;
    }

    /**
     * Records a new active motorcycle. Re-recording the same active ID is allowed
     * only for the same owner and preserves its original creation timestamp.
     * Tombstoned IDs cannot be resurrected.
     *
     * @throws IllegalArgumentException if the location has no world or has a
     *                                  non-finite coordinate
     * @throws IllegalStateException if the ID is tombstoned or its owner changes
     */
    public void record(UUID id, UUID owner, Location location) {
        requireOpenOnOwnerThread();
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Position position = positionOf(location);

        Entry previous = motorcycles.get(id);
        if (previous != null) {
            if (previous.deleted()) {
                throw new IllegalStateException("Cannot resurrect tombstoned motorcycle " + id);
            }
            if (!previous.owner().equals(owner)) {
                throw new IllegalStateException("Cannot change owner of motorcycle " + id);
            }
        }

        long createdAt = previous == null ? System.currentTimeMillis() : previous.createdAt();
        Entry replacement = new Entry(id, owner, position.world(), position.x(), position.y(),
                position.z(), createdAt, false);
        if (!replacement.equals(previous)) {
            motorcycles.put(id, replacement);
            dirty = true;
        }
    }

    /**
     * Updates the world and position of an existing active motorcycle.
     *
     * @return {@code true} when a known active record exists (including when the
     *         supplied position was already current), otherwise {@code false}
     */
    public boolean updatePosition(UUID id, Location location) {
        requireOpenOnOwnerThread();
        Objects.requireNonNull(id, "id");
        Position position = positionOf(location);
        Entry previous = motorcycles.get(id);
        if (previous == null || previous.deleted()) {
            return false;
        }

        Entry replacement = new Entry(previous.id(), previous.owner(), position.world(),
                position.x(), position.y(), position.z(), previous.createdAt(), false);
        if (!replacement.equals(previous)) {
            motorcycles.put(id, replacement);
            dirty = true;
        }
        return true;
    }

    /**
     * Marks one known motorcycle deleted while retaining its tombstone.
     *
     * @return {@code true} only when an active record was newly tombstoned
     */
    public boolean remove(UUID id) {
        requireOpenOnOwnerThread();
        Objects.requireNonNull(id, "id");
        Entry previous = motorcycles.get(id);
        if (previous == null || previous.deleted()) {
            return false;
        }
        motorcycles.put(id, previous.asDeleted());
        dirty = true;
        return true;
    }

    /**
     * Tombstones every active motorcycle.
     *
     * @return number of records newly marked deleted
     */
    public int markAllDeleted() {
        requireOpenOnOwnerThread();
        int changed = 0;
        for (Map.Entry<UUID, Entry> mapEntry : motorcycles.entrySet()) {
            Entry value = mapEntry.getValue();
            if (!value.deleted()) {
                mapEntry.setValue(value.asDeleted());
                changed++;
            }
        }
        if (changed > 0) {
            dirty = true;
        }
        return changed;
    }

    /** Returns whether {@code id} is a known deletion tombstone. */
    public boolean isDeleted(UUID id) {
        requireOpenOnOwnerThread();
        Objects.requireNonNull(id, "id");
        Entry entry = motorcycles.get(id);
        return entry != null && entry.deleted();
    }

    /**
     * Stores a player's latest successful spawn time as epoch milliseconds.
     *
     * @param player player UUID
     * @param epochMillis non-negative epoch timestamp
     */
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

    /** Returns the persisted spawn timestamp, or {@code 0} when none is recorded. */
    public long lastSpawn(UUID player) {
        requireOpenOnOwnerThread();
        Objects.requireNonNull(player, "player");
        return spawnCooldowns.getOrDefault(player, 0L);
    }

    /**
     * Writes pending changes as UTF-8 YAML. The complete content is forced to a
     * temporary file and then moved over the live file atomically when supported;
     * a replace move is used as the filesystem fallback. On failure, the live file
     * is left intact, the dirty flag remains set, and a severe error is logged.
     */
    public void flush() {
        requireOpenOnOwnerThread();
        flushInternal();
    }

    /**
     * Flushes pending changes and closes this index. This method is idempotent;
     * write failures are logged and leave the temporary recovery file in place.
     */
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

        loadMotorcycles(yaml);
        loadSpawnCooldowns(yaml);
    }

    private void loadMotorcycles(YamlConfiguration yaml) {
        ConfigurationSection root = yaml.getConfigurationSection("motorcycles");
        if (root == null) {
            if (yaml.contains("motorcycles")) {
                warnMalformed("motorcycles", "root value is not a YAML section");
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
                motorcycles.put(id, new Entry(id, owner, world, x, y, z, createdAt, deleted));
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
                long timestamp = readNonNegativeLong(root.get(key), "spawn timestamp");
                spawnCooldowns.put(player, timestamp);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping malformed motorcycle spawn cooldown '"
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
        yaml.options().setHeader(List.of(
                "MotoCraft persistent motorcycle index. Edit only while the server is stopped."));
        ConfigurationSection motorcycleRoot = yaml.createSection("motorcycles");

        List<UUID> ids = new ArrayList<>(motorcycles.keySet());
        ids.sort(Comparator.comparing(UUID::toString));
        for (UUID id : ids) {
            Entry entry = motorcycles.get(id);
            ConfigurationSection section = motorcycleRoot.createSection(id.toString());
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
            plugin.getLogger().log(Level.SEVERE,
                    "Could not flush MotoCraft motorcycle index to " + dataFile
                            + "; the previous file was left intact", ex);
            return false;
        }
    }

    /** Promotes a complete temp file only when no committed file exists. */
    private void recoverInterruptedFirstWrite() throws IOException {
        if (!Files.exists(dataFile) && Files.exists(temporaryFile)) {
            moveReplacing(temporaryFile, dataFile);
            plugin.getLogger().warning("Recovered interrupted MotoCraft index write from "
                    + temporaryFile.getFileName());
        }
    }

    /** Keeps invalid YAML available to an administrator before a clean file replaces it. */
    private void preserveMalformedFile(InvalidConfigurationException cause) {
        plugin.getLogger().log(Level.SEVERE,
                "Cannot parse " + dataFile + "; malformed content will not be loaded", cause);
        Path backup = uniqueMalformedBackupPath();
        try {
            Files.copy(dataFile, backup);
            plugin.getLogger().severe("Preserved malformed motorcycle index as " + backup);
        } catch (IOException backupFailure) {
            backupFailure.addSuppressed(cause);
            throw new IllegalStateException(
                    "Cannot preserve malformed motorcycle index before recovery", backupFailure);
        }
    }

    private Path uniqueMalformedBackupPath() {
        long timestamp = System.currentTimeMillis();
        Path candidate = dataDirectory.resolve(FILE_NAME + ".malformed-" + timestamp);
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = dataDirectory.resolve(FILE_NAME + ".malformed-" + timestamp + "-" + suffix++);
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
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        requireFinite(x, "location.x");
        requireFinite(y, "location.y");
        requireFinite(z, "location.z");
        return new Position(world.getUID(), x, y, z);
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

    private void warnMalformed(String id, String reason) {
        plugin.getLogger().warning("Skipping malformed motorcycle record '" + id + "': " + reason);
    }

    private void requireOpenOnOwnerThread() {
        requireOwnerThread();
        if (closed) {
            throw new IllegalStateException("MotorcycleIndex is closed");
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("MotorcycleIndex may only be used from its creating server thread");
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
