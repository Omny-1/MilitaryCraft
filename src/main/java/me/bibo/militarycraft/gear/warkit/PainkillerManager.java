package me.bibo.militarycraft.gear.warkit;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Painkiller: temporarily reduces incoming damage. No ticker - a delayed task removes it. */
public final class PainkillerManager {

    private final WarKitRuntime plugin;
    private final Map<UUID, Long> activeUntil = new HashMap<>();

    public PainkillerManager(WarKitRuntime plugin) {
        this.plugin = plugin;
    }

    public void apply(Player p) {
        Settings s = plugin.settings();
        UUID uuid = p.getUniqueId();
        long until = System.currentTimeMillis() + s.painkillerDurationSeconds * 1000L;
        activeUntil.put(uuid, until);

        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1.2f);
        p.getWorld().spawnParticle(Particle.ITEM, p.getEyeLocation(), 8,
                0.2, 0.2, 0.2, 0.05, ItemStack.of(Material.BONE_MEAL));
        int pct = (int) Math.round(s.painkillerReduction * 100);
        p.sendActionBar(Txt.t("● Painkiller: -" + pct + "% damage for "
                + s.painkillerDurationSeconds + " sec", NamedTextColor.GREEN));

        Bukkit.getScheduler().runTaskLater(plugin.bukkitPlugin(), () -> {
            Long current = activeUntil.get(uuid);
            if (current == null || !current.equals(until)) return; // replaced by a newer dose
            activeUntil.remove(uuid);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                online.sendActionBar(Txt.t("Painkiller effect ended", NamedTextColor.YELLOW));
                online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 0.8f);
            }
        }, s.painkillerDurationSeconds * 20L);
    }

    /** Incoming damage multiplier (1.0 = unchanged). */
    public double multiplier(Player p) {
        Long until = activeUntil.get(p.getUniqueId());
        if (until == null) return 1.0;
        if (until < System.currentTimeMillis()) {
            activeUntil.remove(p.getUniqueId());
            return 1.0;
        }
        return 1.0 - plugin.settings().painkillerReduction;
    }

    public void clear(UUID uuid) {
        activeUntil.remove(uuid);
    }

    public void clearAll() {
        activeUntil.clear();
    }
}
