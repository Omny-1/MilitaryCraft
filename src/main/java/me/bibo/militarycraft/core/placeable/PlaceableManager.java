package me.bibo.militarycraft.core.placeable;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.combat.Explosions;
import me.bibo.militarycraft.core.event.EntityLifecycleSink;
import me.bibo.militarycraft.core.event.ExplosionSink;
import me.bibo.militarycraft.core.key.EntityTag;
import me.bibo.militarycraft.core.key.Keys;
import me.bibo.militarycraft.core.key.Pdc;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Registry, lifecycle router, and isolated tick loop for one placeable type. The
 * durable core entity alone controls forgetting; peripheral chunk unloads only
 * clear their stale wrapper references.
 */
public abstract class PlaceableManager<R extends PlaceableRig>
        implements EntityLifecycleSink, ExplosionSink {

    protected Core core;
    protected final Map<UUID, R> registry = new LinkedHashMap<>();

    private BukkitTask task;
    private boolean attached;
    private long tickCounter;
    private String managedModuleId;
    private String managedTypeId;

    public abstract String moduleId();

    /** Defaults to one placeable type per module; modules may override for variants. */
    public String typeId() {
        return moduleId();
    }

    /** Constructs an unspawned wrapper. Entity creation/adoption remains in the base. */
    protected abstract R newRig(UUID id, Location anchor, double yaw, UUID owner);

    /** AntiAir targeting/fuel and TCK worker reconciliation plug in here. */
    protected void onRigTick(R rig) {
    }

    protected void onTracked(R rig, TrackReason reason) {
    }

    protected void onUntracked(R rig, UntrackReason reason) {
    }

    // ------------------------------------------------------------- attachment / task lifecycle

    public final void attach(Core core) {
        Objects.requireNonNull(core, "core");
        if (attached) {
            if (this.core != core) {
                throw new IllegalStateException("placeable manager is already attached to another core");
            }
            return;
        }
        if (this.core != null && this.core != core) {
            throw new IllegalStateException("placeable manager cannot be reattached to another core");
        }

        String module = PlaceableState.requireStableId(moduleId(), "module id");
        String type = PlaceableState.requireStableId(typeId(), "type id");
        if (managedModuleId != null && (!managedModuleId.equals(module) || !managedTypeId.equals(type))) {
            throw new IllegalStateException("placeable manager identity changed between attachments");
        }
        this.core = core;
        this.managedModuleId = module;
        this.managedTypeId = type;
        core.events().register(this);
        attached = true;
    }

    public final void start() {
        requireAttached();
        if (task != null) {
            return;
        }
        task = core.scheduler().runTaskTimer(core.plugin(), this::tick, 1L, 1L);
    }

    public final void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Persists and relinquishes wrappers while deliberately leaving world entities intact. */
    public final void shutdown() {
        stop();
        for (R rig : new ArrayList<>(registry.values())) {
            try {
                rig.persistState();
            } catch (RuntimeException ex) {
                log(Level.WARNING, "Could not persist placeable " + rig.id() + " during shutdown", ex);
            }
            notifyUntracked(rig, UntrackReason.SHUTDOWN);
            rig.detachForUnload();
        }
        registry.clear();
        if (attached) {
            core.events().unregister(this);
            attached = false;
        }
    }

    // ------------------------------------------------------------- create / adopt / forget

    public final R create(Location anchor, double yaw) {
        return create(anchor, yaw, null);
    }

    public final R create(Location anchor, double yaw, UUID owner) {
        requireAttached();
        Objects.requireNonNull(anchor, "anchor");
        double normalizedYaw = PlaceableState.normalizeYaw(yaw);
        UUID id;
        do {
            id = UUID.randomUUID();
        } while (registry.containsKey(id));

        R rig = constructValidated(id, anchor.clone(), normalizedYaw, owner);
        rig.spawnNew();
        registry.put(id, rig);
        notifyTracked(rig, TrackReason.CREATED);
        return rig;
    }

    /** Adopts all managed cores currently present in loaded chunks. */
    public final void adoptExisting() {
        requireAttached();
        for (World world : Bukkit.getWorlds()) {
            try {
                adopt(world.getEntities(), false);
            } catch (RuntimeException ex) {
                log(Level.WARNING, "Could not scan loaded " + managedTypeId + " entities in " + world.getName(), ex);
            }
        }
    }

    /** Public collection form is useful to modules and focused lifecycle tests. */
    public final void adopt(Collection<Entity> entities) {
        requireAttached();
        adopt(entities, true);
    }

    private void adopt(Collection<Entity> entities, boolean expandCoreGroups) {
        Map<UUID, List<Entity>> groups = new LinkedHashMap<>();
        for (Entity entity : new ArrayList<>(entities)) {
            if (entity == null || !matchesManager(entity)) {
                continue;
            }
            Optional<UUID> parsedId = entityId(entity);
            if (parsedId.isEmpty()) {
                safeRemove(entity);
                continue;
            }
            UUID id = parsedId.get();
            R tracked = registry.get(id);
            if (tracked != null) {
                try {
                    tracked.reconcileLoadedEntity(entity);
                } catch (RuntimeException ex) {
                    log(Level.WARNING, "Could not reconcile late-loading placeable entity " + entity.getUniqueId(), ex);
                }
                continue;
            }
            groups.computeIfAbsent(id, ignored -> new ArrayList<>()).add(entity);
        }

        for (Map.Entry<UUID, List<Entity>> entry : groups.entrySet()) {
            ArmorStand seedCore = PlaceableRig.selectDurableCore(entry.getValue());
            if (seedCore == null) {
                continue; // peripherals can load before the durable core
            }
            Collection<Entity> group = expandCoreGroups
                    ? expandLoadedGroup(seedCore.getWorld(), entry.getKey(), entry.getValue())
                    : entry.getValue();
            adoptGroup(entry.getKey(), group);
        }
    }

    private Collection<Entity> expandLoadedGroup(World world, UUID id, Collection<Entity> seeds) {
        Map<UUID, Entity> result = new LinkedHashMap<>();
        for (Entity seed : seeds) {
            result.put(seed.getUniqueId(), seed);
        }
        try {
            for (Entity candidate : world.getEntities()) {
                if (matchesManager(candidate) && entityId(candidate).filter(id::equals).isPresent()) {
                    result.put(candidate.getUniqueId(), candidate);
                }
            }
        } catch (RuntimeException ex) {
            log(Level.WARNING, "Could not expand loaded entity group for placeable " + id, ex);
        }
        return result.values();
    }

    private void adoptGroup(UUID id, Collection<Entity> entities) {
        ArmorStand durableCore = PlaceableRig.selectDurableCore(entities);
        if (durableCore == null) {
            return;
        }
        R rig = null;
        try {
            PlaceableRig.Snapshot snapshot = PlaceableRig.readSnapshot(durableCore);
            rig = constructValidated(id, snapshot.anchor(), snapshot.yaw(), snapshot.owner());
            rig.adopt(snapshot, durableCore, entities);
            R previous = registry.putIfAbsent(id, rig);
            if (previous != null) {
                rig.detachForUnload();
                for (Entity entity : entities) {
                    previous.reconcileLoadedEntity(entity);
                }
                return;
            }
            notifyTracked(rig, TrackReason.ADOPTED);
        } catch (PlaceableRig.UnsupportedSchemaException ex) {
            log(Level.WARNING, "Leaving newer-schema placeable " + id + " untouched: " + ex.getMessage(), null);
        } catch (RuntimeException ex) {
            log(Level.WARNING, "Could not rehydrate " + managedTypeId + " placeable " + id, ex);
            if (rig != null) {
                rig.removeEntities();
            }
            removeGroup(entities);
        }
    }

    /** Relinquishes a wrapper without deleting its persistent entities. */
    public final boolean forget(UUID id) {
        R rig = registry.get(id);
        if (rig == null) {
            return false;
        }
        forgetInternal(rig, UntrackReason.FORGOTTEN);
        return true;
    }

    private R constructValidated(UUID id, Location anchor, double yaw, UUID owner) {
        R rig = Objects.requireNonNull(newRig(id, anchor, yaw, owner), "newRig returned null");
        if (!id.equals(rig.id())) {
            throw new IllegalStateException("newRig changed the requested UUID");
        }
        if (!managedModuleId.equals(rig.moduleId()) || !managedTypeId.equals(rig.typeId())) {
            throw new IllegalStateException("newRig identity does not match its manager");
        }
        if (anchor.getWorld() != rig.world()) {
            throw new IllegalStateException("newRig changed the requested world");
        }
        return rig;
    }

    // ------------------------------------------------------------- EventBus lifecycle

    @Override
    public final void onEntitiesLoad(EntitiesLoadEvent event) {
        adopt(event.getEntities(), true);
    }

    @Override
    public final void onEntitiesUnload(EntitiesUnloadEvent event) {
        onEntitiesUnload(event.getEntities());
    }

    public final void onEntitiesUnload(Collection<Entity> entities) {
        if (!attached) {
            return;
        }
        Set<UUID> unloadingCores = new LinkedHashSet<>();
        for (Entity entity : entities) {
            if (entity == null || !matchesManager(entity)) {
                continue;
            }
            Optional<UUID> parsedId = entityId(entity);
            if (parsedId.isEmpty()) {
                continue;
            }
            R rig = registry.get(parsedId.get());
            if (rig == null) {
                continue;
            }
            if (rig.ownsCore(entity)) {
                unloadingCores.add(rig.id());
            } else {
                rig.noteEntityUnloaded(entity);
            }
        }
        for (UUID id : unloadingCores) {
            R rig = registry.get(id);
            if (rig != null) {
                forgetInternal(rig, UntrackReason.CORE_UNLOADED);
            }
        }
    }

    // ------------------------------------------------------------- ExplosionSink

    @Override
    public final void onEntityExplode(EntityExplodeEvent event) {
        if (!Explosions.isInternal()) {
            routeExplosion(event.getLocation(), Explosions.powerFor(event.getEntity()));
        }
    }

    @Override
    public final void onBlockExplode(BlockExplodeEvent event) {
        if (!Explosions.isInternal()) {
            routeExplosion(event.getBlock().getLocation().add(0.5, 0.5, 0.5), 5.0);
        }
    }

    public final void routeExplosion(Location location, double power) {
        if (location == null || location.getWorld() == null || !Double.isFinite(power) || power <= 0.0) {
            return;
        }
        for (R rig : new ArrayList<>(registry.values())) {
            if (!rig.isActive()) {
                cleanupInvalid(rig);
                continue;
            }
            try {
                rig.applyExplosion(location, power);
            } catch (RuntimeException ex) {
                log(Level.WARNING, "Explosion routing failed for placeable " + rig.id(), ex);
            }
            if (!rig.isActive() && registry.get(rig.id()) == rig) {
                untrackRemovedRig(rig, UntrackReason.DESTROYED);
            }
        }
    }

    // ------------------------------------------------------------- tick / persistence

    private void tick() {
        tickCounter++;
        boolean repair = tickCounter % 20L == 0L;
        for (R rig : new ArrayList<>(registry.values())) {
            if (!rig.isActive()) {
                cleanupInvalid(rig);
                continue;
            }
            try {
                rig.ensureStationary();
                if (repair) {
                    rig.repairCluster();
                }
            } catch (RuntimeException ex) {
                log(Level.WARNING, "Placeable repair failed for " + rig.id(), ex);
            }
            try {
                onRigTick(rig);
            } catch (RuntimeException ex) {
                log(Level.WARNING, "Placeable tick hook failed for " + rig.id(), ex);
            }
            if (!rig.isActive()) {
                untrackRemovedRig(rig, UntrackReason.DESTROYED);
                continue;
            }
            try {
                rig.refreshModel();
            } catch (RuntimeException ex) {
                log(Level.WARNING, "Placeable model refresh failed for " + rig.id(), ex);
            }
            try {
                rig.tickPersist();
            } catch (RuntimeException ex) {
                log(Level.WARNING, "Placeable persistence failed for " + rig.id(), ex);
            }
        }
    }

    private void cleanupInvalid(R rig) {
        try {
            rig.removeEntities();
        } catch (RuntimeException ex) {
            log(Level.WARNING, "Could not clean invalid placeable " + rig.id(), ex);
        }
        untrackRemovedRig(rig, UntrackReason.INVALID_CORE);
    }

    // ------------------------------------------------------------- registry / removal / purge

    public final R byId(UUID id) {
        return registry.get(id);
    }

    public final R byEntity(Entity entity) {
        if (entity == null || !matchesManager(entity)) {
            return null;
        }
        return entityId(entity).map(registry::get).orElse(null);
    }

    public final Collection<R> all() {
        return List.copyOf(registry.values());
    }

    public final int count() {
        return registry.size();
    }

    public final int countByOwner(UUID owner) {
        if (owner == null) {
            return 0;
        }
        int count = 0;
        for (R rig : registry.values()) {
            if (rig.owner().filter(owner::equals).isPresent()) {
                count++;
            }
        }
        return count;
    }

    public final boolean destroy(R rig) {
        if (!isTracked(rig)) {
            return false;
        }
        try {
            rig.destroy(true);
        } catch (RuntimeException ex) {
            log(Level.WARNING, "Destruction effects failed for placeable " + rig.id(), ex);
        } finally {
            untrackRemovedRig(rig, UntrackReason.DESTROYED);
        }
        return true;
    }

    public final boolean remove(R rig) {
        if (!isTracked(rig)) {
            return false;
        }
        try {
            rig.removeEntities();
        } catch (RuntimeException ex) {
            log(Level.WARNING, "Could not remove placeable " + rig.id(), ex);
        } finally {
            untrackRemovedRig(rig, UntrackReason.REMOVED);
        }
        return true;
    }

    public final boolean remove(R rig, boolean effects) {
        return effects ? destroy(rig) : remove(rig);
    }

    public final PurgeResult purgeAll() {
        requireAttached();
        int tracked = registry.size();
        for (R rig : new ArrayList<>(registry.values())) {
            try {
                rig.removeEntities();
            } catch (RuntimeException ex) {
                log(Level.WARNING, "Could not purge tracked placeable " + rig.id(), ex);
            } finally {
                untrackRemovedRig(rig, UntrackReason.PURGED);
            }
        }

        int strays = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (matchesManager(entity)) {
                    safeRemove(entity);
                    strays++;
                }
            }
        }
        return new PurgeResult(tracked, strays);
    }

    private boolean isTracked(R rig) {
        return rig != null && registry.get(rig.id()) == rig;
    }

    private void forgetInternal(R rig, UntrackReason reason) {
        if (!isTracked(rig)) {
            return;
        }
        try {
            rig.persistState();
        } catch (RuntimeException ex) {
            log(Level.WARNING, "Could not persist placeable " + rig.id() + " before forgetting", ex);
        }
        registry.remove(rig.id(), rig);
        notifyUntracked(rig, reason);
        rig.detachForUnload();
    }

    private void untrackRemovedRig(R rig, UntrackReason reason) {
        if (registry.remove(rig.id(), rig)) {
            notifyUntracked(rig, reason);
        }
    }

    // ------------------------------------------------------------- identity / hooks

    private boolean matchesManager(Entity entity) {
        return !(entity instanceof Player)
                && managedModuleId != null
                && managedModuleId.equals(EntityTag.moduleOf(entity))
                && managedTypeId.equals(Pdc.getString(
                entity.getPersistentDataContainer(), Keys.of("core", "type"), null));
    }

    private Optional<UUID> entityId(Entity entity) {
        String value = Pdc.getString(
                entity.getPersistentDataContainer(), Keys.of(managedModuleId, "id"), null);
        return PlaceableState.parseUuid(value);
    }

    private void notifyTracked(R rig, TrackReason reason) {
        try {
            onTracked(rig, reason);
        } catch (RuntimeException ex) {
            log(Level.WARNING, "Placeable tracked hook failed for " + rig.id(), ex);
        }
    }

    private void notifyUntracked(R rig, UntrackReason reason) {
        try {
            onUntracked(rig, reason);
        } catch (RuntimeException ex) {
            log(Level.WARNING, "Placeable untracked hook failed for " + rig.id(), ex);
        }
    }

    private void requireAttached() {
        if (!attached || core == null) {
            throw new IllegalStateException("placeable manager is not attached");
        }
    }

    private void log(Level level, String message, Throwable error) {
        if (core == null) {
            return;
        }
        if (error == null) {
            core.logger().log(level, message);
        } else {
            core.logger().log(level, message, error);
        }
    }

    private static void removeGroup(Collection<Entity> entities) {
        for (Entity entity : entities) {
            safeRemove(entity);
        }
    }

    private static void safeRemove(Entity entity) {
        if (entity == null || entity instanceof Player) {
            return;
        }
        try {
            entity.remove();
        } catch (RuntimeException ignored) {
        }
    }

    public final boolean isAttached() {
        return attached;
    }

    public final boolean isRunning() {
        return task != null;
    }

    public enum TrackReason {
        CREATED,
        ADOPTED
    }

    public enum UntrackReason {
        DESTROYED,
        REMOVED,
        CORE_UNLOADED,
        FORGOTTEN,
        INVALID_CORE,
        SHUTDOWN,
        PURGED
    }

    public record PurgeResult(int trackedRigs, int strayEntities) {
    }
}
