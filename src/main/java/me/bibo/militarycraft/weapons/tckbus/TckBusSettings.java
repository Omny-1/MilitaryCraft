package me.bibo.militarycraft.weapons.tckbus;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Typed, validated snapshot of config.yml. Re-created on every reload, so listeners
 * and the tick loop always read a consistent, parsed copy (never the raw YAML).
 */
public final class TckBusSettings {

    private final Plugin plugin;

    // placement
    public final boolean consumeItem;
    public final int maxPerPlayer;
    public final int maxLoaded;
    public final boolean yawSnap;

    // model
    public final Material bodyBlock;
    public final Material camoLightBlock;
    public final Material camoMidBlock;
    public final Material camoBlackBlock;
    public final Material glassBlock;
    public final Material wheelBlock;
    public final Material trimBlock;
    public final Material chromeBlock;
    public final Material lightBlock;
    public final float viewRange;
    public final boolean rounded;
    public final boolean camo;
    public final boolean textEnabled;
    public final String textContent;
    public final TextColor textColor;
    public final float textScale;
    public final double textYawOffset;

    // durability
    public final double creeperDamage;
    public final double creepersToDestroy;
    public final double maxHealth;
    public final double meleeDamage;
    public final double arrowDamage;
    public final double contactRadius;
    public final boolean debris;
    public final boolean dropPlacerOnDestroy;

    // workers
    public final EntityType workerType;
    public final int workerCount;
    public final String workerName;
    public final boolean workerShowName;
    public final double workerHealth;
    public final Material handItem;
    public final Material helmet;
    public final Material chestplate;
    public final Material leggings;
    public final Material boots;
    public final Color uniformColor;
    public final double workerSpeed;
    public final double aggroRange;
    public final double leashRange;
    public final double wanderRadius;
    public final int retargetTicks;
    public final double activationRange;

    // snatch
    public final boolean snatchEnabled;
    public final double meleeRange;
    public final int hitCooldownTicks;
    public final double hitDamage;
    public final int stunTicks;
    public final double captureRadius;
    public final int requiredWorkers;
    public final boolean ignoreCreative;
    public final boolean ignoreOwner;
    public final int escapeSpeedTicks;
    public final int freezeSlowness;
    public final int immunityTicks;
    public final double warnRange;
    public final String warnTitle;
    public final int warnCooldownTicks;

    // capture
    public final int pullTicks;
    public final String deathMessage;
    public final boolean killPlayer;

    public final boolean debug;

    public final String defaultSkinId;
    private final Map<String, Skin> skinsByKey;
    private final List<String> skinIds;

    public static final class Skin {
        public final String id;
        public final String displayName;
        public final String busName;
        public final String itemName;
        public final String workerName;
        public final String workerSingular;
        public final String workerPlural;
        public final Material bodyBlock;
        public final Material camoLightBlock;
        public final Material camoMidBlock;
        public final Material camoBlackBlock;
        public final Material glassBlock;
        public final Material wheelBlock;
        public final Material trimBlock;
        public final Material chromeBlock;
        public final Material lightBlock;
        public final boolean textEnabled;
        public final String textContent;
        public final TextColor textColor;
        public final Color uniformColor;

        private Skin(String id, String displayName, String busName, String itemName,
                     String workerName, String workerSingular, String workerPlural,
                     Material bodyBlock, Material camoLightBlock, Material camoMidBlock,
                     Material camoBlackBlock, Material glassBlock, Material wheelBlock,
                     Material trimBlock, Material chromeBlock, Material lightBlock,
                     boolean textEnabled, String textContent, TextColor textColor,
                     Color uniformColor) {
            this.id = id;
            this.displayName = displayName;
            this.busName = busName;
            this.itemName = itemName;
            this.workerName = workerName;
            this.workerSingular = workerSingular;
            this.workerPlural = workerPlural;
            this.bodyBlock = bodyBlock;
            this.camoLightBlock = camoLightBlock;
            this.camoMidBlock = camoMidBlock;
            this.camoBlackBlock = camoBlackBlock;
            this.glassBlock = glassBlock;
            this.wheelBlock = wheelBlock;
            this.trimBlock = trimBlock;
            this.chromeBlock = chromeBlock;
            this.lightBlock = lightBlock;
            this.textEnabled = textEnabled;
            this.textContent = textContent;
            this.textColor = textColor;
            this.uniformColor = uniformColor;
        }

        public Material materialFor(TckBusModel.Role role) {
            return switch (role) {
                case BODY -> bodyBlock;
                case CAMO_LIGHT -> camoLightBlock;
                case CAMO_MID -> camoMidBlock;
                case CAMO_BLACK -> camoBlackBlock;
                case GLASS -> glassBlock;
                case WHEEL -> wheelBlock;
                case TRIM -> trimBlock;
                case CHROME -> chromeBlock;
                case LIGHT -> lightBlock;
            };
        }
    }

    public TckBusSettings(Plugin plugin, ConfigurationSection root) {
        this.plugin = plugin;
        ConfigurationSection c = root != null ? root : new MemoryConfiguration();

        consumeItem = c.getBoolean("placement.consume-item", true);
        maxPerPlayer = c.getInt("placement.max-per-player", 0);
        maxLoaded = c.getInt("placement.max-loaded", 40);
        yawSnap = c.getBoolean("placement.yaw-snap", false);

        bodyBlock = block(c.getString("model.body"), Material.GREEN_TERRACOTTA);
        camoLightBlock = block(c.getString("model.camo-light"), Material.WHITE_TERRACOTTA);
        camoMidBlock = block(c.getString("model.camo-mid"), Material.MOSS_BLOCK);
        camoBlackBlock = block(c.getString("model.camo-black"), Material.BLACK_CONCRETE);
        glassBlock = block(c.getString("model.glass"), Material.BLACK_STAINED_GLASS);
        wheelBlock = block(c.getString("model.wheel"), Material.BLACK_CONCRETE);
        trimBlock = block(c.getString("model.trim"), Material.POLISHED_BLACKSTONE);
        chromeBlock = block(c.getString("model.chrome"), Material.IRON_BLOCK);
        lightBlock = block(c.getString("model.light"), Material.GLOWSTONE);
        viewRange = (float) c.getDouble("model.view-range", 3.0);
        rounded = c.getBoolean("model.rounded", true);
        camo = c.getBoolean("model.camo", false);
        textEnabled = c.getBoolean("model.text.enabled", true);
        textContent = c.getString("model.text.content", "TCK");
        textColor = color(c.getString("model.text.color"), TextColor.color(0xFFFFFF));
        textScale = (float) c.getDouble("model.text.scale", 1.6);
        textYawOffset = c.getDouble("model.text.yaw-offset", 0.0);

        creeperDamage = c.getDouble("durability.creeper-damage", 50.0);
        creepersToDestroy = Math.max(0.1, c.getDouble("durability.creepers-to-destroy", 1.0));
        maxHealth = creeperDamage * creepersToDestroy;
        meleeDamage = Math.max(0.0, c.getDouble("durability.melee-damage", 10.0));
        arrowDamage = Math.max(0.0, c.getDouble("durability.arrow-damage", 0.0));
        contactRadius = c.getDouble("durability.contact-radius", 3.0);
        debris = c.getBoolean("durability.debris", true);
        dropPlacerOnDestroy = c.getBoolean("durability.drop-placer-on-destroy", false);

        workerType = mobType(c.getString("workers.type"), EntityType.PILLAGER);
        workerCount = Math.max(1, Math.min(6, c.getInt("workers.count", 2)));
        workerName = c.getString("workers.name", "&2TCK Officer");
        workerShowName = c.getBoolean("workers.show-name", false);
        workerHealth = Math.max(1.0, c.getDouble("workers.health", 30.0));
        handItem = itemOrNull(c.getString("workers.hand-item"), Material.IRON_AXE);
        helmet = itemOrNull(c.getString("workers.uniform.helmet"), Material.IRON_HELMET);
        chestplate = itemOrNull(c.getString("workers.uniform.chestplate"), Material.LEATHER_CHESTPLATE);
        leggings = itemOrNull(c.getString("workers.uniform.leggings"), Material.LEATHER_LEGGINGS);
        boots = itemOrNull(c.getString("workers.uniform.boots"), Material.LEATHER_BOOTS);
        uniformColor = bukkitColor(c.getString("workers.uniform.color"), Color.fromRGB(0x3B3B23));
        workerSpeed = Math.max(0.1, c.getDouble("workers.speed", 1.15));
        aggroRange = Math.max(1.0, c.getDouble("workers.aggro-range", 14.0));
        leashRange = Math.max(aggroRange, c.getDouble("workers.leash-range", 22.0));
        wanderRadius = Math.max(1.0, c.getDouble("workers.wander-radius", 6.0));
        retargetTicks = Math.max(1, c.getInt("workers.retarget-ticks", 8));
        activationRange = Math.max(8.0, c.getDouble("workers.activation-range", 40.0));

        snatchEnabled = c.getBoolean("snatch.enabled", true);
        meleeRange = Math.max(0.5, c.getDouble("snatch.melee-range", 2.4));
        hitCooldownTicks = Math.max(1, c.getInt("snatch.hit-cooldown-ticks", 30));
        hitDamage = Math.max(0.0, c.getDouble("snatch.hit-damage", 1.0));
        stunTicks = Math.max(5, c.getInt("snatch.stun-ticks", 40));
        captureRadius = Math.max(1.0, c.getDouble("snatch.capture-radius", 4.0));
        requiredWorkers = Math.max(1, c.getInt("snatch.required-workers", 2));
        ignoreCreative = c.getBoolean("snatch.ignore-creative", true);
        ignoreOwner = c.getBoolean("snatch.ignore-owner", false);
        escapeSpeedTicks = Math.max(0, c.getInt("snatch.escape-speed-ticks", 60));
        freezeSlowness = Math.max(0, Math.min(255, c.getInt("snatch.freeze-slowness-amplifier", 6)));
        immunityTicks = Math.max(0, c.getInt("snatch.immunity-ticks", 40));
        warnRange = Math.max(1.0, c.getDouble("snatch.warn-range", 16.0));
        warnTitle = c.getString("snatch.warn-title", "&cDODGER, RUN!!!");
        warnCooldownTicks = Math.max(20, c.getInt("snatch.warn-cooldown-ticks", 100));

        pullTicks = Math.max(4, c.getInt("capture.pull-ticks", 30));
        deathMessage = c.getString("capture.death-message",
                "&7Player &f%player% &7was killed... or rather &4mobilized&7.");
        killPlayer = c.getBoolean("capture.kill-player", true);

        debug = c.getBoolean("debug", false);

        Skin baseSkin = new Skin(
                "tck",
                "TCK",
                "TCK Bus",
                "&a&lTCK Summons",
                workerName,
                "TCK Officer",
                "TCK Officers",
                bodyBlock,
                camoLightBlock,
                camoMidBlock,
                camoBlackBlock,
                glassBlock,
                wheelBlock,
                trimBlock,
                chromeBlock,
                lightBlock,
                textEnabled,
                textContent,
                textColor,
                uniformColor);

        Skin tzahalSkin = new Skin(
                "tzahal",
                "Tzahal",
                "Tzahal Bus",
                "&7&lTzahal Summons",
                "&7Mossadik",
                "Mossadik",
                "Mossadiks",
                Material.GRAY_TERRACOTTA,
                Material.LIGHT_GRAY_TERRACOTTA,
                Material.GRAY_CONCRETE,
                Material.BLACK_CONCRETE,
                glassBlock,
                wheelBlock,
                trimBlock,
                chromeBlock,
                lightBlock,
                textEnabled,
                "Tzahal",
                TextColor.color(0xE6E6E6),
                Color.fromRGB(0x4A4F54));

        Map<String, Skin> byId = new LinkedHashMap<>();
        Map<String, Skin> byKey = new LinkedHashMap<>();
        registerSkin(byId, byKey, readSkin(c.getConfigurationSection("skins.variants.tck"), baseSkin),
                List.of("default", "standard", "standart", "tck"));
        registerSkin(byId, byKey, readSkin(c.getConfigurationSection("skins.variants.tzahal"), tzahalSkin),
                List.of("tzahal", "tsahal", "zahal", "idf"));

        ConfigurationSection variants = c.getConfigurationSection("skins.variants");
        if (variants != null) {
            for (String key : variants.getKeys(false)) {
                String normalized = normalizeSkinKey(key);
                if (normalized.equals("tck") || normalized.equals("tzahal")) {
                    continue;
                }
                ConfigurationSection section = variants.getConfigurationSection(key);
                if (section != null) {
                    registerSkin(byId, byKey, readSkin(section, baseSkin), section.getStringList("aliases"));
                }
            }
        }
        Skin defaultSkin = byKey.getOrDefault(normalizeSkinKey(c.getString("skins.default", "tck")),
                byKey.get("tck"));
        defaultSkinId = defaultSkin.id;
        skinsByKey = Collections.unmodifiableMap(byKey);
        skinIds = List.copyOf(byId.keySet());
    }

    /** Resolve the configured block material for a model part role. */
    public Material materialFor(TckBusModel.Role role) {
        return switch (role) {
            case BODY -> bodyBlock;
            case CAMO_LIGHT -> camoLightBlock;
            case CAMO_MID -> camoMidBlock;
            case CAMO_BLACK -> camoBlackBlock;
            case GLASS -> glassBlock;
            case WHEEL -> wheelBlock;
            case TRIM -> trimBlock;
            case CHROME -> chromeBlock;
            case LIGHT -> lightBlock;
        };
    }

    public Skin defaultSkin() {
        return skin(defaultSkinId);
    }

    public Skin skin(String key) {
        Skin resolved = key == null ? null : skinsByKey.get(normalizeSkinKey(key));
        return resolved != null ? resolved : skinsByKey.get(defaultSkinId);
    }

    public boolean isSkinName(String key) {
        return key != null && skinsByKey.containsKey(normalizeSkinKey(key));
    }

    public List<String> skinSuggestions() {
        return skinIds;
    }

    // ----------------------------------------------------------------- parsing helpers

    private Skin readSkin(ConfigurationSection section, Skin base) {
        if (section == null) {
            return base;
        }
        String id = normalizeSkinKey(section.getString("id", section.getName()));
        return new Skin(
                id,
                section.getString("display-name", base.displayName),
                section.getString("bus-name", base.busName),
                section.getString("item-name", base.itemName),
                section.getString("workers.name", base.workerName),
                section.getString("workers.singular", base.workerSingular),
                section.getString("workers.plural", base.workerPlural),
                block(section.getString("model.body"), base.bodyBlock),
                block(section.getString("model.camo-light"), base.camoLightBlock),
                block(section.getString("model.camo-mid"), base.camoMidBlock),
                block(section.getString("model.camo-black"), base.camoBlackBlock),
                block(section.getString("model.glass"), base.glassBlock),
                block(section.getString("model.wheel"), base.wheelBlock),
                block(section.getString("model.trim"), base.trimBlock),
                block(section.getString("model.chrome"), base.chromeBlock),
                block(section.getString("model.light"), base.lightBlock),
                section.getBoolean("model.text.enabled", base.textEnabled),
                section.getString("model.text.content", base.textContent),
                color(section.getString("model.text.color"), base.textColor),
                bukkitColor(section.getString("workers.uniform-color"), base.uniformColor));
    }

    private void registerSkin(Map<String, Skin> byId, Map<String, Skin> byKey, Skin skin, List<String> aliases) {
        if (skin == null || skin.id == null || skin.id.isBlank()) {
            return;
        }
        byId.put(skin.id, skin);
        byKey.put(skin.id, skin);
        for (String alias : aliases) {
            String key = normalizeSkinKey(alias);
            if (!key.isBlank()) {
                byKey.put(key, skin);
            }
        }
    }

    private String normalizeSkinKey(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private Material block(String name, Material fallback) {
        Material m = matchMaterial(name);
        if (m == null) {
            return fallback;
        }
        if (!m.isBlock()) {
            warn("Material '" + name + "' is not a block, using " + fallback);
            return fallback;
        }
        return m;
    }

    /** A material that may legitimately be empty (no helmet, no hand item, ...). */
    private Material itemOrNull(String name, Material fallback) {
        if (name != null && (name.isBlank() || name.equalsIgnoreCase("none") || name.equalsIgnoreCase("air"))) {
            return null;
        }
        Material m = matchMaterial(name);
        return m != null ? m : fallback;
    }

    private Material matchMaterial(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Material m = Material.matchMaterial(name.trim());
        if (m == null) {
            warn("Unknown material '" + name + "'");
        }
        return m;
    }

    private EntityType mobType(String name, EntityType fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            EntityType t = EntityType.valueOf(name.trim().toUpperCase());
            Class<?> clazz = t.getEntityClass();
            if (clazz != null && Mob.class.isAssignableFrom(clazz)) {
                return t;
            }
            warn("Entity type '" + name + "' is not a usable mob, using " + fallback);
        } catch (IllegalArgumentException ex) {
            warn("Unknown entity type '" + name + "', using " + fallback);
        }
        return fallback;
    }

    private TextColor color(String hex, TextColor fallback) {
        if (hex == null || hex.isBlank()) {
            return fallback;
        }
        TextColor parsed = TextColor.fromHexString(hex.trim().startsWith("#") ? hex.trim() : "#" + hex.trim());
        return parsed != null ? parsed : fallback;
    }

    private Color bukkitColor(String hex, Color fallback) {
        TextColor tc = color(hex, null);
        return tc != null ? Color.fromRGB(tc.value() & 0xFFFFFF) : fallback;
    }

    private void warn(String msg) {
        plugin.getLogger().log(Level.WARNING, "[config] " + msg);
    }
}


