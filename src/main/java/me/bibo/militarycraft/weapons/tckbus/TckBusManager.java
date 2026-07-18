package me.bibo.militarycraft.weapons.tckbus;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registry of every TckBusRig in loaded chunks plus the single per-tick loop. Buses
 * live as persistent entities; their wrappers are rebuilt on entity-load and
 * dropped on entity-unload, so they survive chunk reloads and restarts. Also owns
 * the {@link TckBusSnatchManager}, which it ticks after the buses each tick.
 */
public final class TckBusManager {

    public static final String MODULE_ID = "tckbus";

    private final TckBusRuntime plugin;
    private final TckBusSnatchManager snatch;
    private final Map<UUID, TckBusRig> buses = new LinkedHashMap<>();
    private BukkitTask task;
    private long tickCounter;

    /** How often (ticks) a loaded TckBusRig tops its workers back up. */
    private static final long HEAL_PERIOD = 40L;

    public TckBusManager(TckBusRuntime plugin) {
        this.plugin = plugin;
        this.snatch = new TckBusSnatchManager(plugin, this);
    }

    public TckBusSnatchManager snatch() {
        return snatch;
    }

    // --------------------------------------------------------------- lifecycle

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin.plugin(), this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void adoptExisting() {
        for (World world : Bukkit.getWorlds()) {
            onEntitiesLoad(world.getEntities());
        }
    }

    public void shutdown() {
        stop();
        snatch.releaseAll();
        for (TckBusRig b : new ArrayList<>(buses.values())) {
            b.persistState();
        }
        buses.clear();
    }

    private void tick() {
        tickCounter++;
        TckBusSettings cfg = plugin.config();

        Iterator<TckBusRig> it = buses.values().iterator();
        while (it.hasNext()) {
            TckBusRig TckBusRig = it.next();
            if (!TckBusRig.isActive()) {
                TckBusRig.removeEntities();
                it.remove();
                continue;
            }
            if (tickCounter % HEAL_PERIOD == 0) {
                try {
                    TckBusRig.healWorkers();
                } catch (Exception ex) {
                    plugin.getLogger().warning("TckBusRig heal failed: " + ex);
                }
            }
            try {
                TckBusRig.tick(tickCounter, cfg);
            } catch (Exception ex) {
                plugin.getLogger().warning("TckBusRig tick failed: " + ex);
            }
        }

        try {
            snatch.tick();
        } catch (Exception ex) {
            plugin.getLogger().warning("Snatch tick failed: " + ex);
        }
    }

    // --------------------------------------------------------------- chunk (de)hydration

    public void onEntitiesLoad(Collection<Entity> entities) {
        Map<UUID, List<Entity>> groups = new HashMap<>();
        for (Entity e : entities) {
            if (!e.getScoreboardTags().contains(TckBusKeys.SCOREBOARD_TAG)) {
                continue;
            }
            String idStr = e.getPersistentDataContainer().get(TckBusKeys.BUS_ID, PersistentDataType.STRING);
            if (idStr == null) {
                continue;
            }
            UUID id;
            try {
                id = UUID.fromString(idStr);
            } catch (IllegalArgumentException ex) {
                e.remove();
                continue;
            }
            TckBusRig loaded = buses.get(id);
            if (loaded != null) {
                loaded.adopt(e); // late-loading worker from a neighbouring chunk
                continue;
            }
            groups.computeIfAbsent(id, k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<UUID, List<Entity>> entry : groups.entrySet()) {
            TckBusRig b = TckBusRig.rehydrate(plugin, entry.getKey(), entry.getValue());
            if (b != null) {
                buses.put(entry.getKey(), b);
            } else {
                for (Entity e : entry.getValue()) {
                    e.remove();
                }
            }
        }
    }

    public void onEntitiesUnload(Collection<Entity> entities) {
        for (Entity e : entities) {
            String idStr = e.getPersistentDataContainer().get(TckBusKeys.BUS_ID, PersistentDataType.STRING);
            if (idStr == null) {
                continue;
            }
            TckBusRig b;
            try {
                b = buses.get(UUID.fromString(idStr));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (b == null) {
                continue;
            }
            b.persistState();
            snatch.onBusUnload(b);
            buses.remove(b.id());
        }
    }

    // --------------------------------------------------------------- registry

    public TckBusRig create(Location at, double yaw, UUID owner) {
        return create(at, yaw, owner, plugin.config().defaultSkinId);
    }

    public TckBusRig create(Location at, double yaw, UUID owner, String skinId) {
        TckBusRig b = TckBusRig.create(plugin, at, yaw, owner, skinId);
        buses.put(b.id(), b);
        return b;
    }

    public TckBusRig byId(UUID id) {
        return id == null ? null : buses.get(id);
    }

    public TckBusRig byEntity(Entity entity) {
        String id = entity.getPersistentDataContainer().get(TckBusKeys.BUS_ID, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        try {
            return buses.get(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public TckBusRig busByWorker(UUID workerId) {
        for (TckBusRig b : buses.values()) {
            if (b.hasWorker(workerId)) {
                return b;
            }
        }
        return null;
    }

    public Collection<TckBusRig> all() {
        return buses.values();
    }

    public int count() {
        return buses.size();
    }

    public int countByOwner(UUID owner) {
        if (owner == null) {
            return 0;
        }
        int n = 0;
        for (TckBusRig b : buses.values()) {
            if (owner.equals(b.owner())) n++;
        }
        return n;
    }

    /** Count alive TCK workers within {@code radius} of a point (any TckBusRig). */
    public int countWorkersNear(World world, Location loc, double radius) {
        double rSq = radius * radius;
        int n = 0;
        for (TckBusRig b : buses.values()) {
            if (b.world() != world) {
                continue;
            }
            for (Mob w : b.workers()) {
                if (w != null && w.isValid() && !w.isDead()
                        && w.getLocation().distanceSquared(loc) <= rSq) {
                    n++;
                }
            }
        }
        return n;
    }

    // --------------------------------------------------------------- damage

    public void damageBusesFromExplosion(Location loc, double power) {
        TckBusSettings cfg = plugin.config();
        for (TckBusRig b : new ArrayList<>(buses.values())) {
            if (!b.isActive() || b.world() != loc.getWorld()) {
                continue;
            }
            double dist = b.minBlastDistance(loc);
            double contact = cfg.contactRadius;
            double radius = power * 2.0 + contact;
            if (dist > radius) {
                continue;
            }
            double falloff = dist <= contact ? 1.0
                    : Math.max(0.0, 1.0 - (dist - contact) / (radius - contact));
            double dmg = cfg.creeperDamage * (power / 3.0) * falloff;
            if (dmg > 0) {
                b.damage(dmg);
            }
        }
    }

    // --------------------------------------------------------------- removal

    public void remove(TckBusRig TckBusRig, boolean effects) {
        snatch.onBusUnload(TckBusRig);
        TckBusRig.destroy(effects);
        buses.remove(TckBusRig.id());
    }

    public int[] cleanupAll() {
        int count = buses.size();
        snatch.releaseAll();
        for (TckBusRig b : new ArrayList<>(buses.values())) {
            b.removeEntities();
        }
        buses.clear();

        int orphans = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getScoreboardTags().contains(TckBusKeys.SCOREBOARD_TAG)
                        || e.getPersistentDataContainer().has(TckBusKeys.BUS_ID, PersistentDataType.STRING)) {
                    e.remove();
                    orphans++;
                }
            }
        }
        return new int[]{count, orphans};
    }
}


