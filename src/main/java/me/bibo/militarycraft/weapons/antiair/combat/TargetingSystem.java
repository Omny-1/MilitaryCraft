package me.bibo.militarycraft.weapons.antiair.combat;

import me.bibo.militarycraft.weapons.antiair.AntiAirRuntime;
import me.bibo.militarycraft.weapons.antiair.config.AntiAirConfig;
import me.bibo.militarycraft.weapons.antiair.turret.Mode;
import me.bibo.militarycraft.weapons.antiair.turret.Turret;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Finds targets for a turret (by mode) and fires its fast little bullets as cheap
 * hitscan rays with a particle tracer. Accuracy is governed by spread:
 * <ul>
 *   <li><b>SVO</b> - spread grows with distance to the turret; sampled uniformly
 *       by area so ≈12% of bullets hit a player at the far edge of the range.
 *   <li><b>Normies</b> - a tiny constant jitter only (no distance falloff).
 * </ul>
 */
public final class TargetingSystem {

    private final AntiAirRuntime plugin;
    private final Color tracerColor = Color.fromRGB(255, 220, 90);

    public TargetingSystem(AntiAirRuntime plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------- acquisition

    /**
     * Find the best valid target for this turret. Targeting priority: a target it
     * can actually SHOOT (clear line of sight) is preferred over a closer one
     * hiding behind a wall — so the turret picks the nearest target it can hit,
     * not just the nearest target. If nothing has line of sight it falls back to
     * the nearest (it tracks it, and will open up the moment the wall is gone).
     */
    public LivingEntity acquire(Turret turret) {
        AntiAirConfig cfg = plugin.config();
        Location center = turret.sightWorld();

        // SVO only ever shoots players, so iterate the (tiny) player list instead
        // of scanning every entity in a 60-block cube — far cheaper.
        Collection<? extends Entity> candidates;
        if (turret.mode() == Mode.SVO) {
            candidates = turret.world().getPlayers();
        } else {
            candidates = turret.world().getNearbyEntities(center, cfg.normiesRange, cfg.normiesRange,
                    cfg.normiesRange, e -> isValidTarget(turret, e, cfg));
        }
        return pickBest(turret, cfg, center, candidates);
    }

    /** Nearest in-range target with line of sight; else nearest in-range overall. */
    private LivingEntity pickBest(Turret turret, AntiAirConfig cfg, Location center,
                                  Collection<? extends Entity> candidates) {
        double range = (turret.mode() == Mode.SVO) ? cfg.svoRange : cfg.normiesRange;
        double rangeSq = range * range;

        List<LivingEntity> inRange = new ArrayList<>();
        for (Entity e : candidates) {
            if (!isValidTarget(turret, e, cfg)) {
                continue;
            }
            if (e.getLocation().distanceSquared(center) <= rangeSq) {
                inRange.add((LivingEntity) e);
            }
        }
        if (inRange.isEmpty()) {
            return null;
        }
        inRange.sort(Comparator.comparingDouble(le -> le.getLocation().distanceSquared(center)));

        // Walk from nearest outward; the first one we have line of sight to wins.
        // Cap the line-of-sight checks so a huge mob crowd can't spike the tick.
        int checks = Math.min(inRange.size(), 16);
        for (int i = 0; i < checks; i++) {
            LivingEntity le = inRange.get(i);
            if (hasLineOfSight(turret, le)) {
                return le;
            }
        }
        return inRange.get(0); // none of the nearest are reachable — track the closest
    }

    /** Clear shot from the gun to a target's centre (no solid blocks between)? */
    public boolean hasLineOfSight(Turret turret, LivingEntity target) {
        Location from = turret.sightWorld();
        Location aim = aimPoint(target);
        Vector dir = aim.toVector().subtract(from.toVector());
        double dist = dir.length();
        if (dist < 1e-3) {
            return true;
        }
        RayTraceResult res = turret.world().rayTraceBlocks(from, dir.multiply(1.0 / dist), dist,
                FluidCollisionMode.NEVER, true);
        return res == null;
    }

    /**
     * If this target is a player riding one of our vehicles (TankCraft / JetCraft /
     * AirshipCraft), return the vehicle entity they ride. They're invulnerable
     * inside it, so the turret should rocket the vehicle, not the player.
     */
    public Entity ridingVehicleCore(LivingEntity target) {
        if (!(target instanceof Player p)) {
            return null;
        }
        Entity v = p.getVehicle();
        if (v == null) {
            return null;
        }
        var handle = plugin.core().vehicles().riddenBy(p);
        if (handle != null && handle.isActive() && handle.coreEntity() != null) {
            return handle.coreEntity();
        }
        for (String tag : plugin.config().vehicleTags) {
            if (v.getScoreboardTags().contains(tag)) {
                return v;
            }
        }
        return null;
    }

    /** Whether an entity is a legal target for this turret's current mode. */
    public boolean isValidTarget(Turret turret, Entity e, AntiAirConfig cfg) {
        if (!(e instanceof LivingEntity le) || le.isDead() || !le.isValid()) {
            return false;
        }
        switch (turret.mode()) {
            case SVO -> {
                if (!(le instanceof Player p)) {
                    return false;
                }
                if (cfg.svoIgnoreOwner && p.getUniqueId().equals(turret.owner())) {
                    return false;
                }
                GameMode gm = p.getGameMode();
                if (gm == GameMode.SPECTATOR) {
                    return false;
                }
                if (cfg.svoIgnoreCreative && gm == GameMode.CREATIVE) {
                    return false;
                }
                return true;
            }
            case NORMIES_PHANTOMS -> {
                return le instanceof Phantom;
            }
            case NORMIES_HOSTILES -> {
                return le instanceof Monster || le instanceof Phantom
                        || le instanceof Slime || le instanceof Ghast;
            }
            default -> {
                return false;
            }
        }
    }

    /** The point on a target the turret aims at (its bounding-box centre). */
    public Location aimPoint(LivingEntity target) {
        Vector c = target.getBoundingBox().getCenter();
        return new Location(target.getWorld(), c.getX(), c.getY(), c.getZ());
    }

    // ----------------------------------------------------------------- firing

    /** Fire one burst of bullets at the target. */
    public void fireBurst(Turret turret, LivingEntity target) {
        AntiAirConfig cfg = plugin.config();
        for (int i = 0; i < cfg.burst; i++) {
            fireOne(turret, target, cfg, i);
        }
    }

    private void fireOne(Turret turret, LivingEntity target, AntiAirConfig cfg, int idx) {
        Location muzzle = turret.muzzleWorld();
        Location aim = aimPoint(target);
        Vector base = aim.toVector().subtract(muzzle.toVector());
        double dist = base.length();
        if (dist < 1e-3) {
            return;
        }
        base.multiply(1.0 / dist);

        double spreadDeg = (turret.mode() == Mode.SVO)
                ? lerp(cfg.svoMinSpread, cfg.svoMaxSpread, clamp01(dist / cfg.svoRange))
                : cfg.normiesSpread;
        Vector dir = applySpread(base, spreadDeg);

        // Hitscan: stop at the first wall, or the first valid target if unobstructed.
        Predicate<Entity> filter = e -> isValidTarget(turret, e, cfg);
        RayTraceResult res = turret.world().rayTrace(muzzle, dir, cfg.bulletRange,
                FluidCollisionMode.NEVER, true, cfg.raySize, filter);

        Location end;
        if (res != null) {
            Vector hp = res.getHitPosition();
            end = new Location(turret.world(), hp.getX(), hp.getY(), hp.getZ());
            Entity hit = res.getHitEntity();
            if (hit instanceof LivingEntity le && isValidTarget(turret, le, cfg)) {
                applyHit(turret, le, dir, cfg);
            } else {
                turret.world().spawnParticle(Particle.BLOCK, end, 6, 0.05, 0.05, 0.05, 0.0,
                        org.bukkit.Material.STONE.createBlockData());
            }
        } else {
            end = muzzle.clone().add(dir.clone().multiply(cfg.bulletRange));
        }

        drawTracer(muzzle, end, cfg);
        if (idx == 0) {
            muzzleEffects(turret, muzzle);
        }
    }

    private void applyHit(Turret turret, LivingEntity le, Vector dir, AntiAirConfig cfg) {
        // SVO hits players for half (configurable); other modes keep full damage.
        double dmg = cfg.bulletDamage;
        if (turret.mode() == Mode.SVO) {
            dmg *= cfg.svoDamageMultiplier;
        }
        Player source = (turret.owner() != null) ? plugin.getServer().getPlayer(turret.owner()) : null;
        try {
            if (source != null && source.isOnline()) {
                le.damage(dmg, source);
            } else {
                le.damage(dmg);
            }
        } catch (Exception ignored) {
            le.damage(dmg);
        }
        if (cfg.knockback > 0) {
            le.setVelocity(le.getVelocity().add(dir.clone().multiply(cfg.knockback)));
        }
        Location at = aimPoint(le);
        le.getWorld().spawnParticle(Particle.CRIT, at, 5, 0.1, 0.1, 0.1, 0.0);
        le.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, at, 3, 0.1, 0.1, 0.1, 0.0);
    }

    // ----------------------------------------------------------------- rockets

    /**
     * Fire one anti-vehicle rocket at the vehicle a player is riding. It's a
     * creeper-power blast AT the vehicle; the vehicle's own plugin listens for
     * explosions and converts it into ≈1 creeper of HP damage. Our own turrets are
     * shielded from the blast via the internal-explosion flag.
     */
    public void fireRocket(Turret turret, Entity vehicle) {
        AntiAirConfig cfg = plugin.config();
        Location muzzle = turret.muzzleWorld();
        Location at = vehicle.getLocation().add(0, 0.6, 0);

        muzzle.getWorld().playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2.2f, 0.7f);
        muzzle.getWorld().spawnParticle(Particle.FLAME, muzzle, 14, 0.1, 0.1, 0.1, 0.05);
        muzzle.getWorld().spawnParticle(Particle.FLASH, muzzle, 1, 0, 0, 0, 0);
        drawRocketTrail(muzzle, at);

        // Snapshot the velocity of everything the blast could shove (the vehicle rig
        // + any players), so we can undo the explosion's KNOCKBACK afterwards. A
        // vehicle's seat is a gravity-less armor stand: even a tiny shove sends it
        // (and the rider's camera) drifting off the model/hitbox. The rocket must
        // only subtract vehicle HP through the shared combat service, never move anything.
        boolean handledDirectly = plugin.core().combat().antiAirHit(vehicle);
        if (!handledDirectly) {
            double r = Math.max(6.0, cfg.svoRocketPower * 2.0 + 6.0);
            java.util.List<Entity> affected = new ArrayList<>(at.getWorld().getNearbyEntities(
                    at, r, r, r, e -> e instanceof Player || isVehiclePart(e)));
            java.util.Map<Entity, Vector> preVel = new java.util.HashMap<>();
            for (Entity e : affected) {
                preVel.put(e, e.getVelocity());
            }

        // The explosion's SOURCE must sit at the vehicle, not at the far-away turret:
        // the vehicle plugin reads the blast's reference point from the source/event,
        // so a turret-located source made the vehicle measure distance from the AntiAir turret
        // (hence "only damaged when close"). Spawn a throwaway invisible marker AT the
        // vehicle, tagged so the vehicle recognises it as an AntiAir hit, use it as the
        // source, then remove it. break-blocks=true so EntityExplodeEvent reliably
        // fires; our DamageListener clears the blocks + cancels the blast damage.
            org.bukkit.entity.ArmorStand src = at.getWorld().spawn(at, org.bukkit.entity.ArmorStand.class, a -> {
                a.setInvisible(true);
                a.setMarker(true);
                a.setGravity(false);
                a.setSilent(true);
                a.setInvulnerable(true);
                a.setCollidable(false);
                a.setPersistent(false);
                a.addScoreboardTag("antiaircraft_entity");
            });
            plugin.turrets().setInternalExplosion(true);
            try {
                at.getWorld().createExplosion(at, (float) cfg.svoRocketPower, false, true, src);
            } finally {
                plugin.turrets().setInternalExplosion(false);
                if (src.isValid()) {
                    src.remove();
                }
            }

            // Undo all knockback the fallback blast applied.
            for (Entity e : affected) {
                Vector v = preVel.get(e);
                if (v != null && e.isValid()) {
                    e.setVelocity(v);
                }
            }
        }
        if (cfg.debug) {
            plugin.getLogger().info("[pvo] rocket -> " + vehicle.getType() + " at " + at
                    + " (turret " + String.format(java.util.Locale.US, "%.1f",
                    turret.anchor().distance(at)) + "m away)");
        }

        at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at, 2, 0.5, 0.5, 0.5, 0);
        at.getWorld().spawnParticle(Particle.FLAME, at, 18, 0.8, 0.6, 0.8, 0.06);
        at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 24, 1.0, 0.7, 1.0, 0.1);
        at.getWorld().spawnParticle(Particle.CRIT, at, 20, 1.1, 0.7, 1.1, 0.12);
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.7f);
    }

    /** True if the entity is part of a ridable vehicle (carries a vehicle tag). */
    private boolean isVehiclePart(Entity e) {
        for (String tag : plugin.config().vehicleTags) {
            if (e.getScoreboardTags().contains(tag)) {
                return true;
            }
        }
        return false;
    }

    private void drawRocketTrail(Location from, Location to) {
        Vector step = to.toVector().subtract(from.toVector());
        double len = step.length();
        if (len < 1e-3) {
            return;
        }
        int n = Math.max(3, Math.min(40, (int) len));
        step.multiply(1.0 / n);
        Location p = from.clone();
        for (int i = 0; i <= n; i++) {
            from.getWorld().spawnParticle(Particle.FLAME, p, 1, 0.0, 0.0, 0.0, 0.0);
            if ((i & 1) == 0) {
                from.getWorld().spawnParticle(Particle.SMOKE, p, 1, 0.0, 0.0, 0.0, 0.0);
            }
            p.add(step.getX(), step.getY(), step.getZ());
        }
    }

    // --------------------------------------------------------------- effects

    private void drawTracer(Location from, Location to, AntiAirConfig cfg) {
        if (cfg.tracerDensity <= 0) {
            return;
        }
        Vector step = to.toVector().subtract(from.toVector());
        double len = step.length();
        if (len < 1e-3) {
            return;
        }
        int n = Math.max(2, Math.min(cfg.tracerDensity, (int) (len * 1.5)));
        step.multiply(1.0 / n);
        Particle.DustOptions dust = new Particle.DustOptions(tracerColor, 0.6f);
        Location p = from.clone();
        for (int i = 0; i <= n; i++) {
            from.getWorld().spawnParticle(Particle.DUST, p, 1, 0.0, 0.0, 0.0, 0.0, dust);
            p.add(step.getX(), step.getY(), step.getZ());
        }
    }

    private void muzzleEffects(Turret turret, Location muzzle) {
        turret.world().spawnParticle(Particle.FLAME, muzzle, 3, 0.05, 0.05, 0.05, 0.01);
        turret.world().spawnParticle(Particle.SMOKE, muzzle, 2, 0.05, 0.05, 0.05, 0.01);
        // Keep the audio cheap at 10 rounds/s: a short snappy report.
        if (ThreadLocalRandom.current().nextBoolean()) {
            turret.world().playSound(muzzle, Sound.ITEM_CROSSBOW_SHOOT, 0.5f, 1.7f);
        }
    }

    // ----------------------------------------------------------------- spread

    /** Deviate {@code dir} by a random angle within a cone, uniform by area. */
    private Vector applySpread(Vector dir, double spreadDeg) {
        if (spreadDeg <= 0.0) {
            return dir.clone();
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double thetaMax = Math.toRadians(spreadDeg);
        double theta = thetaMax * Math.sqrt(rng.nextDouble()); // uniform-by-area
        double azimuth = rng.nextDouble() * Math.PI * 2.0;

        // Orthonormal basis (u, v) perpendicular to dir.
        Vector u = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (u.lengthSquared() < 1e-6) {
            u = dir.clone().crossProduct(new Vector(1, 0, 0));
        }
        u.normalize();
        Vector v = dir.clone().crossProduct(u).normalize();

        double sin = Math.sin(theta);
        Vector perp = u.multiply(Math.cos(azimuth) * sin).add(v.multiply(Math.sin(azimuth) * sin));
        return dir.clone().multiply(Math.cos(theta)).add(perp).normalize();
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
