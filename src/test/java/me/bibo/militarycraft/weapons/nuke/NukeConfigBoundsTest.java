package me.bibo.militarycraft.weapons.nuke;

import me.bibo.militarycraft.core.util.Bounds;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The nuclear strike reads several values that amplify work done on the main thread. The
 * crater is assembled as a {@code (2r+1)^2} column list before a single block is changed,
 * so an unbounded radius is an out-of-memory stall rather than a bigger explosion.
 *
 * <p>These tests cover the two halves of that guard: that {@link Bounds} really caps and
 * really rejects non-finite input, and that no value shipped in {@code config.yml} sits
 * outside the range {@code NukeSequence} enforces. The second half matters because a
 * shipped default above a cap would be silently clamped, quietly changing the gameplay
 * everyone gets out of the box.
 */
class NukeConfigBoundsTest {

    @Test
    void boundsCapsAndRejectsNonFiniteValues() {
        assertEquals(128.0, Bounds.ranged(10_000.0, 1.0, 128.0, 64.0), "value above the cap");
        assertEquals(1.0, Bounds.ranged(-5.0, 1.0, 128.0, 64.0), "value below the floor");
        assertEquals(64.0, Bounds.ranged(Double.NaN, 1.0, 128.0, 64.0), "NaN falls back");
        assertEquals(64.0, Bounds.ranged(Double.POSITIVE_INFINITY, 1.0, 128.0, 64.0), "infinity falls back");
        assertEquals(128, Bounds.ranged(10_000, 0, 128), "int above the cap");
    }

    @Test
    void shippedNukeDefaultsAreInsideTheEnforcedRanges() throws Exception {
        String text = Files.readString(Path.of("src/main/resources/config.yml"));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new StringReader(text));

        // Keep these in step with the caps applied in NukeSequence's constructor.
        assertWithin(yaml, "nuke.bomber-speed", 0.1, 20.0);
        assertWithin(yaml, "nuke.bomber-exit-distance", 20, 2048);
        assertWithin(yaml, "nuke.engine-sound-volume", 0.0, 12.0);
        assertWithin(yaml, "nuke.fall-sound-volume", 0.0, 12.0);
        assertWithin(yaml, "nuke.bomb-fall-speed", 0.05, 10.0);
        assertWithin(yaml, "nuke.bomb-accel-ticks", 1, 1200);
        assertWithin(yaml, "nuke.damage-radius", 1.0, 512.0);
        assertWithin(yaml, "nuke.max-damage", 0.0, 10_000.0);
        assertWithin(yaml, "nuke.max-knockback", 0.0, 100.0);
        assertWithin(yaml, "nuke.crater-radius", 0, 128);
        assertWithin(yaml, "nuke.crater-depth", 0, 96);
        assertWithin(yaml, "nuke.crater-columns-per-tick", 32, 5000);
        assertWithin(yaml, "nuke.blindness-radius", 0.0, 512.0);
        assertWithin(yaml, "nuke.blindness-seconds", 0, 600);
        assertWithin(yaml, "nuke.radiation-seconds", 0, 600);
        assertWithin(yaml, "nuke.warning-radius", 0.0, 1024.0);
    }

    private static void assertWithin(YamlConfiguration yaml, String path, double min, double max) {
        assertTrue(yaml.isSet(path), "missing config key: " + path);
        double value = yaml.getDouble(path);
        assertTrue(value >= min && value <= max,
                path + " = " + value + " is outside the enforced range [" + min + ", " + max + "], "
                        + "so the shipped default would be silently clamped");
    }
}
