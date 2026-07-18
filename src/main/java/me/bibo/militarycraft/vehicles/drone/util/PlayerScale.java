package me.bibo.militarycraft.vehicles.drone.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;

/**
 * Shrinks the operator while they fly the UAV so the camera sits closer to the
 * airframe. Uses the vanilla {@code minecraft:scale} attribute via a removable
 * modifier (looked up by key so we don't depend on the {@code Attribute} enum
 * renames across the 1.21.x line), and cleanly takes it back off on exit.
 */
public final class PlayerScale {

    private static Attribute scaleAttribute;
    private static NamespacedKey modifierKey;

    private PlayerScale() {
    }

    public static void init(Plugin plugin) {
        scaleAttribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("scale"));
        modifierKey = new NamespacedKey("dronecraft", "drone_zoom");
    }

    /** Make the player this scale (1.0 = normal). No-op on old builds. */
    public static void apply(Player player, double scale) {
        if (scaleAttribute == null) {
            return;
        }
        AttributeInstance inst = player.getAttribute(scaleAttribute);
        if (inst == null) {
            return;
        }
        clear(player);
        double amount = scale - inst.getBaseValue(); // ADD_NUMBER: final = base + amount
        inst.addModifier(new AttributeModifier(modifierKey, amount, AttributeModifier.Operation.ADD_NUMBER));
    }

    /** Remove our scale modifier, restoring the player's normal size. */
    public static void clear(Player player) {
        if (scaleAttribute == null) {
            return;
        }
        AttributeInstance inst = player.getAttribute(scaleAttribute);
        if (inst == null) {
            return;
        }
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
            if (modifierKey.equals(m.getKey())) {
                inst.removeModifier(m);
            }
        }
    }
}
