package me.bibo.militarycraft.core.vehicle;

import me.bibo.militarycraft.core.key.Keys;
import me.bibo.militarycraft.core.key.Pdc;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Shared pilot-protection rule used by every {@link VehicleManager} (§5.3): a seated
 * driver takes 0 direct damage, and armour-cloak state follows the live set of
 * current drivers. One copy, generalised from TankCraft's {@code DamageListener} /
 * {@code TankManager.reconcileCloak}.
 */
public final class PilotProtection {

    private static final byte TRUE = 1;

    private PilotProtection() {
    }

    /**
     * Cancels the event outright (0 direct damage). Returns the
     * {@link EntityDamageByEntityEvent} to convert into vehicle HP loss, or
     * {@code null} if there's nothing to convert (an explosion — handled separately
     * via {@code ExplosionSink} — or a non-entity cause).
     */
    public static EntityDamageByEntityEvent protect(EntityDamageEvent event) {
        event.setCancelled(true);
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return null;
        }
        return event instanceof EntityDamageByEntityEvent ebe ? ebe : null;
    }

    /** Persists the pre-ride visibility so a crash cannot leave the player invisible. */
    public static void rememberVisibility(Player player) {
        Pdc.setByte(player.getPersistentDataContainer(), Keys.of("core", "pilot_cloaked"), TRUE);
        Pdc.setByte(player.getPersistentDataContainer(), Keys.of("core", "pilot_was_invisible"),
                player.isInvisible() ? TRUE : (byte) 0);
    }

    /** Restores visibility and clears the crash-recovery marker. */
    public static void restoreVisibility(Player player, Boolean inMemoryFallback) {
        byte marked = Pdc.getByte(player.getPersistentDataContainer(),
                Keys.of("core", "pilot_cloaked"), (byte) 0);
        if (marked == TRUE) {
            boolean wasInvisible = Pdc.getByte(player.getPersistentDataContainer(),
                    Keys.of("core", "pilot_was_invisible"), (byte) 0) == TRUE;
            player.setInvisible(wasInvisible);
        } else if (inMemoryFallback != null) {
            player.setInvisible(inMemoryFallback);
        }
        player.getPersistentDataContainer().remove(Keys.of("core", "pilot_cloaked"));
        player.getPersistentDataContainer().remove(Keys.of("core", "pilot_was_invisible"));
    }

    /** Called on join to recover a marker left by an interrupted server process. */
    public static void recoverStaleVisibility(Player player) {
        if (Pdc.getByte(player.getPersistentDataContainer(),
                Keys.of("core", "pilot_cloaked"), (byte) 0) == TRUE) {
            restoreVisibility(player, null);
        }
    }

    /**
     * Hides armour for newly-seated drivers (and periodically re-sends so players who
     * come into view also see no armour), and restores it for anyone no longer driving.
     *
     * @param cloaked        mutable set of driver UUIDs currently cloaked (manager-owned state)
     * @param currentDrivers every UUID that is a seated driver of one of this manager's vehicles right now
     * @param periodic       true on the manager's periodic re-send tick
     */
    public static void reconcileCloak(Set<UUID> cloaked, Set<UUID> currentDrivers, boolean periodic) {
        for (Iterator<UUID> it = cloaked.iterator(); it.hasNext(); ) {
            UUID u = it.next();
            if (!currentDrivers.contains(u)) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    PilotCloak.show(p);
                }
                it.remove();
            }
        }
        for (UUID u : currentDrivers) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) {
                continue;
            }
            boolean firstTime = cloaked.add(u);
            if (firstTime || periodic) {
                PilotCloak.hide(p);
            }
        }
    }
}
