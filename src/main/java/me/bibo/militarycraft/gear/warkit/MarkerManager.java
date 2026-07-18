package me.bibo.militarycraft.gear.warkit;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Target marker: a temporary mark. Only the marker owner sees the particle beacon
 * (personal force particles, rendered up to ~512 blocks) plus distance in action bar.
 */
public final class MarkerManager {

    private record Mark(UUID target, String targetName, long expiresAt) {}

    private static final Particle.DustOptions BEAM_DUST =
            new Particle.DustOptions(Color.fromRGB(0xFF2A2A), 1.8f);

    private final WarKitRuntime plugin;
    private final Map<UUID, Mark> marksByOwner = new HashMap<>();

    public MarkerManager(WarKitRuntime plugin) {
        this.plugin = plugin;
    }

    public void mark(Player owner, Player target) {
        long expires = System.currentTimeMillis() + plugin.settings().markerDurationSeconds * 1000L;
        Mark previous = marksByOwner.put(owner.getUniqueId(), new Mark(target.getUniqueId(), target.getName(), expires));
        if (previous != null && !previous.target().equals(target.getUniqueId())) {
            Player oldTarget = Bukkit.getPlayer(previous.target());
            if (oldTarget != null) {
                oldTarget.sendMessage(Txt.t("◎ The old mark was removed from you.", NamedTextColor.YELLOW));
            }
        }

        owner.sendMessage(Txt.t("◎ Target marked: " + target.getName() + " ("
                + Txt.mmss(plugin.settings().markerDurationSeconds) + ")", NamedTextColor.GOLD));
        owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.6f);

        target.sendMessage(Txt.t("⚠ You were marked! Someone can see your position.",
                NamedTextColor.RED));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 0.6f);
    }

    /** Player death: remove their own mark and all marks on them. */
    public void onDeath(UUID dead) {
        marksByOwner.remove(dead);
        Iterator<Map.Entry<UUID, Mark>> it = marksByOwner.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Mark> e = it.next();
            if (e.getValue().target().equals(dead)) {
                Player owner = Bukkit.getPlayer(e.getKey());
                if (owner != null) {
                    owner.sendMessage(Txt.t("◎ Target eliminated.", NamedTextColor.GOLD));
                }
                it.remove();
            }
        }
    }

    public void clearAll() {
        marksByOwner.clear();
    }

    /** Called by the shared ticker every 10 ticks. */
    public void tick(long now) {
        if (marksByOwner.isEmpty()) return;
        Iterator<Map.Entry<UUID, Mark>> it = marksByOwner.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Mark> e = it.next();
            Mark mark = e.getValue();
            if (now >= mark.expiresAt()) {
                it.remove();
                Player owner = Bukkit.getPlayer(e.getKey());
                if (owner != null) {
                    owner.sendMessage(Txt.t("◎ Target marker faded: " + mark.targetName(),
                            NamedTextColor.YELLOW));
                }
                continue;
            }
            Player owner = Bukkit.getPlayer(e.getKey());
            if (owner == null) continue;
            Player target = Bukkit.getPlayer(mark.target());
            if (target == null || target.isDead()) continue;

            long secondsLeft = (mark.expiresAt() - now) / 1000L;
            if (owner.getWorld().equals(target.getWorld())) {
                Location l = target.getLocation();
                for (int i = 0; i < 6; i++) {
                    owner.spawnParticle(Particle.DUST,
                            l.getX(), l.getY() + 2.4 + i * 0.55, l.getZ(),
                            1, 0, 0, 0, 0, BEAM_DUST, true);
                }
                int dist = (int) owner.getLocation().distance(l);
                owner.sendActionBar(Txt.t("◎ " + mark.targetName() + " - " + dist + " m - "
                        + Txt.mmss(secondsLeft), NamedTextColor.RED));
            } else {
                owner.sendActionBar(Txt.t("◎ " + mark.targetName() + " - in another world - "
                        + Txt.mmss(secondsLeft), NamedTextColor.RED));
            }
        }
    }
}
