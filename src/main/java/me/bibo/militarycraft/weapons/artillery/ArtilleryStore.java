package me.bibo.militarycraft.weapons.artillery;

import me.bibo.militarycraft.core.Core;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** YAML-backed registry for every artillery installation. */
final class ArtilleryStore {

    private final Core core;
    private final File file;
    private final File fallbackFile;
    private final Map<UUID, Artillery> byId = new LinkedHashMap<>();
    private final Map<String, Artillery> byPosition = new LinkedHashMap<>();
    private ArtillerySettings settings;
    private boolean writable = true;

    ArtilleryStore(Core core, ArtillerySettings settings) {
        this.core = core;
        this.settings = settings;
        this.file = new File(ArtilleryYamlFiles.moduleDataFolder(core), "artillery-data.yml");
        this.fallbackFile = new File(core.plugin().getDataFolder(), "artillery-data.yml");
    }

    void load() {
        byId.clear();
        byPosition.clear();
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
            core.logger().severe("Could not read " + source.getPath()
                    + "; the original file will not be overwritten: "
                    + ex.getMessage());
            return;
        }
        ConfigurationSection root = yaml.getConfigurationSection("artilleries");
        if (root == null) {
            if (!yaml.getKeys(false).isEmpty()) {
                writable = false;
                core.logger().severe("artillery-data.yml has no 'artilleries' section; refusing to overwrite it.");
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
                String worldName = section.getString("world", "");
                if (worldName.isBlank()) {
                    throw new IllegalArgumentException("world is missing");
                }
                UUID id = parseUuid(key);
                if (id == null) {
                    id = legacyId(worldName, section.getInt("x"), section.getInt("y"), section.getInt("z"));
                }
                UUID worldId = parseUuid(section.getString("world-uuid", ""));
                if (worldId == null) {
                    worldId = worldId(worldName);
                }
                Artillery artillery = new Artillery(id, worldId, worldName,
                        section.getInt("x"), section.getInt("y"), section.getInt("z"),
                        (float) section.getDouble("yaw", 0.0),
                        section.getInt("ammo", settings.maxAmmo),
                        Math.max(0L, section.getLong("last-shot", 0L)),
                        section.getInt("health", settings.maxHits));
                artillery.clampState(settings);
                if (byPosition.containsKey(artillery.positionKey())) {
                    throw new IllegalArgumentException("duplicate block position");
                }
                byId.put(id, artillery);
                byPosition.put(artillery.positionKey(), artillery);
            } catch (RuntimeException ex) {
                malformed = true;
                core.logger().warning("Skipping malformed artillery entry '" + key + "': " + ex.getMessage());
            }
        }
        if (malformed) {
            writable = false;
            core.logger().severe("artillery-data.yml contains malformed entries; loaded valid entries read-only "
                    + "so the damaged data is preserved.");
        }
        core.logger().info("Loaded " + byId.size() + " artillery installation(s).");
    }

    boolean save() {
        if (!writable) {
            core.logger().severe("Refusing to overwrite an unreadable or malformed artillery-data.yml.");
            return false;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        int index = 0;
        for (Artillery artillery : byId.values()) {
            String base = "artilleries." + index++;
            yaml.set(base + ".world", artillery.worldName());
            yaml.set(base + ".x", artillery.x());
            yaml.set(base + ".y", artillery.y());
            yaml.set(base + ".z", artillery.z());
            yaml.set(base + ".yaw", artillery.yaw());
            yaml.set(base + ".ammo", artillery.ammo());
            yaml.set(base + ".last-shot", artillery.lastShotMillis());
            yaml.set(base + ".health", artillery.health());
        }
        try {
            ArtilleryYamlFiles.saveAtomically(yaml, file);
            return true;
        } catch (IOException ex) {
            core.logger().severe("Could not save artillery-data.yml: " + ex.getMessage());
            return false;
        }
    }

    Artillery create(Location location, float yaw) {
        if (!writable) {
            throw new IllegalStateException("artillery-data.yml is not writable");
        }
        Artillery artillery = new Artillery(UUID.randomUUID(), location.getWorld().getUID(),
                location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                yaw, settings.maxAmmo, 0L, settings.maxHits);
        byId.put(artillery.id(), artillery);
        byPosition.put(artillery.positionKey(), artillery);
        if (!save()) {
            byId.remove(artillery.id());
            byPosition.remove(artillery.positionKey());
            throw new IllegalStateException("could not persist the artillery registry");
        }
        return artillery;
    }

    Artillery get(Location location) {
        return location == null || location.getWorld() == null
                ? null : byPosition.get(Artillery.positionKey(location));
    }

    Artillery get(UUID id) {
        return byId.get(id);
    }

    boolean contains(Artillery artillery) {
        return artillery != null && byId.get(artillery.id()) == artillery;
    }

    boolean remove(Artillery artillery) {
        if (artillery == null || byId.get(artillery.id()) != artillery) {
            return false;
        }
        byId.remove(artillery.id());
        byPosition.remove(artillery.positionKey());
        if (save()) {
            return true;
        }
        byId.put(artillery.id(), artillery);
        byPosition.put(artillery.positionKey(), artillery);
        return false;
    }

    Collection<Artillery> all() {
        return List.copyOf(byId.values());
    }

    List<Artillery> inChunk(UUID worldId, int chunkX, int chunkZ) {
        List<Artillery> result = new ArrayList<>();
        for (Artillery artillery : byId.values()) {
            if (artillery.worldId().equals(worldId)
                    && (artillery.x() >> 4) == chunkX && (artillery.z() >> 4) == chunkZ) {
                result.add(artillery);
            }
        }
        return result;
    }

    void setSettings(ArtillerySettings settings) {
        this.settings = settings;
        boolean changed = false;
        for (Artillery artillery : byId.values()) {
            changed |= artillery.clampState(settings);
        }
        if (changed) {
            save();
        }
    }

    void clearOperators() {
        for (Artillery artillery : byId.values()) {
            artillery.setOperator(null);
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

    private static UUID legacyId(String worldName, int x, int y, int z) {
        String raw = worldName + ";" + x + ";" + y + ";" + z;
        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static UUID worldId(String worldName) {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world != null) {
            return world.getUID();
        }
        return UUID.nameUUIDFromBytes(("world:" + worldName).getBytes(StandardCharsets.UTF_8));
    }
}
