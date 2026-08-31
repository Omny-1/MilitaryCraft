package me.bibo.militarycraft.vehicles.pickup.listeners;

import java.util.UUID;
import me.bibo.militarycraft.vehicles.pickup.PickupRuntime;
import me.bibo.militarycraft.vehicles.pickup.vehicle.Pickup;
import me.bibo.militarycraft.vehicles.pickup.vehicle.PickupManager;
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
import org.bukkit.projectiles.ProjectileSource;

/**
 * Everything that can hurt a pickup: direct damage to its hitboxes, and explosions nearby.
 *
 * <p>Explosions are caught before they resolve so the blast can be applied to the vehicle rather
 * than to the invisible entities it is built from - those would simply be deleted.
 */
public final class DamageListener
implements Listener {
    private final PickupRuntime plugin;

    public DamageListener(PickupRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (this.plugin.pickups().isInternalExplosion()
                || me.bibo.militarycraft.core.combat.Explosions.isInternal()) {
            return;
        }
        Entity src = event.getEntity();
        if (src != null && src.getScoreboardTags().contains("antiaircraft_entity")) {
            this.plugin.pickups().damagePickupsFromAntiAir(event.getLocation());
            return;
        }
        this.plugin.pickups().damagePickupsFromExplosion(event.getLocation(), powerFor(src));
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (this.plugin.pickups().isInternalExplosion()
                || me.bibo.militarycraft.core.combat.Explosions.isInternal()) {
            return;
        }
        this.plugin.pickups().damagePickupsFromExplosion(event.getBlock().getLocation().add(0.5, 0.5, 0.5), 5.0);
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onDamage(EntityDamageEvent event) {
        Entity ent = event.getEntity();
        if (ent instanceof Player) {
            Player player = (Player)ent;
            Pickup seated = this.plugin.pickups().byDriver(player.getUniqueId());
            if (seated == null) {
                seated = this.plugin.pickups().byPassenger(player.getUniqueId());
            }
            if (seated == null) {
                seated = this.plugin.pickups().byGunner(player.getUniqueId());
            }
            if (seated == null) {
                return;
            }
            if (!shouldPickupAbsorb(event)) {
                return;
            }
            event.setCancelled(true);
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
                return;
            }
            if (event instanceof EntityDamageByEntityEvent) {
                EntityDamageByEntityEvent ebe = (EntityDamageByEntityEvent)event;
                Entity damager = ebe.getDamager();
                if (damager instanceof Player) {
                    Player attacker = (Player)damager;
                    if (this.plugin.pickups().isCrew(attacker.getUniqueId())) {
                        return;
                    }
                }
                if (damager instanceof Projectile) {
                    Player shooter;
                    UUID shooterId;
                    Projectile proj = (Projectile)damager;
                    if (!PickupManager.isWeaponProjectile(proj)) {
                        return;
                    }
                    ProjectileSource shooterSrc = proj.getShooter();
                    if (shooterSrc instanceof Player && ((shooterId = (shooter = (Player)shooterSrc).getUniqueId()).equals(seated.driver()) || shooterId.equals(seated.passenger()) || shooterId.equals(seated.gunner()))) {
                        return;
                    }
                    double pct = proj instanceof Fireball ? this.plugin.config().weaponFireballPercent : this.plugin.config().weaponArrowPercent;
                    seated.damage(seated.maxHealth() * pct / 100.0);
                    proj.remove();
                    return;
                }
                this.plugin.pickups().meleeDamageFromEntity(damager, seated, player.getLocation().add(0.0, 1.0, 0.0));
            }
            return;
        }
        if (this.plugin.pickups().byEntity(ent) != null) {
            event.setCancelled(true);
        }
    }

    private static boolean shouldPickupAbsorb(EntityDamageEvent event) {
        return switch (event.getCause()) {
            case EntityDamageEvent.DamageCause.ENTITY_ATTACK, EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK, EntityDamageEvent.DamageCause.PROJECTILE, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION -> true;
            default -> false;
        };
    }

    private static double powerFor(Entity e) {
        if (e instanceof Creeper) {
            Creeper creeper = (Creeper)e;
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
