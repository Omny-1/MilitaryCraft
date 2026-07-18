package me.bibo.militarycraft.core.vehicle;

import me.bibo.militarycraft.core.key.Keys;
import me.bibo.militarycraft.core.key.Pdc;
import org.bukkit.entity.Player;

/**
 * Crash-recovery net for pilot invisibility. Each shipping vehicle module manages its
 * own driver cloak; this only unwinds a persisted marker so an interrupted server
 * process cannot strand a player invisible. A later join reconciles the marker back to
 * the player's real pre-ride visibility.
 */
public final class PilotProtection {

    private static final byte TRUE = 1;

    private PilotProtection() {
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
}
