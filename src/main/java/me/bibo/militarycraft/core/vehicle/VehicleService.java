package me.bibo.militarycraft.core.vehicle;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * The unified query surface other modules use instead of scoreboard-tag strings.
 */
public interface VehicleService {

    /** @return the vehicle {@code anyPart} belongs to (seat, hitbox or any display part), or null. */
    VehicleHandle vehicleOf(Entity anyPart);

    /** @return the vehicle {@code player} is seated in, walking a nested vehicle stack, or null. */
    VehicleHandle riddenBy(Player player);

    /** @return the owning module id for {@code anyPart}, or null. */
    String typeOf(Entity anyPart);

    Collection<VehicleHandle> all();

    /** Registers a read/act view over an original restored vehicle manager. */
    void registerProvider(VehicleProvider provider);

    /** Removes a restored-manager view before that module shuts down. */
    void unregisterProvider(VehicleProvider provider);

    /** Removes every tracked vehicle and tagged stray currently present in loaded worlds. */
    PurgeResult purgeAll();

    record PurgeResult(int tracked, int strays) {
        public int total() {
            return tracked + strays;
        }
    }
}
