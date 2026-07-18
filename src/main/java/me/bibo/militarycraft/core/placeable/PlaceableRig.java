package me.bibo.militarycraft.core.placeable;

import me.bibo.militarycraft.core.combat.Explosions;
import me.bibo.militarycraft.core.key.EntityTag;
import me.bibo.militarycraft.core.key.Keys;
import me.bibo.militarycraft.core.key.Pdc;
import me.bibo.militarycraft.core.model.Part;
import me.bibo.militarycraft.core.model.Transforms;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent stationary display cluster shared by CP4 placeables. One marker
 * ArmorStand is the durable state authority; hitboxes, displays, and optional
 * module-owned auxiliary entities are repairable peripherals.
 */
public abstract class PlaceableRig {

    static final String ROLE_CORE = "core";
    static final String ROLE_HITBOX = "hitbox";
    static final String ROLE_PART = "part";

    private static final String TYPE_KEY = "type";
    private static final String ROLE_KEY = "role";
    private static final String ELEMENT_ID_KEY = "part_id";
    private static final String INDEX_KEY = "index";
    private static final String SCHEMA_KEY = "schema";
    private static final String HEALTH_KEY = "health";
    private static final String OWNER_KEY = "owner";
    private static final String ANCHOR_X_KEY = "anchor_x";
    private static final String ANCHOR_Y_KEY = "anchor_y";
    private static final String ANCHOR_Z_KEY = "anchor_z";
    private static final String ANCHOR_YAW_KEY = "anchor_yaw";

    private final UUID id;
    private final String moduleId;
    private final String typeId;
    private final World world;
    private final Location anchor;

    private double yaw;
    private UUID owner;
    private double health;

    private ArmorStand coreEntity;
    private final Map<String, Interaction> hitboxes = new LinkedHashMap<>();
    private final Map<String, Display> displays = new LinkedHashMap<>();
    private final Map<UUID, Entity> auxiliaryEntities = new LinkedHashMap<>();

    private boolean spawned;
    private boolean stateDirty;
    private int persistCooldown;
    private boolean modelDirty = true;
    private boolean forceAllTransforms = true;
    private double lastRenderedYaw = Double.NaN;

    protected PlaceableRig(UUID id, String moduleId, String typeId,
                           Location anchor, double yaw, UUID owner) {
        this.id = Objects.requireNonNull(id, "id");
        this.moduleId = PlaceableState.requireStableId(moduleId, "module id");
        this.typeId = PlaceableState.requireStableId(typeId, "type id");
        Objects.requireNonNull(anchor, "anchor");
        this.world = Objects.requireNonNull(anchor.getWorld(), "anchor world");
        requireUsableAnchor(anchor);
        this.anchor = new Location(world, anchor.getX(), anchor.getY(), anchor.getZ());
        this.yaw = PlaceableState.normalizeYaw(yaw);
        this.owner = owner;
    }

    public abstract PlaceableModel model();

    public abstract double maxHealth();

    /** Current PDC schema understood by this concrete rig. */
    protected int schemaVersion() {
        return PlaceableState.FIRST_SCHEMA_VERSION;
    }

    /** Writes module-owned fields onto the durable core entity. */
    protected void writeExtraState(PersistentDataContainer pdc) {
    }

    /** Reads module-owned fields. Older supported schemas can be migrated here. */
    protected void readExtraState(PersistentDataContainer pdc, int persistedSchemaVersion) {
    }

    protected void onSpawnEffects() {
    }

    protected void onRehydrated() {
    }

    protected void onDamaged(double amount) {
    }

    protected void onDestroyEffects() {
        Location centre = damageCenter();
        Explosions.createExplosion(world, centre, 2.5f, false, false);
        Explosions.impactFx(centre);
    }

    /**
     * Allows TCK-style persistent workers without teaching the foundation their AI.
     * Accepted entities are tracked, removed with the rig, and forgotten on unload.
     */
    protected boolean acceptAuxiliaryEntity(Entity entity, String role) {
        return false;
    }

    /** Default rigid transform; AntiAir can override for turret/gun articulation. */
    protected Transformation transformFor(PlaceableModel.PartSpec spec) {
        Part part = spec.part();
        Quaternionf facing = Transforms.yawQuat(yaw);
        Vector3f worldOffset = facing.transform(new Vector3f(part.offset));
        Quaternionf baseRotation = new Quaternionf()
                .rotateY((float) Math.toRadians(-part.yaw))
                .rotateX((float) Math.toRadians(part.pitch))
                .rotateZ((float) Math.toRadians(part.roll));
        return Transforms.build(part, worldOffset, new Quaternionf(facing).mul(baseRotation));
    }

    /** Hook for group-aware transform skipping. Forced refreshes must always return true. */
    protected boolean partNeedsRefresh(PlaceableModel.PartSpec spec, boolean yawChanged, boolean forced) {
        return true;
    }

    /** Hitbox offsets are local-space Interaction base locations. */
    protected Location hitboxLocation(PlaceableModel.HitboxSpec spec) {
        Vector3f offset = Transforms.localPointToWorld(spec.offset(), yaw);
        return new Location(world, anchor.getX() + offset.x, anchor.getY() + offset.y, anchor.getZ() + offset.z);
    }

    protected Location damageCenter() {
        return anchor.clone().add(0.0, definition().height() * 0.5, 0.0);
    }

    protected double creeperDamageUnit() {
        return checkedMaxHealth() / 4.0;
    }

    protected double explosionContactRadius() {
        return 2.0;
    }

    // ------------------------------------------------------------- construction / adoption

    final void spawnNew() {
        if (spawned || coreEntity != null) {
            throw new IllegalStateException("placeable rig is already spawned");
        }
        health = checkedMaxHealth();
        PlaceableModel definition = definition();
        try {
            coreEntity = spawnCore();
            for (int i = 0; i < definition.hitboxes().size(); i++) {
                PlaceableModel.HitboxSpec spec = definition.hitboxes().get(i);
                hitboxes.put(spec.id(), spawnHitbox(spec, i));
            }
            for (int i = 0; i < definition.parts().size(); i++) {
                PlaceableModel.PartSpec spec = definition.parts().get(i);
                displays.put(spec.id(), spawnDisplay(spec, i, definition));
            }
            spawned = true;
            forceRefresh();
            persistState();
            onSpawnEffects();
        } catch (RuntimeException ex) {
            removeEntities();
            throw ex;
        }
    }

    final void adopt(Snapshot snapshot, ArmorStand selectedCore, Collection<Entity> entities) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(selectedCore, "selectedCore");
        int currentSchema = checkedSchemaVersion();
        if (snapshot.schemaVersion() > currentSchema) {
            throw new UnsupportedSchemaException(snapshot.schemaVersion(), currentSchema);
        }
        if (selectedCore.getWorld() != world) {
            throw new IllegalArgumentException("durable core is in a different world");
        }

        coreEntity = selectedCore;
        health = PlaceableState.clampHealth(snapshot.health(), checkedMaxHealth());
        spawned = true;
        readExtraState(selectedCore.getPersistentDataContainer(), snapshot.schemaVersion());
        reconcileCluster(entities);
        forceRefresh();
        persistState();
        onRehydrated();
    }

    private ArmorStand spawnCore() {
        return world.spawn(anchor, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setSilent(true);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setPersistent(true);
            tagEntity(stand, ROLE_CORE, null, -1);
        });
    }

    private Interaction spawnHitbox(PlaceableModel.HitboxSpec spec, int index) {
        Location location = hitboxLocation(spec);
        return world.spawn(location, Interaction.class, interaction -> {
            configureHitbox(interaction, spec);
            tagEntity(interaction, ROLE_HITBOX, spec.id(), index);
        });
    }

    private Display spawnDisplay(PlaceableModel.PartSpec spec, int index, PlaceableModel definition) {
        Part part = spec.part();
        Display display = part.isText()
                ? world.spawn(anchor, TextDisplay.class)
                : world.spawn(anchor, BlockDisplay.class);
        try {
            configureDisplay(display, spec, definition);
            tagEntity(display, ROLE_PART, spec.id(), index);
            return display;
        } catch (RuntimeException ex) {
            safeRemove(display);
            throw ex;
        }
    }

    // ------------------------------------------------------------- stable grouping / repair

    private void reconcileCluster(Collection<Entity> entities) {
        PlaceableModel definition = definition();
        Map<String, PlaceableModel.PartSpec> expectedParts = partSpecs(definition);
        Map<String, PlaceableModel.HitboxSpec> expectedHitboxes = hitboxSpecs(definition);
        hitboxes.clear();
        displays.clear();
        auxiliaryEntities.clear();

        List<Entity> ordered = new ArrayList<>(entities);
        ordered.sort(Comparator.comparing(entity -> entity.getUniqueId().toString()));
        for (Entity entity : ordered) {
            if (entity == null || entity.getUniqueId().equals(coreEntity.getUniqueId())) {
                continue;
            }
            String role = roleOf(entity);
            switch (role == null ? "" : role) {
                case ROLE_CORE -> safeRemove(entity);
                case ROLE_HITBOX -> adoptHitbox(entity, expectedHitboxes, definition);
                case ROLE_PART -> adoptDisplay(entity, expectedParts, definition);
                default -> adoptAuxiliaryOrRemove(entity, role);
            }
        }
        tagEntity(coreEntity, ROLE_CORE, null, -1);
        repairMissing(definition, false);
    }

    final void reconcileLoadedEntity(Entity entity) {
        if (entity == null || !matchesIdentity(entity)) {
            return;
        }
        if (ownsCore(entity)) {
            tagEntity(entity, ROLE_CORE, null, -1);
            return;
        }
        String role = roleOf(entity);
        if (ROLE_CORE.equals(role)) {
            safeRemove(entity);
            return;
        }
        PlaceableModel definition = definition();
        if (ROLE_HITBOX.equals(role)) {
            adoptHitbox(entity, hitboxSpecs(definition), definition);
        } else if (ROLE_PART.equals(role)) {
            adoptDisplay(entity, partSpecs(definition), definition);
        } else {
            adoptAuxiliaryOrRemove(entity, role);
        }
        forceRefresh();
    }

    final void repairCluster() {
        if (!isActive()) {
            return;
        }
        PlaceableModel definition = definition();
        removeObsoleteHitboxes(hitboxSpecs(definition));
        removeObsoleteDisplays(partSpecs(definition));
        auxiliaryEntities.values().removeIf(entity -> entity == null || !entity.isValid());
        tagEntity(coreEntity, ROLE_CORE, null, -1);
        repairMissing(definition, false);
        forceRefresh();
    }

    private void repairMissing(PlaceableModel definition, boolean allowChunkLoad) {
        for (int i = 0; i < definition.hitboxes().size(); i++) {
            PlaceableModel.HitboxSpec spec = definition.hitboxes().get(i);
            Interaction current = hitboxes.get(spec.id());
            if (current == null || !current.isValid()) {
                hitboxes.remove(spec.id());
                Location target = hitboxLocation(spec);
                if (allowChunkLoad || isChunkLoaded(target)) {
                    hitboxes.put(spec.id(), spawnHitbox(spec, i));
                }
            } else {
                configureHitbox(current, spec);
                tagEntity(current, ROLE_HITBOX, spec.id(), i);
            }
        }
        for (int i = 0; i < definition.parts().size(); i++) {
            PlaceableModel.PartSpec spec = definition.parts().get(i);
            Display current = displays.get(spec.id());
            if (current == null || !current.isValid() || !displayMatches(current, spec)) {
                safeRemove(current);
                displays.remove(spec.id());
                if (allowChunkLoad || isChunkLoaded(anchor)) {
                    displays.put(spec.id(), spawnDisplay(spec, i, definition));
                }
            } else {
                configureDisplay(current, spec, definition);
                tagEntity(current, ROLE_PART, spec.id(), i);
            }
        }
    }

    private void adoptHitbox(Entity entity, Map<String, PlaceableModel.HitboxSpec> expected,
                             PlaceableModel definition) {
        String elementId = resolveElementId(entity, expected.keySet(), definition.hitboxes());
        if (!(entity instanceof Interaction interaction) || elementId == null) {
            safeRemove(entity);
            return;
        }
        Interaction existing = hitboxes.get(elementId);
        if (existing != null && existing.isValid() && !existing.getUniqueId().equals(interaction.getUniqueId())) {
            safeRemove(interaction);
            return;
        }
        PlaceableModel.HitboxSpec spec = expected.get(elementId);
        configureHitbox(interaction, spec);
        tagEntity(interaction, ROLE_HITBOX, elementId, indexOfHitbox(definition, elementId));
        hitboxes.put(elementId, interaction);
    }

    private void adoptDisplay(Entity entity, Map<String, PlaceableModel.PartSpec> expected,
                              PlaceableModel definition) {
        String elementId = resolveElementId(entity, expected.keySet(), definition.parts());
        PlaceableModel.PartSpec spec = elementId == null ? null : expected.get(elementId);
        if (!(entity instanceof Display display) || spec == null || !displayMatches(display, spec)) {
            safeRemove(entity);
            return;
        }
        Display existing = displays.get(elementId);
        if (existing != null && existing.isValid() && !existing.getUniqueId().equals(display.getUniqueId())) {
            safeRemove(display);
            return;
        }
        configureDisplay(display, spec, definition);
        tagEntity(display, ROLE_PART, elementId, indexOfPart(definition, elementId));
        displays.put(elementId, display);
    }

    private void adoptAuxiliaryOrRemove(Entity entity, String role) {
        if (role != null && !isReservedRole(role) && acceptAuxiliaryEntity(entity, role)) {
            tagEntity(entity, role, null, -1);
            auxiliaryEntities.put(entity.getUniqueId(), entity);
        } else {
            safeRemove(entity);
        }
    }

    private void removeObsoleteHitboxes(Map<String, PlaceableModel.HitboxSpec> expected) {
        Iterator<Map.Entry<String, Interaction>> iterator = hitboxes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Interaction> entry = iterator.next();
            if (!expected.containsKey(entry.getKey())) {
                safeRemove(entry.getValue());
                iterator.remove();
            } else if (entry.getValue() == null || !entry.getValue().isValid()) {
                iterator.remove();
            }
        }
    }

    private void removeObsoleteDisplays(Map<String, PlaceableModel.PartSpec> expected) {
        Iterator<Map.Entry<String, Display>> iterator = displays.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Display> entry = iterator.next();
            PlaceableModel.PartSpec spec = expected.get(entry.getKey());
            if (spec == null || !displayMatches(entry.getValue(), spec)) {
                safeRemove(entry.getValue());
                iterator.remove();
            } else if (entry.getValue() == null || !entry.getValue().isValid()) {
                iterator.remove();
            }
        }
    }

    private static Map<String, PlaceableModel.PartSpec> partSpecs(PlaceableModel definition) {
        Map<String, PlaceableModel.PartSpec> result = new LinkedHashMap<>();
        for (PlaceableModel.PartSpec spec : definition.parts()) {
            result.put(spec.id(), spec);
        }
        return result;
    }

    private static Map<String, PlaceableModel.HitboxSpec> hitboxSpecs(PlaceableModel definition) {
        Map<String, PlaceableModel.HitboxSpec> result = new LinkedHashMap<>();
        for (PlaceableModel.HitboxSpec spec : definition.hitboxes()) {
            result.put(spec.id(), spec);
        }
        return result;
    }

    private String resolveElementId(Entity entity, Collection<String> expectedIds, List<?> orderedSpecs) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String persisted = Pdc.getString(pdc, Keys.of("core", ELEMENT_ID_KEY), null);
        if (persisted != null && expectedIds.contains(persisted)) {
            return persisted;
        }
        int index = Pdc.getInt(pdc, Keys.of("core", INDEX_KEY), -1);
        if (index < 0 || index >= orderedSpecs.size()) {
            return null;
        }
        Object spec = orderedSpecs.get(index);
        if (spec instanceof PlaceableModel.PartSpec part) {
            return part.id();
        }
        if (spec instanceof PlaceableModel.HitboxSpec hitbox) {
            return hitbox.id();
        }
        return null;
    }

    // ------------------------------------------------------------- rendering / stationarity

    public final void refreshModel() {
        if (!isActive()) {
            return;
        }
        ensureStationary();
        if (!modelDirty) {
            return;
        }
        PlaceableModel definition = definition();
        boolean forced = forceAllTransforms;
        boolean yawChanged = forced || Double.isNaN(lastRenderedYaw)
                || Math.abs(lastRenderedYaw - yaw) > 1.0e-5;

        for (PlaceableModel.HitboxSpec spec : definition.hitboxes()) {
            Interaction interaction = hitboxes.get(spec.id());
            if (interaction != null && interaction.isValid()) {
                interaction.teleport(hitboxLocation(spec));
            }
        }
        for (PlaceableModel.PartSpec spec : definition.parts()) {
            Display display = displays.get(spec.id());
            if (display == null || !display.isValid()) {
                continue;
            }
            display.teleport(anchor);
            if (forced || partNeedsRefresh(spec, yawChanged, false)) {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(definition.displayConfig().interpolationDuration());
                display.setTransformation(transformFor(spec));
            }
        }
        lastRenderedYaw = yaw;
        modelDirty = false;
        forceAllTransforms = false;
    }

    final void ensureStationary() {
        if (coreEntity == null || !coreEntity.isValid()) {
            return;
        }
        Location location = coreEntity.getLocation();
        if (location.getWorld() != world
                || Math.abs(location.getX() - anchor.getX()) > 0.03
                || Math.abs(location.getY() - anchor.getY()) > 0.03
                || Math.abs(location.getZ() - anchor.getZ()) > 0.03) {
            coreEntity.teleport(anchor);
        }
    }

    protected final void markModelDirty() {
        modelDirty = true;
    }

    protected final void forceRefresh() {
        modelDirty = true;
        forceAllTransforms = true;
        lastRenderedYaw = Double.NaN;
        if (spawned) {
            refreshModel();
        }
    }

    protected final boolean setYaw(double yaw) {
        if (!Double.isFinite(yaw)) {
            return false;
        }
        double normalized = PlaceableState.normalizeYaw(yaw);
        if (Math.abs(this.yaw - normalized) <= 1.0e-7) {
            return false;
        }
        this.yaw = normalized;
        markModelDirty();
        markStateDirty();
        return true;
    }

    protected final void setOwner(UUID owner) {
        if (Objects.equals(this.owner, owner)) {
            return;
        }
        this.owner = owner;
        markStateDirty();
    }

    // ------------------------------------------------------------- persistence

    public final void persistState() {
        if (coreEntity == null || !coreEntity.isValid()) {
            return;
        }
        PersistentDataContainer pdc = coreEntity.getPersistentDataContainer();
        tagEntity(coreEntity, ROLE_CORE, null, -1);
        Pdc.setInt(pdc, Keys.of("core", SCHEMA_KEY), checkedSchemaVersion());
        health = PlaceableState.clampHealth(health, checkedMaxHealth());
        Pdc.setDouble(pdc, Keys.of("core", HEALTH_KEY), health);
        Pdc.setDouble(pdc, Keys.of("core", ANCHOR_X_KEY), anchor.getX());
        Pdc.setDouble(pdc, Keys.of("core", ANCHOR_Y_KEY), anchor.getY());
        Pdc.setDouble(pdc, Keys.of("core", ANCHOR_Z_KEY), anchor.getZ());
        Pdc.setDouble(pdc, Keys.of("core", ANCHOR_YAW_KEY), yaw);
        if (owner == null) {
            pdc.remove(Keys.of("core", OWNER_KEY));
        } else {
            Pdc.setUuid(pdc, Keys.of("core", OWNER_KEY), owner);
        }
        writeExtraState(pdc);
        stateDirty = false;
        persistCooldown = 0;
    }

    public final void tickPersist() {
        if (!stateDirty || !isActive()) {
            return;
        }
        if (++persistCooldown >= 20) {
            persistState();
        }
    }

    protected final void markStateDirty() {
        stateDirty = true;
    }

    // ------------------------------------------------------------- damage / removal

    /** @return true when this hit destroyed the rig. */
    public boolean damage(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0 || !isActive()) {
            return false;
        }
        health = Math.max(0.0, health - amount);
        markStateDirty();
        boolean destroyed = health <= 0.0;
        try {
            onDamaged(amount);
        } finally {
            if (destroyed && isActive()) {
                destroy(true);
            }
        }
        return destroyed;
    }

    public void applyExplosion(Location location, double power) {
        if (location == null || location.getWorld() != world || !Double.isFinite(power)
                || power <= 0.0 || !isActive()) {
            return;
        }
        Location centre = damageCenter();
        double distance = centre.distance(location);
        double contact = Math.max(0.0, explosionContactRadius());
        double radius = power * 2.0 + contact;
        if (!Double.isFinite(radius) || distance > radius) {
            return;
        }
        double falloff = distance <= contact || radius <= contact
                ? 1.0
                : Math.max(0.0, 1.0 - (distance - contact) / (radius - contact));
        double amount = creeperDamageUnit() * (power / 3.0) * falloff;
        if (Double.isFinite(amount) && amount > 0.0) {
            damage(amount);
        }
    }

    public final void destroy(boolean effects) {
        if (!spawned) {
            return;
        }
        try {
            if (effects) {
                onDestroyEffects();
            }
        } finally {
            removeEntities();
        }
    }

    /** Deletes the complete entity cluster without destruction effects or registry changes. */
    public final void removeEntities() {
        spawned = false;
        safeRemove(coreEntity);
        for (Interaction interaction : hitboxes.values()) {
            safeRemove(interaction);
        }
        for (Display display : displays.values()) {
            safeRemove(display);
        }
        for (Entity entity : auxiliaryEntities.values()) {
            safeRemove(entity);
        }
        clearEntityReferences();
        stateDirty = false;
        persistCooldown = 0;
    }

    final void detachForUnload() {
        spawned = false;
        clearEntityReferences();
        stateDirty = false;
        persistCooldown = 0;
    }

    final void noteEntityUnloaded(Entity entity) {
        if (entity == null) {
            return;
        }
        UUID entityId = entity.getUniqueId();
        hitboxes.values().removeIf(hitbox -> hitbox != null && hitbox.getUniqueId().equals(entityId));
        displays.values().removeIf(display -> display != null && display.getUniqueId().equals(entityId));
        auxiliaryEntities.remove(entityId);
    }

    protected final void trackAuxiliaryEntity(Entity entity, String role) {
        Objects.requireNonNull(entity, "entity");
        if (entity instanceof Player || entity.getWorld() != world) {
            throw new IllegalArgumentException("auxiliary entities must be non-player entities in the rig world");
        }
        String checkedRole = PlaceableState.requireStableId(role, "auxiliary role");
        if (isReservedRole(checkedRole)) {
            throw new IllegalArgumentException("auxiliary role is reserved: " + checkedRole);
        }
        tagEntity(entity, checkedRole, null, -1);
        auxiliaryEntities.put(entity.getUniqueId(), entity);
    }

    protected final Collection<Entity> auxiliaryEntities() {
        return List.copyOf(auxiliaryEntities.values());
    }

    private void clearEntityReferences() {
        coreEntity = null;
        hitboxes.clear();
        displays.clear();
        auxiliaryEntities.clear();
        modelDirty = true;
        forceAllTransforms = true;
        lastRenderedYaw = Double.NaN;
    }

    // ------------------------------------------------------------- PDC / snapshot helpers

    private void tagEntity(Entity entity, String role, String elementId, int index) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        Pdc.setUuid(pdc, Keys.of(moduleId, "id"), id);
        Pdc.setString(pdc, Keys.of("core", TYPE_KEY), typeId);
        Pdc.setString(pdc, Keys.of("core", ROLE_KEY), role);
        Pdc.setInt(pdc, Keys.of("core", SCHEMA_KEY), checkedSchemaVersion());
        if (elementId == null) {
            pdc.remove(Keys.of("core", ELEMENT_ID_KEY));
        } else {
            Pdc.setString(pdc, Keys.of("core", ELEMENT_ID_KEY), elementId);
        }
        if (index < 0) {
            pdc.remove(Keys.of("core", INDEX_KEY));
        } else {
            Pdc.setInt(pdc, Keys.of("core", INDEX_KEY), index);
        }
        EntityTag.tag(entity, moduleId);
    }

    static String roleOf(Entity entity) {
        return Pdc.getString(entity.getPersistentDataContainer(), Keys.of("core", ROLE_KEY), null);
    }

    static ArmorStand selectDurableCore(Collection<Entity> entities) {
        ArmorStand selected = null;
        for (Entity entity : entities) {
            if (!(entity instanceof ArmorStand stand) || !ROLE_CORE.equals(roleOf(entity))) {
                continue;
            }
            if (selected == null || stand.getUniqueId().toString().compareTo(selected.getUniqueId().toString()) < 0) {
                selected = stand;
            }
        }
        return selected;
    }

    static Snapshot readSnapshot(ArmorStand durableCore) {
        Location coreLocation = durableCore.getLocation();
        World world = durableCore.getWorld();
        PersistentDataContainer pdc = durableCore.getPersistentDataContainer();
        double x = Pdc.getDouble(pdc, Keys.of("core", ANCHOR_X_KEY), coreLocation.getX());
        double y = Pdc.getDouble(pdc, Keys.of("core", ANCHOR_Y_KEY), coreLocation.getY());
        double z = Pdc.getDouble(pdc, Keys.of("core", ANCHOR_Z_KEY), coreLocation.getZ());
        if (!PlaceableState.isUsableHorizontalCoordinate(x)) {
            x = coreLocation.getX();
        }
        if (!Double.isFinite(y) || y < world.getMinHeight() - 64.0 || y > world.getMaxHeight() + 64.0) {
            y = coreLocation.getY();
        }
        if (!PlaceableState.isUsableHorizontalCoordinate(z)) {
            z = coreLocation.getZ();
        }
        double persistedYaw = Pdc.getDouble(pdc, Keys.of("core", ANCHOR_YAW_KEY), coreLocation.getYaw());
        if (!Double.isFinite(persistedYaw)) {
            persistedYaw = 0.0;
        }
        int schema = PlaceableState.normalizeSchemaVersion(
                Pdc.getInt(pdc, Keys.of("core", SCHEMA_KEY), PlaceableState.FIRST_SCHEMA_VERSION));
        double health = Pdc.getDouble(pdc, Keys.of("core", HEALTH_KEY), Double.NaN);
        UUID owner = Pdc.getUuid(pdc, Keys.of("core", OWNER_KEY), null);
        return new Snapshot(new Location(world, x, y, z), PlaceableState.normalizeYaw(persistedYaw),
                owner, health, schema);
    }

    private boolean matchesIdentity(Entity entity) {
        if (!moduleId.equals(EntityTag.moduleOf(entity))) {
            return false;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (!typeId.equals(Pdc.getString(pdc, Keys.of("core", TYPE_KEY), null))) {
            return false;
        }
        return id.equals(Pdc.getUuid(pdc, Keys.of(moduleId, "id"), null));
    }

    final boolean ownsCore(Entity entity) {
        return coreEntity != null && entity != null
                && coreEntity.getUniqueId().equals(entity.getUniqueId());
    }

    private static boolean isReservedRole(String role) {
        return ROLE_CORE.equals(role) || ROLE_HITBOX.equals(role) || ROLE_PART.equals(role);
    }

    private static void requireUsableAnchor(Location anchor) {
        if (!PlaceableState.isUsableHorizontalCoordinate(anchor.getX())
                || !Double.isFinite(anchor.getY())
                || !PlaceableState.isUsableHorizontalCoordinate(anchor.getZ())) {
            throw new IllegalArgumentException("anchor coordinates must be finite and inside world limits");
        }
    }

    private PlaceableModel definition() {
        return Objects.requireNonNull(model(), "model");
    }

    private double checkedMaxHealth() {
        double maximum = maxHealth();
        PlaceableState.requireFinite(maximum, "maximum health");
        if (maximum <= 0.0) {
            throw new IllegalStateException("maximum health must be positive");
        }
        return maximum;
    }

    private int checkedSchemaVersion() {
        int version = schemaVersion();
        if (version < PlaceableState.FIRST_SCHEMA_VERSION) {
            throw new IllegalStateException("schema version must be at least "
                    + PlaceableState.FIRST_SCHEMA_VERSION);
        }
        return version;
    }

    private boolean isChunkLoaded(Location location) {
        return world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private static boolean displayMatches(Display display, PlaceableModel.PartSpec spec) {
        return display != null && (spec.part().isText()
                ? display instanceof TextDisplay
                : display instanceof BlockDisplay);
    }

    private static void configureHitbox(Interaction interaction, PlaceableModel.HitboxSpec spec) {
        interaction.setInteractionWidth(spec.width());
        interaction.setInteractionHeight(spec.height());
        interaction.setResponsive(true);
        interaction.setPersistent(true);
    }

    private static void configureDisplay(Display display, PlaceableModel.PartSpec spec,
                                         PlaceableModel definition) {
        Part part = spec.part();
        if (display instanceof TextDisplay text) {
            text.text(net.kyori.adventure.text.Component.text(part.text == null ? "" : part.text));
            text.setBillboard(Display.Billboard.FIXED);
            text.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            text.setSeeThrough(false);
            text.setShadowed(false);
        } else if (display instanceof BlockDisplay block) {
            block.setBlock(part.material.createBlockData());
        } else {
            throw new IllegalArgumentException("unsupported display entity: " + display.getType());
        }
        definition.displayConfig().apply(display);
    }

    private static int indexOfPart(PlaceableModel definition, String id) {
        for (int i = 0; i < definition.parts().size(); i++) {
            if (definition.parts().get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfHitbox(PlaceableModel definition, String id) {
        for (int i = 0; i < definition.hitboxes().size(); i++) {
            if (definition.hitboxes().get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
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

    // ------------------------------------------------------------- accessors

    public final UUID id() {
        return id;
    }

    public final String moduleId() {
        return moduleId;
    }

    public final String typeId() {
        return typeId;
    }

    public final World world() {
        return world;
    }

    public final Location anchor() {
        return anchor.clone();
    }

    public final Location location() {
        return anchor();
    }

    public final double yaw() {
        return yaw;
    }

    public final double health() {
        return health;
    }

    public final Optional<UUID> owner() {
        return Optional.ofNullable(owner);
    }

    public final ArmorStand coreEntity() {
        return coreEntity;
    }

    public final Map<String, Interaction> hitboxes() {
        return Map.copyOf(hitboxes);
    }

    public final Map<String, Display> displays() {
        return Map.copyOf(displays);
    }

    public final boolean isActive() {
        return spawned && coreEntity != null && coreEntity.isValid();
    }

    public final boolean isSpawned() {
        return spawned;
    }

    record Snapshot(Location anchor, double yaw, UUID owner, double health, int schemaVersion) {
    }

    static final class UnsupportedSchemaException extends RuntimeException {

        private UnsupportedSchemaException(int persisted, int supported) {
            super("placeable schema " + persisted + " is newer than supported schema " + supported);
        }
    }
}
