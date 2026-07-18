package me.bibo.militarycraft.gear.warkit.listener;

import me.bibo.militarycraft.gear.warkit.Txt;
import me.bibo.militarycraft.gear.warkit.WarItems;
import me.bibo.militarycraft.gear.warkit.WarKitRuntime;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Gas mask: blocks external harmful effects and damage from combat potions. */
public final class EffectListener implements Listener {

    /** Harmful effects filtered by the gas mask and removed by the medkit. */
    public static final Set<PotionEffectType> HARMFUL = Set.of(
            PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.SLOWNESS,
            PotionEffectType.MINING_FATIGUE, PotionEffectType.WEAKNESS, PotionEffectType.BLINDNESS,
            PotionEffectType.DARKNESS, PotionEffectType.NAUSEA, PotionEffectType.HUNGER,
            PotionEffectType.LEVITATION, PotionEffectType.UNLUCK, PotionEffectType.GLOWING,
            PotionEffectType.INFESTED, PotionEffectType.OOZING, PotionEffectType.WEAVING,
            PotionEffectType.WIND_CHARGED, PotionEffectType.INSTANT_DAMAGE, PotionEffectType.BAD_OMEN);

    /** Sources filtered by the mask: anything applied from outside the player. */
    private static final Set<EntityPotionEffectEvent.Cause> EXTERNAL = Set.of(
            EntityPotionEffectEvent.Cause.AREA_EFFECT_CLOUD,
            EntityPotionEffectEvent.Cause.ARROW,
            EntityPotionEffectEvent.Cause.ATTACK,
            EntityPotionEffectEvent.Cause.POTION_SPLASH);

    private final WarKitRuntime plugin;
    private final Map<UUID, Long> lastFx = new HashMap<>();

    public EffectListener(WarKitRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getAction() != EntityPotionEffectEvent.Action.ADDED
                && e.getAction() != EntityPotionEffectEvent.Action.CHANGED) return;
        if (!EXTERNAL.contains(e.getCause())) return;
        if (!HARMFUL.contains(e.getModifiedType())) return;
        if (!plugin.items().isWearingHelmet(p, WarItems.GAS_MASK)) return;
        e.setCancelled(true);
        maskFx(p);
    }

    /** Direct damage from harming potions and clouds is filtered too. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPotionDamage(EntityDamageByEntityEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.MAGIC) return;
        if (!(e.getDamager() instanceof ThrownPotion) && !(e.getDamager() instanceof AreaEffectCloud)) return;
        if (!(e.getEntity() instanceof Player p)) return;
        if (!plugin.items().isWearingHelmet(p, WarItems.GAS_MASK)) return;
        e.setCancelled(true);
        maskFx(p);
    }

    /** Filter feedback, rate-limited because clouds spam events. */
    private void maskFx(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastFx.get(p.getUniqueId());
        if (last != null && now - last < 1000) return;
        lastFx.put(p.getUniqueId(), now);
        p.getWorld().spawnParticle(Particle.SNEEZE, p.getEyeLocation(), 6, 0.2, 0.2, 0.2, 0.02);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PUFFER_FISH_BLOW_OUT, 0.6f, 1.6f);
        p.sendActionBar(Txt.t("Gas mask filtered the effect", NamedTextColor.GREEN));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastFx.remove(e.getPlayer().getUniqueId());
    }
}
