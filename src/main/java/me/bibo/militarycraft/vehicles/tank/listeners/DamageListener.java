package me.bibo.militarycraft.vehicles.tank.listeners;

import me.bibo.militarycraft.vehicles.tank.TankRuntime;
import me.bibo.militarycraft.vehicles.tank.tank.Tank;
import me.bibo.militarycraft.vehicles.tank.tank.TankManager;
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
 * Routes explosion damage into the tank HP model and shields the raw tank
 * entities from vanilla damage.
 */
public final class DamageListener implements Listener {

    private final TankRuntime plugin;

    public DamageListener(TankRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (plugin.tanks().isInternalExplosion()
                || me.bibo.militarycraft.core.combat.Explosions.isInternal()) {
            return; // our own shell, already handled
        }
        Entity src = event.getEntity();
        if (src != null && src.getScoreboardTags().contains("antiaircraft_entity")) {
            plugin.tanks().damageTanksFromAntiAir(event.getLocation());
            return;
        }
        plugin.tanks().damageTanksFromExplosion(event.getLocation(), powerFor(src));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.tanks().isInternalExplosion()
                || me.bibo.militarycraft.core.combat.Explosions.isInternal()) {
            return; // our own shell, already handled
        }
        plugin.tanks().damageTanksFromExplosion(event.getBlock().getLocation().add(0.5, 0.5, 0.5), 5.0);
    }

    /**
     * A seated driver is invulnerable, but incoming weapon damage is routed onto
     * the tank. That lets any current or future custom weapon break occupied
     * tanks if it damages the player entity.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity ent = event.getEntity();
        if (ent instanceof Player player) {
            Tank seated = plugin.tanks().byDriver(player.getUniqueId());
            if (seated == null) {
                return;
            }
            event.setCancelled(true);
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                    || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
                return;
            }
            if (event instanceof EntityDamageByEntityEvent ebe) {
                Entity damager = ebe.getDamager();
                if (damager instanceof Player atk && plugin.tanks().byDriver(atk.getUniqueId()) != null) {
                    return; // a seated driver's own swing is the fire button
                }
                if (damager instanceof Projectile proj) {
                    if (!TankManager.isWeaponProjectile(proj)) {
                        return;
                    }
                    if (proj.getShooter() instanceof Player shooter
                            && seated.driver() != null
                            && shooter.getUniqueId().equals(seated.driver())) {
                        return;
                    }
                    double pct = (proj instanceof Fireball)
                            ? plugin.config().weaponFireballPercent : plugin.config().weaponArrowPercent;
                    seated.damage(seated.maxHealth() * pct / 100.0);
                    proj.remove();
                    return;
                }
                plugin.tanks().meleeDamageFromEntity(damager, seated,
                        player.getLocation().add(0, 1.0, 0));
            }
            return;
        }
        if (plugin.tanks().byEntity(ent) != null) {
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
