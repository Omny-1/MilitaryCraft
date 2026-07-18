package me.bibo.militarycraft.weapons.antiair.turret;

import me.bibo.militarycraft.weapons.antiair.AntiAirRuntime;
import me.bibo.militarycraft.weapons.antiair.combat.TargetingSystem;
import me.bibo.militarycraft.weapons.antiair.config.AntiAirConfig;
import me.bibo.militarycraft.weapons.antiair.gui.TurretMenu;
import me.bibo.militarycraft.weapons.antiair.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
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
 * Registry of all turrets in loaded chunks, plus the single per-tick AI loop:
 * acquire a target (throttled), slew onto it, burn fuel while engaging, and fire.
 * Turrets live as persistent entities; their wrappers are rebuilt on entity-load
 * and dropped on entity-unload, so they survive chunk reloads and restarts.
 */
public final class TurretManager {

    private final AntiAirRuntime plugin;
    private final TargetingSystem targeting;
    private final Map<UUID, Turret> turrets = new LinkedHashMap<>();
    private final Map<UUID, LivingEntity> targets = new HashMap<>();
    private BukkitTask task;
    private boolean internalExplosion;
    private long tickCounter;

    public TurretManager(AntiAirRuntime plugin) {
        this.plugin = plugin;
        this.targeting = new TargetingSystem(plugin);
    }

    public TargetingSystem targeting() {
        return targeting;
    }

    // --------------------------------------------------------------- lifecycle

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin.bukkitPlugin(), this::tick, 1L, 1L);
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
        for (Turret t : new ArrayList<>(turrets.values())) {
            t.persistState();
        }
        turrets.clear();
        targets.clear();
    }

    private void tick() {
        tickCounter++;
        AntiAirConfig cfg = plugin.config();

        Iterator<Turret> it = turrets.values().iterator();
        while (it.hasNext()) {
            Turret turret = it.next();
            if (!turret.isActive()) {
                targets.remove(turret.id());
                turret.removeEntities();
                it.remove();
                continue;
            }
            try {
                tickTurret(turret, cfg);
            } catch (Exception ex) {
                plugin.getLogger().warning("Turret tick failed: " + ex);
            }
        }

        if ((tickCounter % 10) == 0) {
            refreshOpenMenus();
        }
    }

    private void tickTurret(Turret turret, AntiAirConfig cfg) {
        turret.stabilize();
        turret.coolHeat(cfg); // barrels always shed heat, firing or not

        // (Re)acquire on the scan cadence, staggered per turret so they don't all
        // scan on the same tick. Skip the scan for unfuelled turrets — they can't
        // fire anyway, so there's no point scanning every entity in range.
        int interval = cfg.scanIntervalTicks;
        long phase = Math.floorMod(turret.id().hashCode(), interval);
        if (((tickCounter + phase) % interval) == 0) {
            if (turret.hasPower()) {
                LivingEntity acquired = targeting.acquire(turret);
                targets.put(turret.id(), acquired);
                turret.setCurrentTarget(acquired != null ? acquired.getUniqueId() : null);
            } else {
                targets.remove(turret.id());
                turret.setCurrentTarget(null);
            }
        }

        LivingEntity target = validatedTarget(turret, cfg);

        boolean engaging = target != null;
        // Non-consuming readiness check: fuel is only actually spent on a shot.
        boolean hasFuel = turret.hasPower();

        if (turret.fireCooldown() > 0) {
            turret.setFireCooldown(turret.fireCooldown() - 1);
        }
        if (turret.rocketCooldown() > 0) {
            turret.setRocketCooldown(turret.rocketCooldown() - 1);
        }

        if (engaging && hasFuel) {
            turret.aimAt(targeting.aimPoint(target));
            turret.slew(cfg);

            // Is the target riding one of our vehicles (tank/jet/airship)? Then it's
            // anti-vehicle mode: hit the VEHICLE with rockets until it breaks.
            Entity vehicle = (turret.mode() == Mode.SVO)
                    ? targeting.ridingVehicleCore(target) : null;

            boolean aimedReady = turret.isAimed(cfg) && !turret.isOverheated();
            // Bullets need a clear muzzle line. The anti-vehicle ROCKET does NOT —
            // it's a guided strike on the already-acquired vehicle, so it connects at
            // ANY distance (the low gun line grazing terrain no longer blocks it) and
            // the vehicle takes the same damage point-blank or at the edge of range.
            boolean canFire = aimedReady
                    && (vehicle != null || hasLineOfSight(turret, target, cfg));
            if (canFire) {
                if (turret.incSpinUp()) {
                    // just started spinning the barrels up — spool-up whir + a burst of sparks
                    Location spin = turret.muzzleWorld();
                    turret.world().playSound(spin, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
                    turret.world().spawnParticle(Particle.ELECTRIC_SPARK, spin, 10, 0.2, 0.2, 0.2, 0.05);
                }
                if (turret.spinUp() >= cfg.spinUpTicks) {
                    if (vehicle != null) {
                        if (turret.rocketCooldown() <= 0 && turret.spendShot(cfg)) {
                            targeting.fireRocket(turret, vehicle);
                            turret.setRocketCooldown(cfg.svoRocketIntervalTicks);
                            turret.addFireHeat(cfg);
                        }
                    } else if (turret.fireCooldown() <= 0 && turret.spendShot(cfg)) {
                        // Fuel is spent ONLY here, on the actual shot — standby burns nothing.
                        targeting.fireBurst(turret, target);
                        turret.setFireCooldown(cfg.fireIntervalTicks);
                        turret.addFireHeat(cfg);
                    }
                }
            } else {
                turret.resetSpinUp();
                if (turret.isOverheated() && (tickCounter % 8) == 0) {
                    // overheated with an enemy present: hiss steam off the barrels
                    Location c = turret.muzzleWorld();
                    c.getWorld().spawnParticle(Particle.LARGE_SMOKE, c, 5, 0.15, 0.15, 0.15, 0.01);
                }
            }
        } else {
            turret.aimRest(cfg);
            turret.slew(cfg);
            turret.resetSpinUp();
            if (engaging && !hasFuel && (tickCounter % 20) == 0) {
                // enemy present but no fuel: cough smoke so it's obvious
                Location c = turret.muzzleWorld();
                c.getWorld().spawnParticle(Particle.SMOKE, c, 6, 0.1, 0.1, 0.1, 0.01);
            }
        }

        turret.refreshModel();
        turret.persistThrottled();
    }

    /** Re-validate the cached target; clear it if it died/left/changed legality. */
    private LivingEntity validatedTarget(Turret turret, AntiAirConfig cfg) {
        LivingEntity target = targets.get(turret.id());
        if (target == null) {
            return null;
        }
        double range = (turret.mode() == Mode.SVO) ? cfg.svoRange : cfg.normiesRange;
        if (target.isDead() || !target.isValid()
                || target.getWorld() != turret.world()
                || target.getLocation().distanceSquared(turret.sightWorld()) > range * range
                || !targeting.isValidTarget(turret, target, cfg)) {
            targets.remove(turret.id());
            turret.setCurrentTarget(null);
            return null;
        }
        return target;
    }

    private boolean hasLineOfSight(Turret turret, LivingEntity target, AntiAirConfig cfg) {
        if (!cfg.requireLineOfSight) {
            return true;
        }
        Location muzzle = turret.muzzleWorld();
        Location aim = targeting.aimPoint(target);
        org.bukkit.util.Vector dir = aim.toVector().subtract(muzzle.toVector());
        double dist = dir.length();
        if (dist < 1e-3) {
            return true;
        }
        org.bukkit.util.RayTraceResult res = turret.world().rayTraceBlocks(muzzle,
                dir.multiply(1.0 / dist), dist, org.bukkit.FluidCollisionMode.NEVER, true);
        return res == null; // nothing solid in the way
    }

    private void refreshOpenMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = p.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof TurretMenu menu) {
                Turret t = byId(menu.turretId());
                if (t != null && t.isActive()) {
                    menu.render(t);
                }
            }
        }
    }

    // --------------------------------------------------------------- chunk (de)hydration

    public void onEntitiesLoad(Collection<Entity> entities) {
        Map<UUID, List<Entity>> groups = new HashMap<>();
        for (Entity e : entities) {
            if (!e.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
                continue;
            }
            String idStr = e.getPersistentDataContainer().get(Keys.TURRET_ID, PersistentDataType.STRING);
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
            if (turrets.containsKey(id)) {
                continue;
            }
            groups.computeIfAbsent(id, k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<UUID, List<Entity>> entry : groups.entrySet()) {
            Turret t = Turret.rehydrate(plugin, entry.getKey(), entry.getValue());
            if (t != null) {
                turrets.put(entry.getKey(), t);
            } else {
                for (Entity e : entry.getValue()) {
                    e.remove();
                }
            }
        }
    }

    public void onEntitiesUnload(Collection<Entity> entities) {
        for (Entity e : entities) {
            String idStr = e.getPersistentDataContainer().get(Keys.TURRET_ID, PersistentDataType.STRING);
            if (idStr == null) {
                continue;
            }
            Turret t;
            try {
                t = turrets.get(UUID.fromString(idStr));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (t == null) {
                continue;
            }
            t.persistState();
            forget(t);
        }
    }

    // --------------------------------------------------------------- registry

    public Turret create(Location at, double yaw, UUID owner) {
        Turret t = Turret.create(plugin, at, yaw, owner);
        turrets.put(t.id(), t);
        return t;
    }

    public int countByOwner(UUID owner) {
        if (owner == null) {
            return 0;
        }
        int n = 0;
        for (Turret t : turrets.values()) {
            if (owner.equals(t.owner())) {
                n++;
            }
        }
        return n;
    }

    public Turret byId(UUID id) {
        return id == null ? null : turrets.get(id);
    }

    public Turret byEntity(Entity entity) {
        String id = entity.getPersistentDataContainer().get(Keys.TURRET_ID, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        try {
            return turrets.get(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public Collection<Turret> all() {
        return turrets.values();
    }

    public int count() {
        return turrets.size();
    }

    // --------------------------------------------------------------- damage

    public void damageTurretsFromExplosion(Location loc, double power) {
        AntiAirConfig cfg = plugin.config();
        for (Turret t : new ArrayList<>(turrets.values())) {
            applyBlast(t, loc, power, cfg);
        }
    }

    /** Distance/power-scaled explosion damage, calibrated so 1 creeper = full HP. */
    private void applyBlast(Turret turret, Location loc, double power, AntiAirConfig cfg) {
        if (!turret.isActive() || turret.world() != loc.getWorld()) {
            return;
        }
        double dist = turret.minBlastDistance(loc);
        double contact = cfg.contactRadius;
        double radius = power * 2.0 + contact;
        if (dist > radius) {
            return;
        }
        double falloff = dist <= contact ? 1.0 : Math.max(0.0, 1.0 - (dist - contact) / (radius - contact));
        double dmg = cfg.creeperDamage * (power / 3.0) * falloff;
        if (dmg > 0) {
            turret.damage(dmg);
        }
    }

    public void setInternalExplosion(boolean value) {
        this.internalExplosion = value;
    }

    public boolean isInternalExplosion() {
        return internalExplosion;
    }

    // --------------------------------------------------------------- removal

    public void remove(Turret turret, boolean effects) {
        if (effects) {
            turret.destroy(true);
        } else {
            turret.removeEntities();
        }
        forget(turret);
    }

    private void forget(Turret turret) {
        turrets.remove(turret.id());
        targets.remove(turret.id());
    }

    public int[] cleanupAll() {
        int count = turrets.size();
        for (Turret t : new ArrayList<>(turrets.values())) {
            t.removeEntities();
        }
        turrets.clear();
        targets.clear();

        int orphans = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)
                        || e.getPersistentDataContainer().has(Keys.TURRET_ID, PersistentDataType.STRING)) {
                    e.remove();
                    orphans++;
                }
            }
        }
        return new int[]{count, orphans};
    }
}
