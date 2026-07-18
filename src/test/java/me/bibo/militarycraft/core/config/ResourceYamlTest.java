package me.bibo.militarycraft.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceYamlTest {

    private static final List<String> MODULES = List.of(
            "camera", "tank", "kamaz", "jet", "helicopter", "airship", "drone",
            "moto", "pickup", "train", "antiair", "tckbus", "airstrike", "nuke",
            "warkit", "artillery");
    private static final Pattern PERMISSION_NODE =
            Pattern.compile("(?m)^  ((?:militarycraft|svoart|nuke|airstrike|tckbus|helicraft|tankcraft|kamazcraft|jetcraft|airshipcraft|dronecraft|motocraft|pickupcraft|traincraft|antiaircraft|vehiclecamera|warkit)\\.(?:[a-z0-9_-]+\\.)*[a-z0-9_-]+):\\s*$");
    private static final Pattern COMMAND_PERMISSION_LITERAL =
            Pattern.compile("\"((?:militarycraft|svoart|nuke|airstrike|tckbus|helicraft|tankcraft|kamazcraft|jetcraft|airshipcraft|dronecraft|motocraft|pickupcraft|traincraft|antiaircraft|vehiclecamera|warkit)\\.(?:[a-z0-9_-]+\\.)*[a-z0-9_-]+)\"");

    @Test
    void pluginYamlParsesAndHasUniquePermissionNodes() throws Exception {
        String text = Files.readString(Path.of("src/main/resources/plugin.yml"));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new StringReader(text));

        assertEquals("MilitaryCraft", yaml.getString("name"));
        assertEquals("me.bibo.militarycraft.MilitaryCraftPlugin", yaml.getString("main"));
        assertNotNull(yaml.getConfigurationSection("commands.mc"));
        assertNotNull(yaml.getConfigurationSection("permissions"));

        Set<String> seen = permissionNodes(text);
        assertTrue(seen.contains("militarycraft.artillery.fire"));
        assertTrue(seen.contains("militarycraft.antiair.give"));
        assertTrue(seen.contains("tankcraft.use"));
        assertTrue(seen.contains("tankcraft.drive"));
        assertTrue(seen.contains("tankcraft.place"));
        assertTrue(seen.contains("tankcraft.give"));
        assertTrue(seen.contains("tankcraft.spawn"));
        assertTrue(seen.contains("tankcraft.admin"));
        assertTrue(seen.contains("kamazcraft.use"));
        assertTrue(seen.contains("kamazcraft.place"));
        assertTrue(seen.contains("kamazcraft.give"));
        assertTrue(seen.contains("kamazcraft.spawn"));
        assertTrue(seen.contains("kamazcraft.admin"));
        assertTrue(seen.contains("jetcraft.use"));
        assertTrue(seen.contains("jetcraft.place"));
        assertTrue(seen.contains("jetcraft.admin"));
        assertTrue(seen.contains("airshipcraft.use"));
        assertTrue(seen.contains("airshipcraft.place"));
        assertTrue(seen.contains("airshipcraft.admin"));
        assertTrue(seen.contains("dronecraft.use"));
        assertTrue(seen.contains("dronecraft.place"));
        assertTrue(seen.contains("dronecraft.admin"));
        assertTrue(seen.contains("motocraft.use"));
        assertTrue(seen.contains("motocraft.place"));
        assertTrue(seen.contains("motocraft.give"));
        assertTrue(seen.contains("motocraft.spawn"));
        assertTrue(seen.contains("motocraft.admin"));
        assertTrue(seen.contains("pickupcraft.use"));
        assertTrue(seen.contains("pickupcraft.drive"));
        assertTrue(seen.contains("pickupcraft.passenger"));
        assertTrue(seen.contains("pickupcraft.gun"));
        assertTrue(seen.contains("pickupcraft.place"));
        assertTrue(seen.contains("pickupcraft.give"));
        assertTrue(seen.contains("pickupcraft.spawn"));
        assertTrue(seen.contains("pickupcraft.admin"));
        assertTrue(seen.contains("traincraft.use"));
        assertTrue(seen.contains("traincraft.place"));
        assertTrue(seen.contains("traincraft.give"));
        assertTrue(seen.contains("traincraft.admin"));
        assertTrue(seen.contains("antiaircraft.use"));
        assertTrue(seen.contains("antiaircraft.place"));
        assertTrue(seen.contains("antiaircraft.admin"));
        assertTrue(seen.contains("tckbus.use"));
        assertTrue(seen.contains("tckbus.place"));
        assertTrue(seen.contains("tckbus.admin"));
        assertTrue(seen.contains("tckbus.immune"));
        assertTrue(seen.contains("helicraft.use"));
        assertTrue(seen.contains("helicraft.place"));
        assertTrue(seen.contains("helicraft.admin"));
        assertTrue(seen.contains("militarycraft.airstrike.call"));
        assertTrue(seen.contains("airstrike.use"));
        assertTrue(seen.contains("airstrike.give"));
        assertTrue(seen.contains("airstrike.reload"));
        assertTrue(seen.contains("airstrike.bypass-cooldown"));
        assertTrue(seen.contains("nuke.use"));
        assertTrue(seen.contains("nuke.give"));
        assertTrue(seen.contains("nuke.reload"));
        assertTrue(seen.contains("nuke.bypass-cooldown"));
        assertTrue(seen.contains("vehiclecamera.admin"));
        assertTrue(seen.contains("warkit.admin"));
        assertTrue(seen.contains("militarycraft.warkit.give"));
    }

    @Test
    void pluginYamlDeclaresEveryLiteralCommandPermission() throws Exception {
        Set<String> declared = permissionNodes(Files.readString(Path.of("src/main/resources/plugin.yml")));
        Set<String> used = new HashSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files
                    .filter(path -> path.getFileName().toString().contains("Command"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                Matcher matcher = COMMAND_PERMISSION_LITERAL.matcher(Files.readString(file));
                while (matcher.find()) {
                    used.add(matcher.group(1));
                }
            }
        }

        assertTrue(!used.isEmpty(), "no command permissions were scanned");
        for (String permission : used) {
            assertTrue(declared.contains(permission), "missing plugin.yml permission: " + permission);
        }
    }

    @Test
    void configYamlParsesAndDeclaresEveryModuleSection() throws Exception {
        String text = Files.readString(Path.of("src/main/resources/config.yml"));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new StringReader(text));

        assertNotNull(yaml.getConfigurationSection("modules"));
        for (String module : MODULES) {
            assertTrue(yaml.isBoolean("modules." + module + ".enabled"),
                    "missing modules." + module + ".enabled");
            assertNotNull(yaml.getConfigurationSection(module), "missing module section: " + module);
        }
    }

    private static Set<String> permissionNodes(String text) {
        Matcher matcher = PERMISSION_NODE.matcher(text);
        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            assertTrue(seen.add(matcher.group(1)), "duplicate permission node: " + matcher.group(1));
        }
        return seen;
    }
}
