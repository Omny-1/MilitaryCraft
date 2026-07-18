package me.bibo.militarycraft.gear.warkit.weapon;

import me.bibo.militarycraft.core.combat.VehicleHit;
import me.bibo.militarycraft.core.vehicle.DisplayVehicle;
import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import me.bibo.militarycraft.gear.warkit.Txt;
import me.bibo.militarycraft.gear.warkit.TeamRules;
import me.bibo.militarycraft.gear.warkit.SpectatorBlock;
import me.bibo.militarycraft.gear.warkit.WarKitRuntime;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Auto sentry: static base plus a rotating gun head that tracks and fires at targets. */
public final class SentryManager {

    private static final Particle.DustOptions TRACER =
            new Particle.DustOptions(Color.fromRGB(0xFF6A3D), 0.7f);
    private static final Particle.DustOptions LOCK_BEAM =
            new Particle.DustOptions(Color.fromRGB(0xFF1E1E), 0.55f);
    private static final int TASK_PERIOD = 2;       // task tick for smooth rotation
    private static final float TURN_STEP = 0.30f;   // max head turn per tick, radians
    private static final float FIRE_CONE = 0.45f;   // fires once aimed inside this cone, radians

    /** Rotating head beam: display plus local box, with forward = +X. */
    private static final class HeadPart {
        final BlockDisplay display;
        final float cf, cu, cr, hf, hu, hr;
        HeadPart(BlockDisplay d, float cf, float cu, float cr, float hf, float hu, float hr) {
            this.display = d;
            this.cf = cf; this.cu = cu; this.cr = cr;
            this.hf = hf; this.hu = hu; this.hr = hr;
        }
    }

    private static final class Sentry {
        final UUID owner;
        final Location loc;                          // rotation axis at the base center
        final List<Entity> base = new ArrayList<>(); // static parts
        final List<HeadPart> head = new ArrayList<>(); // rotating head
        Interaction hitbox;
        int ammo;
        long expireAt;
        BukkitTask task;
        float yaw;        // current head direction, radians
        float desiredYaw; // desired look direction
        int shotTimer;
        double health;

        Sentry(UUID owner, Location loc, int ammo, long expireAt, double health) {
            this.owner = owner;
            this.loc = loc;
            this.ammo = ammo;
            this.expireAt = expireAt;
            this.health = health;
        }
    }

    private record SentryTarget(LivingEntity living, VehicleHandle vehicle) {
        Location point() {
            if (living != null) {
                return living.getEyeLocation();
            }
            if (vehicle == null || !vehicle.isActive() || vehicle.location() == null) {
                return null;
            }
            double height = vehicle instanceof DisplayVehicle display
                    ? Math.max(0.5, display.model().height() * 0.5)
                    : 1.0;
            return vehicle.location().clone().add(0, height, 0);
        }
    }

    private final WarKitRuntime plugin;
    private final List<Sentry> sentries = new ArrayList<>();

    public SentryManager(WarKitRuntime plugin) {
        this.plugin = plugin;
    }

    private WeaponConfig w() {
        return plugin.weaponConfig();
    }

    public boolean deploy(Player owner, Location at) {
        if (SpectatorBlock.deny(owner)) return false;
        WeaponConfig w = w();
        long count = sentries.stream().filter(s -> s.owner.equals(owner.getUniqueId())).count();
        if (count >= w.sentryMaxPerPlayer) {
            owner.sendActionBar(Txt.t("Turret limit reached", NamedTextColor.YELLOW));
            return false;
        }
        World world = at.getWorld();
        Vector fwd = cardinal(owner);
        Vector right = new Vector(-fwd.getZ(), 0, fwd.getX());

        Sentry s = new Sentry(owner.getUniqueId(), at.clone(),
                w.sentryAmmo, System.currentTimeMillis() + w.sentryLifeSeconds * 1000L, w.sentryHealth);
        Vector look = owner.getLocation().getDirection();
        s.yaw = (float) Math.atan2(-look.getZ(), look.getX());
        s.desiredYaw = s.yaw;

        // --- static base: plate, 3 supports, and central post ---
        s.base.add(part(world, at, fwd, right, 0.0, 0.0, 0.0, 0.95, 0.12, 0.95, Material.DEEPSLATE_TILES));
        s.base.add(part(world, at, fwd, right, 0.34, 0.0, 0.1, 0.14, 0.42, 0.14, Material.DARK_OAK_LOG));
        s.base.add(part(world, at, fwd, right, -0.28, 0.3, 0.1, 0.14, 0.42, 0.14, Material.DARK_OAK_LOG));
        s.base.add(part(world, at, fwd, right, -0.28, -0.3, 0.1, 0.14, 0.42, 0.14, Material.DARK_OAK_LOG));
        s.base.add(part(world, at, fwd, right, 0.0, 0.0, 0.42, 0.42, 0.38, 0.42, Material.IRON_BLOCK));

        // --- rotating head: forward=+X, up=+Y, right=+Z; center plus half-extents ---
        s.head.add(head(world, at, 0.05f, 1.03f, 0.0f, 0.31f, 0.25f, 0.34f, Material.NETHERITE_BLOCK)); // body
        s.head.add(head(world, at, 0.05f, 1.03f, 0.40f, 0.25f, 0.23f, 0.05f, Material.COPPER_BLOCK));   // armor L
        s.head.add(head(world, at, 0.05f, 1.03f, -0.40f, 0.25f, 0.23f, 0.05f, Material.COPPER_BLOCK));  // armor R
        s.head.add(head(world, at, 0.08f, 1.38f, 0.0f, 0.17f, 0.10f, 0.21f, Material.DEEPSLATE_TILES)); // dome
        s.head.add(head(world, at, 0.34f, 1.07f, 0.0f, 0.05f, 0.09f, 0.17f, Material.REDSTONE_BLOCK));  // eye
        s.head.add(head(world, at, 0.36f, 1.03f, 0.0f, 0.12f, 0.17f, 0.26f, Material.COAL_BLOCK));      // face shield
        s.head.add(head(world, at, 0.85f, 0.945f, 0.15f, 0.40f, 0.065f, 0.065f, Material.IRON_BLOCK));  // barrel L
        s.head.add(head(world, at, 0.85f, 0.945f, -0.15f, 0.40f, 0.065f, 0.065f, Material.IRON_BLOCK)); // barrel R
        s.head.add(head(world, at, 1.28f, 0.965f, 0.15f, 0.08f, 0.085f, 0.085f, Material.COAL_BLOCK));  // muzzle L
        s.head.add(head(world, at, 1.28f, 0.965f, -0.15f, 0.08f, 0.085f, 0.085f, Material.COAL_BLOCK)); // muzzle R
        s.head.add(head(world, at, -0.28f, 1.15f, 0.0f, 0.17f, 0.23f, 0.28f, Material.COPPER_BLOCK));   // drum
        s.head.add(head(world, at, -0.12f, 1.55f, 0.32f, 0.03f, 0.25f, 0.03f, Material.COAL_BLOCK));    // antenna
        updateHead(s);

        s.hitbox = world.spawn(at.clone().add(0, 0.5, 0), Interaction.class, in -> {
            in.setInteractionWidth(1.1f);
            in.setInteractionHeight(1.5f);
            in.setResponsive(true);
            in.setPersistent(false);
        });
        sentries.add(s);

        world.playSound(at, Sound.BLOCK_NETHERITE_BLOCK_PLACE, 1f, 1.2f);
        world.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.8f);
        owner.sendActionBar(Txt.t("Auto sentry deployed", NamedTextColor.GOLD));

        s.task = plugin.getServer().getScheduler().runTaskTimer(plugin.bukkitPlugin(), () -> tickSentry(s), TASK_PERIOD, TASK_PERIOD);
        return true;
    }

    private void tickSentry(Sentry s) {
        if (s.hitbox == null || !s.hitbox.isValid() || s.ammo <= 0
                || System.currentTimeMillis() >= s.expireAt) {
            destroy(s, true);
            return;
        }
        WeaponConfig w = w();
        World world = s.loc.getWorld();
        Location sensor = s.loc.clone().add(0, 1.0, 0);

        Player ownerP = Bukkit.getPlayer(s.owner);
        SentryTarget target = acquire(sensor, w.sentryRange, ownerP);

        if (target != null) {
            Location targetPoint = target.point();
            if (targetPoint != null) {
                s.desiredYaw = yawTo(s.loc, targetPoint);
            }
            s.yaw = slew(s.yaw, s.desiredYaw, TURN_STEP);
        }
        updateHead(s);

        if (s.health <= w.sentryHealth * 0.35 && ThreadLocalRandom.current().nextInt(3) == 0) {
            world.spawnParticle(Particle.LARGE_SMOKE, s.loc.clone().add(0, 1.0, 0),
                    1, 0.12, 0.08, 0.12, 0.01);
            world.spawnParticle(Particle.ELECTRIC_SPARK, s.loc.clone().add(0, 1.05, 0),
                    1, 0.18, 0.12, 0.18, 0.01);
        }

        if (target == null) {
            if (ThreadLocalRandom.current().nextInt(8) == 0) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, sensor, 1, 0.1, 0.1, 0.1, 0);
            }
            return;
        }

        s.shotTimer += TASK_PERIOD;
        if (s.shotTimer % 8 == 0) {
            drawLockBeam(s, target);
        }
        boolean aligned = Math.abs(wrapPi(s.desiredYaw - s.yaw)) < FIRE_CONE;
        if (aligned && s.shotTimer >= w.sentryFireCooldownTicks) {
            s.shotTimer = 0;
            fire(s, target, ownerP, world);
        }
    }

    private void fire(Sentry s, SentryTarget target, Player ownerP, World world) {
        WeaponConfig w = w();
        double fc = Math.cos(s.yaw), fz = -Math.sin(s.yaw);
        Location muzzle = s.loc.clone().add(fc * 1.35, 0.95, fz * 1.35);
        Location targetPoint = target.point();
        if (targetPoint == null) return;
        Vector base = targetPoint.toVector().subtract(muzzle.toVector());
        if (base.lengthSquared() < 1e-4) return;
        Vector dir = applySpread(base.normalize(), w.sentrySpreadDeg);

        RayTraceResult res = world.rayTrace(muzzle, dir, w.sentryRange, FluidCollisionMode.NEVER,
                true, 0.3, e -> GunService.isValidTarget(e, ownerP)
                        && (ownerP == null || !TeamRules.sameSvoTeam(ownerP, e)));
        var ownerVehicle = ownerP == null ? null : plugin.core().vehicles().riddenBy(ownerP);
        VehicleHit vehicleHit = plugin.core().combat().rayTrace(muzzle, dir, w.sentryRange, 0.35,
                ownerVehicle == null ? null : ownerVehicle.id());
        double vanillaDistance = res != null && res.getHitPosition() != null
                ? muzzle.toVector().distance(res.getHitPosition())
                : Double.POSITIVE_INFINITY;
        Vector end;
        if (vehicleHit != null && vehicleHit.distance() <= vanillaDistance) {
            double vehicleDamage = w.sentryDamage * w.vehicleBulletDamageMultiplier;
            if (plugin.core().combat().directDamage(vehicleHit.vehicle(), vehicleDamage)) {
                world.playSound(vehicleHit.point(), Sound.ITEM_SHIELD_BLOCK, 0.7f, 0.75f);
                world.spawnParticle(Particle.CRIT, vehicleHit.point(), 5, 0.14, 0.14, 0.14, 0.03);
            }
            end = vehicleHit.point().toVector();
        } else if (res != null && res.getHitEntity() instanceof LivingEntity le) {
            if (TeamRules.canDamage(ownerP, le)) {
                if (ownerP != null) le.damage(w.sentryDamage, ownerP);
                else le.damage(w.sentryDamage);
            }
            end = res.getHitPosition();
            world.playSound(le.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.2f);
        } else if (res != null && res.getHitPosition() != null) {
            end = res.getHitPosition();
        } else {
            end = muzzle.toVector().add(dir.multiply(w.sentryRange));
        }

        drawTracer(world, muzzle.toVector(), end);
        world.playSound(muzzle, Sound.ENTITY_GENERIC_EXPLODE, 0.35f, 1.8f);
        world.spawnParticle(Particle.FLASH, muzzle.clone().add(dir.clone().multiply(0.4)), 1, 0, 0, 0, 0);
        s.ammo--;
    }

    /** Rotates every head beam around the vertical axis using the current yaw. */
    private void updateHead(Sentry s) {
        Quaternionf rot = new Quaternionf().rotateY(s.yaw);
        for (HeadPart hp : s.head) {
            if (!hp.display.isValid()) continue;
            Vector3f scale = new Vector3f(hp.hf * 2f, hp.hu * 2f, hp.hr * 2f);
            Vector3f tr = rot.transform(new Vector3f(hp.cf - hp.hf, hp.cu - hp.hu, hp.cr - hp.hr));
            hp.display.setTransformation(new Transformation(tr, new Quaternionf(rot), scale, new Quaternionf()));
        }
    }

    private float yawTo(Location pivot, Location target) {
        double dx = target.getX() - pivot.getX();
        double dz = target.getZ() - pivot.getZ();
        if (dx * dx + dz * dz < 1e-6) return 0f;
        return (float) Math.atan2(-dz, dx);
    }

    private float slew(float cur, float desired, float step) {
        float diff = wrapPi(desired - cur);
        if (Math.abs(diff) <= step) return wrapPi(desired);
        return wrapPi(cur + Math.signum(diff) * step);
    }

    private float wrapPi(float a) {
        while (a > (float) Math.PI) a -= (float) (2 * Math.PI);
        while (a < (float) -Math.PI) a += (float) (2 * Math.PI);
        return a;
    }

    private SentryTarget acquire(Location sensor, double range, Player ownerP) {
        World world = sensor.getWorld();
        SentryTarget best = null;
        double bestD2 = range * range;
        for (Entity e : world.getNearbyEntities(sensor, range, range, range)) {
            if (!GunService.isValidTarget(e, ownerP)) continue;
            if (ownerP != null && TeamRules.sameSvoTeam(ownerP, e)) continue;
            LivingEntity le = (LivingEntity) e;
            Location tc = le.getEyeLocation();
            double d2 = tc.distanceSquared(sensor);
            if (d2 > bestD2) continue;
            if (!clearLineOfSight(sensor, tc)) continue;
            bestD2 = d2;
            best = new SentryTarget(le, null);
        }
        var ownerVehicle = ownerP == null ? null : plugin.core().vehicles().riddenBy(ownerP);
        UUID excludedVehicle = ownerVehicle == null ? null : ownerVehicle.id();
        for (VehicleHit hit : plugin.core().combat().vehiclesNear(sensor, range, excludedVehicle)) {
            double d2 = hit.point().distanceSquared(sensor);
            if (d2 > bestD2) continue;
            if (!clearLineOfSight(sensor, hit.point())) continue;
            bestD2 = d2;
            best = new SentryTarget(null, hit.vehicle());
        }
        return best;
    }

    private boolean clearLineOfSight(Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        double dist = dir.length();
        if (dist < 1e-3) return true;
        RayTraceResult rt = from.getWorld().rayTraceBlocks(from, dir.normalize(), dist,
                FluidCollisionMode.NEVER, true);
        return rt == null;
    }

    private Vector applySpread(Vector dir, double spreadDeg) {
        if (spreadDeg <= 0) return dir;
        double spread = Math.tan(Math.toRadians(spreadDeg));
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-6) right = new Vector(1, 0, 0);
        else right.normalize();
        Vector up = dir.clone().crossProduct(right).normalize();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double a = (r.nextDouble() * 2 - 1) * spread;
        double b = (r.nextDouble() * 2 - 1) * spread;
        return dir.clone().add(right.multiply(a)).add(up.multiply(b)).normalize();
    }

    private void drawTracer(World world, Vector start, Vector end) {
        Vector path = end.clone().subtract(start);
        double length = path.length();
        if (length < 0.1) return;
        Vector step = path.normalize().multiply(2.0);
        Vector point = start.clone().add(step);
        int steps = Math.min(24, (int) (length / 2.0));
        for (int i = 0; i < steps; i++) {
            world.spawnParticle(Particle.DUST, point.getX(), point.getY(), point.getZ(), 1, 0, 0, 0, 0, TRACER);
            point.add(step);
        }
    }

    private void drawLockBeam(Sentry s, SentryTarget target) {
        World world = s.loc.getWorld();
        double fc = Math.cos(s.yaw), fz = -Math.sin(s.yaw);
        Location start = s.loc.clone().add(fc * 1.05, 1.12, fz * 1.05);
        Location targetPoint = target.point();
        if (targetPoint == null) return;
        Vector path = targetPoint.toVector().subtract(start.toVector());
        double length = path.length();
        if (length < 0.3) return;
        int steps = Math.min(8, Math.max(2, (int) (length / 1.5)));
        Vector step = path.normalize().multiply(length / steps);
        Location point = start.clone();
        for (int i = 0; i < steps; i++) {
            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, LOCK_BEAM);
            point.add(step);
        }
    }

    // ------------------------------------------------------------------
    //  Interaction and cleanup
    // ------------------------------------------------------------------

    public boolean isSentryInteraction(Entity e) {
        if (!(e instanceof Interaction)) return false;
        for (Sentry s : sentries) if (e.equals(s.hitbox)) return true;
        return false;
    }

    public void interact(Player p, Entity hitbox) {
        if (SpectatorBlock.deny(p)) return;
        Sentry s = null;
        for (Sentry x : sentries) if (hitbox.equals(x.hitbox)) { s = x; break; }
        if (s == null) return;
        if (s.owner.equals(p.getUniqueId()) && p.isSneaking()) {
            p.sendActionBar(Txt.t("Turret is anchored and cannot be picked up", NamedTextColor.YELLOW));
            p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.7f, 0.8f);
        } else if (!s.owner.equals(p.getUniqueId())) {
            p.sendActionBar(Txt.t("This is not your turret", NamedTextColor.YELLOW));
        } else {
            p.sendActionBar(Txt.t("Turret remains active until its timer ends or it is destroyed", NamedTextColor.YELLOW));
        }
    }

    /** Player attack against a turret; breaks it when health reaches zero. */
    public void damageSentry(Player attacker, Entity hitbox) {
        if (SpectatorBlock.deny(attacker)) return;
        Sentry s = null;
        for (Sentry x : sentries) if (hitbox.equals(x.hitbox)) { s = x; break; }
        if (s == null) return;

        double dmg = 2.0;
        org.bukkit.attribute.AttributeInstance ai = attacker.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
        if (ai != null) dmg = Math.max(1.0, ai.getValue());
        s.health -= dmg;

        World world = s.loc.getWorld();
        world.playSound(s.loc, Sound.ENTITY_IRON_GOLEM_HURT, 0.75f, 1.45f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, s.loc.clone().add(0, 1.0, 0),
                10, 0.35, 0.35, 0.35, 0.04);
        world.spawnParticle(Particle.CRIT, s.loc.clone().add(0, 0.8, 0),
                8, 0.35, 0.35, 0.35, 0.08);
        if (s.health <= 0) {
            world.spawnParticle(Particle.EXPLOSION, s.loc.clone().add(0, 0.8, 0), 2, 0.25, 0.25, 0.25, 0);
            world.spawnParticle(Particle.LARGE_SMOKE, s.loc.clone().add(0, 0.8, 0), 18, 0.35, 0.35, 0.35, 0.03);
            world.playSound(s.loc, Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);
            world.playSound(s.loc, Sound.BLOCK_ANVIL_DESTROY, 0.7f, 1.35f);
            destroy(s, false);
            attacker.sendActionBar(Txt.t("Turret destroyed", NamedTextColor.RED));
        } else {
            attacker.sendActionBar(Txt.t("Turret damaged (" + (int) Math.ceil(s.health) + " HP)",
                    NamedTextColor.YELLOW));
        }
    }

    private void destroy(Sentry s, boolean effect) {
        if (s.task != null) {
            s.task.cancel();
            s.task = null;
        }
        if (effect && s.loc.getWorld() != null) {
            s.loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, s.loc.clone().add(0, 0.8, 0), 12, 0.3, 0.3, 0.3, 0.02);
            s.loc.getWorld().playSound(s.loc, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
        }
        for (Entity e : s.base) if (e.isValid()) e.remove();
        for (HeadPart hp : s.head) if (hp.display.isValid()) hp.display.remove();
        if (s.hitbox != null && s.hitbox.isValid()) s.hitbox.remove();
        sentries.remove(s);
    }

    public void cleanupAll() {
        for (Sentry s : new ArrayList<>(sentries)) destroy(s, false);
        sentries.clear();
    }

    // ------------------------------------------------------------------
    //  Geometry
    // ------------------------------------------------------------------

    private Vector cardinal(Player p) {
        Vector f = p.getLocation().getDirection();
        f.setY(0);
        if (f.lengthSquared() < 1e-4) return new Vector(0, 0, 1);
        if (Math.abs(f.getX()) >= Math.abs(f.getZ())) return new Vector(Math.signum(f.getX()), 0, 0);
        return new Vector(0, 0, Math.signum(f.getZ()));
    }

    /** Rotating head beam: display at the pivot, local box applied in updateHead. */
    private HeadPart head(World world, Location pivot, float cf, float cu, float cr,
                          float hf, float hu, float hr, Material mat) {
        BlockDisplay d = world.spawn(pivot, BlockDisplay.class, bd -> {
            bd.setBlock(mat.createBlockData());
            bd.setPersistent(false);
            bd.setInterpolationDelay(0);
            bd.setInterpolationDuration(TASK_PERIOD);
        });
        return new HeadPart(d, cf, cu, cr, hf, hu, hr);
    }

    /** Static base beam aligned to cardinal directions. */
    private BlockDisplay part(World world, Location base, Vector fwd, Vector right,
                              double fOff, double rOff, double yOff,
                              double len, double hei, double wid, Material mat) {
        double px = base.getX() + fwd.getX() * fOff + right.getX() * rOff;
        double py = base.getY() + yOff;
        double pz = base.getZ() + fwd.getZ() * fOff + right.getZ() * rOff;
        Location pos = new Location(world, px, py, pz);

        float sx = (float) (len * Math.abs(fwd.getX()) + wid * Math.abs(right.getX()));
        float sz = (float) (len * Math.abs(fwd.getZ()) + wid * Math.abs(right.getZ()));
        if (sx < 0.05f) sx = (float) Math.min(len, wid);
        if (sz < 0.05f) sz = (float) Math.min(len, wid);
        final float fsx = sx, fsz = sz, fsy = (float) hei;

        return world.spawn(pos, BlockDisplay.class, bd -> {
            bd.setBlock(mat.createBlockData());
            bd.setPersistent(false);
            bd.setTransformation(new Transformation(
                    new Vector3f(-fsx / 2f, 0f, -fsz / 2f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(fsx, fsy, fsz),
                    new AxisAngle4f(0f, 0f, 0f, 1f)));
        });
    }
}
