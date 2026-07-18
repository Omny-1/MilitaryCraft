package me.bibo.militarycraft.weapons.tckbus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Routes explosion damage into the TckBusRig HP model (≈1 creeper = destroyed), shields
 * the raw TckBusRig STRUCTURE from vanilla damage (but leaves the workers fully killable),
 * lets the owner pick a TckBusRig back up, and clears worker drops / announces progress.
 */
public final class TckBusDamageListener implements Listener {

    private final TckBusRuntime plugin;

    public TckBusDamageListener(TckBusRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        plugin.buses().damageBusesFromExplosion(event.getLocation(), powerFor(event.getEntity()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        plugin.buses().damageBusesFromExplosion(
                event.getBlock().getLocation().add(0.5, 0.5, 0.5), 5.0);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity ent = event.getEntity();
        TckBusRig TckBusRig = plugin.buses().byEntity(ent);
        if (TckBusRig == null) {
            return;
        }
        String role = ent.getPersistentDataContainer().get(TckBusKeys.ROLE, PersistentDataType.STRING);
        if ("worker".equals(role)) {
            return; // TCK workers must be killable - leave their damage alone
        }
        // TckBusRig structure: never let vanilla damage the raw entities; we run our own model.
        event.setCancelled(true);

        if (!(event instanceof EntityDamageByEntityEvent ebe)) {
            return;
        }
        Entity damager = ebe.getDamager();
        if (damager instanceof Player p) {
            boolean owner = TckBusRig.owner() != null && TckBusRig.owner().equals(p.getUniqueId());
            if (p.isSneaking() && (owner || p.hasPermission("tckbus.admin"))) {
                plugin.buses().remove(TckBusRig, false);
                p.getInventory().addItem(TckBusItem.create(TckBusRig.skin()));
                p.sendActionBar(Component.text(TckBusRig.skin().busName + " removed.", NamedTextColor.GREEN));
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7f, 1.2f);
                return;
            }
            if (!TckBusRig.breakable()) {
                p.sendActionBar(TckBusRig.protectedHint());
                return;
            }
            double dmg = plugin.config().meleeDamage;
            if (dmg > 0) {
                TckBusRig.damage(dmg);
            }
        } else if (damager instanceof Projectile) {
            if (!TckBusRig.breakable()) {
                return;
            }
            double dmg = plugin.config().arrowDamage;
            if (dmg > 0) {
                TckBusRig.damage(dmg);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorkerDeath(EntityDeathEvent event) {
        LivingEntity ent = event.getEntity();
        String role = ent.getPersistentDataContainer().get(TckBusKeys.ROLE, PersistentDataType.STRING);
        if (!"worker".equals(role)) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);

        TckBusRig TckBusRig = plugin.buses().busByWorker(ent.getUniqueId());
        if (TckBusRig == null) {
            return;
        }
        // Only a player kill counts toward the defeat gate; anything else (natural
        // death, hazards, a world quirk) is an accidental vanish that re-spawns.
        boolean byPlayer = ent.getKiller() != null;
        int remaining = TckBusRig.onWorkerDeath(ent.getUniqueId(), byPlayer);
        if (!byPlayer) {
            return; // will quietly re-spawn on the next TckBusRig tick — no announcement
        }
        if (remaining <= 0) {
            TckBusRig.messageNearby(Component.text("All " + TckBusRig.skin().workerPlural + " are dead! Now break the bus.", NamedTextColor.GREEN), 30);
            TckBusRig.world().playSound(TckBusRig.anchor(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 0.8f);
        } else {
            TckBusRig.messageNearby(Component.text(TckBusRig.skin().workerSingular + " killed. Remaining: " + remaining, NamedTextColor.YELLOW), 30);
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


