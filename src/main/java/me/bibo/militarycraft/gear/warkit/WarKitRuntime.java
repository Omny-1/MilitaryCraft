package me.bibo.militarycraft.gear.warkit;

import me.bibo.militarycraft.MilitaryCraftPlugin;
import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.gear.warkit.weapon.DeployableManager;
import me.bibo.militarycraft.gear.warkit.weapon.ExplosivesManager;
import me.bibo.militarycraft.gear.warkit.weapon.FallImmunity;
import me.bibo.militarycraft.gear.warkit.weapon.GadgetService;
import me.bibo.militarycraft.gear.warkit.weapon.GrenadeService;
import me.bibo.militarycraft.gear.warkit.weapon.GunService;
import me.bibo.militarycraft.gear.warkit.weapon.SentryManager;
import me.bibo.militarycraft.gear.warkit.weapon.SprayService;
import me.bibo.militarycraft.gear.warkit.weapon.TrenchService;
import me.bibo.militarycraft.gear.warkit.weapon.WeaponConfig;
import me.bibo.militarycraft.gear.warkit.weapon.Weapons;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.logging.Logger;

public final class WarKitRuntime {

    private static final String MODULE_ID = "warkit";

    private final Core core;
    private ConfigurationSection config;
    private Settings settings;
    private WeaponConfig weaponConfig;
    private WarItems items;
    private Weapons weapons;
    private ChannelManager channels;
    private PainkillerManager painkiller;
    private CamoManager camo;
    private MarkerManager marker;
    private GunService guns;
    private GrenadeService grenades;
    private SprayService spray;
    private DeployableManager deployables;
    private FallImmunity fallImmunity;
    private TrenchService trench;
    private ExplosivesManager explosives;
    private GadgetService gadgets;
    private SentryManager sentries;
    private BukkitTask tickerTask;

    WarKitRuntime(Core core) {
        this.core = core;
        loadConfigSnapshot();
        items = new WarItems(this);
        weapons = new Weapons(this);
        weapons.registerAll();
        channels = new ChannelManager(this);
        painkiller = new PainkillerManager(this);
        camo = new CamoManager(this);
        marker = new MarkerManager(this);
        guns = new GunService(this);
        grenades = new GrenadeService(this);
        spray = new SprayService(this);
        deployables = new DeployableManager(this);
        fallImmunity = new FallImmunity();
        trench = new TrenchService(this);
        explosives = new ExplosivesManager(this);
        gadgets = new GadgetService(this);
        sentries = new SentryManager(this);
    }

    void startTicker() {
        stopTicker();
        tickerTask = getServer().getScheduler().runTaskTimer(bukkitPlugin(), new Ticker(this), 10L, 10L);
    }

    void shutdown() {
        stopTicker();
        if (camo != null) camo.deactivateAll();
        if (channels != null) channels.cancelAll();
        if (guns != null) guns.cancelAllReloads();
        if (spray != null) spray.cleanupAll();
        if (grenades != null) grenades.cleanupAll();
        if (trench != null) trench.cancelAll();
        if (gadgets != null) gadgets.cleanupAll();
        if (painkiller != null) painkiller.clearAll();
        if (marker != null) marker.clearAll();
        if (deployables != null) deployables.cleanupAll();
        if (explosives != null) explosives.cleanupAll();
        if (sentries != null) sentries.cleanupAll();
    }

    public void reloadSettings() {
        if (channels != null) channels.cancelAll();
        if (guns != null) guns.cancelAllReloads();
        if (spray != null) spray.cleanupAll();
        if (trench != null) trench.cancelAll();
        loadConfigSnapshot();
    }

    public MilitaryCraftPlugin bukkitPlugin() {
        return core.plugin();
    }

    public Core core() {
        return core;
    }

    public Server getServer() {
        return core.plugin().getServer();
    }

    public Logger getLogger() {
        return core.logger();
    }

    public File getDataFolder() {
        return moduleDataFolder(core);
    }

    public ConfigurationSection getConfig() {
        return config;
    }

    public Settings settings() {
        return settings;
    }

    public WeaponConfig weaponConfig() {
        return weaponConfig;
    }

    public WarItems items() {
        return items;
    }

    public Weapons weapons() {
        return weapons;
    }

    public ChannelManager channels() {
        return channels;
    }

    public PainkillerManager painkiller() {
        return painkiller;
    }

    public CamoManager camo() {
        return camo;
    }

    public MarkerManager marker() {
        return marker;
    }

    public GunService guns() {
        return guns;
    }

    public GrenadeService grenades() {
        return grenades;
    }

    public SprayService spray() {
        return spray;
    }

    public DeployableManager deployables() {
        return deployables;
    }

    public FallImmunity fallImmunity() {
        return fallImmunity;
    }

    public TrenchService trench() {
        return trench;
    }

    public ExplosivesManager explosives() {
        return explosives;
    }

    public GadgetService gadgets() {
        return gadgets;
    }

    public SentryManager sentries() {
        return sentries;
    }

    private void stopTicker() {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
    }

    private void loadConfigSnapshot() {
        this.config = section(core);
        this.settings = new Settings(config);
        this.weaponConfig = new WeaponConfig(config);
    }

    private static ConfigurationSection section(Core core) {
        ConfigurationSection current = core.plugin().getConfig().getConfigurationSection(MODULE_ID);
        if (isOriginalShape(current)) {
            return current;
        }
        File legacy = new File(moduleDataFolder(core), "config.yml");
        if (legacy.isFile()) {
            return YamlConfiguration.loadConfiguration(legacy);
        }
        return current != null ? current : new YamlConfiguration();
    }

    private static boolean isOriginalShape(ConfigurationSection section) {
        return section != null
                && section.contains("medkit.channel-seconds")
                && section.contains("weapons.rifle.damage")
                && section.contains("weapons.sentry-gun.damage");
    }

    private static File moduleDataFolder(Core core) {
        File parent = core.plugin().getDataFolder().getParentFile();
        if (parent == null) {
            return new File(core.plugin().getDataFolder(), "WarKit");
        }
        return new File(parent, "WarKit");
    }
}
