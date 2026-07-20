package me.bibo.militarycraft.vehicles.drone.listeners;

import me.bibo.militarycraft.vehicles.drone.DroneRuntime;
import me.bibo.militarycraft.vehicles.drone.drone.Drone;
import me.bibo.militarycraft.vehicles.drone.util.Keys;
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
 * Routes explosion damage into the UAV HP model and shields the raw UAV entities
 * and the seated operator from vanilla damage. Weapon damage to the UAV (melee,
 * arrows, fireballs) is applied to its HP directly by the manager, so it can be
 * shot down occupied or empty.
 */
public final class DamageListener implements Listener {

    private final DroneRuntime plugin;

    public DamageListener(DroneRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (plugin.drones().isInternalExplosion()
                || me.bibo.militarycraft.core.combat.Explosions.isInternal()) {
            return; // our own warhead / rocket, already handled
        }
        Entity src = event.getEntity();
        if (src != null && src.getScoreboardTags().contains("antiaircraft_entity")) {
            plugin.drones().damageDronesFromAntiAir(event.getLocation());
            return;
        }
        plugin.drones().damageDronesFromExplosion(event.getLocation(), powerFor(src));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.drones().isInternalExplosion()
                || me.bibo.militarycraft.core.combat.Explosions.isInternal()) {
            return;
        }
        plugin.drones().damageDronesFromExplosion(event.getBlock().getLocation().add(0.5, 0.5, 0.5), 5.0);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity ent = event.getEntity();
        if (ent instanceof Player player) {
            // the operator who set off a munition is immune to its own blast
            java.util.UUID immune = plugin.drones().munitionImmunePilot();
            if (immune != null && immune.equals(player.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            Drone seated = plugin.drones().byDriver(player.getUniqueId());
            if (seated == null) {
                return;
            }
            // The seated operator is invulnerable - the hit is routed onto the UAV, so
            // any weapon that can hurt a player brings down the UAV it controls.
            double dmg = event.getDamage();
            event.setCancelled(true);
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                    || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
                return; // area-handled
            }
            if (event instanceof EntityDamageByEntityEvent ebe) {
                Entity damager = ebe.getDamager();
                if (damager instanceof Player atk && plugin.drones().byDriver(atk.getUniqueId()) != null) {
                    return;
                }
                if (damager instanceof Projectile proj) {
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
        Drone owner = plugin.drones().byEntity(ent);
        if (owner != null) {
            event.setCancelled(true);
            // breaking the control stand recalls its operator to the launch point
            String role = ent.getPersistentDataContainer()
                    .get(Keys.DRONE_PART, org.bukkit.persistence.PersistentDataType.STRING);
            if ("stand".equals(role)) {
                plugin.drones().recall(owner);
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
