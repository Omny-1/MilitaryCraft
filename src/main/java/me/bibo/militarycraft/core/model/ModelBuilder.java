package me.bibo.militarycraft.core.model;

import me.bibo.militarycraft.core.key.EntityTag;
import me.bibo.militarycraft.core.key.Keys;
import me.bibo.militarycraft.core.key.Pdc;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.UUID;

/**
 * Spawn helpers for a vehicle's entity cluster (§5.5): the invisible seat, its
 * Interaction hitboxes, and its BlockDisplay/TextDisplay model parts. Every spawned
 * entity is tagged identically regardless of vehicle: {@link EntityTag} (scoreboard +
 * {@code core_module}), {@code core_role} (seat/hitbox/part), {@code core_index}
 * (array position, hitboxes/parts only) and {@code <moduleId>_id} (this vehicle's UUID)
 * — the same scheme {@link me.bibo.militarycraft.core.vehicle.DisplayVehicle#group}
 * reads back on rehydrate.
 */
public final class ModelBuilder {

    public ArmorStand spawnSeat(Location at, String moduleId, UUID vehicleId) {
        return at.getWorld().spawn(at, ArmorStand.class, a -> {
            a.setInvisible(true);
            a.setGravity(false);
            a.setMarker(false);
            a.setSmall(true);
            a.setBasePlate(false);
            a.setArms(false);
            a.setSilent(true);
            a.setInvulnerable(true);
            a.setCollidable(false);
            a.setPersistent(true);
            tag(a, moduleId, vehicleId, "seat", -1);
        });
    }

    public Interaction spawnHitbox(Location at, float width, float height, int index,
                                    String moduleId, UUID vehicleId) {
        return at.getWorld().spawn(at, Interaction.class, in -> {
            in.setInteractionWidth(width);
            in.setInteractionHeight(height);
            in.setResponsive(true);
            in.setPersistent(true);
            tag(in, moduleId, vehicleId, "hitbox", index);
        });
    }

    public BlockDisplay spawnBlockDisplay(Location at, Part part, int index,
                                           String moduleId, UUID vehicleId, DisplayConfig config) {
        return at.getWorld().spawn(at, BlockDisplay.class, b -> {
            b.setBlock(part.material.createBlockData());
            config.apply(b);
            tag(b, moduleId, vehicleId, "part", index);
        });
    }

    public TextDisplay spawnTextDisplay(Location at, Part part, int index,
                                         String moduleId, UUID vehicleId, DisplayConfig config) {
        return at.getWorld().spawn(at, TextDisplay.class, t -> {
            t.text(Component.text(part.text != null ? part.text : ""));
            t.setBillboard(Display.Billboard.FIXED);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setSeeThrough(false);
            t.setShadowed(false);
            config.apply(t);
            tag(t, moduleId, vehicleId, "part", index);
        });
    }

    private void tag(Entity e, String moduleId, UUID vehicleId, String role, int index) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        Pdc.setString(pdc, Keys.of(moduleId, "id"), vehicleId.toString());
        Pdc.setString(pdc, Keys.of("core", "role"), role);
        if (index >= 0) {
            Pdc.setInt(pdc, Keys.of("core", "index"), index);
        }
        EntityTag.tag(e, moduleId);
    }
}
