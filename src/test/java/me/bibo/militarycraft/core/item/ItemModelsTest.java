package me.bibo.militarycraft.core.item;

import me.bibo.militarycraft.core.config.ModuleConfig;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Custom item models must stay off unless a server opts in, because a client without the
 * pack renders an unresolved model reference as the missing-model placeholder rather than
 * falling back to the item's normal appearance.
 */
class ItemModelsTest {

    @AfterEach
    void resetSharedState() {
        ItemModels.refresh(new ModuleConfig(new MemoryConfiguration()));
    }

    @Test
    void offWhenTheKeyIsMissingEntirely() {
        ItemModels.refresh(new ModuleConfig(new MemoryConfiguration()));
        assertFalse(ItemModels.enabled(), "a config without the key must not apply models");
    }

    @Test
    void offWhenTheConfigIsNull() {
        ItemModels.refresh(null);
        assertFalse(ItemModels.enabled(), "a missing config must not apply models");
    }

    @Test
    void followsTheConfiguredValueBothWays() {
        MemoryConfiguration config = new MemoryConfiguration();

        config.set("resource-pack.models", true);
        ItemModels.refresh(new ModuleConfig(config));
        assertTrue(ItemModels.enabled(), "models should turn on when asked");

        config.set("resource-pack.models", false);
        ItemModels.refresh(new ModuleConfig(config));
        assertFalse(ItemModels.enabled(), "models should turn back off on reload");
    }

    @Test
    void theShippedConfigLeavesModelsOff() throws Exception {
        String text = Files.readString(Path.of("src/main/resources/config.yml"));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new StringReader(text));

        assertTrue(yaml.isSet("resource-pack.models"), "the key must be documented in config.yml");
        assertFalse(yaml.getBoolean("resource-pack.models"),
                "the shipped default must be off so the plugin works without the pack");
    }
}
