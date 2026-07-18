package me.bibo.militarycraft.weapons.artillery;

import me.bibo.militarycraft.core.Core;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Corruption-preserving reads and atomic replacement for artillery state files. */
final class ArtilleryYamlFiles {

    private ArtilleryYamlFiles() {
    }

    static YamlConfiguration load(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        return yaml;
    }

    static File moduleDataFolder(Core core) {
        File parent = core.plugin().getDataFolder().getParentFile();
        if (parent == null) {
            return new File(core.plugin().getDataFolder(), "SvoArtillery");
        }
        return new File(parent, "SvoArtillery");
    }

    static void saveAtomically(YamlConfiguration yaml, File file) throws IOException {
        File folder = file.getParentFile();
        if (folder != null && !folder.exists() && !folder.mkdirs()) {
            throw new IOException("could not create plugin data folder");
        }

        File temporary = new File(folder, file.getName() + ".tmp");
        try {
            yaml.save(temporary);
            try {
                Files.move(temporary.toPath(), file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }
}
