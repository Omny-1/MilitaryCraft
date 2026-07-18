package me.bibo.militarycraft.weapons.antiair.turret;

import me.bibo.militarycraft.weapons.antiair.AntiAirRuntime;
import me.bibo.militarycraft.weapons.antiair.config.AntiAirConfig;
import me.bibo.militarycraft.weapons.antiair.fuel.FuelTable;
import me.bibo.militarycraft.weapons.antiair.items.TurretItem;
import me.bibo.militarycraft.weapons.antiair.model.Part;
import me.bibo.militarycraft.weapons.antiair.model.Transforms;
import me.bibo.militarycraft.weapons.antiair.model.TurretModel;
import me.bibo.militarycraft.weapons.antiair.util.Keys;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A single anti-air turret: an invisible armor-stand core (the position anchor +
 * the "invisible carrier block"), one Interaction hitbox for clicking/damage, and
 * a set of block-display model parts forming the CIWS. Full state (mode, health,
 * aim, fuel) is mirrored onto the core's persistent data so the turret survives
 * chunk reloads and restarts.
 */
public final class Turret {

    private final AntiAirRuntime plugin;
    private final UUID id;
    private final World world;
    private final Location anchor; // base centre, on the ground

    private double yaw;          // current traverse, degrees
    private double pitch;        // current elevation, degrees (negative = barrels up)
    private double targetYaw;    // desired traverse
    private double targetPitch;  // desired elevation
    private double health;
    private Mode mode = Mode.NORMIES_PHANTOMS;
    private UUID owner;

    // fuel (furnace-like): a lit charge (burnTime ticks) plus a reserve stack.
    private int burnTime;
    private int burnTimeTotal;
    private Material fuelType;
    private int fuelCount;

    // combat pacing
    private int fireCooldown;
    private int rocketCooldown; // anti-vehicle rocket cadence (slower than bullets)
    private UUID currentTarget; // cached acquired target (entity uuid)
    private double heat;        // 0..1; rises while firing, always cools
    private boolean overheated; // locked out until cooled to the resume threshold
    private int spinUp;         // ticks the barrels have been winding up

    private ArmorStand core;
    private Interaction hitbox;
    private final List<Display> displays = new ArrayList<>();
    private final List<Part> partDefs = new ArrayList<>();
    private boolean spawned;

    // last-pushed angles, for cheap dirty checking
    private double lastYaw = Double.NaN, lastPitch;
    private boolean firstPush = true;

    private int persistThrottle;

    private Turret(AntiAirRuntime plugin, UUID id, World world, double x, double y, double z,
                   double yaw, double pitch, double health) {
        this.plugin = plugin;
        this.id = id;
        this.world = world;
        this.anchor = new Location(world, x, y, z);
        this.yaw = yaw;
        this.targetYaw = yaw;
        this.pitch = pitch;
        this.targetPitch = pitch;
        this.health = health;
    }

    // ----------------------------------------------------------------- factories

    public static Turret create(AntiAirRuntime plugin, Location at, double yaw, UUID owner) {
        AntiAirConfig cfg = plugin.config();
        Turret t = new Turret(plugin, UUID.randomUUID(), at.getWorld(),
                at.getX(), at.getY(), at.getZ(), yaw, -cfg.restPitchDeg, cfg.maxHealth);
        t.owner = owner;
        t.spawnEntities();
        return t;
    }

    /** Rebuild a turret from its existing (persistent) entities found in a chunk. */
    public static Turret rehydrate(AntiAirRuntime plugin, UUID id, List<Entity> entities) {
        AntiAirConfig cfg = plugin.config();
        List<Part> parts = TurretModel.parts(cfg.rounded);

        ArmorStand core = null;
        Interaction hitbox = null;
        Map<Integer, Display> partByIndex = new HashMap<>();
        List<Display> allParts = new ArrayList<>();

        for (Entity e : entities) {
            String role = e.getPersistentDataContainer().get(Keys.TURRET_PART, PersistentDataType.STRING);
            if (role == null) {
                continue;
            }
            Integer idx = e.getPersistentDataContainer().get(Keys.PART_INDEX, PersistentDataType.INTEGER);
            switch (role) {
                case "core" -> {
                    if (e instanceof ArmorStand a) core = a;
                }
                case "hitbox" -> {
                    if (e instanceof Interaction in) hitbox = in;
                }
                case "part" -> {
                    if (e instanceof Display d) {
                        allParts.add(d);
                        if (idx != null && idx >= 0) partByIndex.putIfAbsent(idx, d);
                    }
                }
                default -> {
                }
            }
        }
        if (core == null) {
            return null; // anchor gone — drop the group
        }

        PersistentDataContainer pdc = core.getPersistentDataContainer();
        double yaw = pdc.getOrDefault(Keys.STATE_YAW, PersistentDataType.DOUBLE, 0.0);
        double pitch = pdc.getOrDefault(Keys.STATE_PITCH, PersistentDataType.DOUBLE, -cfg.restPitchDeg);
        double hp = pdc.getOrDefault(Keys.STATE_HEALTH, PersistentDataType.DOUBLE, cfg.maxHealth);
        Location loc = core.getLocation();

        Turret t = new Turret(plugin, id, core.getWorld(), loc.getX(), loc.getY(), loc.getZ(), yaw, pitch, hp);
        t.core = core;
        t.spawned = true;
        t.mode = Mode.fromString(pdc.get(Keys.STATE_MODE, PersistentDataType.STRING), Mode.NORMIES_PHANTOMS);
        t.burnTime = pdc.getOrDefault(Keys.STATE_BURN, PersistentDataType.INTEGER, 0);
        t.burnTimeTotal = pdc.getOrDefault(Keys.STATE_BURN_TOTAL, PersistentDataType.INTEGER, 0);
        t.fuelCount = pdc.getOrDefault(Keys.STATE_FUEL_COUNT, PersistentDataType.INTEGER, 0);
        String fuelName = pdc.get(Keys.STATE_FUEL_TYPE, PersistentDataType.STRING);
        if (fuelName != null) {
            Material m = Material.matchMaterial(fuelName);
            if (m != null && FuelTable.isFuel(m) && t.fuelCount > 0) {
                t.fuelType = m;
            } else {
                t.fuelCount = 0;
            }
        }
        String ownerStr = pdc.get(Keys.OWNER, PersistentDataType.STRING);
        if (ownerStr != null) {
            try {
                t.owner = UUID.fromString(ownerStr);
            } catch (IllegalArgumentException ignored) {
            }
        }

        // model displays: adopt if exactly matching, else rebuild
        boolean complete = allParts.size() == parts.size() && partByIndex.size() == parts.size();
        for (int i = 0; complete && i < parts.size(); i++) {
            if (partByIndex.get(i) == null) {
                complete = false;
            }
        }
        if (complete) {
            for (int i = 0; i < parts.size(); i++) {
                t.displays.add(partByIndex.get(i));
                t.partDefs.add(parts.get(i));
            }
        } else {
            for (Display d : allParts) {
                if (d != null) d.remove();
            }
            t.buildDisplays(loc);
        }

        if (hitbox == null) {
            t.buildHitbox(loc);
        } else {
            t.hitbox = hitbox;
        }

        t.forceRefresh();
        return t;
    }

    // ----------------------------------------------------------------- spawning

    private void spawnEntities() {
        Location base = anchor.clone();
        core = world.spawn(base, ArmorStand.class, a -> {
            a.setInvisible(true);
            a.setGravity(false);
            a.setMarker(true);
            a.setSmall(true);
            a.setBasePlate(false);
            a.setArms(false);
            a.setSilent(true);
            a.setInvulnerable(true);
            a.setCollidable(false);
            a.setPersistent(true);
            tagEntity(a, "core", -1);
        });
        if (owner != null) {
            core.getPersistentDataContainer().set(Keys.OWNER, PersistentDataType.STRING, owner.toString());
        }
        buildHitbox(base);
        buildDisplays(base);
        spawned = true;
        forceRefresh();
        persistState();
    }

    private void buildHitbox(Location base) {
        hitbox = world.spawn(base, Interaction.class, in -> {
            in.setInteractionWidth(TurretModel.HITBOX_WIDTH);
            in.setInteractionHeight(TurretModel.HITBOX_HEIGHT);
            in.setResponsive(true);
            in.setPersistent(true);
            tagEntity(in, "hitbox", -1);
        });
        // place its base so the box is centred on HITBOX_CENTER
        Location w = anchor.clone().add(0, TurretModel.HITBOX_CENTER.y - TurretModel.HITBOX_HEIGHT / 2.0, 0);
        hitbox.teleport(w);
    }

    private void buildDisplays(Location base) {
        AntiAirConfig cfg = plugin.config();
        List<Part> parts = TurretModel.parts(cfg.rounded);
        for (int index = 0; index < parts.size(); index++) {
            Part part = parts.get(index);
            Material mat = cfg.materialFor(part.role);
            final int idx = index;
            BlockDisplay d = world.spawn(base, BlockDisplay.class, b -> {
                b.setBlock(mat.createBlockData());
                b.setBrightness(new Display.Brightness(15, 15));
                b.setViewRange(cfg.viewRange);
                b.setTeleportDuration(Math.max(1, cfg.interpolationTicks));
                b.setInterpolationDuration(cfg.interpolationTicks);
                b.setPersistent(true);
                tagEntity(b, "part", idx);
            });
            displays.add(d);
            partDefs.add(part);
        }
    }

    private void tagEntity(Entity e, String role, int index) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        pdc.set(Keys.TURRET_ID, PersistentDataType.STRING, id.toString());
        pdc.set(Keys.TURRET_PART, PersistentDataType.STRING, role);
        if (index >= 0) {
            pdc.set(Keys.PART_INDEX, PersistentDataType.INTEGER, index);
        }
        e.addScoreboardTag(Keys.SCOREBOARD_TAG);
    }

    /** Mirror the live state onto the core so a reload can restore it. */
    public void persistState() {
        if (core == null || !core.isValid()) {
            return;
        }
        PersistentDataContainer pdc = core.getPersistentDataContainer();
        pdc.set(Keys.STATE_MODE, PersistentDataType.STRING, mode.name());
        pdc.set(Keys.STATE_HEALTH, PersistentDataType.DOUBLE, health);
        pdc.set(Keys.STATE_YAW, PersistentDataType.DOUBLE, yaw);
        pdc.set(Keys.STATE_PITCH, PersistentDataType.DOUBLE, pitch);
        pdc.set(Keys.STATE_BURN, PersistentDataType.INTEGER, burnTime);
        pdc.set(Keys.STATE_BURN_TOTAL, PersistentDataType.INTEGER, burnTimeTotal);
        pdc.set(Keys.STATE_FUEL_COUNT, PersistentDataType.INTEGER, fuelCount);
        if (fuelType != null && fuelCount > 0) {
            pdc.set(Keys.STATE_FUEL_TYPE, PersistentDataType.STRING, fuelType.name());
        } else {
            pdc.remove(Keys.STATE_FUEL_TYPE);
        }
    }

    /**
     * Cheap periodic persistence so we don't write PDC every tick. Mode/fuel/
     * health/damage already persist immediately on change; this only refreshes the
     * (non-critical) aim + burn snapshot, so a slow cadence is fine — on reload the
     * turret re-aims within a tick anyway.
     */
    public void persistThrottled() {
        if (++persistThrottle >= 60) {
            persistThrottle = 0;
            persistState();
        }
    }

    // ----------------------------------------------------------------- rendering

    private void forceRefresh() {
        firstPush = true;
        refreshModel();
    }

    /**
     * Push model-part transforms when the aim angles changed (anchor never moves).
     * Group-aware: BASE parts are pushed only once, TURRET parts only when yaw
     * changes, GUN parts when yaw OR pitch changes — so a pure elevation tweak only
     * re-sends ~12 barrel transforms instead of the whole turret.
     */
    public void refreshModel() {
        if (!spawned || core == null) {
            return;
        }
        boolean yawChanged = firstPush || Math.abs(lastYaw - yaw) > 1e-3;
        boolean pitchChanged = firstPush || Math.abs(lastPitch - pitch) > 1e-3;
        if (!yawChanged && !pitchChanged) {
            return;
        }
        int interp = plugin.config().interpolationTicks;
        for (int i = 0; i < displays.size(); i++) {
            Part part = partDefs.get(i);
            boolean need = switch (part.pivot) {
                case BASE -> firstPush;
                case TURRET -> yawChanged;
                case GUN -> yawChanged || pitchChanged;
            };
            if (!need) {
                continue;
            }
            Display d = displays.get(i);
            if (d == null || !d.isValid()) {
                continue;
            }
            Transformation t = Transforms.forPart(part, yaw, pitch, TurretModel.GUN_PIVOT);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(interp);
            d.setTransformation(t);
        }
        lastYaw = yaw;
        lastPitch = pitch;
        firstPush = false;
    }

    /** Snap the rig back onto its anchor if something external shoved the core. */
    public void stabilize() {
        if (!spawned || core == null || !core.isValid()) {
            return;
        }
        Location cl = core.getLocation();
        if (cl.getWorld() != anchor.getWorld()) {
            return;
        }
        if (Math.abs(cl.getX() - anchor.getX()) > 0.03
                || Math.abs(cl.getY() - anchor.getY()) > 0.03
                || Math.abs(cl.getZ() - anchor.getZ()) > 0.03) {
            core.teleport(anchor);
        }
    }

    // ----------------------------------------------------------------- geometry / aim

    /** World location of a fixed reference point on the column to aim FROM. */
    public Location sightWorld() {
        return anchor.clone().add(0, TurretModel.SIGHT.y, 0);
    }

    /** World location of the muzzle (barrel tips) for the current aim. */
    public Location muzzleWorld() {
        Vector3f off = Transforms.gunPointToWorld(TurretModel.MUZZLE, yaw, pitch, TurretModel.GUN_PIVOT);
        return new Location(world, anchor.getX() + off.x, anchor.getY() + off.y, anchor.getZ() + off.z);
    }

    /** Unit world vector the bore currently points along. */
    public Vector boreDirection() {
        Vector3f f = Transforms.forward(yaw, pitch);
        return new Vector(f.x, f.y, f.z);
    }

    /** Aim the turret at a world point: compute the desired yaw/pitch. */
    public void aimAt(Location target) {
        Location from = sightWorld();
        double dx = target.getX() - from.getX();
        double dy = target.getY() - from.getY();
        double dz = target.getZ() - from.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6) {
            return;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        targetPitch = Math.toDegrees(Math.asin(-Math.max(-1.0, Math.min(1.0, dy))));
    }

    /** Relax the barrels to the idle elevation, keeping the current heading. */
    public void aimRest(AntiAirConfig cfg) {
        targetPitch = -cfg.restPitchDeg;
    }

    /** Ease the current angles toward the target by at most slewRate per tick. */
    public void slew(AntiAirConfig cfg) {
        double rate = cfg.slewRateDeg;
        yaw = approachAngle(yaw, targetYaw, rate);
        pitch = approachAngle(pitch, targetPitch, rate);
    }

    /** True once the bore is within tolerance of the target angles. */
    public boolean isAimed(AntiAirConfig cfg) {
        return Math.abs(shortestDelta(yaw, targetYaw)) <= cfg.aimToleranceDeg
                && Math.abs(shortestDelta(pitch, targetPitch)) <= cfg.aimToleranceDeg;
    }

    private static double approachAngle(double cur, double goal, double maxStep) {
        double d = shortestDelta(cur, goal);
        if (Math.abs(d) <= maxStep) {
            return normalize(goal);
        }
        return normalize(cur + Math.signum(d) * maxStep);
    }

    private static double shortestDelta(double cur, double goal) {
        double d = (goal - cur) % 360.0;
        if (d > 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }

    private static double normalize(double a) {
        a %= 360.0;
        if (a > 180.0) a -= 360.0;
        if (a < -180.0) a += 360.0;
        return a;
    }

    /** Smallest distance from {@code loc} to any key point of the turret. */
    public double minBlastDistance(Location loc) {
        double best = Double.MAX_VALUE;
        for (Vector3f kp : TurretModel.BLAST_POINTS) {
            double dx = anchor.getX() + kp.x - loc.getX();
            double dy = anchor.getY() + kp.y - loc.getY();
            double dz = anchor.getZ() + kp.z - loc.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < best) {
                best = d;
            }
        }
        return Math.sqrt(best);
    }

    // ----------------------------------------------------------------- fuel

    /** True if it could fire right now (a lit charge or a reserve to light). */
    public boolean hasPower() {
        return burnTime > 0 || (fuelType != null && fuelCount > 0);
    }

    /**
     * Spend fuel for ONE shot. Lights a fresh item from the reserve when the
     * current charge runs out. Called only when the gun actually fires, so a
     * standby turret burns nothing. @return true if there was fuel to fire.
     */
    public boolean spendShot(AntiAirConfig cfg) {
        if (burnTime <= 0) {
            if (fuelType == null || fuelCount <= 0) {
                return false;
            }
            int t = (int) Math.round(FuelTable.burnTicks(fuelType) * cfg.fuelBurnMultiplier);
            if (t <= 0) {
                fuelType = null;
                fuelCount = 0;
                return false;
            }
            burnTimeTotal = t;
            burnTime = t;
            fuelCount--;
            if (fuelCount <= 0) {
                fuelType = null;
                fuelCount = 0;
            }
        }
        burnTime -= cfg.fuelConsumePerShot; // each shot costs this much burn
        if (burnTime < 0) {
            burnTime = 0;
        }
        return true;
    }

    /** Shots left on the current lit charge (excludes the reserve stack). */
    public int litShotsLeft() {
        int perShot = Math.max(1, plugin.config().fuelConsumePerShot);
        return burnTime / perShot;
    }

    public int burnTime() {
        return burnTime;
    }

    public int burnTimeTotal() {
        return Math.max(burnTimeTotal, burnTime);
    }

    public Material fuelType() {
        return fuelType;
    }

    public int fuelCount() {
        return fuelCount;
    }

    /** Replace the reserve fuel stack (used by the GUI). Validates it is fuel. */
    public void setFuel(Material type, int count) {
        if (type != null && count > 0 && FuelTable.isFuel(type)) {
            this.fuelType = type;
            this.fuelCount = count;
        } else {
            this.fuelType = null;
            this.fuelCount = 0;
        }
        persistState();
    }

    // ----------------------------------------------------------------- combat / damage

    /** @return true if the turret was destroyed by this hit. */
    public boolean damage(double amount) {
        if (amount <= 0 || !spawned) {
            return false;
        }
        health -= amount;
        persistState();
        if (health <= 0) {
            destroy(true);
            return true;
        }
        Location c = anchor.clone().add(0, 1.8, 0);
        world.spawnParticle(Particle.CRIT, c, 8, 1.0, 1.0, 1.0, 0.0);
        world.spawnParticle(Particle.LARGE_SMOKE, c, 6, 0.8, 1.0, 0.8, 0.02);
        return false;
    }

    public void destroy(boolean effects) {
        if (effects && spawned) {
            Location c = anchor.clone().add(0, 1.6, 0);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, c, 3, 1.0, 1.0, 1.0, 0);
            world.spawnParticle(Particle.LARGE_SMOKE, c, 80, 1.6, 1.4, 1.6, 0.05);
            world.spawnParticle(Particle.FLAME, c, 40, 1.2, 1.2, 1.2, 0.06);
            world.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 5f, 0.6f);
            if (plugin.config().debris) {
                flingDebris(c);
            }
            if (plugin.config().dropItemOnDestroy) {
                world.dropItemNaturally(anchor.clone().add(0, 0.5, 0), TurretItem.create(plugin));
            }
            if (plugin.config().fuelDropOnDestroy && fuelType != null && fuelCount > 0) {
                world.dropItemNaturally(anchor.clone().add(0, 0.5, 0),
                        new org.bukkit.inventory.ItemStack(fuelType, fuelCount));
            }
        }
        removeEntities();
    }

    private void flingDebris(Location c) {
        java.util.Random rng = new java.util.Random();
        Material mat = plugin.config().housingBlock;
        for (int i = 0; i < 12; i++) {
            BlockDisplay chunk = world.spawn(c, BlockDisplay.class, b -> {
                b.setBlock(mat.createBlockData());
                b.setBrightness(new Display.Brightness(15, 15));
                b.setPersistent(false);
                Transformation t = b.getTransformation();
                t.getScale().set(0.6f);
                b.setTransformation(t);
            });
            Vector v = new Vector(rng.nextGaussian() * 0.4, 0.3 + rng.nextDouble() * 0.5,
                    rng.nextGaussian() * 0.4);
            new org.bukkit.scheduler.BukkitRunnable() {
                int life = 40;
                final Vector vel = v;

                @Override
                public void run() {
                    if (life-- <= 0 || !chunk.isValid()) {
                        chunk.remove();
                        cancel();
                        return;
                    }
                    vel.setY(vel.getY() - 0.04);
                    chunk.teleport(chunk.getLocation().add(vel));
                }
            }.runTaskTimer(plugin.bukkitPlugin(), 1, 1);
        }
    }

    /** Delete every entity belonging to this turret (does not touch the registry). */
    public void removeEntities() {
        if (core != null) {
            core.remove();
        }
        if (hitbox != null) {
            hitbox.remove();
        }
        for (Display d : displays) {
            if (d != null) {
                d.remove();
            }
        }
        displays.clear();
        partDefs.clear();
        spawned = false;
    }

    // ----------------------------------------------------------------- getters / setters

    public UUID id() {
        return id;
    }

    public World world() {
        return world;
    }

    public Location anchor() {
        return anchor;
    }

    public boolean isActive() {
        return spawned && core != null && core.isValid();
    }

    public ArmorStand core() {
        return core;
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        currentTarget = null;
        persistState();
    }

    public double health() {
        return health;
    }

    public double maxHealth() {
        return plugin.config().maxHealth;
    }

    public UUID owner() {
        return owner;
    }

    public int fireCooldown() {
        return fireCooldown;
    }

    public void setFireCooldown(int v) {
        this.fireCooldown = v;
    }

    public int rocketCooldown() {
        return rocketCooldown;
    }

    public void setRocketCooldown(int v) {
        this.rocketCooldown = v;
    }

    public UUID currentTarget() {
        return currentTarget;
    }

    public void setCurrentTarget(UUID t) {
        this.currentTarget = t;
    }

    // ----------------------------------------------------------------- heat / spin-up

    public double heat() {
        return heat;
    }

    public boolean isOverheated() {
        return overheated;
    }

    public int spinUp() {
        return spinUp;
    }

    /** Wind the barrels up one tick. @return true if they were already at rest. */
    public boolean incSpinUp() {
        return spinUp++ == 0;
    }

    public void resetSpinUp() {
        spinUp = 0;
    }

    /** Shed heat (called every tick); clears the overheat lock once cool enough. */
    public void coolHeat(me.bibo.militarycraft.weapons.antiair.config.AntiAirConfig cfg) {
        if (heat > 0) {
            heat = Math.max(0.0, heat - cfg.heatCoolPerTick);
        }
        if (overheated && heat <= cfg.heatResume) {
            overheated = false;
        }
    }

    /** Add the heat of one fired burst; trips the overheat lock at the top. */
    public void addFireHeat(me.bibo.militarycraft.weapons.antiair.config.AntiAirConfig cfg) {
        heat += cfg.heatPerShot * Math.max(1, cfg.burst);
        if (heat >= 1.0) {
            heat = 1.0;
            overheated = true;
        }
    }
}
