package me.bibo.militarycraft.gear.warkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The plugin's single periodic task (every 10 ticks):
 * visor night vision, marker beacons, and camouflage checks.
 */
public final class Ticker implements Runnable {

    /**
     * Visor night vision lasts longer than 10 seconds so the client does not flicker
     * when the effect nears its end.
     */
    private static final int NV_DURATION_TICKS = 400;

    private final WarKitRuntime plugin;
    /** Players whose night vision was granted by the visor; external potions are left alone. */
    private final Set<UUID> nightVisionGranted = new HashSet<>();

    public Ticker(WarKitRuntime plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.camo().tick();
        plugin.marker().tick(System.currentTimeMillis());
        plugin.deployables().tick();
        plugin.explosives().tick();
        plugin.gadgets().scannerTick();
        visorTick();
    }

    /** Grants night vision to assault visor helmet wearers. */
    private void visorTick() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            ItemStack helmet = viewer.getInventory().getHelmet();
            boolean wearing = WarItems.VISOR_HELMET.equals(plugin.items().id(helmet));
            tickNightVision(viewer, wearing);
        }
    }

    /** Maintains visor night vision and removes it when the helmet is taken off. */
    private void tickNightVision(Player viewer, boolean wearing) {
        UUID uuid = viewer.getUniqueId();
        if (wearing) {
            PotionEffect current = viewer.getPotionEffect(PotionEffectType.NIGHT_VISION);
            // Do not overwrite or take ownership of a longer player-sourced effect.
            if (current == null || current.getDuration() <= NV_DURATION_TICKS) {
                viewer.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                        NV_DURATION_TICKS, 0, true, false, true));
                nightVisionGranted.add(uuid);
            }
        } else if (nightVisionGranted.remove(uuid)) {
            PotionEffect current = viewer.getPotionEffect(PotionEffectType.NIGHT_VISION);
            if (current != null && current.getDuration() <= NV_DURATION_TICKS) {
                viewer.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
        }
    }
}
