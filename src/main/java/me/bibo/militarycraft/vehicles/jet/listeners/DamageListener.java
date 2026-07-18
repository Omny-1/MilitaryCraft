package me.bibo.militarycraft.vehicles.jet.listeners;

import me.bibo.militarycraft.vehicles.jet.JetRuntime;
import me.bibo.militarycraft.vehicles.jet.jet.Jet;
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

/** Routes explosions into jet HP and shields raw display entities. */
public final class DamageListener implements Listener {

    private final JetRuntime plugin;

    public DamageListener(JetRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (plugin.jets().isInternalExplosion()
                || me.bibo.militarycraft.core.combat.Explosions.isInternal()) {
            return;
        }
        Entity src = event.getEntity();
        if (src != null && src.getScoreboardTags().contains("antiaircraft_entity")) {
            plugin.jets().damageJetsFromAntiAir(event.getLocation());
            return;
        }
        plugin.jets().damageJetsFromExplosion(event.getLocation(), powerFor(src));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.jets().isInternalExplosion()
                || me.bibo.militarycraft.core.combat.Explosions.isInternal()) {
            return;
        }
        plugin.jets().damageJetsFromExplosion(event.getBlock().getLocation().add(0.5, 0.5, 0.5), 5.0);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity ent = event.getEntity();
        if (ent instanceof Player player) {
            Jet seated = plugin.jets().byDriver(player.getUniqueId());
            if (seated == null) {
                return;
            }
            double dmg = event.getDamage();
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (isPersonalPilotDamage(cause)) {
                return;
            }
            event.setCancelled(true);
            if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                    || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
                return;
            }
            if (event instanceof EntityDamageByEntityEvent ebe) {
                Entity damager = ebe.getDamager();
                if (damager instanceof Player atk && plugin.jets().byDriver(atk.getUniqueId()) != null) {
                    return; // a seated pilot's own swing is the fire button
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
            double environmental = environmentalJetDamage(cause, dmg);
            if (environmental > 0) {
                seated.damage(environmental);
            }
            return;
        }
        if (plugin.jets().byEntity(ent) != null) {
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

    private static boolean isPersonalPilotDamage(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case STARVATION, VOID, WORLD_BORDER, KILL -> true;
            default -> false;
        };
    }

    private static double environmentalJetDamage(EntityDamageEvent.DamageCause cause, double damage) {
        return switch (cause) {
            case FIRE, FIRE_TICK, HOT_FLOOR, LAVA, MELTING -> damage * 0.8;
            case CONTACT, CRAMMING, FALL, FALLING_BLOCK, FLY_INTO_WALL, SUFFOCATION -> damage * 0.45;
            case DROWNING, DRAGON_BREATH, DRYOUT, FREEZE, LIGHTNING, MAGIC, POISON, SONIC_BOOM, WITHER -> damage;
            case CUSTOM -> damage * 0.5;
            default -> 0.0;
        };
    }
}
