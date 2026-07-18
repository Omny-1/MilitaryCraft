package me.bibo.militarycraft.vehicles.moto.listeners;

import me.bibo.militarycraft.vehicles.moto.MotoRuntime;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** Routes blast damage into motorcycle HP and shields only MotoCraft's helper entities. */
public final class DamageListener implements Listener {

    private final MotoRuntime plugin;

    public DamageListener(MotoRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (plugin.motorcycles().isInternalExplosionEvent()
                || me.bibo.militarycraft.core.combat.Explosions.isInternal()) {
            return;
        }
        Entity source = event.getEntity();
        String antiAirTag = plugin.config().antiAirScoreboardTag;
        if (source != null && !antiAirTag.isEmpty()
                && source.getScoreboardTags().contains(antiAirTag)) {
            plugin.motorcycles().damageMotorcyclesFromAntiAir(event.getLocation());
            return;
        }
        plugin.motorcycles().damageMotorcyclesFromExplosion(event.getLocation(), powerFor(source));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.motorcycles().isInternalExplosionEvent()
                || me.bibo.militarycraft.core.combat.Explosions.isInternal()) {
            return;
        }
        plugin.motorcycles().damageMotorcyclesFromExplosion(
                event.getBlock().getLocation().add(0.5, 0.5, 0.5), 5.0);
    }

    /**
     * Riders remain ordinary, visible and damageable players. Only the invisible
     * anchor, transient seats/hitboxes and display parts are protected here.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHelperDamage(EntityDamageEvent event) {
        if (plugin.motorcycles().byEntity(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    private static double powerFor(Entity entity) {
        if (entity instanceof Creeper creeper) {
            return creeper.isPowered() ? 6.0 : 3.0;
        }
        if (entity instanceof TNTPrimed) {
            return 4.0;
        }
        if (entity instanceof EnderCrystal) {
            return 6.0;
        }
        if (entity instanceof Fireball) {
            return 1.0;
        }
        return 4.0;
    }
}
