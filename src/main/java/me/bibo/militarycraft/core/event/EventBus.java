package me.bibo.militarycraft.core.event;

import me.bibo.militarycraft.core.vehicle.PilotProtection;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registers ONE Bukkit listener (this class) for every hot, shared event group and fans
 * each event out to whatever sinks modules have registered (§3.5). With zero sinks
 * registered every handler is just a no-op loop over an empty list — safe by construction.
 */
public final class EventBus implements Listener {

    private final Logger logger;
    private final CopyOnWriteArrayList<ExplosionSink> explosionSinks = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<EntityLifecycleSink> lifecycleSinks = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<InteractSink> interactSinks = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<DamageSink> damageSinks = new CopyOnWriteArrayList<>();

    public EventBus(Plugin plugin) {
        this.logger = plugin.getLogger();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getOnlinePlayers().forEach(PilotProtection::recoverStaleVisibility);
    }

    /** Registers {@code sink} against every sink interface it implements. */
    public void register(Object sink) {
        if (sink instanceof ExplosionSink s) {
            explosionSinks.addIfAbsent(s);
        }
        if (sink instanceof EntityLifecycleSink s) {
            lifecycleSinks.addIfAbsent(s);
        }
        if (sink instanceof InteractSink s) {
            interactSinks.addIfAbsent(s);
        }
        if (sink instanceof DamageSink s) {
            damageSinks.addIfAbsent(s);
        }
    }

    public void unregister(Object sink) {
        explosionSinks.remove(sink);
        lifecycleSinks.remove(sink);
        interactSinks.remove(sink);
        damageSinks.remove(sink);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (ExplosionSink s : explosionSinks) {
            invoke(s, () -> s.onEntityExplode(event));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (ExplosionSink s : explosionSinks) {
            invoke(s, () -> s.onBlockExplode(event));
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (EntityLifecycleSink s : lifecycleSinks) {
            invoke(s, () -> s.onEntitiesLoad(event));
        }
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (EntityLifecycleSink s : lifecycleSinks) {
            invoke(s, () -> s.onEntitiesUnload(event));
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        for (InteractSink s : interactSinks) {
            invoke(s, () -> s.onPlayerInteract(event));
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        for (InteractSink s : interactSinks) {
            invoke(s, () -> s.onPlayerInteractEntity(event));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        for (DamageSink s : damageSinks) {
            invoke(s, () -> s.onEntityDamage(event));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        PilotProtection.recoverStaleVisibility(event.getPlayer());
    }

    private void invoke(Object sink, Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "MilitaryCraft event sink failed: " + sink.getClass().getName(), ex);
        }
    }
}
