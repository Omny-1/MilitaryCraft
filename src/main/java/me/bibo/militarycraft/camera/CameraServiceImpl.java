package me.bibo.militarycraft.camera;

import me.bibo.militarycraft.core.util.MathUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class CameraServiceImpl implements CameraService {

    private static final int MAX_VEHICLE_DEPTH = 4;
    private static final double MIN_SCALE = 0.0625;
    private static final double MAX_SCALE = 16.0;
    private static final Map<String, String> TYPE_TO_SCOREBOARD_TAG = Map.ofEntries(
            Map.entry("tank", "tankcraft_entity"),
            Map.entry("kamaz", "kamazcraft_entity"),
            Map.entry("pickup", "pickupcraft_entity"),
            Map.entry("jet", "jetcraft_entity"),
            Map.entry("helicopter", "helicraft_entity"),
            Map.entry("airship", "airshipcraft_entity"),
            Map.entry("drone", "dronecraft_entity"),
            Map.entry("moto", "motocraft_entity"),
            Map.entry("train", "traincraft_entity"),
            Map.entry("antiair", "antiaircraft_entity"),
            Map.entry("tckbus", "tckbus_entity"));

    private final NamespacedKey modifierKey = new NamespacedKey("camera", "zoom");
    private final Attribute scaleAttribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("scale"));
    private final Map<String, Double> tagScales = new ConcurrentHashMap<>();
    private final Map<String, Double> compatibilityScales = new ConcurrentHashMap<>();

    @Override
    public void registerScale(String vehicleType, double scale) {
        if (vehicleType == null || vehicleType.isBlank()) {
            throw new IllegalArgumentException("Vehicle type must not be blank");
        }
        double safeScale = Double.isFinite(scale) ? scale : 1.0;
        String tag = TYPE_TO_SCOREBOARD_TAG.getOrDefault(vehicleType, vehicleType);
        compatibilityScales.put(tag, MathUtil.clamp(safeScale, MIN_SCALE, MAX_SCALE));
    }

    void loadConfigValues(ConfigurationSection config, Logger logger) {
        Map<String, Double> loaded = new HashMap<>();
        ConfigurationSection vehicles = config != null ? config.getConfigurationSection("vehicles") : null;
        if (vehicles != null) {
            for (String tag : vehicles.getKeys(false)) {
                double scale = vehicles.getDouble(tag, 1.0);
                double clamped = MathUtil.clamp(scale, MIN_SCALE, MAX_SCALE);
                if (scale != clamped && logger != null) {
                    logger.warning("Scale " + scale + " for '" + tag + "' is out of range, clamped to " + clamped + ".");
                }
                loaded.put(tag, clamped);
            }
        }
        tagScales.clear();
        tagScales.putAll(compatibilityScales);
        tagScales.putAll(loaded);
    }

    public void reconcileAll() {
        if (scaleAttribute == null) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Double desired = desiredScaleFor(p);
            if (desired == null) {
                clear(p);
            } else {
                apply(p, desired);
            }
        }
    }

    public void clearAll() {
        if (scaleAttribute == null) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            clear(p);
        }
    }

    void clearPlayer(Player player) {
        if (scaleAttribute != null && player != null) {
            clear(player);
        }
    }

    private Double desiredScaleFor(Player player) {
        Entity vehicle = player.getVehicle();
        int depth = 0;
        while (vehicle != null && depth++ < MAX_VEHICLE_DEPTH) {
            for (String tag : vehicle.getScoreboardTags()) {
                Double scale = tagScales.get(tag);
                if (scale != null) {
                    return scale;
                }
            }
            vehicle = vehicle.getVehicle();
        }
        return null;
    }

    private void apply(Player player, double scale) {
        AttributeInstance inst = player.getAttribute(scaleAttribute);
        if (inst == null) {
            return;
        }
        double amount = scale - inst.getBaseValue();
        AttributeModifier existing = findOurModifier(inst);
        if (existing != null) {
            if (Math.abs(existing.getAmount() - amount) < 1.0e-6) {
                return;
            }
            inst.removeModifier(existing);
        }
        // Transient: a camera zoom is ephemeral view state and must never persist with
        // player data (a persistent modifier survives crash/plugin removal and strands
        // the player zoomed). Any stale persistent modifier is removed above on next apply/clear.
        inst.addTransientModifier(new AttributeModifier(modifierKey, amount, AttributeModifier.Operation.ADD_NUMBER));
    }

    private void clear(Player player) {
        AttributeInstance inst = player.getAttribute(scaleAttribute);
        if (inst == null) {
            return;
        }
        AttributeModifier existing = findOurModifier(inst);
        if (existing != null) {
            inst.removeModifier(existing);
        }
    }

    private AttributeModifier findOurModifier(AttributeInstance inst) {
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
            if (modifierKey.equals(m.getKey())) {
                return m;
            }
        }
        return null;
    }
}
