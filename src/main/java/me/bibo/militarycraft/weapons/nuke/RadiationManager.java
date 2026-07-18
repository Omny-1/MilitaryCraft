package me.bibo.militarycraft.weapons.nuke;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class RadiationManager {

    private final NukeManager manager;
    private final Map<UUID, Long> radiatedUntil = new ConcurrentHashMap<>();
    private final ItemStack rottenFlesh = new ItemStack(Material.ROTTEN_FLESH);
    private final Particle.DustOptions toxicDust =
            new Particle.DustOptions(Color.fromRGB(120, 210, 60), 1.4f);

    private BukkitTask task;

    RadiationManager(NukeManager manager) {
        this.manager = manager;
    }

    void start() {
        this.task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(manager.core().plugin(), 20L, 20L);
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        radiatedUntil.clear();
    }

    void irradiate(Player player, int seconds) {
        long until = System.currentTimeMillis() + seconds * 1000L;
        radiatedUntil.merge(player.getUniqueId(), until, Math::max);
        player.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("☢", NamedTextColor.GREEN),
                Component.text("You absorbed a dose of radiation...", NamedTextColor.DARK_GREEN),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(300),
                        java.time.Duration.ofMillis(2000),
                        java.time.Duration.ofMillis(800))));
    }

    private void tick() {
        if (radiatedUntil.isEmpty()) {
            return;
        }
        double damage = manager.settings().getDouble("radiation-damage", 1.0);
        long now = System.currentTimeMillis();
        DamageSource magic = DamageSource.builder(DamageType.MAGIC).build();

        var iterator = radiatedUntil.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            Player player = manager.core().plugin().getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }
            if (now >= entry.getValue()) {
                iterator.remove();
                player.sendActionBar(Component.text("Radiation has faded.", NamedTextColor.GRAY));
                continue;
            }
            GameMode mode = player.getGameMode();
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || player.isDead()) {
                continue;
            }

            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 45, 0, true, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, true, false, false));
            player.damage(damage, magic);

            Location at = player.getLocation().add(0, 1.0, 0);
            player.getWorld().spawnParticle(Particle.ITEM, at, 6, 0.4, 0.6, 0.4, 0.02, rottenFlesh);
            player.getWorld().spawnParticle(Particle.DUST, at, 10, 0.5, 0.7, 0.5, toxicDust);
            player.sendActionBar(Component.text("☢ Radiation is eating you from within ☢", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_DROWN, 0.5f, 0.7f);
        }
    }
}
