package me.bibo.militarycraft.gear.warkit.listener;

import me.bibo.militarycraft.gear.warkit.WarItems;
import me.bibo.militarycraft.gear.warkit.WarKitRuntime;
import me.bibo.militarycraft.gear.warkit.TeamRules;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Set;

/** Combat mechanics: painkiller, target marker, camouflage breaks, and channel interruption on damage. */
public final class CombatListener implements Listener {

    /**
     * Damage-over-time causes (fire, poison...) do not interrupt bandaging or repairs,
     * otherwise a burning player could never recover.
     */
    private static final Set<EntityDamageEvent.DamageCause> DOT_CAUSES = Set.of(
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.POISON,
            EntityDamageEvent.DamageCause.WITHER,
            EntityDamageEvent.DamageCause.STARVATION,
            EntityDamageEvent.DamageCause.DROWNING,
            EntityDamageEvent.DamageCause.FREEZE,
            EntityDamageEvent.DamageCause.CONTACT,
            EntityDamageEvent.DamageCause.HOT_FLOOR);

    private final WarKitRuntime plugin;

    public CombatListener(WarKitRuntime plugin) {
        this.plugin = plugin;
    }

    /** Scoreboard svoteam: direct teammate damage is blocked, utility effects stay allowed. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeamDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player target)) return;
        Player attacker = playerSource(e.getDamager());
        if (attacker != null && TeamRules.sameSvoTeam(attacker, target) && holdsWarKitItem(attacker)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeamExplosionDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player target)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && e.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return;
        }
        if (TeamRules.isProtectedExplosionDamage(target)) {
            e.setCancelled(true);
        }
    }

    /** Temporary fall immunity after an impulse grenade. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (e.getEntity() instanceof Player p && plugin.fallImmunity().has(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    /** Painkiller: reduces incoming damage by N percent. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        double mult = plugin.painkiller().multiplier(p);
        if (mult < 1.0) {
            e.setDamage(e.getDamage() * mult);
        }
    }

    /** Hitting a player with the marker applies a target mark. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMarkerHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player owner) || !(e.getEntity() instanceof Player target)) return;
        ItemStack hand = owner.getInventory().getItemInMainHand();
        if (!WarItems.MARKER.equals(plugin.items().id(hand))) return;
        e.setCancelled(true);
        hand.setAmount(hand.getAmount() - 1);
        plugin.marker().mark(owner, target);
    }

    /** Real player damage interrupts bandaging/repairs and breaks camouflage. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageMonitor(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getFinalDamage() <= 0) return;
        if (!DOT_CAUSES.contains(e.getCause())) {
            plugin.channels().interrupt(p, "Damage interrupted the action");
        }
        plugin.camo().breakDisguise(p, "Damage broke camouflage");
    }

    /** Attacking while disguised breaks camouflage. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttackMonitor(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player attacker) {
            plugin.camo().breakDisguise(attacker, "Attack broke camouflage");
        }
    }

    private Player playerSource(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            return source instanceof Player p ? p : null;
        }
        return null;
    }

    private boolean holdsWarKitItem(Player p) {
        return plugin.items().id(p.getInventory().getItemInMainHand()) != null
                || plugin.items().id(p.getInventory().getItemInOffHand()) != null;
    }
}
