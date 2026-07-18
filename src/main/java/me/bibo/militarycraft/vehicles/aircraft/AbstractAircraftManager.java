package me.bibo.militarycraft.vehicles.aircraft;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.combat.Projectiles;
import me.bibo.militarycraft.core.vehicle.DisplayVehicle;
import me.bibo.militarycraft.core.vehicle.VehicleManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Common combat plumbing for aircraft managers: melee/projectile damage and munition ticking. */
public abstract class AbstractAircraftManager<V extends DisplayVehicle> extends VehicleManager<V> {

    private final Map<UUID, Long> meleeCd = new HashMap<>();
    private final HashSet<BukkitTask> effectTasks = new HashSet<>();
    private BukkitTask munitionTask;
    private int cleanupTicks;

    protected AbstractAircraftManager(Core core) {
        this.core = core;
    }

    public Core core() {
        return core;
    }

    protected abstract double weaponMeleePercent();

    protected abstract double weaponArrowPercent();

    protected abstract double weaponFireballPercent();

    protected abstract int weaponMeleeCooldownMs();

    protected int projectileSweepIntervalTicks() {
        return 3;
    }

    protected abstract List<AirMunition> munitions();

    protected void tickMunitions() {
        effectTasks.removeIf(BukkitTask::isCancelled);
        if (++cleanupTicks >= 200) {
            cleanupTicks = 0;
            long cutoff = System.currentTimeMillis() - Math.max(1000, weaponMeleeCooldownMs());
            meleeCd.values().removeIf(last -> last < cutoff);
        }
        Iterator<AirMunition> it = munitions().iterator();
        while (it.hasNext()) {
            AirMunition munition = it.next();
            if (!munition.tick() || munition.isDead()) {
                it.remove();
            }
        }
    }

    protected boolean hasMunitionSlot(int maxActive) {
        int limit = AircraftSafety.clamp(maxActive, 1, AircraftSafety.MAX_ACTIVE_MUNITIONS);
        return munitions().size() < limit;
    }

    protected final void addMunition(AirMunitionSpec spec, Location start, Vector velocity, DisplayVehicle owner) {
        munitions().add(new AirMunition(core, spec, start, velocity, owner, this::trackEffectTask));
    }

    private void trackEffectTask(BukkitTask task) {
        effectTasks.removeIf(BukkitTask::isCancelled);
        if (effectTasks.size() >= AircraftSafety.MAX_EFFECT_TASKS) {
            task.cancel();
            return;
        }
        effectTasks.add(task);
    }

    protected final void trimMunitions(int maxActive) {
        int limit = AircraftSafety.clamp(maxActive, 1, AircraftSafety.MAX_ACTIVE_MUNITIONS);
        List<AirMunition> active = munitions();
        if (active.size() > limit) {
            active.subList(limit, active.size()).clear();
        }
    }

    protected void clearAircraftCombat() {
        munitions().clear();
        meleeCd.clear();
        effectTasks.forEach(BukkitTask::cancel);
        effectTasks.clear();
    }

    @Override
    public void start() {
        if (munitionTask != null) {
            return;
        }
        super.start();
        munitionTask = core.scheduler().runTaskTimer(core.plugin(), this::tickMunitions, 1L, 1L);
    }

    @Override
    public void stop() {
        if (munitionTask != null) {
            munitionTask.cancel();
            munitionTask = null;
        }
        clearAircraftCombat();
        super.stop();
    }

    protected boolean shouldSweepProjectiles(V vehicle) {
        int interval = Math.max(1, projectileSweepIntervalTicks());
        return interval <= 1 || Math.floorMod(vehicle.world().getFullTime() + vehicle.id().hashCode(), interval) == 0;
    }

    @Override
    protected double vehicleEntityMeleePercent() {
        return weaponMeleePercent();
    }

    @Override
    protected double vehicleEntityArrowPercent() {
        return weaponArrowPercent();
    }

    @Override
    protected double vehicleEntityFireballPercent() {
        return weaponFireballPercent();
    }

    protected void sweepProjectiles(V vehicle) {
        UUID driver = vehicle.driver();
        for (Projectile proj : projectilesInBody(vehicle, 0.45)) {
            if (driver != null && proj.getShooter() instanceof Player sp && sp.getUniqueId().equals(driver)) {
                continue;
            }
            double pct = proj instanceof Fireball ? weaponFireballPercent() : weaponArrowPercent();
            Location at = proj.getLocation();
            proj.remove();
            weaponHitFx(at);
            if (vehicle.damage(vehicle.maxHealth() * pct / 100.0)) {
                return;
            }
        }
    }

    @Override
    protected void onDriverAttacked(V vehicle, Player driver, EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player attacker && core.vehicles().riddenBy(attacker) != null) {
            return;
        }
        if (damager instanceof Projectile proj) {
            if (!Projectiles.isWeaponProjectile(proj)) {
                return;
            }
            if (proj.getShooter() instanceof Player shooter
                    && vehicle.driver() != null
                    && shooter.getUniqueId().equals(vehicle.driver())) {
                return;
            }
            double pct = proj instanceof Fireball ? weaponFireballPercent() : weaponArrowPercent();
            vehicle.damage(vehicle.maxHealth() * pct / 100.0);
            proj.remove();
            return;
        }
        meleeDamageFromEntity(damager, vehicle, driver.getLocation().add(0, 1.0, 0));
    }

    public void meleeFromPlayer(Player attacker) {
        if (core.vehicles().riddenBy(attacker) != null) {
            return;
        }
        MeleeHit hit = findMeleeTarget(attacker, 4.5, 0.35);
        if (hit == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        V target = (V) hit.vehicle();
        meleeDamageFromEntity(attacker, target, hit.point());
    }

    protected void meleeDamageFromEntity(Entity attacker, V target, Location hitAt) {
        if (attacker == null || target == null || !target.isActive()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = meleeCd.get(attacker.getUniqueId());
        if (last != null && now - last < weaponMeleeCooldownMs()) {
            return;
        }
        meleeCd.put(attacker.getUniqueId(), now);
        weaponHitFx(hitAt);
        target.damage(target.maxHealth() * weaponMeleePercent() / 100.0);
    }

    protected void weaponHitFx(Location at) {
        World w = at.getWorld();
        if (w == null) {
            return;
        }
        w.spawnParticle(Particle.CRIT, at, 8, 0.25, 0.25, 0.25, 0.1);
        w.spawnParticle(Particle.ELECTRIC_SPARK, at, 6, 0.2, 0.2, 0.2, 0.05);
        w.playSound(at, Sound.ENTITY_IRON_GOLEM_HURT, 0.6f, 1.3f);
    }

    @Override
    public int[] purgeAll() {
        clearAircraftCombat();
        return super.purgeAll();
    }
}
