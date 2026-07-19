package me.bibo.militarycraft.gear.warkit.weapon;

import me.bibo.militarycraft.core.combat.VehicleHit;
import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import me.bibo.militarycraft.gear.warkit.Txt;
import me.bibo.militarycraft.gear.warkit.TeamRules;
import me.bibo.militarycraft.gear.warkit.SpectatorBlock;
import me.bibo.militarycraft.gear.warkit.WarItems;
import me.bibo.militarycraft.gear.warkit.WarKitRuntime;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Grenades, grenade launcher, Patriot, Molotov cocktail, and sleep gas. */
public final class GrenadeService implements Listener {

    private static final int PATRIOT_ARM_TICKS = 8;

    private final WarKitRuntime plugin;
    private final NamespacedKey projKey;
    private final Set<Entity> activeEntities = new HashSet<>();
    private final Set<BukkitRunnable> activeTasks = new HashSet<>();

    private record LockTarget(LivingEntity living, VehicleHandle vehicle) {
        boolean valid() {
            return living != null ? living.isValid() && !living.isDead()
                    : vehicle != null && vehicle.isActive();
        }

        Location point() {
            if (living != null) {
                return living.getLocation().add(0, living.getHeight() * 0.5, 0);
            }
            if (vehicle == null || !vehicle.isActive() || vehicle.location() == null) {
                return null;
            }
            // live vehicles don't expose model height; flat 1.0 matches shipped behaviour
            return vehicle.location().clone().add(0, 1.0, 0);
        }
    }

    public GrenadeService(WarKitRuntime plugin) {
        this.plugin = plugin;
        this.projKey = new NamespacedKey("warkit", "proj_type");
    }

    private WeaponConfig w() {
        return plugin.weaponConfig();
    }

    private void schedule(BukkitRunnable task, long delay, long period) {
        activeTasks.add(task);
        task.runTaskTimer(plugin.bukkitPlugin(), delay, period);
    }

    private void finishTask(BukkitRunnable task) {
        activeTasks.remove(task);
        task.cancel();
    }

    public void cleanupAll() {
        for (BukkitRunnable task : Set.copyOf(activeTasks)) task.cancel();
        activeTasks.clear();
        for (Entity entity : Set.copyOf(activeEntities)) {
            if (entity.isValid()) entity.remove();
        }
        activeEntities.clear();
    }

    // ------------------------------------------------------------------
    //  Thrown grenades: consumables from a stack.
    // ------------------------------------------------------------------

    public void throwGrenade(Player p, ItemStack item, String id) {
        if (SpectatorBlock.deny(p)) return;
        if (p.hasCooldown(item)) return;
        double speed = switch (id) {
            case Weapons.FRAG_GRENADE -> w().fragThrowSpeed;
            case Weapons.SMOKE_GRENADE -> w().smokeThrowSpeed;
            case Weapons.FLASH_GRENADE -> w().flashThrowSpeed;
            case Weapons.IMPULSE_GRENADE -> w().impulseThrowSpeed;
            case Weapons.SLEEP_GAS -> w().gasThrowSpeed;
            default -> 1.2;
        };
        Material visual = item.getType();
        Location eye = p.getEyeLocation();
        World world = p.getWorld();

        Item ent = world.dropItem(eye, ItemStack.of(visual));
        ent.setVelocity(eye.getDirection().multiply(speed).add(new Vector(0, 0.15, 0)));
        ent.setPickupDelay(Integer.MAX_VALUE);
        ent.setPersistent(false);
        ent.setUnlimitedLifetime(true);
        ent.setCanMobPickup(false);
        activeEntities.add(ent);

        item.setAmount(item.getAmount() - 1);
        p.setCooldown(item, 8); // light anti-spam
        world.playSound(eye, Sound.ENTITY_SNOWBALL_THROW, 1f, 0.8f);

        double fuse = switch (id) {
            case Weapons.FRAG_GRENADE -> w().fragFuseSeconds;
            case Weapons.SMOKE_GRENADE -> 1.5;
            case Weapons.FLASH_GRENADE -> 1.5;
            case Weapons.IMPULSE_GRENADE -> w().impulseFuseSeconds;
            case Weapons.SLEEP_GAS -> 1.5;
            default -> 2.0;
        };
        int fuseTicks = Math.max(4, (int) Math.round(fuse * 20));

        // Fuse ticking.
        BukkitRunnable fuseTask = new BukkitRunnable() {
            int t = 0;
            Location lastLoc = ent.getLocation();
            @Override public void run() {
                if (ent.isValid()) {
                    lastLoc = ent.getLocation();
                } else {
                    // If the grenade disappears early, detonate at its last known position.
                    activeEntities.remove(ent);
                    detonateGrenade(lastLoc, p, id);
                    finishTask(this);
                    return;
                }
                if (t % 6 == 0) {
                    ent.getWorld().playSound(ent.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.6f, 2f);
                    ent.getWorld().spawnParticle(Particle.SMOKE, ent.getLocation().add(0, 0.2, 0),
                            2, 0.05, 0.05, 0.05, 0.01);
                }
                if (++t >= fuseTicks) {
                    Location at = ent.getLocation();
                    ent.remove();
                    activeEntities.remove(ent);
                    detonateGrenade(at, p, id);
                    finishTask(this);
                }
            }
        };
        schedule(fuseTask, 1L, 1L);

        p.sendActionBar(Txt.t("Grenade thrown", NamedTextColor.YELLOW));
    }

    private void detonateGrenade(Location at, Player thrower, String id) {
        switch (id) {
            case Weapons.FRAG_GRENADE -> explode(at, thrower, w().fragRadius, w().fragDamage, false);
            case Weapons.SMOKE_GRENADE -> spawnSmoke(at, w().smokeRadius, w().smokeDurationSeconds);
            case Weapons.FLASH_GRENADE -> detonateFlash(at);
            case Weapons.IMPULSE_GRENADE -> detonateImpulse(at);
            case Weapons.SLEEP_GAS -> spawnGas(at, w().gasRadius, w().gasDurationSeconds, w().gasImmobilizeAfterSeconds);
            default -> { }
        }
    }

    // ------------------------------------------------------------------
    //  Grenade launcher: arcing projectile with bounce and timed detonation.
    // ------------------------------------------------------------------

    public void fireLauncher(Player p, ItemStack item) {
        if (SpectatorBlock.deny(p)) return;
        if (plugin.guns().isReloading(p)) return;
        WeaponConfig w = w();
        if (p.hasCooldown(item)) return;
        int ammo = plugin.weapons().getAmmo(item);
        if (ammo <= 0) {
            plugin.guns().startReload(p, Weapons.GRENADE_LAUNCHER, w.glMag, w.glReloadSeconds);
            return;
        }
        ammo--;
        plugin.weapons().setAmmo(item, ammo);
        p.getInventory().setItemInMainHand(item);
        p.setCooldown(item, w.glFireCooldownTicks);

        Vector vel = p.getEyeLocation().getDirection().multiply(w.glSpeed);
        vel.setY(vel.getY() + 0.25); // arc
        Location start = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.8));
        spawnBouncingGrenade(p, start, vel, (int) Math.round(w.glFuseSeconds * 20),
                w.glExplosionPower * 1.5, w.glExplosionPower * 4);

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.3f);
        p.spawnParticle(Particle.SMOKE, p.getEyeLocation(), 6, 0.1, 0.1, 0.1, 0.02);
        p.sendActionBar(plugin.guns().ammoBar(Weapons.GRENADE_LAUNCHER, ammo, w.glMag));
    }

    private void spawnBouncingGrenade(Player shooter, Location start, Vector vel,
                                      int fuseTicks, double radius, double damage) {
        World world = start.getWorld();
        // Projectile is a BlockDisplay that bounces along the trajectory and vanishes on explosion.
        float gs = 0.4f;
        BlockDisplay proj = world.spawn(start, BlockDisplay.class, d -> {
            d.setBlock(Material.NETHERITE_BLOCK.createBlockData());
            d.setPersistent(false);
            d.setTransformation(new Transformation(
                    new Vector3f(-gs / 2f, -gs / 2f, -gs / 2f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(gs, gs, gs),
                    new AxisAngle4f(0f, 0f, 0f, 1f)));
        });
        activeEntities.add(proj);

        final Vector v = vel.clone();
        final double damp = w().glBounceDamping;
        BukkitRunnable flightTask = new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!proj.isValid()) {
                    activeEntities.remove(proj);
                    finishTask(this);
                    return;
                }
                v.setY(v.getY() - 0.04); // gravity
                Location cur = proj.getLocation();
                boolean bounced = false;
                if (!cur.clone().add(v.getX(), 0, 0).getBlock().isPassable()) { v.setX(-v.getX() * damp); bounced = true; }
                if (!cur.clone().add(0, v.getY(), 0).getBlock().isPassable()) { v.setY(-v.getY() * damp); bounced = true; }
                if (!cur.clone().add(0, 0, v.getZ()).getBlock().isPassable()) { v.setZ(-v.getZ() * damp); bounced = true; }
                Location next = cur.add(v);
                proj.teleport(next);
                if (bounced) world.playSound(next, Sound.BLOCK_METAL_HIT, 0.5f, 1.4f);
                world.spawnParticle(Particle.SMOKE, next, 2, 0.04, 0.04, 0.04, 0.005);
                if (++t >= fuseTicks) {
                    Location at = proj.getLocation();
                    proj.remove();
                    activeEntities.remove(proj);
                    explode(at, shooter, radius, damage, false);
                    finishTask(this);
                }
            }
        };
        schedule(flightTask, 1L, 1L);
    }

    // ------------------------------------------------------------------
    //  Patriot: 8 rockets, no reload.
    // ------------------------------------------------------------------

    public void firePatriot(Player p, ItemStack item) {
        if (SpectatorBlock.deny(p)) return;
        if (p.hasCooldown(item)) return;
        WeaponConfig w = w();
        int ammo = plugin.weapons().getAmmo(item);
        if (ammo <= 0) {
            p.sendActionBar(Txt.t("Patriot empty - reload impossible", NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1f, 1f);
            return;
        }
        ammo--;
        plugin.weapons().setAmmo(item, ammo);
        p.getInventory().setItemInMainHand(item);
        p.setCooldown(item, w.patriotCooldownSeconds * 20);

        LockTarget lock = acquireLock(p, w.patriotLockRange, w.patriotLockConeDeg);
        Location start = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(1.7));
        ArmorStand missile = p.getWorld().spawn(start, ArmorStand.class, a -> {
            a.setMarker(false);
            a.setSmall(true);
            a.setInvisible(true);
            a.setGravity(false);
            a.setInvulnerable(false);
            a.setPersistent(false);
            a.setSilent(true);
            // No helmet item: non-head items render skewed there, so only the particle trail is shown.
            a.getPersistentDataContainer().set(projKey, PersistentDataType.STRING, "patriot_missile");
        });
        activeEntities.add(missile);
        final Vector vel = p.getEyeLocation().getDirection().multiply(w.patriotSpeed);
        final double speed = w.patriotSpeed;
        final double turn = w.patriotTurnRate;
        final int left = ammo;
        var ownVehicle = plugin.core().vehicles().riddenBy(p);
        final UUID ownVehicleId = ownVehicle == null ? null : ownVehicle.id();

        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.4f, 0.8f);
        p.sendActionBar(lock != null
                ? Txt.t("Lock acquired! Rockets left: " + left, NamedTextColor.RED)
                : Txt.t("Launch without lock. Rockets left: " + left, NamedTextColor.YELLOW));

        BukkitRunnable missileTask = new BukkitRunnable() {
            int life = 0;
            @Override public void run() {
                if (!missile.isValid()) {
                    activeEntities.remove(missile);
                    finishTask(this);
                    return;
                }
                if (++life > 200) {
                    missile.remove();
                    activeEntities.remove(missile);
                    finishTask(this);
                    return;
                }

                if (lock != null && lock.valid()) {
                    Location lockPoint = lock.point();
                    Vector desired = lockPoint == null ? null
                            : lockPoint.toVector().subtract(missile.getLocation().toVector());
                    if (desired != null && desired.lengthSquared() > 1e-6) {
                        desired.normalize().multiply(speed);
                        vel.add(desired.subtract(vel).multiply(turn));
                        double sp = vel.length();
                        if (sp > 1e-6) vel.multiply(speed / sp);
                    }
                }

                Location cur = missile.getLocation();
                Location next = cur.clone().add(vel);
                World world = missile.getWorld();
                world.spawnParticle(Particle.FLAME, cur, 3, 0.03, 0.03, 0.03, 0.01);
                world.spawnParticle(Particle.LARGE_SMOKE, cur, 2, 0.03, 0.03, 0.03, 0.01);

                if (next.getBlock().getType().isSolid()) {
                    if (life <= PATRIOT_ARM_TICKS) fizzlePatriot(cur, missile);
                    else detonatePatriot(cur, missile, null);
                    return;
                }
                if (life <= PATRIOT_ARM_TICKS) {
                    missile.teleport(next.setDirection(vel));
                    return;
                }
                VehicleHit vehicleImpact = plugin.core().combat().vehicleNear(cur, 1.3, ownVehicleId);
                if (vehicleImpact != null) {
                    detonatePatriot(vehicleImpact.point(), missile, vehicleImpact);
                    return;
                }
                for (Entity e : world.getNearbyEntities(cur, 1.3, 1.3, 1.3)) {
                    // Exclude the shooter so the rocket does not detonate immediately on launch.
                    if (!e.getUniqueId().equals(p.getUniqueId())
                            && GunService.isValidTarget(e, p)
                            && !TeamRules.sameSvoTeam(p, e)) {
                        detonatePatriot(cur, missile, null);
                        return;
                    }
                }
                missile.teleport(next.setDirection(vel));
            }

            private void detonatePatriot(Location at, ArmorStand m, VehicleHit directVehicle) {
                // Explosion is attributed to the shooter but the shooter is immune.
                if (directVehicle != null && w().patriotVehicleDirectDamage > 0) {
                    plugin.core().combat().directDamage(directVehicle.vehicle(), w().patriotVehicleDirectDamage);
                }
                explode(at, p, w().patriotExplosionPower * 1.5, w().patriotExplosionPower * 5, false,
                        p.getUniqueId());
                m.remove();
                activeEntities.remove(m);
                finishTask(this);
            }

            private void fizzlePatriot(Location at, ArmorStand m) {
                World world = at.getWorld();
                world.spawnParticle(Particle.SMOKE, at, 12, 0.18, 0.18, 0.18, 0.02);
                world.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 0.9f, 1.4f);
                m.remove();
                activeEntities.remove(m);
                finishTask(this);
            }
        };
        schedule(missileTask, 1L, 1L);
    }

    private LockTarget acquireLock(Player shooter, double range, double coneDeg) {
        Vector aim = shooter.getEyeLocation().getDirection();
        Location eye = shooter.getEyeLocation();
        double bestDot = Math.cos(Math.toRadians(coneDeg));
        LockTarget best = null;
        for (Entity e : shooter.getNearbyEntities(range, range, range)) {
            if (!GunService.isValidTarget(e, shooter)) continue;
            if (TeamRules.sameSvoTeam(shooter, e)) continue;
            LivingEntity le = (LivingEntity) e;
            Vector to = le.getEyeLocation().toVector().subtract(eye.toVector());
            double dist = to.length();
            if (dist < 1e-3 || dist > range) continue;
            double dot = aim.dot(to.multiply(1.0 / dist));
            if (dot > bestDot && shooter.hasLineOfSight(le)) {
                bestDot = dot;
                best = new LockTarget(le, null);
            }
        }
        var ownVehicle = plugin.core().vehicles().riddenBy(shooter);
        UUID excludedVehicle = ownVehicle == null ? null : ownVehicle.id();
        for (VehicleHit hit : plugin.core().combat().vehiclesNear(eye, range, excludedVehicle)) {
            Vector to = hit.point().toVector().subtract(eye.toVector());
            double dist = to.length();
            if (dist < 1e-3 || dist > range) continue;
            Vector dir = to.clone().multiply(1.0 / dist);
            double dot = aim.dot(dir);
            if (dot <= bestDot) continue;
            if (eye.getWorld().rayTraceBlocks(eye, dir, dist, FluidCollisionMode.NEVER, true) != null) {
                continue;
            }
            bestDot = dot;
            best = new LockTarget(null, hit.vehicle());
        }
        return best;
    }

    // ------------------------------------------------------------------
    //  Detonation effects
    // ------------------------------------------------------------------

    /** Shrapnel-free explosion: falloff damage plus knockback, without block destruction. */
    public void explode(Location center, Player source, double radius, double maxDamage, boolean fire) {
        explode(center, source, radius, maxDamage, fire, null);
    }

    /** Same as above, but one entity is immune, for example the Patriot shooter. */
    public void explode(Location center, Player source, double radius, double maxDamage, boolean fire, UUID immune) {
        World world = center.getWorld();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 30, radius * 0.4, radius * 0.4, radius * 0.4, 0.05);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.4f, 1f);

        for (Entity e : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!affectable(e)) continue;
            if (immune != null && e.getUniqueId().equals(immune)) continue;
            LivingEntity le = (LivingEntity) e;
            double dist = le.getLocation().add(0, le.getHeight() * 0.5, 0).distance(center);
            if (dist > radius) continue;
            double falloff = Math.max(0.2, 1.0 - dist / radius);
            boolean canDamage = TeamRules.canDamage(source, le);
            if (canDamage) {
                if (source != null) le.damage(maxDamage * falloff, source);
                else le.damage(maxDamage * falloff);
            }
            if (canDamage && fire && le.getFireTicks() < 40) le.setFireTicks(60);

            Vector kb = le.getLocation().toVector().subtract(center.toVector());
            if (kb.lengthSquared() < 1e-4) kb = new Vector(0, 1, 0);
            kb.normalize().multiply(0.9 * falloff).setY(0.35 * falloff + 0.25);
            le.setVelocity(le.getVelocity().add(kb));
        }
        plugin.core().combat().radiusDamage(center, radius, maxDamage, null, null);
    }

    private void spawnSmoke(Location center, double radius, double durationSeconds) {
        World world = center.getWorld();
        int durTicks = (int) Math.round(durationSeconds * 20);
        world.playSound(center, Sound.ENTITY_TNT_PRIMED, 1f, 1.4f);
        BukkitRunnable smokeTask = new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= durTicks) {
                    finishTask(this);
                    return;
                }
                ThreadLocalRandom r = ThreadLocalRandom.current();
                // Dense sphere fill.
                for (int i = 0; i < 60; i++) {
                    double dx = (r.nextDouble() * 2 - 1) * radius;
                    double dy = (r.nextDouble() * 2 - 1) * radius * 0.7 + radius * 0.4;
                    double dz = (r.nextDouble() * 2 - 1) * radius;
                    if (dx * dx + dz * dz > radius * radius) continue;
                    world.spawnParticle(Particle.LARGE_SMOKE,
                            center.getX() + dx, center.getY() + dy, center.getZ() + dz,
                            0, 0, 0.01, 0, 0.001);
                }
                for (int i = 0; i < 20; i++) {
                    double dx = (r.nextDouble() * 2 - 1) * radius;
                    double dz = (r.nextDouble() * 2 - 1) * radius;
                    world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                            center.getX() + dx, center.getY() + 0.2, center.getZ() + dz,
                            0, 0, 0.02, 0, 0.002);
                }
                double r2 = radius * radius;
                for (Player pl : world.getPlayers()) {
                    if (pl.getLocation().distanceSquared(center) <= r2) {
                        pl.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                                30, 0, true, false, false));
                    }
                }
                t += 4;
            }
        };
        schedule(smokeTask, 0L, 4L);
    }

    private void detonateFlash(Location center) {
        World world = center.getWorld();
        world.spawnParticle(Particle.FLASH, center, 4);
        world.spawnParticle(Particle.FIREWORK, center, 70, 0.5, 0.5, 0.5, 0.35);
        world.spawnParticle(Particle.END_ROD, center, 50, 0.3, 0.3, 0.3, 0.45);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 2f);
        world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.4f, 2f);

        double radius = w().flashRadius;
        double r2 = radius * radius;
        for (Player pl : world.getPlayers()) {
            if (pl.getGameMode() == GameMode.SPECTATOR || pl.getGameMode() == GameMode.CREATIVE) continue;
            double distSq = pl.getEyeLocation().distanceSquared(center);
            if (distSq > r2) continue;

            Vector dir = center.toVector().subtract(pl.getEyeLocation().toVector());
            double dist = dir.length();
            if (dist < 1e-3) dist = 1e-3;
            Vector toFlash = dir.clone().multiply(1.0 / dist);

            // Whether the player is looking toward the flash.
            double facing = pl.getEyeLocation().getDirection().dot(toFlash);
            if (facing <= 0.05) continue; // looked away and avoided it

            // A wall blocks the flash.
            RayTraceResult block = world.rayTraceBlocks(pl.getEyeLocation(), toFlash, dist,
                    FluidCollisionMode.NEVER, true);
            if (block != null && block.getHitBlock() != null) continue;

            double distFactor = Math.max(0.35, 1.0 - Math.sqrt(distSq) / radius);
            double strength = facing * distFactor;
            int blindTicks = Math.max(50, (int) (w().flashBlindSeconds * 20 * strength));
            pl.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindTicks, 0, true, false, false));
            pl.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,
                    (int) (blindTicks * 1.5), 0, true, false, false));
            pl.playSound(pl.getLocation(), Sound.ITEM_ELYTRA_FLYING, 1.2f, 2f);
        }
    }

    private void detonateImpulse(Location center) {
        World world = center.getWorld();
        world.spawnParticle(Particle.SONIC_BOOM, center, 1);
        world.spawnParticle(Particle.GUST, center, 1);
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1.5f);

        double radius = w().impulseRadius;
        double radiusSquared = radius * radius;
        for (Player player : world.getPlayers()) {
            if (!affectable(player)) continue;
            if (player.getLocation().distanceSquared(center) > radiusSquared) continue;
            pushPlayer(player);
        }
    }

    private void pushPlayer(Player player) {
        Vector push = player.getEyeLocation().getDirection();
        push.setY(0);
        if (push.lengthSquared() < 1e-4) push = new Vector(0, 0, 1);
        else push.normalize();
        push.multiply(w().impulseForward);
        push.setY(w().impulseUp);
        plugin.fallImmunity().grant(player.getUniqueId(), w().impulseNoFallSeconds);
        player.sendActionBar(Txt.t("Impulse push!", NamedTextColor.AQUA));
        applyVelocityBurst(player, push);
    }

    private void applyVelocityBurst(Player player, Vector velocity) {
        Vector v = velocity.clone();
        player.setVelocity(v);
        // Repeat on ticks 1 and 2; ground friction often cancels the first push.
        plugin.getServer().getScheduler().runTaskLater(plugin.bukkitPlugin(), () -> {
            if (player.isOnline() && !player.isDead()) player.setVelocity(v);
        }, 1L);
        plugin.getServer().getScheduler().runTaskLater(plugin.bukkitPlugin(), () -> {
            if (player.isOnline() && !player.isDead()) player.setVelocity(v);
        }, 2L);
    }

    // ------------------------------------------------------------------
    //  Molotov cocktail: contact detonation that spreads fire.
    // ------------------------------------------------------------------

    public void throwMolotov(Player p, ItemStack item) {
        if (SpectatorBlock.deny(p)) return;
        if (p.hasCooldown(item)) return;
        Snowball molotov = p.launchProjectile(Snowball.class,
                p.getEyeLocation().getDirection().multiply(w().molotovThrowSpeed),
                s -> {
                    s.getPersistentDataContainer().set(projKey, PersistentDataType.STRING, "molotov");
                    s.setItem(ItemStack.of(Material.BLAZE_POWDER));
                });
        activeEntities.add(molotov);
        item.setAmount(item.getAmount() - 1);
        p.setCooldown(item, 12);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 0.9f);
        p.sendActionBar(Txt.t("Molotov thrown", NamedTextColor.YELLOW));
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent e) {
        Projectile proj = e.getEntity();
        if (!"molotov".equals(proj.getPersistentDataContainer().get(projKey, PersistentDataType.STRING))) return;
        Player src = proj.getShooter() instanceof Player pl ? pl : null;
        Location at = e.getHitBlock() != null
                ? e.getHitBlock().getLocation().add(0.5, 1.0, 0.5)
                : proj.getLocation();
        igniteArea(at, w().molotovRadius, src);
        activeEntities.remove(proj);
        proj.remove();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPatriotMissileDamage(EntityDamageByEntityEvent e) {
        if (!"patriot_missile".equals(e.getEntity().getPersistentDataContainer()
                .get(projKey, PersistentDataType.STRING))) {
            return;
        }
        e.setCancelled(true);
        if (e.getDamager() instanceof Player p && SpectatorBlock.deny(p)) return;
        Entity missile = e.getEntity();
        Location at = missile.getLocation();
        activeEntities.remove(missile);
        missile.remove();
        at.getWorld().spawnParticle(Particle.SMOKE, at, 16, 0.2, 0.2, 0.2, 0.03);
        at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 10, 0.15, 0.15, 0.15, 0.02);
        at.getWorld().playSound(at, Sound.ENTITY_ITEM_BREAK, 1f, 1.4f);
        if (e.getDamager() instanceof Player p) {
            p.sendActionBar(Txt.t("Rocket shot down", NamedTextColor.GREEN));
        }
    }

    private void igniteArea(Location center, double radius, Player source) {
        World world = center.getWorld();
        world.spawnParticle(Particle.FLAME, center, 40, radius * 0.5, 0.3, radius * 0.5, 0.02);
        world.spawnParticle(Particle.LAVA, center, 8, radius * 0.4, 0.2, radius * 0.4, 0);
        world.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.8f);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);

        int ri = (int) Math.ceil(radius);
        double r2 = radius * radius;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                if (dx * dx + dz * dz > r2) continue;
                if (rnd.nextDouble() > 0.6) continue; // patchy fire, not a solid carpet
                for (int dy = 2; dy >= -2; dy--) {
                    Block b = world.getBlockAt(center.getBlockX() + dx, center.getBlockY() + dy, center.getBlockZ() + dz);
                    if (b.isPassable() && b.getType() != Material.FIRE
                            && b.getRelative(BlockFace.DOWN).getType().isSolid()) {
                        b.setType(Material.FIRE, true);
                        break;
                    }
                }
            }
        }
        for (Entity e : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!affectable(e)) continue;
            LivingEntity le = (LivingEntity) e;
            if (le.getLocation().distance(center) > radius + 1) continue;
            if (!TeamRules.canDamage(source, le)) continue;
            le.setFireTicks(Math.max(le.getFireTicks(), w().molotovFireSeconds));
            if (source != null) le.damage(2.0, source);
            else le.damage(2.0);
        }
        plugin.core().combat().radiusDamage(center, radius + 1.0, 2.0, null, null);
    }

    // ------------------------------------------------------------------
    //  Sleep gas: blindness/fatigue cloud, then immobilization.
    // ------------------------------------------------------------------

    private void spawnGas(Location center, double radius, int durationSeconds, int immobilizeAfterSeconds) {
        World world = center.getWorld();
        int durTicks = durationSeconds * 20;
        int immobTicks = immobilizeAfterSeconds * 20;
        double r2 = radius * radius;
        Map<UUID, Integer> inside = new HashMap<>();
        world.playSound(center, Sound.ENTITY_TNT_PRIMED, 1f, 1.2f);

        BukkitRunnable gasTask = new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= durTicks) {
                    finishTask(this);
                    return;
                }
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (int i = 0; i < 45; i++) {
                    double dx = (r.nextDouble() * 2 - 1) * radius;
                    double dy = (r.nextDouble() * 2 - 1) * radius * 0.6 + radius * 0.4;
                    double dz = (r.nextDouble() * 2 - 1) * radius;
                    if (dx * dx + dz * dz > r2) continue;
                    world.spawnParticle(Particle.SNEEZE,
                            center.getX() + dx, center.getY() + dy, center.getZ() + dz, 0, 0, 0.01, 0, 0.001);
                    if (i % 3 == 0) {
                        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                                center.getX() + dx, center.getY() + dy, center.getZ() + dz, 0, 0, 0.01, 0, 0.002);
                    }
                }
                Set<UUID> still = new HashSet<>();
                for (Player pl : world.getPlayers()) {
                    GameMode gm = pl.getGameMode();
                    if (gm == GameMode.SPECTATOR || gm == GameMode.CREATIVE) continue;
                    if (pl.getLocation().distanceSquared(center) > r2) continue;
                    if (plugin.items().isWearingHelmet(pl, WarItems.GAS_MASK)) continue; // gas mask protects
                    still.add(pl.getUniqueId());
                    int ticks = inside.merge(pl.getUniqueId(), 5, Integer::sum);
                    pl.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, true, false, false));
                    pl.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 2, true, false, false));
                    if (ticks >= immobTicks) {
                        pl.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 6, true, false, false));
                        pl.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 2, true, false, false));
                        pl.sendActionBar(Txt.t("Gas immobilized you - cannot move", NamedTextColor.RED));
                    } else {
                        pl.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, true, false, false));
                        pl.sendActionBar(Txt.t("Sleep gas - move out!", NamedTextColor.AQUA));
                    }
                }
                inside.keySet().removeIf(u -> !still.contains(u));
                t += 5;
            }
        };
        schedule(gasTask, 0L, 5L);
    }

    /** Target eligible for AoE effects, including the thrower. */
    public static boolean affectable(Entity e) {
        if (!(e instanceof LivingEntity)) return false;
        if (e instanceof ArmorStand || e instanceof Display || e instanceof Interaction) return false;
        if (e instanceof Player pl) {
            return switch (pl.getGameMode()) {
                case SPECTATOR, CREATIVE -> false;
                default -> true;
            };
        }
        return true;
    }
}
