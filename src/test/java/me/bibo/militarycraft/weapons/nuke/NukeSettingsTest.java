package me.bibo.militarycraft.weapons.nuke;

import me.bibo.militarycraft.core.config.ModuleConfig;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NukeSettingsTest {

    @Test
    void readsOriginalFlatNukeStrikeConfigKeys() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("damage-radius", 128.0);
        config.set("crater-radius", 64);
        config.set("cooldown-seconds", 120);
        config.set("messages.item-given", "&aOriginal message");

        NukeSettings settings = new NukeSettings(new ModuleConfig(config));

        assertEquals(128.0, settings.getDouble("damage-radius", 0.0));
        assertEquals(64, settings.getInt("crater-radius", 0));
        assertEquals(120, settings.getInt("cooldown-seconds", 0));
        assertEquals("&aOriginal message", settings.message("item-given"));
    }
}
