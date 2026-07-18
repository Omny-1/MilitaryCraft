package me.bibo.militarycraft.core.vehicle;

import io.papermc.paper.entity.TeleportFlag;
import me.bibo.militarycraft.core.combat.Explosions;
import me.bibo.militarycraft.core.key.Keys;
import me.bibo.militarycraft.core.key.Pdc;
import me.bibo.militarycraft.core.model.DisplayConfig;
import me.bibo.militarycraft.core.model.ModelBuilder;
import me.bibo.militarycraft.core.model.Part;
import me.bibo.militarycraft.core.model.VehicleModel;
import me.bibo.militarycraft.core.util.Fx;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Shared vehicle lifecycle machinery (§5.1/§5.5), generalised from TankCraft's
 * {@code Tank}/KamazCraft's {@code Truck}/JetCraft's {@code Jet}. The base holds NO
 * articulation angles — each concrete vehicle owns its own angle fields (hull yaw,
 * turret yaw, barrel pitch, roll, ...) and supplies them to the abstract hooks below.
 * There is no concrete subclass yet (that's CP3); every method here is exercised for
 * the first time by whichever vehicle lands first.
 */
public abstract class DisplayVehicle implements VehicleHandle {

    protected final UUID id;
    protected final World world;
    /** x/y/z only; orientation is owned entirely by the subclass. Mutable in place (control code writes it directly). */
    protected final Location anchor;

    protected double health;
    protected UUID driver;

    protected ArmorStand seat;
    protected final List<Interaction> hitboxes = new ArrayList<>();
    protected final List<Display> displays = new ArrayList<>();
    protected final List<Part> partDefs = new ArrayList<>();
    /** Extra ridden seats beyond the driver's (e.g. a Pickup gunner); empty by default. */
    protected final List<ArmorStand> extraSeats = new ArrayList<>();

    private boolean spawned;
    private boolean stateDirty;
    private int persistCooldown;
    private long damageEffectTicks;

    /** Display config used at spawn time; reused by refreshModel for interpolation-duration. */
    private DisplayConfig displayConfig = DisplayConfig.STANDARD;

    // last-pushed anchor, for cheap dirty-checking in refreshModel
    private double lastX = Double.NaN, lastY, lastZ;

    protected DisplayVehicle(UUID id, World world, double x, double y, double z, double health) {
        this.id = Objects.requireNonNull(id, "id");
        this.world = Objects.requireNonNull(world, "world");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Vehicle anchor coordinates must be finite");
        }
        this.anchor = new Location(world, x, y, z);
        this.health = Double.isFinite(health) ? Math.max(0.0, health) : 0.0;
    }

    // ------------------------------------------------------------- abstract hooks (§5.5)

    public abstract String moduleId();

    /** Parts + dims (WIDTH/HEIGHT/LENGTH) + seatHeight + hitbox layout. */
    public abstract VehicleModel model();

    public abstract double maxHealth();

    /** Uses core Transforms + this vehicle's own current articulation angles. */
    public abstract Transformation transformFor(Part part);

    /** Articulated hitbox placement for {@code index} (0..hitboxZOffsets.length-1). */
    public abstract Location hitboxLocation(int index);

    /** Persist this vehicle's own angles/extra fields onto the seat. */
    public abstract void writeExtraState(PersistentDataContainer pdc);

    /** Restore this vehicle's own angles/extra fields from the seat. */
    public abstract void readExtraState(PersistentDataContainer pdc);

    // ------------------------------------------------------------- overridable hooks (sane defaults)

    protected void onSpawnEffects() {
    }

    /** Default: the standard internal explosion + smoke + sound (dedupe of every source plugin's destroy() fx). */
    protected void onDestroyEffects() {
        Location c = anchor.clone().add(0, model().height() / 2.0, 0);
        Explosions.createExplosion(world, c, 2.5f, false, false);
        Explosions.impactFx(c);
    }

    /**
     * Whether a part's transform needs recomputing this refresh. Default: always
     * {@code true} (recompute every part every call — refreshModel itself is only
     * invoked when something might have moved, so this is cheap by construction).
     * Tank-style vehicles override this to restore their own per-group skip
     * optimisation (HULL vs TURRET vs BARREL); that micro-opt is NOT forced here.
     */
    protected boolean partNeedsRetransform(Part part, boolean anchorMoved, boolean anythingChanged) {
        return true;
    }

    /**
     * Where seat {@code index} (0 = driver, using {@link #seatLocation()}; 1+ =
     * {@link #extraSeats}) sits in world space when the anchor moves. Default: only
     * the driver seat is repositioned (extra seats need vehicle-specific offsets);
     * a multi-seat vehicle (e.g. Pickup's gunner) overrides this for index &gt;= 1.
     */
    protected Location extraSeatLocation(int index) {
        return null;
    }

    /** Where the driver's seat sits in world space. Default: straight up by {@link #seatHeight()}. */
    protected Location seatLocation() {
        return anchor.clone().add(0, seatHeight(), 0);
    }

    /**
     * The yaw used for local-space hit tests (ray-trace/melee/projectile-sweep in
     * {@link VehicleManager}): hull yaw for ground vehicles, current heading for
     * aircraft. Default 0 (a vehicle with no meaningful "facing", e.g. a purely
     * vertical/stationary one). This hook generalises Tank's world&lt;-&gt;local hit-test
     * math without hardcoding hull-yaw-only semantics into the base.
     */
    public double facingYaw() {
        return 0.0;
    }

    public int seatCount() {
        return 1;
    }

    public double seatHeight() {
        return model().seatHeight();
    }

    // ------------------------------------------------------------- spawning (concrete/shared)

    /**
     * Spawns the seat, hitbox row and display parts from {@link #model()} and tags
     * every entity (§5.5). Call this from the subclass's own {@code create(...)}
     * static factory AFTER its angle fields are initialised (mirrors
     * {@code Tank.create}: construct, then spawn).
     */
    protected final void spawnCluster(ModelBuilder models) {
        try {
            this.seat = models.spawnSeat(seatLocation(), moduleId(), id);
            VehicleModel m = model();
            for (int i = 0; i < m.hitboxZOffsets().length; i++) {
                hitboxes.add(models.spawnHitbox(hitboxLocation(i), m.width(), m.height(), i, moduleId(), id));
            }
            List<Part> parts = m.parts();
            for (int i = 0; i < parts.size(); i++) {
                Part part = parts.get(i);
                Display d = part.isText()
                        ? models.spawnTextDisplay(anchor, part, i, moduleId(), id, displayConfig)
                        : models.spawnBlockDisplay(anchor, part, i, moduleId(), id, displayConfig);
                displays.add(d);
                partDefs.add(part);
            }
            spawned = true;
            onSpawnEffects();
            forceRefresh();
            persistState();
        } catch (RuntimeException ex) {
            removeEntities();
            throw ex;
        }
    }

    // ------------------------------------------------------------- rehydration (concrete/shared)
    //
    // Java can't let an abstract class "virtually construct" its own concrete
    // subclass, so unlike spawnCluster() this can't be one template method the base
    // fully drives. Recipe a concrete <X>.rehydrate(id, entities) follows (mirrors
    // Tank.rehydrate, using these shared pieces instead of copy-pasted grouping code):
    //   1. Groups g = DisplayVehicle.group(entities, <X>Model.parts(cfg).size(), HITBOX_COUNT);
    //   2. if (g.seat == null) return null;
    //   3. find the anchor hitbox (g.hitboxes[CENTER_INDEX], falling back to the first
    //      of g.strayHitboxes) -> its .getLocation() is the anchor; if null, return null.
    //   4. for (Display d : g.parts) if (d == null) return null; // incomplete model
    //   5. read health via DisplayVehicle.readHealth(g.seat.getPersistentDataContainer(), cfg.maxHealth)
    //   6. construct the <X> instance (id, world, anchor xyz, health)
    //   7. v.readExtraState(g.seat.getPersistentDataContainer()); // subclass's own angles
    //   8. v.adopt(g, parts, models);
    //   9. return v;

    /** Regrouped entities of a single vehicle, keyed by role/index PDC (§5.5). */
    public static final class Groups {
        public ArmorStand seat;
        public final List<Interaction> strayHitboxes = new ArrayList<>();
        public final List<Entity> discarded = new ArrayList<>();
        public Interaction[] hitboxes;
        public Display[] parts;
    }

    /**
     * Regroup one vehicle's entities by their {@code core_role}/{@code core_index}
     * PDC. {@code partCount}/{@code hitboxCount} come from the subclass's own static
     * model shape (known before an instance exists, e.g. {@code TankModel.parts(...)}).
     */
    public static Groups group(List<Entity> entities, int partCount, int hitboxCount) {
        Groups g = new Groups();
        g.hitboxes = new Interaction[hitboxCount];
        g.parts = new Display[partCount];
        for (Entity e : entities) {
            PersistentDataContainer pdc = e.getPersistentDataContainer();
            String role = Pdc.getString(pdc, Keys.of("core", "role"), null);
            if (role == null) {
                g.discarded.add(e);
                continue;
            }
            switch (role) {
                case "seat" -> {
                    if (e instanceof ArmorStand a) {
                        if (g.seat == null) {
                            g.seat = a;
                        } else {
                            g.discarded.add(a);
                        }
                    } else {
                        g.discarded.add(e);
                    }
                }
                case "hitbox" -> {
                    if (e instanceof Interaction i) {
                        g.strayHitboxes.add(i);
                        int idx = Pdc.getInt(pdc, Keys.of("core", "index"), -1);
                        if (idx >= 0 && idx < hitboxCount && g.hitboxes[idx] == null) {
                            g.hitboxes[idx] = i;
                        } else {
                            g.discarded.add(i);
                        }
                    } else {
                        g.discarded.add(e);
                    }
                }
                case "part" -> {
                    int idx = Pdc.getInt(pdc, Keys.of("core", "index"), -1);
                    if (e instanceof Display d && idx >= 0 && idx < partCount && g.parts[idx] == null) {
                        g.parts[idx] = d;
                    } else {
                        g.discarded.add(e);
                    }
                }
                default -> g.discarded.add(e);
            }
        }
        return g;
    }

    /**
     * Finish wiring a rehydrated instance from already-validated {@link Groups}
     * (the caller must already have checked {@code g.seat != null} and every
     * {@code g.parts[i] != null} — an incomplete part set means "return null,
     * caller cleans up the stray entities" and can't be recovered here). Rebuilds
     * the hitbox row if it was incomplete.
     */
    protected final void adopt(Groups g, List<Part> parts, ModelBuilder models) {
        this.seat = g.seat;
        for (Entity entity : g.discarded) {
            if (entity != null) {
                entity.remove();
            }
        }
        boolean completeHitboxes = true;
        for (Interaction box : g.hitboxes) {
            if (box == null) {
                completeHitboxes = false;
                break;
            }
        }
        if (completeHitboxes) {
            for (Interaction box : g.hitboxes) {
                hitboxes.add(box);
            }
        } else {
            for (Interaction stray : g.strayHitboxes) {
                if (stray != null) {
                    stray.remove();
                }
            }
            rebuildHitboxes(models);
        }
        for (Display d : g.parts) {
            displays.add(d);
        }
        partDefs.addAll(parts);
        spawned = true;
        forceRefresh();
    }

    private void rebuildHitboxes(ModelBuilder models) {
        hitboxes.clear();
        VehicleModel m = model();
        for (int i = 0; i < m.hitboxZOffsets().length; i++) {
            hitboxes.add(models.spawnHitbox(hitboxLocation(i), m.width(), m.height(), i, moduleId(), id));
        }
    }

    // ------------------------------------------------------------- persistence

    public final void persistState() {
        if (seat == null || !seat.isValid()) {
            return;
        }
        PersistentDataContainer pdc = seat.getPersistentDataContainer();
        Pdc.setDouble(pdc, Keys.of("core", "health"), health);
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

    /** Reads the health this vehicle was persisted with (fallback if never persisted). Call from a subclass's static rehydrate. */
    public static double readHealth(PersistentDataContainer pdc, double fallback) {
        double maximum = Double.isFinite(fallback) ? Math.max(0.0, fallback) : 0.0;
        double persisted = Pdc.getDouble(pdc, Keys.of("core", "health"), maximum);
        if (!Double.isFinite(persisted)) {
            return maximum;
        }
        return Math.max(0.0, Math.min(maximum, persisted));
    }

    // ------------------------------------------------------------- rendering

    private void markClean() {
        lastX = anchor.getX();
        lastY = anchor.getY();
        lastZ = anchor.getZ();
    }

    protected final void forceRefresh() {
        lastX = Double.NaN;
        refreshModel();
    }

    /**
     * Push only what changed: teleport the entities when the anchor moved, and
     * recompute every part's transform via {@link #partNeedsRetransform} (default:
     * always). Bakes in the smoothness gotcha: {@code setInterpolationDelay(0)} +
     * {@code setInterpolationDuration} (matching the spawn-time teleport duration)
     * on every display that gets a new transform.
     */
    public void refreshModel() {
        if (!spawned || seat == null) {
            return;
        }
        boolean first = Double.isNaN(lastX);
        boolean moved = first
                || Math.abs(lastX - anchor.getX()) > 1e-5
                || Math.abs(lastY - anchor.getY()) > 1e-5
                || Math.abs(lastZ - anchor.getZ()) > 1e-5;

        Location base = new Location(world, anchor.getX(), anchor.getY(), anchor.getZ());
        if (moved) {
            seat.teleport(seatLocation(), TeleportFlag.EntityState.RETAIN_PASSENGERS);
            for (int i = 0; i < extraSeats.size(); i++) {
                ArmorStand extra = extraSeats.get(i);
                if (extra == null || !extra.isValid()) {
                    continue;
                }
                Location loc = extraSeatLocation(i + 1);
                if (loc != null) {
                    extra.teleport(loc, TeleportFlag.EntityState.RETAIN_PASSENGERS);
                }
            }
        }
        for (int i = 0; i < hitboxes.size(); i++) {
            Interaction box = hitboxes.get(i);
            if (box != null && box.isValid()) {
                box.teleport(hitboxLocation(i));
            }
        }
        for (int i = 0; i < displays.size(); i++) {
            Display d = displays.get(i);
            if (d == null || !d.isValid()) {
                continue;
            }
            if (moved) {
                d.teleport(base);
            }
            Part part = partDefs.get(i);
            if (partNeedsRetransform(part, moved, true)) {
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(displayConfig.interpolationDuration());
                d.setTransformation(transformFor(part));
            }
        }
        markClean();
    }

    // ------------------------------------------------------------- damage / destruction

    /** @return true if this hit destroyed the vehicle. */
    public boolean damage(double amount) {
        if (!Double.isFinite(amount) || amount <= 0 || !isActive()) {
            return false;
        }
        health = Math.max(0.0, health - amount);
        markStateDirty();
        if (health <= 0) {
            destroy(true);
            return true;
        }
        Fx.burst(anchor.clone().add(0, model().height() * 0.5, 0), Particle.CRIT, 8, 0.8, 0.6, 0.8, 0.0);
        return false;
    }

    @Override
    public double repair(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0 || !isActive() || maxHealth() <= 0.0) {
            return 0.0;
        }
        double before = health;
        health = Math.min(maxHealth(), health + amount);
        double restored = health - before;
        if (restored <= 0.0) {
            return 0.0;
        }
        markStateDirty();
        Location at = anchor.clone().add(0.0, model().height() * 0.55, 0.0);
        Fx.burst(at, Particle.WAX_ON, 10, 0.7, 0.45, 0.7, 0.02);
        Fx.sound(at, Sound.BLOCK_ANVIL_USE, 0.55f, 1.65f);
        return restored;
    }

    /**
     * Flat 1-creeper hit, no distance falloff, no knockback, no block break (§6) —
     * the unit is {@link #creeperDamageUnit()}. The per-vehicle hook honours each source
     * plugin's own tunable creeper-damage/creepers-to-destroy ratio. Default here is
     * {@code maxHealth() / 4.0} (TankCraft's own default ratio); a concrete vehicle
     * with its own config value overrides {@link #creeperDamageUnit()}.
     */
    @Override
    public final void applyAntiAirHit() {
        if (!isActive()) {
            return;
        }
        damage(creeperDamageUnit());
    }

    /** Distance-falloff blast damage, generalised from Tank/Kamaz's {@code applyBlastTo}. */
    @Override
    public void applyExplosion(Location loc, double power) {
        if (loc == null || !Double.isFinite(power) || power <= 0 || !isActive() || world != loc.getWorld()) {
            return;
        }
        Location centre = anchor.clone().add(0, model().height() / 2.0, 0);
        double dist = centre.distance(loc);
        double contact = 2.0;
        double radius = power * 2.0 + contact;
        if (dist > radius) {
            return;
        }
        double falloff = dist <= contact ? 1.0 : Math.max(0.0, 1.0 - (dist - contact) / (radius - contact));
        double dmg = creeperDamageUnit() * (power / 3.0) * falloff;
        if (dmg > 0) {
            damage(dmg);
        }
    }

    /** The HP one "creeper hit" represents for this vehicle (see {@link #applyAntiAirHit}). */
    protected double creeperDamageUnit() {
        return maxHealth() / 4.0;
    }

    /** Low-health smoke/spark/sound, generalised from Tank's {@code tickDamageEffects}. Call once per tick per active vehicle. */
    public void tickDamageEffects() {
        if (!isActive() || maxHealth() <= 0) {
            return;
        }
        damageEffectTicks++;
        double ratio = health / maxHealth();
        if (ratio > 0.5) {
            return;
        }
        int period = ratio > 0.25 ? 30 : 12;
        if (damageEffectTicks % period != 0) {
            return;
        }
        Location vent = anchor.clone().add(0, model().height() * 0.72, 0);
        Fx.burst(vent, Particle.LARGE_SMOKE, ratio > 0.25 ? 2 : 4, 0.45, 0.25, 0.45, 0.015);
        if (ratio <= 0.25) {
            Fx.burst(vent, Particle.SMOKE, 5, 0.5, 0.25, 0.5, 0.02);
            Fx.burst(vent, Particle.ELECTRIC_SPARK, 2, 0.3, 0.2, 0.3, 0.01);
        }
        if (ratio <= 0.1 && damageEffectTicks % 60 == 0) {
            Fx.sound(vent, Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1.8f);
        }
    }

    public final void destroy(boolean effects) {
        if (!spawned) {
            return;
        }
        spawned = false;
        if (effects) {
            onDestroyEffects();
        }
        removeEntities();
    }

    /** Delete every entity belonging to this vehicle (does not touch any registry). */
    public void removeEntities() {
        if (seat != null) {
            if (!seat.getPassengers().isEmpty()) {
                seat.eject();
            }
            seat.remove();
        }
        for (ArmorStand s : extraSeats) {
            if (s != null) {
                if (!s.getPassengers().isEmpty()) {
                    s.eject();
                }
                s.remove();
            }
        }
        extraSeats.clear();
        for (Interaction box : hitboxes) {
            if (box != null) {
                box.remove();
            }
        }
        hitboxes.clear();
        for (Display d : displays) {
            if (d != null) {
                d.remove();
            }
        }
        displays.clear();
        partDefs.clear();
        spawned = false;
    }

    // ------------------------------------------------------------- riding

    public boolean isOccupied() {
        return driver != null;
    }

    public boolean mount(Player player) {
        if (player == null || !isActive() || driver != null || player.isInsideVehicle()
                || !seat.getPassengers().isEmpty() || !seat.addPassenger(player)) {
            return false;
        }
        driver = player.getUniqueId();
        return true;
    }

    public void clearDriver() {
        driver = null;
        persistState();
    }

    public void eject() {
        if (seat != null) {
            seat.eject();
        }
        driver = null;
    }

    public UUID driver() {
        return driver;
    }

    // ------------------------------------------------------------- VehicleHandle

    @Override
    public final UUID id() {
        return id;
    }

    @Override
    public final String type() {
        return moduleId();
    }

    @Override
    public final Entity coreEntity() {
        return seat;
    }

    @Override
    public final Location location() {
        return anchor.clone();
    }

    @Override
    public boolean isActive() {
        return spawned && seat != null && seat.isValid();
    }

    public boolean isSpawned() {
        return spawned;
    }

    // ------------------------------------------------------------- getters

    public World world() {
        return world;
    }

    public Location anchor() {
        return anchor;
    }

    public ArmorStand seat() {
        return seat;
    }

    public List<Interaction> hitboxes() {
        return hitboxes;
    }

    @Override
    public List<Interaction> collisionEntities() {
        return List.copyOf(hitboxes);
    }

    public double health() {
        return health;
    }
}
