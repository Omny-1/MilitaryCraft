package me.bibo.militarycraft.weapons.antiair.listeners;

import me.bibo.militarycraft.weapons.antiair.AntiAirRuntime;
import me.bibo.militarycraft.weapons.antiair.items.TurretItem;
import me.bibo.militarycraft.weapons.antiair.turret.Turret;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
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
 * Routes explosion damage into the turret HP model (calibrated so ONE creeper
 * blast destroys it), protects the raw turret entities from vanilla damage, and
 * lets the owner pick the turret up with a sneak-left-click.
 */
public final class DamageListener implements Listener {

    private final AntiAirRuntime plugin;

    public DamageListener(AntiAirRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (plugin.turrets().isInternalExplosion()) {
            // Our own anti-vehicle rocket: keep terrain intact (the vehicle plugin
            // still reads the blast location for HP damage), and never hurt our own
            // turrets with it.
            event.blockList().clear();
            return;
        }
        plugin.turrets().damageTurretsFromExplosion(event.getLocation(), powerFor(event.getEntity()));
    }

    /**
     * An anti-vehicle rocket is a real explosion only so the vehicle's own plugin
     * registers it as HP damage (via EntityExplodeEvent). The raw blast must NOT
     * touch living entities — otherwise it kills the (otherwise invulnerable) pilot
     * instead of the vehicle. So while our rocket is detonating, cancel ALL
     * explosion damage to every entity; the vehicle still loses HP through the
     * area event, the player is left untouched.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onRocketBlastDamage(EntityDamageEvent event) {
        if (!plugin.turrets().isInternalExplosion()) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.turrets().isInternalExplosion()) {
            return;
        }
        plugin.turrets().damageTurretsFromExplosion(
                event.getBlock().getLocation().add(0.5, 0.5, 0.5), 5.0);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity ent = event.getEntity();
        Turret turret = plugin.turrets().byEntity(ent);
        if (turret == null) {
            return;
        }
        // Shield the raw entity from vanilla damage; we run our own HP model.
        event.setCancelled(true);

        if (!(event instanceof EntityDamageByEntityEvent ebe)) {
            return;
        }
        Entity damager = ebe.getDamager();
        if (damager instanceof Player p) {
            // Owner (or admin) sneak-left-click = pick the turret back up.
            boolean owner = turret.owner() != null && turret.owner().equals(p.getUniqueId());
            if (p.isSneaking() && (owner || p.hasPermission("antiaircraft.admin"))) {
                plugin.turrets().remove(turret, false);
                p.getInventory().addItem(TurretItem.create(plugin));
                p.sendActionBar(Component.text("Anti-Air removed.", NamedTextColor.GREEN));
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7f, 1.2f);
                return;
            }
            double dmg = plugin.config().meleeDamage;
            if (dmg > 0) {
                turret.damage(dmg);
            }
        } else if (damager instanceof Projectile) {
            double dmg = plugin.config().arrowDamage;
            if (dmg > 0) {
                turret.damage(dmg);
            }
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
