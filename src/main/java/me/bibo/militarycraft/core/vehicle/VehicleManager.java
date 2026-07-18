package me.bibo.militarycraft.core.vehicle;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.combat.Projectiles;
import me.bibo.militarycraft.core.event.DamageSink;
import me.bibo.militarycraft.core.event.EntityLifecycleSink;
import me.bibo.militarycraft.core.key.EntityTag;
import me.bibo.militarycraft.core.key.Keys;
import me.bibo.militarycraft.core.key.Pdc;
import me.bibo.militarycraft.core.model.VehicleModel;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Registry + per-tick loop + adopt/forget + pilot protection/cloak + ray/melee/sweep,
 * generalised from TankCraft's {@code TankManager} (§5.2). Registers itself with
 * {@link VehicleService} and the {@code EventBus} on {@link #attach}.
 *
 * <p>A concrete manager supplies {@link #create}, {@link #rehydrate}, {@link #moduleId}
 * and {@link #driveTick} (its own {@code <X>Controller.drive(...)} call using its own
 * config type — kept out of this generic base entirely).
 */
public abstract class VehicleManager<V extends DisplayVehicle> implements EntityLifecycleSink, DamageSink {

    protected Core core;
    protected final Map<UUID, V> registry = new LinkedHashMap<>();
    protected final Map<UUID, UUID> driverToVehicle = new HashMap<>();
    private BukkitTask task;
    private boolean attached;
    private long tickCounter;
    private final Set<UUID> cloaked = new HashSet<>();
    private final Map<UUID, Boolean> previousInvisible = new HashMap<>();

    // ------------------------------------------------------------- abstract hooks

    public abstract String moduleId();

    public abstract V create(Location at, double yaw);

    /** @return null if the entity group is incomplete/mismatched (caller removes the stray entities). */
    public abstract V rehydrate(UUID id, List<Entity> entities);

    /** Drive {@code vehicle} for its seated {@code driver} this tick (delegates to the vehicle's own {@code <X>Controller}). */
    protected abstract void driveTick(V vehicle, Player driver);

    /**
     * Per-vehicle work that runs every tick for each ACTIVE vehicle regardless of a
     * driver — water/drown, projectile-sweep, reload/weapon-lock/overheat countdown,
     * etc. (source Tank/Kamaz do these whether occupied or not). Default: no-op. The
     * vehicle may self-destruct here (e.g. drown), so the loop re-checks {@code isActive()}
     * after it.
     */
    protected void onVehicleTick(V vehicle) {
    }

    /**
     * Convert an attack on the seated driver into vehicle HP loss. Default: none —
     * the driver still takes 0 damage (see {@link PilotProtection}) but nothing is
     * transferred to the vehicle either. CP3 vehicles override this to apply their
     * own weapon percentages (fireball/arrow/melee) + cooldowns, mirroring
     * TankCraft's {@code DamageListener}.
     */
    protected void onDriverAttacked(V vehicle, Player driver, EntityDamageByEntityEvent event) {
    }

    protected double vehicleEntityMeleePercent() {
        return 4.0;
    }

    protected double vehicleEntityArrowPercent() {
        return 6.0;
    }

    protected double vehicleEntityFireballPercent() {
        return 12.0;
    }

    protected boolean isVehicleCrew(V vehicle, Player player) {
        return player != null && vehicle.driver() != null && player.getUniqueId().equals(vehicle.driver());
    }

    // ------------------------------------------------------------- lifecycle

    /** Registers this manager with {@link VehicleService} and the shared {@code EventBus} (§5.2). */
    public final void attach(Core core) {
        if (attached) {
            if (this.core != core) {
                throw new IllegalStateException("Vehicle manager is already attached to another core");
            }
            return;
        }
        this.core = core;
        core.events().register(this);
        core.vehicles().registerManager(this);
        attached = true;
    }

    public void start() {
        if (task != null) {
            return;
        }
        if (!attached) {
            throw new IllegalStateException("Vehicle manager must be attached before it is started");
        }
        task = core.scheduler().runTaskTimer(core.plugin(), this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** On enable: adopt any of this module's vehicle entities already present in loaded chunks. */
    public void adoptExisting() {
        for (World world : Bukkit.getWorlds()) {
            onEntitiesLoad(world.getEntities());
        }
    }

    /** On disable: eject drivers and persist state, but keep the entities. */
    public void shutdown() {
        stop();
        try {
            for (V v : new ArrayList<>(registry.values())) {
                try {
                    UUID d = v.driver();
                    if (d != null) {
                        Player p = Bukkit.getPlayer(d);
                        if (p != null) {
                            restoreDriver(p);
                        }
                    }
                    v.eject();
                    v.persistState();
                } catch (RuntimeException ex) {
                    core.logger().log(Level.WARNING,
                            "Could not persist " + moduleId() + " vehicle " + v.id() + " during shutdown", ex);
                }
            }
        } finally {
            registry.clear();
            driverToVehicle.clear();
            cloaked.clear();
            previousInvisible.clear();
            if (attached) {
                core.events().unregister(this);
                core.vehicles().unregisterManager(this);
                attached = false;
            }
        }
    }

    private void tick() {
        tickCounter++;
        VehicleBlockTriggers.releaseExpired();
        Iterator<V> it = registry.values().iterator();
        while (it.hasNext()) {
            V v = it.next();
            if (!v.isActive()) {
                UUID d = v.driver();
                if (d != null) {
                    driverToVehicle.remove(d);
                    Player p = Bukkit.getPlayer(d);
                    if (p != null) {
                        restoreDriver(p);
                    }
                }
                v.removeEntities();
                it.remove();
                continue;
            }
            try {
                onVehicleTick(v);
            } catch (Exception ex) {
                core.logger().log(Level.WARNING, "Vehicle tick failed for " + moduleId(), ex);
            }
            if (!v.isActive()) {
                continue;
            }
            UUID driverId = v.driver();
            if (driverId != null) {
                Player driver = Bukkit.getPlayer(driverId);
                if (driver == null || !driver.isOnline()
                        || driver.getVehicle() == null || !driver.getVehicle().equals(v.seat())) {
                    if (driver != null) {
                        restoreDriver(driver);
                    }
                    v.clearDriver();
                    driverToVehicle.remove(driverId);
                } else {
                    try {
                        driveTick(v, driver);
                    } catch (Exception ex) {
                        core.logger().log(Level.WARNING, "Drive tick failed for " + moduleId(), ex);
                    }
                    if (!v.isActive()) {
                        continue;
                    }
                }
            }
            VehicleBlockTriggers.touch(v);
            v.tickDamageEffects();
            v.tickPersist();
        }
        reconcileCloak();
    }

    private void reconcileCloak() {
        Set<UUID> current = new HashSet<>();
        for (V v : registry.values()) {
            if (v.driver() != null) {
                current.add(v.driver());
            }
        }
        PilotProtection.reconcileCloak(cloaked, current, tickCounter % 100 == 0);
    }

    // ------------------------------------------------------------- chunk (de)hydration

    @Override
    public final void onEntitiesLoad(EntitiesLoadEvent event) {
        onEntitiesLoad(event.getEntities());
    }

    @Override
    public final void onEntitiesUnload(EntitiesUnloadEvent event) {
        onEntitiesUnload(event.getEntities());
    }

    public void onEntitiesLoad(Collection<Entity> entities) {
        Map<UUID, List<Entity>> groups = new HashMap<>();
        for (Entity e : entities) {
            if (!moduleId().equals(EntityTag.moduleOf(e))) {
                continue;
            }
            String idStr = Pdc.getString(e.getPersistentDataContainer(), Keys.of(moduleId(), "id"), null);
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
            if (registry.containsKey(id)) {
                continue; // already tracked
            }
            groups.computeIfAbsent(id, k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<UUID, List<Entity>> entry : groups.entrySet()) {
            V v;
            try {
                v = rehydrate(entry.getKey(), entry.getValue());
            } catch (RuntimeException ex) {
                core.logger().log(Level.WARNING,
                        "Could not rehydrate " + moduleId() + " vehicle " + entry.getKey(), ex);
                for (Entity entity : entry.getValue()) {
                    entity.remove();
                }
                continue;
            }
            if (v != null) {
                registry.put(entry.getKey(), v);
            } else {
                for (Entity e : entry.getValue()) {
                    e.remove(); // incomplete/garbage group
                }
            }
        }
    }

    public void onEntitiesUnload(Collection<Entity> entities) {
        for (Entity e : entities) {
            String idStr = Pdc.getString(e.getPersistentDataContainer(), Keys.of(moduleId(), "id"), null);
            if (idStr == null) {
                continue;
            }
            V v;
            try {
                v = registry.get(UUID.fromString(idStr));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (v == null) {
                continue;
            }
            UUID d = v.driver();
            if (d != null) {
                Player p = Bukkit.getPlayer(d);
                if (p != null) {
                    restoreDriver(p);
                }
                v.eject();
            }
            v.persistState();
            forget(v);
        }
    }

    // ------------------------------------------------------------- registry

    public V spawn(Location at, double yaw) {
        V v = create(at, yaw);
        registry.put(v.id(), v);
        return v;
    }

    public V byId(UUID id) {
        return registry.get(id);
    }

    public V byDriver(UUID playerId) {
        UUID vid = driverToVehicle.get(playerId);
        return vid == null ? null : registry.get(vid);
    }

    public V byEntity(Entity entity) {
        if (entity == null) {
            return null;
        }
        String id = Pdc.getString(entity.getPersistentDataContainer(), Keys.of(moduleId(), "id"), null);
        if (id == null) {
            return null;
        }
        try {
            return registry.get(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public Collection<V> all() {
        return List.copyOf(registry.values());
    }

    public int count() {
        return registry.size();
    }

    // ------------------------------------------------------------- riding

    public boolean enter(V vehicle, Player player) {
        if (vehicle == null || player == null || !vehicle.isActive() || vehicle.isOccupied()
                || player.isInsideVehicle() || core.vehicles().riddenBy(player) != null
                || byDriver(player.getUniqueId()) != null) {
            return false;
        }
        if (!vehicle.mount(player)) {
            return false;
        }
        driverToVehicle.put(player.getUniqueId(), vehicle.id());
        previousInvisible.put(player.getUniqueId(), player.isInvisible());
        PilotProtection.rememberVisibility(player);
        player.setInvisible(true);
        cloaked.add(player.getUniqueId());
        PilotCloak.hide(player);
        return true;
    }

    public void handleDismount(Player player) {
        UUID vid = driverToVehicle.remove(player.getUniqueId());
        if (vid != null) {
            restoreDriver(player);
            V v = registry.get(vid);
            if (v != null) {
                v.clearDriver();
            }
        }
    }

    private void restoreDriver(Player player) {
        Boolean wasInvisible = previousInvisible.remove(player.getUniqueId());
        PilotProtection.restoreVisibility(player, wasInvisible);
        PilotCloak.show(player);
        cloaked.remove(player.getUniqueId());
    }

    // ------------------------------------------------------------- pilot protection

    @Override
    public void onEntityDamage(EntityDamageEvent event) {
        Entity ent = event.getEntity();
        if (ent instanceof Player player) {
            V seated = byDriver(player.getUniqueId());
            if (seated == null) {
                return;
            }
            EntityDamageByEntityEvent ebe = PilotProtection.protect(event);
            if (ebe != null) {
                onDriverAttacked(seated, player, ebe);
            }
            return;
        }
        handleVehicleEntityDamage(event);
    }

    protected final boolean handleVehicleEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        V vehicle = byEntity(entity);
        if (vehicle == null) {
            if (moduleId().equals(EntityTag.moduleOf(entity))) {
                event.setCancelled(true);
                return true;
            }
            return false;
        }
        event.setCancelled(true); // shield raw entities from vanilla damage, then route it to vehicle HP.
        double damage = vehicleEntityDamage(vehicle, event);
        if (damage <= 0.0) {
            return true;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && shouldIgnoreVehicleDamager(vehicle, byEntity.getDamager())) {
            return true;
        }
        vehicle.damage(damage);
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Projectile projectile
                && projectile.isValid()) {
            projectile.remove();
        }
        return true;
    }

    protected double vehicleEntityDamage(V vehicle, EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return 0.0;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Fireball) {
                return percentDamage(vehicle, vehicleEntityFireballPercent());
            }
            if (damager instanceof AbstractArrow || damager instanceof Firework) {
                return percentDamage(vehicle, vehicleEntityArrowPercent());
            }
            if (damager instanceof Snowball || damager instanceof Egg) {
                return Math.min(2.0, Math.max(0.5, percentDamage(vehicle, 0.5)));
            }
            if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                    || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
                return percentDamage(vehicle, vehicleEntityMeleePercent());
            }
        }
        return Math.max(0.0, event.getDamage());
    }

    private double percentDamage(V vehicle, double percent) {
        if (!Double.isFinite(percent) || percent <= 0.0 || vehicle.maxHealth() <= 0.0) {
            return 0.0;
        }
        return vehicle.maxHealth() * percent / 100.0;
    }

    private boolean shouldIgnoreVehicleDamager(V vehicle, Entity damager) {
        Player attacker = null;
        if (damager instanceof Player player) {
            attacker = player;
        } else if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                attacker = player;
            }
        }
        return attacker != null && isVehicleCrew(vehicle, attacker);
    }

    // ------------------------------------------------------------- removal

    public void remove(V vehicle, boolean effects) {
        UUID d = vehicle.driver();
        if (d != null) {
            Player p = Bukkit.getPlayer(d);
            if (p != null) {
                restoreDriver(p);
            }
        }
        if (effects) {
            vehicle.destroy(true);
        } else {
            vehicle.eject();
            vehicle.removeEntities();
        }
        forget(vehicle);
    }

    private void forget(V vehicle) {
        registry.remove(vehicle.id());
        driverToVehicle.values().removeIf(id -> id.equals(vehicle.id()));
    }

    /** @return {@code [trackedRemoved, strayEntitiesSwept]}. */
    public int[] purgeAll() {
        int trackedCount = registry.size();
        for (V v : new ArrayList<>(registry.values())) {
            UUID d = v.driver();
            if (d != null) {
                Player p = Bukkit.getPlayer(d);
                if (p != null) {
                    restoreDriver(p);
                }
            }
            v.eject();
            v.removeEntities();
        }
        registry.clear();
        driverToVehicle.clear();
        cloaked.clear();
        previousInvisible.clear();

        int strays = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (moduleId().equals(EntityTag.moduleOf(e))) {
                    e.remove();
                    strays++;
                }
            }
        }
        return new int[]{trackedCount, strays};
    }

    // ------------------------------------------------------------- ray/melee/sweep (generalised from TankManager)

    public V rayTraceFrom(Location eye, double reach) {
        World world = eye.getWorld();
        if (world == null) {
            return null;
        }
        Vector dir = eye.getDirection();
        V best = null;
        double bestDist = reach;
        for (V v : registry.values()) {
            if (!v.isActive() || v.world() != world) {
                continue;
            }
            RayHit hit = rayTraceBody(v, eye.toVector(), dir, reach, 0.35);
            if (hit == null || hit.distance() >= bestDist) {
                continue;
            }
            if (world.rayTraceBlocks(eye, dir, Math.max(0.1, hit.distance() - 0.05),
                    FluidCollisionMode.NEVER, true) != null) {
                continue;
            }
            bestDist = hit.distance();
            best = v;
        }
        return best;
    }

    /** One melee target for {@code attacker}'s swing, or null. Caller applies its own cooldown/damage/fx. */
    public MeleeHit findMeleeTarget(Player attacker, double reach, double pad) {
        World w = attacker.getWorld();
        Location eye = attacker.getEyeLocation();
        Vector dir = eye.getDirection();
        V best = null;
        double bestDist = reach;
        Vector hitAt = null;
        for (V v : registry.values()) {
            if (!v.isActive() || v.world() != w) {
                continue;
            }
            RayHit r = rayTraceBody(v, eye.toVector(), dir, reach, pad);
            if (r == null || r.distance() >= bestDist) {
                continue;
            }
            bestDist = r.distance();
            best = v;
            hitAt = r.position();
        }
        if (best == null) {
            return null;
        }
        if (w.rayTraceBlocks(eye, dir, Math.max(0.1, bestDist - 0.05), FluidCollisionMode.NEVER, true) != null) {
            return null;
        }
        return new MeleeHit(best, new Location(w, hitAt.getX(), hitAt.getY(), hitAt.getZ()), bestDist);
    }

    /** Weapon projectiles currently inside {@code vehicle}'s body box. Caller removes/damages/fx's them. */
    public List<Projectile> projectilesInBody(V vehicle, double pad) {
        List<Projectile> out = new ArrayList<>();
        for (Entity e : vehicle.world().getNearbyEntities(bodySearchBox(vehicle, pad))) {
            if (e instanceof Projectile proj && Projectiles.isWeaponProjectile(proj)
                    && insideBody(vehicle, proj.getLocation().toVector(), pad)) {
                out.add(proj);
            }
        }
        return out;
    }

    private RayHit rayTraceBody(V v, Vector origin, Vector direction, double reach, double pad) {
        VehicleModel m = v.model();
        BoundingBox localBox = new BoundingBox(
                -m.width() / 2.0 - pad, -pad, -m.length() / 2.0 - pad,
                m.width() / 2.0 + pad, m.height() + pad, m.length() / 2.0 + pad);
        RayTraceResult r = localBox.rayTrace(worldToLocal(v, origin), worldDirToLocal(v, direction), reach);
        if (r == null) {
            return null;
        }
        Vector world = localToWorld(v, r.getHitPosition());
        return new RayHit(world, origin.distance(world));
    }

    private boolean insideBody(V v, Vector world, double pad) {
        VehicleModel m = v.model();
        Vector local = worldToLocal(v, world);
        return local.getX() >= -m.width() / 2.0 - pad && local.getX() <= m.width() / 2.0 + pad
                && local.getY() >= -pad && local.getY() <= m.height() + pad
                && local.getZ() >= -m.length() / 2.0 - pad && local.getZ() <= m.length() / 2.0 + pad;
    }

    private BoundingBox bodySearchBox(V v, double pad) {
        VehicleModel m = v.model();
        Location a = v.anchor();
        double halfWidth = m.width() / 2.0 + pad;
        double halfLength = m.length() / 2.0 + pad;
        double radius = Math.sqrt(halfWidth * halfWidth + halfLength * halfLength);
        return new BoundingBox(a.getX() - radius, a.getY() - pad, a.getZ() - radius,
                a.getX() + radius, a.getY() + m.height() + pad, a.getZ() + radius);
    }

    private Vector worldToLocal(V v, Vector world) {
        Location a = v.anchor();
        double dx = world.getX() - a.getX();
        double dz = world.getZ() - a.getZ();
        double yaw = Math.toRadians(v.facingYaw());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vector(dx * cos + dz * sin, world.getY() - a.getY(), -dx * sin + dz * cos);
    }

    private Vector worldDirToLocal(V v, Vector dir) {
        double yaw = Math.toRadians(v.facingYaw());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vector(dir.getX() * cos + dir.getZ() * sin, dir.getY(), -dir.getX() * sin + dir.getZ() * cos);
    }

    private Vector localToWorld(V v, Vector local) {
        Location a = v.anchor();
        double yaw = Math.toRadians(v.facingYaw());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vector(
                a.getX() + local.getX() * cos - local.getZ() * sin,
                a.getY() + local.getY(),
                a.getZ() + local.getX() * sin + local.getZ() * cos);
    }

    private record RayHit(Vector position, double distance) {
    }

    public record MeleeHit(DisplayVehicle vehicle, Location point, double distance) {
    }
}
