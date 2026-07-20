package me.bibo.militarycraft.core.event;

import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Consumes damage events routed through {@link EventBus} (pilot protection + hit routing).
 * A single {@link EntityDamageEvent} callback also receives every {@code EntityDamageByEntityEvent}
 * (it doesn't declare its own handler list, so it shares the base event's - same idiom TankCraft's
 * DamageListener uses); implementers {@code instanceof}-check when they need the damager.
 */
public interface DamageSink {

    void onEntityDamage(EntityDamageEvent event);
}
