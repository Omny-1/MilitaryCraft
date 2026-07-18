package me.bibo.militarycraft.gear.warkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Long actions with an action bar progress indicator (bandaging, repair).
 * Interrupted by damage, item changes, death or quit.
 */
public final class ChannelManager {

    private final WarKitRuntime plugin;
    private final Map<UUID, Active> active = new HashMap<>();

    private static final class Active {
        final String itemId;
        final String title;
        final int totalTicks;
        final Sound loopSound;
        final int cooldownTicks;
        final Consumer<Player> onInterrupt; // may be null
        final Consumer<Player> onComplete;
        int elapsed;
        BukkitTask task;

        Active(String itemId, String title, int totalTicks, Sound loopSound,
               int cooldownTicks, Consumer<Player> onInterrupt, Consumer<Player> onComplete) {
            this.itemId = itemId;
            this.title = title;
            this.totalTicks = totalTicks;
            this.loopSound = loopSound;
            this.cooldownTicks = cooldownTicks;
            this.onInterrupt = onInterrupt;
            this.onComplete = onComplete;
        }
    }

    public ChannelManager(WarKitRuntime plugin) {
        this.plugin = plugin;
    }

    public boolean isChanneling(Player p) {
        return active.containsKey(p.getUniqueId());
    }

    public void start(Player p, String itemId, double seconds, String title, Sound loopSound,
                      int cooldownTicks, Consumer<Player> onInterrupt, Consumer<Player> onComplete) {
        cancelSilent(p);
        int total = Math.max(2, (int) Math.round(seconds * 20));
        Active a = new Active(itemId, title, total, loopSound, cooldownTicks, onInterrupt, onComplete);
        a.task = Bukkit.getScheduler().runTaskTimer(plugin.bukkitPlugin(), () -> tick(p, a), 0L, 2L);
        active.put(p.getUniqueId(), a);
    }

    private void tick(Player p, Active a) {
        if (!p.isOnline() || p.isDead()) {
            stop(p.getUniqueId(), a);
            if (a.onInterrupt != null) a.onInterrupt.accept(p);
            return;
        }
        if (a.loopSound != null && a.elapsed % 20 == 0) {
            p.playSound(p.getLocation(), a.loopSound, 0.6f, 1.2f);
        }
        a.elapsed += 2;
        if (a.elapsed < a.totalTicks) {
            p.sendActionBar(progressBar(a.title, a.elapsed, a.totalTicks));
            return;
        }
        stop(p.getUniqueId(), a);
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!a.itemId.equals(plugin.items().id(hand))) {
            if (a.onInterrupt != null) a.onInterrupt.accept(p);
            p.sendActionBar(Txt.t("✖ Item removed - action cancelled", NamedTextColor.RED));
            return;
        }
        if (a.cooldownTicks > 0) p.setCooldown(hand, a.cooldownTicks);
        hand.setAmount(hand.getAmount() - 1);
        a.onComplete.accept(p);
    }

    public void interrupt(Player p, String reason) {
        Active a = active.remove(p.getUniqueId());
        if (a == null) return;
        a.task.cancel();
        if (a.onInterrupt != null) a.onInterrupt.accept(p);
        p.sendActionBar(Txt.t("✖ " + reason, NamedTextColor.RED));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
    }

    public void cancelSilent(Player p) {
        Active a = active.remove(p.getUniqueId());
        if (a == null) return;
        a.task.cancel();
        if (a.onInterrupt != null) a.onInterrupt.accept(p);
    }

    public void cancelAll() {
        for (Map.Entry<UUID, Active> e : Map.copyOf(active).entrySet()) {
            e.getValue().task.cancel();
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && e.getValue().onInterrupt != null) {
                e.getValue().onInterrupt.accept(p);
            }
        }
        active.clear();
    }

    private void stop(UUID uuid, Active a) {
        active.remove(uuid);
        a.task.cancel();
    }

    private static Component progressBar(String title, int elapsed, int total) {
        int filled = Math.min(10, (int) Math.round(10.0 * elapsed / total));
        return Txt.t(title + " ", NamedTextColor.WHITE)
                .append(Txt.t("▰".repeat(filled), NamedTextColor.GREEN))
                .append(Txt.t("▱".repeat(10 - filled), NamedTextColor.DARK_GRAY));
    }
}
