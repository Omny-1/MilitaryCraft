package me.bibo.militarycraft;

import me.bibo.militarycraft.camera.CameraModule;
import me.bibo.militarycraft.camera.CameraServiceImpl;
import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.combat.VehicleCombatServiceImpl;
import me.bibo.militarycraft.core.command.CommandAccess;
import me.bibo.militarycraft.core.command.RootCommand;
import me.bibo.militarycraft.core.event.EventBus;
import me.bibo.militarycraft.core.item.ItemFactory;
import me.bibo.militarycraft.core.key.Keys;
import me.bibo.militarycraft.core.module.MilitaryModule;
import me.bibo.militarycraft.core.module.ModuleManager;
import me.bibo.militarycraft.core.vehicle.VehicleServiceImpl;
import me.bibo.militarycraft.gear.warkit.WarKitModule;
import me.bibo.militarycraft.vehicles.airship.AirshipModule;
import me.bibo.militarycraft.vehicles.drone.DroneModule;
import me.bibo.militarycraft.vehicles.helicopter.HelicopterModule;
import me.bibo.militarycraft.vehicles.jet.JetModule;
import me.bibo.militarycraft.vehicles.kamaz.KamazModule;
import me.bibo.militarycraft.vehicles.moto.MotoModule;
import me.bibo.militarycraft.vehicles.pickup.PickupModule;
import me.bibo.militarycraft.vehicles.tank.TankModule;
import me.bibo.militarycraft.vehicles.train.TrainModule;
import me.bibo.militarycraft.weapons.airstrike.AirstrikeModule;
import me.bibo.militarycraft.weapons.antiair.AntiAirModule;
import me.bibo.militarycraft.weapons.artillery.ArtilleryModule;
import me.bibo.militarycraft.weapons.nuke.NukeModule;
import me.bibo.militarycraft.weapons.tckbus.TckBusModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** The only {@link JavaPlugin} in the merged project (§1). */
public final class MilitaryCraftPlugin extends JavaPlugin {

    private Core core;
    private ModuleManager moduleManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Keys.init(this);

        EventBus events = new EventBus(this); // self-registers the core Bukkit listeners
        CommandAccess commandAccess = new CommandAccess(this);
        getServer().getPluginManager().registerEvents(commandAccess, this);
        RootCommand rootCommand = new RootCommand(this, commandAccess);
        ItemFactory items = new ItemFactory();

        VehicleServiceImpl vehicles = new VehicleServiceImpl();
        VehicleCombatServiceImpl combat = new VehicleCombatServiceImpl(vehicles);
        events.register(combat); // the one ExplosionSink that routes blasts to every vehicle (§11)
        CameraServiceImpl camera = new CameraServiceImpl();

        this.core = new Core(this, events, rootCommand, items, vehicles, combat, camera);

        PluginCommand command = getCommand("mc");
        if (command == null) {
            getLogger().severe("Command 'mc' is missing from plugin.yml - disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        command.setExecutor(rootCommand);
        command.setTabCompleter(rootCommand);

        // Keep this explicit: one bootstrap, deterministic module order, no classpath scanning.
        this.moduleManager = new ModuleManager(this, List.<MilitaryModule>of(
                new CameraModule(camera),
                new TankModule(),
                new KamazModule(),
                new MotoModule(),
                new PickupModule(),
                new JetModule(),
                new HelicopterModule(),
                new AirshipModule(),
                new DroneModule(),
                new TrainModule(),
                new AntiAirModule(),
                new TckBusModule(),
                new AirstrikeModule(),
                new NukeModule(),
                new WarKitModule(),
                new ArtilleryModule()));
        moduleManager.enableAll(core);

        getLogger().info("MilitaryCraft " + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
    }

    /** `/mc reload`: reload config.yml and push a fresh settings snapshot to every live module. */
    public java.util.List<String> reloadAll() {
        reloadConfig();
        core.refreshConfig();
        return moduleManager.reloadAll(core);
    }

    public Core core() {
        return core;
    }

    public ModuleManager moduleManager() {
        return moduleManager;
    }
}
