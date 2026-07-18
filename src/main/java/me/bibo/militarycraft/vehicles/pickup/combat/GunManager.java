/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  org.bukkit.Color
 *  org.bukkit.FluidCollisionMode
 *  org.bukkit.Location
 *  org.bukkit.Particle
 *  org.bukkit.Particle$DustOptions
 *  org.bukkit.Sound
 *  org.bukkit.World
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.util.RayTraceResult
 *  org.bukkit.util.Vector
 */
package me.bibo.militarycraft.vehicles.pickup.combat;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import me.bibo.militarycraft.core.combat.VehicleHit;
import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import me.bibo.militarycraft.vehicles.pickup.PickupRuntime;
import me.bibo.militarycraft.vehicles.pickup.config.PickupConfig;
import me.bibo.militarycraft.vehicles.pickup.vehicle.Pickup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class GunManager {
    private static final Particle.DustOptions TRACER_DUST = new Particle.DustOptions(Color.fromRGB((int)255, (int)214, (int)120), 0.55f);

    private GunManager() {
    }

    public static boolean fire(PickupRuntime plugin, Pickup pickup, Player gunner) {
        Location impact;
        PickupConfig cfg = plugin.config();
        if (pickup.gunLock() > 0 || pickup.gunCooldown() > 0 || pickup.isOverheated()) {
            return false;
        }
        Location muzzle = pickup.muzzleLocation();
        Vector aimDir = GunManager.directionFromYawPitch(pickup.gunYaw(), pickup.gunPitch());
        if (cfg.spreadDegrees > 0.0) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            double t = Math.tan(Math.toRadians(cfg.spreadDegrees)) * 0.5;
            aimDir.add(new Vector(rng.nextGaussian() * t, rng.nextGaussian() * t, rng.nextGaussian() * t));
            if (aimDir.lengthSquared() > 1.0E-6) {
                aimDir.normalize();
            }
        }
        World world = muzzle.getWorld();
        world.spawnParticle(Particle.FLAME, muzzle, 6, 0.05, 0.05, 0.05, 0.02);
        world.spawnParticle(Particle.SMOKE, muzzle, 3, 0.06, 0.06, 0.06, 0.01);
        world.spawnParticle(Particle.FLASH, muzzle, 1, 0.0, 0.0, 0.0, 0.0);
        world.playSound(muzzle, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 1.9f);
        world.playSound(muzzle, Sound.ITEM_CROSSBOW_SHOOT, 2.0f, 1.6f);
        Hit hit = GunManager.trace(plugin, world, pickup, muzzle, aimDir, cfg.bulletRange);
        Location location = impact = hit != null ? hit.location() : muzzle.clone().add(aimDir.clone().multiply(cfg.bulletRange));
        if (cfg.tracerEffects) {
            GunManager.drawTracer(world, muzzle, impact);
        }
        if (hit != null) {
            GunManager.impactFx(world, hit.location());
            if (hit.vehicle() != null) {
                plugin.bukkitPlugin().core().combat().directDamage(hit.vehicle(), cfg.pickupDamage);
            } else if (hit.living() != null) {
                hit.living().damage(cfg.bulletDamage, (Entity)gunner);
            }
        }
        pickup.setGunCooldown(cfg.fireCooldownTicks);
        if (pickup.recordShotAndCheckOverheat(cfg)) {
            GunManager.overheatFx(world, muzzle, gunner);
        }
        return true;
    }

    private static void overheatFx(World world, Location muzzle, Player gunner) {
        world.spawnParticle(Particle.CLOUD, muzzle, 25, 0.14, 0.14, 0.14, 0.03);
        world.playSound(muzzle, Sound.BLOCK_FIRE_EXTINGUISH, 1.2f, 0.6f);
        world.playSound(muzzle, Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1.0f, 0.7f);
        if (gunner != null) {
            gunner.sendActionBar((Component)Component.text((String)"\ud83d\udd25 Machine gun overheated!", (TextColor)NamedTextColor.RED));
        }
    }

    private static Vector directionFromYawPitch(double yawDeg, double pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double x = -Math.sin(yaw) * Math.cos(pitch);
        double y = -Math.sin(pitch);
        double z = Math.cos(yaw) * Math.cos(pitch);
        return new Vector(x, y, z).normalize();
    }

    private static Hit trace(PickupRuntime plugin, World world, Pickup shooter, Location from, Vector direction, double range) {
        RayTraceResult blockResult = world.rayTraceBlocks(from, direction, range, FluidCollisionMode.NEVER, true);
        double blockDist = blockResult != null && blockResult.getHitPosition() != null ? from.toVector().distance(blockResult.getHitPosition()) : Double.MAX_VALUE;
        Hit best = blockResult != null && blockResult.getHitPosition() != null
                ? new Hit(GunManager.vectorLocation(world, blockResult.getHitPosition()), null, null)
                : null;
        double bestDistance = blockDist;

        VehicleHit vehicleHit = plugin.bukkitPlugin().core().combat().rayTrace(
                from, direction, range, 0.4, shooter.id());
        if (vehicleHit != null && vehicleHit.distance() <= bestDistance) {
            best = new Hit(vehicleHit.point(), vehicleHit.vehicle(), null);
            bestDistance = vehicleHit.distance();
        }

        RayTraceResult entityResult = world.rayTraceEntities(
                from, direction, range, 0.4, e -> GunManager.canHit(plugin, shooter, e));
        if (entityResult != null && entityResult.getHitEntity() instanceof LivingEntity living) {
            Vector hitPos = entityResult.getHitPosition() != null
                    ? entityResult.getHitPosition()
                    : living.getLocation().toVector();
            double entityDistance = from.toVector().distance(hitPos);
            if (entityDistance <= bestDistance) {
                best = new Hit(GunManager.vectorLocation(world, hitPos), null, living);
            }
        }
        return best;
    }

    private static boolean canHit(PickupRuntime plugin, Pickup shooter, Entity e) {
        if (plugin.bukkitPlugin().core().vehicles().vehicleOf(e) != null) {
            return false;
        }
        if (e instanceof LivingEntity) {
            LivingEntity living = (LivingEntity)e;
            return !GunManager.isSameCrew(shooter, living.getUniqueId());
        }
        return false;
    }

    private static boolean isSameCrew(Pickup pickup, UUID uid) {
        return uid.equals(pickup.driver()) || uid.equals(pickup.passenger()) || uid.equals(pickup.gunner());
    }

    private static Location vectorLocation(World world, Vector v) {
        return new Location(world, v.getX(), v.getY(), v.getZ());
    }

    private static void drawTracer(World world, Location from, Location to) {
        Vector delta = to.toVector().subtract(from.toVector());
        double len = delta.length();
        if (len < 0.1) {
            return;
        }
        Vector step = delta.multiply(1.0 / len);
        int points = (int)Math.min(40.0, Math.floor(len / 1.3));
        Location cursor = from.clone();
        for (int i = 0; i < points; ++i) {
            cursor.add(step.clone().multiply(1.3));
            world.spawnParticle(Particle.DUST, cursor, 1, 0.0, 0.0, 0.0, 0.0, (Object)TRACER_DUST);
        }
    }

    private static void impactFx(World world, Location at) {
        world.spawnParticle(Particle.CRIT, at, 6, 0.2, 0.2, 0.2, 0.08);
        world.spawnParticle(Particle.SMOKE, at, 4, 0.15, 0.15, 0.15, 0.01);
        world.playSound(at, Sound.ENTITY_IRON_GOLEM_HURT, 0.5f, 1.5f);
    }

    private record Hit(Location location, VehicleHandle vehicle, LivingEntity living) {
    }
}
