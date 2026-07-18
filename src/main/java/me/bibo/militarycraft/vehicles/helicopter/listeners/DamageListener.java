package me.bibo.militarycraft.vehicles.helicopter.listeners;

import me.bibo.militarycraft.vehicles.helicopter.HelicopterRuntime;
import me.bibo.militarycraft.vehicles.helicopter.helicopter.Helicopter;
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

import java.util.UUID;

/** Routes explosions into helicopter HP and shields raw display entities. */
public final class DamageListener implements Listener {

    private final HelicopterRuntime plugin;

    public DamageListener(HelicopterRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (plugin.helicopters().isInternalExplosion()
                || me.bibo.militarycraft.core.combat.Explosions.isInternal()) {
            return;
        }
        Entity src = event.getEntity();
        if (src != null && src.getScoreboardTags().contains("antiaircraft_entity")) {
            plugin.helicopters().damageShipsFromAntiAir(event.getLocation());
            return;
        }
        plugin.helicopters().damageShipsFromExplosion(event.getLocation(), powerFor(src));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.helicopters().isInternalExplosion()
                || me.bibo.militarycraft.core.combat.Explosions.isInternal()) {
            return;
        }
        plugin.helicopters().damageShipsFromExplosion(event.getBlock().getLocation().add(0.5, 0.5, 0.5), 5.0);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity ent = event.getEntity();
        if (ent instanceof Player player) {
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (isImmuneMunitionDamage(player, cause)) {
                event.setCancelled(true);
                return;
            }
            Helicopter seated = plugin.helicopters().byRider(player.getUniqueId());
            if (seated == null) {
                return;
            }
            if (isPersonalRiderDamage(cause)) {
                return;
            }
            double dmg = event.getDamage();
            event.setCancelled(true);
            if (isExplosionDamage(cause)) {
                return;
            }
            if (event instanceof EntityDamageByEntityEvent ebe) {
                Entity damager = ebe.getDamager();
                if (damager instanceof Player atk && plugin.helicopters().byRider(atk.getUniqueId()) != null) {
                    return; // an onboard player's own swing is the weapon button
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
        if (plugin.helicopters().byEntity(ent) != null) {
            event.setCancelled(true);
        }
    }

    private boolean isImmuneMunitionDamage(Player player, EntityDamageEvent.DamageCause cause) {
        UUID immune = plugin.helicopters().munitionImmunePilot();
        return immune != null && immune.equals(player.getUniqueId()) && isExplosionDamage(cause);
    }

    private static boolean isExplosionDamage(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION;
    }

    private static boolean isPersonalRiderDamage(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case KILL, WORLD_BORDER, CONTACT, SUFFOCATION, FALL, FIRE, FIRE_TICK,
                 MELTING, LAVA, DROWNING, VOID, LIGHTNING, SUICIDE, STARVATION,
                 POISON, MAGIC, WITHER, FALLING_BLOCK, DRAGON_BREATH, CUSTOM,
                 FLY_INTO_WALL, HOT_FLOOR, CAMPFIRE, CRAMMING, DRYOUT, FREEZE,
                 SONIC_BOOM -> true;
            default -> false;
        };
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
