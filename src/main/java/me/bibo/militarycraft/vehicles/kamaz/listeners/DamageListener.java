package me.bibo.militarycraft.vehicles.kamaz.listeners;

import me.bibo.militarycraft.vehicles.kamaz.KamazRuntime;
import me.bibo.militarycraft.vehicles.kamaz.truck.Truck;
import me.bibo.militarycraft.vehicles.kamaz.truck.TruckManager;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Routes explosion damage into the truck HP model and shields the raw truck
 * entities from vanilla damage.
 */
public final class DamageListener implements Listener {

    private final KamazRuntime plugin;

    public DamageListener(KamazRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (plugin.trucks().isInternalExplosion()
                || me.bibo.militarycraft.core.combat.Explosions.isInternal()) {
            return; // our own destruction blast, already handled
        }
        Entity src = event.getEntity();
        if (src != null && src.getScoreboardTags().contains("antiaircraft_entity")) {
            plugin.trucks().damageTrucksFromAntiAir(event.getLocation());
            return;
        }
        plugin.trucks().damageTrucksFromExplosion(event.getLocation(), powerFor(src));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.trucks().isInternalExplosion()
                || me.bibo.militarycraft.core.combat.Explosions.isInternal()) {
            return;
        }
        plugin.trucks().damageTrucksFromExplosion(event.getBlock().getLocation().add(0.5, 0.5, 0.5), 5.0);
    }

    /**
     * A seated driver is invulnerable, but incoming weapon damage is routed onto
     * the truck. That lets any current or future custom weapon break an occupied
     * truck if it damages the player entity.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity ent = event.getEntity();
        if (ent instanceof Player player) {
            Truck seated = plugin.trucks().byDriver(player.getUniqueId());
            if (seated == null) {
                seated = plugin.trucks().byPassenger(player.getUniqueId());
            }
            if (seated == null) {
                return;
            }
            double dmg = event.getDamage();
            event.setCancelled(true);
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                    || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
                return; // explosions reach the truck through the explode events
            }
            if (event instanceof EntityDamageByEntityEvent ebe) {
                Entity damager = ebe.getDamager();
                if (damager instanceof Projectile proj && TruckManager.isWeaponProjectile(proj)) {
                    double pct = (proj instanceof Fireball)
                            ? plugin.config().weaponFireballPercent : plugin.config().weaponArrowPercent;
                    seated.damage(seated.maxHealth() * pct / 100.0);
                    proj.remove();
                    return;
                }
                if (dmg > 0) {
                    seated.damage(dmg);
                }
            }
            return;
        }
        if (plugin.trucks().byEntity(ent) != null) {
            event.setCancelled(true);
        }
    }

    private static double powerFor(Entity e) {
        if (e instanceof Creeper creeper) {
            return creeper.isPowered() ? 6.0 : 3.0;
        }
        if (e instanceof TNTPrimed) {
            return 4.0;
        }
        if (e instanceof EnderCrystal) {
            return 6.0;
        }
        if (e instanceof Fireball) {
            return 1.0;
        }
        return 4.0;
    }
}
