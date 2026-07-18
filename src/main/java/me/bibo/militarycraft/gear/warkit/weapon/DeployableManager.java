package me.bibo.militarycraft.gear.warkit.weapon;

import me.bibo.militarycraft.gear.warkit.Txt;
import me.bibo.militarycraft.gear.warkit.TeamRules;
import me.bibo.militarycraft.gear.warkit.SpectatorBlock;
import me.bibo.militarycraft.gear.warkit.WarKitRuntime;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Deployable state and logic: Maxim machine gun and barbed wire. */
public final class DeployableManager {

    private static final int MAXIM_CAP_PER_PLAYER = 3;
    private static final double MAXIM_BARREL_PIVOT_F = 0.28;
    private static final double MAXIM_BARREL_PIVOT_Y = 0.96;
    private static final double MAXIM_BARREL_MAX_PITCH = 22.0;
    private static final double MAXIM_BARREL_YAW_STEP = 16.0;
    private static final double MAXIM_BARREL_PITCH_STEP = 9.0;

    private static final class Maxim {
        final UUID owner;
        final Location loc;
        final List<Entity> parts = new ArrayList<>();
        final List<TiltingPart> barrel = new ArrayList<>();
        Interaction hitbox;
        UUID mannedBy;
        ArmorStand seat;
        int heat;
        boolean overheated;
        long overheatUntil;
        boolean auto;
        BukkitTask autoTask;
        BukkitTask aimTask;
        Vector forward;
        float forwardYaw;
        double barrelPitchDeg;
        double barrelYawDeg;
        double health;

        Maxim(UUID owner, Location loc) {
            this.owner = owner;
            this.loc = loc;
        }
    }

    private static final class TiltingPart {
        final BlockDisplay display;
        final double fOff, rOff, yOff, len, wid, hei;

        TiltingPart(BlockDisplay display, double fOff, double rOff, double yOff,
                    double len, double wid, double hei) {
            this.display = display;
            this.fOff = fOff;
            this.rOff = rOff;
            this.yOff = yOff;
            this.len = len;
            this.wid = wid;
            this.hei = hei;
        }
    }

    private record BarrelAim(double pitchDeg, double yawDeg) {}

    private static final class Barbed {
        final UUID owner;
        final List<Location> centers;
        final List<BlockDisplay> displays;
        final long expireAt;

        Barbed(UUID owner, List<Location> centers, List<BlockDisplay> displays, long expireAt) {
            this.owner = owner;
            this.centers = centers;
            this.displays = displays;
            this.expireAt = expireAt;
        }

        boolean anyValid() {
            for (BlockDisplay d : displays) if (d.isValid()) return true;
            return false;
        }

        void removeAll() {
            for (BlockDisplay d : displays) if (d.isValid()) d.remove();
        }
    }

    private final WarKitRuntime plugin;
    private final List<Maxim> maxims = new ArrayList<>();
    private final List<Barbed> barbed = new ArrayList<>();
    private long tickParity;

    public DeployableManager(WarKitRuntime plugin) {
        this.plugin = plugin;
    }

    private WeaponConfig w() {
        return plugin.weaponConfig();
    }

    // ------------------------------------------------------------------
    //  Barbed wire wall made from several segments across the player's view.
    // ------------------------------------------------------------------

    public boolean deployBarbed(Player owner, Location at) {
        if (SpectatorBlock.deny(owner)) return false;
        long count = barbed.stream().filter(b -> b.owner.equals(owner.getUniqueId())).count();
        if (count >= w().barbedMaxPerPlayer) {
            owner.sendActionBar(Txt.t("Barbed wire limit reached", NamedTextColor.YELLOW));
            return false;
        }
        World world = at.getWorld();
        Vector fwd = owner.getLocation().getDirection();
        fwd.setY(0);
        if (fwd.lengthSquared() < 1e-4) fwd = new Vector(0, 0, 1);
        else fwd.normalize();
        // Left/right relative to the player: the wall is placed parallel to them.
        Vector right = new Vector(-fwd.getZ(), 0, fwd.getX());

        int n = Math.max(1, w().barbedSegments);
        double spacing = w().barbedSpacing;
        List<Location> centers = new ArrayList<>(n);
        List<BlockDisplay> displays = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            double off = (i - (n - 1) / 2.0) * spacing;
            Location seg = at.clone().add(right.getX() * off, 0, right.getZ() * off);
            BlockDisplay d = world.spawn(seg, BlockDisplay.class, bd -> {
                bd.setBlock(Material.COBWEB.createBlockData());
                bd.setPersistent(false);
                bd.setTransformation(new Transformation(
                        new Vector3f(-0.5f, 0f, -0.5f),
                        new AxisAngle4f(0f, 0f, 0f, 1f),
                        new Vector3f(1.0f, 1.0f, 1.0f),
                        new AxisAngle4f(0f, 0f, 0f, 1f)));
            });
            displays.add(d);
            centers.add(seg.clone().add(0, 0.5, 0));
        }
        barbed.add(new Barbed(owner.getUniqueId(), centers, displays,
                System.currentTimeMillis() + w().barbedLifeSeconds * 1000L));
        world.playSound(at, Sound.BLOCK_CHAIN_PLACE, 1f, 0.8f);
        world.spawnParticle(Particle.CRIT, at.clone().add(0, 0.4, 0), 16, 1.2, 0.2, 0.2, 0.1);
        owner.sendActionBar(Txt.t("Barbed wire wall: " + n + " segments", NamedTextColor.GRAY));
        return true;
    }

    // ------------------------------------------------------------------
    //  Maxim machine gun: shield, barrel jacket, and wheeled mount.
    // ------------------------------------------------------------------

    public boolean deployMaxim(Player owner, Location at) {
        if (SpectatorBlock.deny(owner)) return false;
        long count = maxims.stream().filter(m -> m.owner.equals(owner.getUniqueId())).count();
        if (count >= MAXIM_CAP_PER_PLAYER) {
            owner.sendActionBar(Txt.t("Too many machine guns deployed", NamedTextColor.YELLOW));
            return false;
        }
        World world = at.getWorld();
        Maxim mx = new Maxim(owner.getUniqueId(), at.clone());
        mx.health = w().maximHealth;

        Vector fwd = vectorOf(owner);
        Vector right = new Vector(-fwd.getZ(), 0, fwd.getX());
        mx.forward = fwd.clone();
        mx.forwardYaw = (float) Math.toDegrees(Math.atan2(-fwd.getX(), fwd.getZ())); // fire sector

        // Mount: wooden wheels and a dark axle.
        mx.parts.add(part(world, at, fwd, right, -0.15, 0.55, 0.0, 0.9, 0.14, 0.9, Material.DARK_OAK_PLANKS)); // wheel
        mx.parts.add(part(world, at, fwd, right, -0.15, -0.55, 0.0, 0.9, 0.14, 0.9, Material.DARK_OAK_PLANKS)); // wheel
        mx.parts.add(part(world, at, fwd, right, -0.15, 0.0, 0.45, 0.12, 1.25, 0.12, Material.POLISHED_DEEPSLATE)); // axle
        mx.parts.add(part(world, at, fwd, right, -0.05, 0.0, 0.50, 0.9, 0.5, 0.12, Material.DRIED_KELP_BLOCK)); // frame
        // Body: olive receiver and shield.
        mx.parts.add(part(world, at, fwd, right, 0.1, 0.0, 0.60, 0.7, 0.45, 0.4, Material.DRIED_KELP_BLOCK)); // receiver
        mx.parts.add(part(world, at, fwd, right, 0.28, 0.0, 0.55, 0.14, 1.0, 0.8, Material.DRIED_KELP_BLOCK)); // shield
        // Ribbed barrel jacket and dark muzzle.
        barrelPart(mx, world, at, 0.7, 0.0, 0.92, 1.15, 0.28, 0.28, Material.DEEPSLATE_TILES); // barrel jacket
        barrelPart(mx, world, at, 1.25, 0.0, 0.92, 0.18, 0.16, 0.16, Material.NETHERITE_BLOCK); // muzzle
        updateBarrel(mx, 0.0, 0.0);

        Interaction hb = world.spawn(at.clone(), Interaction.class, in -> {
            in.setInteractionWidth(1.8f);
            in.setInteractionHeight(1.5f);
            in.setResponsive(true);
            in.setPersistent(false);
        });
        mx.hitbox = hb;
        maxims.add(mx);

        world.playSound(at, Sound.BLOCK_ANVIL_PLACE, 1f, 0.8f);
        owner.sendActionBar(Txt.t("Maxim deployed. Right-click it to man it", NamedTextColor.GOLD));
        return true;
    }

    /** Yaw angle difference in the [-180, 180] range. */
    private static double angleDiff(double a, double b) {
        double d = (a - b) % 360;
        if (d < -180) d += 360;
        if (d > 180) d -= 360;
        return d;
    }

    private Vector vectorOf(Player p) {
        Vector f = p.getLocation().getDirection();
        f.setY(0);
        if (f.lengthSquared() < 1e-4) return new Vector(0, 0, 1);
        // Snap to a cardinal direction for clean axis-aligned geometry.
        if (Math.abs(f.getX()) >= Math.abs(f.getZ())) {
            return new Vector(Math.signum(f.getX()), 0, 0);
        }
        return new Vector(0, 0, Math.signum(f.getZ()));
    }

    /** Rectangular BlockDisplay beam oriented along fwd/right cardinal axes. */
    private BlockDisplay part(World world, Location base, Vector fwd, Vector right,
                              double fOff, double rOff, double yOff,
                              double len, double wid, double hei, Material mat) {
        double px = base.getX() + fwd.getX() * fOff + right.getX() * rOff;
        double py = base.getY() + yOff;
        double pz = base.getZ() + fwd.getZ() * fOff + right.getZ() * rOff;
        Location pos = new Location(world, px, py, pz);

        float sx = (float) (len * Math.abs(fwd.getX()) + wid * Math.abs(right.getX()));
        float sz = (float) (len * Math.abs(fwd.getZ()) + wid * Math.abs(right.getZ()));
        if (sx < 0.05f) sx = (float) Math.min(len, wid);
        if (sz < 0.05f) sz = (float) Math.min(len, wid);
        float sy = (float) hei;
        final float fsx = sx, fsz = sz, fsy = sy;

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

    private void barrelPart(Maxim mx, World world, Location base,
                            double fOff, double rOff, double yOff,
                            double len, double wid, double hei, Material mat) {
        BlockDisplay display = world.spawn(base, BlockDisplay.class, bd -> {
            bd.setBlock(mat.createBlockData());
            bd.setPersistent(false);
            bd.setInterpolationDelay(0);
            bd.setInterpolationDuration(4);
        });
        TiltingPart part = new TiltingPart(display, fOff, rOff, yOff, len, wid, hei);
        mx.barrel.add(part);
        mx.parts.add(display);
    }

    private BarrelAim updateBarrelFor(Player p, Maxim mx) {
        double targetPitch = clamp(-p.getLocation().getPitch(),
                -MAXIM_BARREL_MAX_PITCH, MAXIM_BARREL_MAX_PITCH);
        double targetYaw = clamp(angleDiff(p.getLocation().getYaw(), mx.forwardYaw),
                -w().maximAimArcDegrees, w().maximAimArcDegrees);
        mx.barrelPitchDeg = moveTowards(mx.barrelPitchDeg, targetPitch, MAXIM_BARREL_PITCH_STEP);
        mx.barrelYawDeg = moveTowards(mx.barrelYawDeg, targetYaw, MAXIM_BARREL_YAW_STEP);
        updateBarrel(mx, mx.barrelPitchDeg, mx.barrelYawDeg);
        return new BarrelAim(mx.barrelPitchDeg, mx.barrelYawDeg);
    }

    private Location maximMuzzle(Maxim mx, BarrelAim aim) {
        Vector3f point = barrelPoint(mx, 1.42, 1.0, 0.0, aim.pitchDeg(), aim.yawDeg());
        return mx.loc.clone().add(point.x, point.y, point.z);
    }

    private Vector3f barrelPoint(Maxim mx, double f, double y, double r, double pitchDeg, double yawDeg) {
        if (mx.forward == null) return new Vector3f((float) f, (float) y, (float) r);
        float yaw = (float) (Math.atan2(-mx.forward.getZ(), mx.forward.getX()) - Math.toRadians(yawDeg));
        float pitch = (float) Math.toRadians(pitchDeg);
        float baseYaw = (float) Math.atan2(-mx.forward.getZ(), mx.forward.getX());
        Quaternionf yawRot = new Quaternionf().rotateY(baseYaw);
        Quaternionf rot = new Quaternionf().rotateY(yaw).rotateZ(pitch);
        Vector3f pivot = new Vector3f((float) MAXIM_BARREL_PIVOT_F, (float) MAXIM_BARREL_PIVOT_Y, 0f);
        Vector3f pivotWorld = yawRot.transform(new Vector3f(pivot));
        Vector3f rel = new Vector3f((float) (f - MAXIM_BARREL_PIVOT_F),
                (float) (y - MAXIM_BARREL_PIVOT_Y),
                (float) r);
        rot.transform(rel);
        return pivotWorld.add(rel, new Vector3f());
    }

    private Vector maximShotDirection(Player p, Location muzzle, double range) {
        Vector aim = p.getEyeLocation().getDirection();
        if (aim.lengthSquared() < 1e-6) return p.getLocation().getDirection();
        Vector target = p.getEyeLocation().toVector().add(aim.clone().normalize().multiply(range));
        Vector dir = target.subtract(muzzle.toVector());
        return dir.lengthSquared() < 1e-6 ? aim.normalize() : dir.normalize();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double moveTowards(double current, double target, double maxStep) {
        double delta = target - current;
        if (Math.abs(delta) <= maxStep) return target;
        return current + Math.signum(delta) * maxStep;
    }

    private void updateBarrel(Maxim mx, double pitchDeg, double yawDeg) {
        if (mx.forward == null) return;
        float yaw = (float) (Math.atan2(-mx.forward.getZ(), mx.forward.getX()) - Math.toRadians(yawDeg));
        float pitch = (float) Math.toRadians(pitchDeg);
        float baseYaw = (float) Math.atan2(-mx.forward.getZ(), mx.forward.getX());
        Quaternionf yawRot = new Quaternionf().rotateY(baseYaw);
        Quaternionf rot = new Quaternionf().rotateY(yaw).rotateZ(pitch);
        Vector3f pivot = new Vector3f((float) MAXIM_BARREL_PIVOT_F, (float) MAXIM_BARREL_PIVOT_Y, 0f);
        Vector3f pivotWorld = yawRot.transform(new Vector3f(pivot));

        for (TiltingPart part : mx.barrel) {
            if (!part.display.isValid()) continue;
            Vector3f min = new Vector3f((float) (part.fOff - part.len / 2.0),
                    (float) part.yOff,
                    (float) (part.rOff - part.wid / 2.0));
            Vector3f rel = min.sub(pivot, new Vector3f());
            rot.transform(rel);
            Vector3f tr = pivotWorld.add(rel, new Vector3f());
            part.display.setTransformation(new Transformation(tr, new Quaternionf(rot),
                    new Vector3f((float) part.len, (float) part.hei, (float) part.wid),
                    new Quaternionf()));
        }
    }

    public boolean isMaximInteraction(Entity e) {
        if (!(e instanceof Interaction)) return false;
        for (Maxim m : maxims) if (e.equals(m.hitbox)) return true;
        return false;
    }

    /** Player attack against a deployed machine gun; breaks it when health reaches zero. */
    public void damageMaxim(Player attacker, Entity hitbox) {
        if (SpectatorBlock.deny(attacker)) return;
        Maxim mx = null;
        for (Maxim m : maxims) if (hitbox.equals(m.hitbox)) { mx = m; break; }
        if (mx == null) return;

        double dmg = 2.0;
        org.bukkit.attribute.AttributeInstance ai = attacker.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
        if (ai != null) dmg = Math.max(1.0, ai.getValue());
        mx.health -= dmg;

        World world = mx.loc.getWorld();
        world.playSound(mx.loc, Sound.ENTITY_IRON_GOLEM_HURT, 0.8f, 1.2f);
        world.spawnParticle(Particle.CRIT, mx.loc.clone().add(0, 1.0, 0), 8, 0.4, 0.4, 0.4, 0.1);
        if (mx.health <= 0) {
            world.spawnParticle(Particle.EXPLOSION, mx.loc.clone().add(0, 1.0, 0), 2, 0.3, 0.3, 0.3, 0);
            world.spawnParticle(Particle.LARGE_SMOKE, mx.loc.clone().add(0, 1.0, 0), 14, 0.4, 0.4, 0.4, 0.03);
            world.playSound(mx.loc, Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);
            world.playSound(mx.loc, Sound.BLOCK_ANVIL_DESTROY, 0.8f, 1.2f);
            removeMaxim(mx);
            attacker.sendActionBar(Txt.t("Machine gun destroyed", NamedTextColor.RED));
        } else {
            attacker.sendActionBar(Txt.t("Machine gun damaged (" + (int) Math.ceil(mx.health) + " HP)",
                    NamedTextColor.YELLOW));
        }
    }

    /** Right-click deployed machine gun: pick up, man, or fire. */
    public void interactMaxim(Player p, Entity hitbox) {
        if (SpectatorBlock.deny(p)) return;
        Maxim mx = null;
        for (Maxim m : maxims) if (hitbox.equals(m.hitbox)) { mx = m; break; }
        if (mx == null) return;

        if (mx.mannedBy != null && mx.mannedBy.equals(p.getUniqueId())) {
            fire(p);
            return;
        }
        if (p.isSneaking() && mx.owner.equals(p.getUniqueId()) && mx.mannedBy == null) {
            pickup(p, mx);
            return;
        }
        if (mx.mannedBy != null) {
            p.sendActionBar(Txt.t("Machine gun already manned", NamedTextColor.YELLOW));
            return;
        }
        man(p, mx);
    }

    private void man(Player p, Maxim mx) {
        if (isManning(p)) return;
        if (mx.seat != null && mx.seat.isValid() && !mx.seat.getPassengers().isEmpty()) return;
        World world = mx.loc.getWorld();
        Vector fwd = mx.forward == null ? vectorOf(p) : mx.forward;
        // Seat behind the shield and slightly higher so the gunner sees over it.
        // Lowered by one block: small armor stand plus a lowered seat point.
        Location seatLoc = mx.loc.clone().add(fwd.getX() * -0.4, -0.1, fwd.getZ() * -0.4);
        ArmorStand seat = world.spawn(seatLoc, ArmorStand.class, a -> {
            a.setInvisible(true);
            a.setGravity(false);
            a.setInvulnerable(true);
            a.setBasePlate(false);
            a.setSmall(true);
            a.setSilent(true);
            a.setPersistent(false);
            a.setCollidable(false);
        });
        seat.addPassenger(p);
        mx.seat = seat;
        mx.mannedBy = p.getUniqueId();
        updateBarrelFor(p, mx);
        mx.aimTask = plugin.getServer().getScheduler().runTaskTimer(plugin.bukkitPlugin(), () -> {
            if (!p.isOnline() || mx.mannedBy == null || !p.getUniqueId().equals(mx.mannedBy)) return;
            updateBarrelFor(p, mx);
        }, 1L, 2L);
        p.sendActionBar(Txt.t("Manning machine gun: right-click toggles fire, sneak dismounts", NamedTextColor.GOLD));
        world.playSound(mx.loc, Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 0.8f);
    }

    /** Continuous fire toggle: click to start, click again to stop. */
    private void fire(Player p) {
        Maxim mx = mannedBy(p);
        if (mx == null) return;
        if (mx.auto) {
            stopAuto(mx, p, true);
            return;
        }
        if (mx.overheated) {
            if (System.currentTimeMillis() < mx.overheatUntil) {
                p.sendActionBar(Txt.t("Overheated! Cooling down...", NamedTextColor.RED));
                return;
            }
            mx.overheated = false;
            mx.heat = 0;
        }
        WeaponConfig w = w();
        mx.auto = true;
        p.sendActionBar(Txt.t("Firing! Click again to stop", NamedTextColor.GOLD));
        mx.autoTask = plugin.getServer().getScheduler().runTaskTimer(plugin.bukkitPlugin(), () -> {
            if (mx.mannedBy == null || !p.isOnline()) {
                stopAuto(mx, p, false);
                return;
            }
            // The mount only allows firing into the forward sector.
            if (Math.abs(angleDiff(p.getLocation().getYaw(), mx.forwardYaw)) > w.maximAimArcDegrees) {
                p.sendActionBar(Txt.t("Target outside the machine gun fire sector", NamedTextColor.YELLOW));
                return;
            }
            BarrelAim aim = updateBarrelFor(p, mx);
            Location muzzle = maximMuzzle(mx, aim);
            Vector shotDir = maximShotDirection(p, muzzle, w.maximRange);
            plugin.guns().fireBulletFrom(p, muzzle, shotDir, w.maximDamage, w.maximRange,
                    w.maximSpreadDeg, w.headshotMultiplier);
            muzzle.getWorld().playSound(muzzle, Sound.ENTITY_GENERIC_EXPLODE, 0.55f, 1.5f);
            muzzle.getWorld().spawnParticle(Particle.SMOKE, muzzle, 4, 0.04, 0.04, 0.04, 0.01);
            muzzle.getWorld().spawnParticle(Particle.FLASH, muzzle, 1, 0, 0, 0, 0);
            mx.heat++;
            if (mx.heat >= w.maximOverheatShots) {
                mx.overheated = true;
                mx.overheatUntil = System.currentTimeMillis() + (long) (w.maximCooldownSeconds * 1000);
                p.sendActionBar(Txt.t("Barrel overheated!", NamedTextColor.RED));
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
                stopAuto(mx, p, false);
            }
        }, 0L, Math.max(1, w.maximFireCooldownTicks));
    }

    private void stopAuto(Maxim mx, Player p, boolean msg) {
        mx.auto = false;
        if (mx.autoTask != null) {
            mx.autoTask.cancel();
            mx.autoTask = null;
        }
        if (msg && p != null && p.isOnline()) {
            p.sendActionBar(Txt.t("Fire stopped", NamedTextColor.GRAY));
        }
    }

    public boolean isManning(Player p) {
        return mannedBy(p) != null;
    }

    /** Click fire while the player is already manning the machine gun. */
    public void manualFire(Player p) {
        if (SpectatorBlock.deny(p)) {
            dismount(p);
            return;
        }
        fire(p);
    }

    private Maxim mannedBy(Player p) {
        for (Maxim m : maxims) if (p.getUniqueId().equals(m.mannedBy)) return m;
        return null;
    }

    public void dismount(Player p) {
        Maxim mx = mannedBy(p);
        if (mx == null) return;
        unman(mx);
        if (p.isOnline()) p.sendActionBar(Txt.t("Dismounted machine gun", NamedTextColor.GRAY));
    }

    private void unman(Maxim mx) {
        mx.mannedBy = null;
        stopAuto(mx, null, false);
        if (mx.aimTask != null) {
            mx.aimTask.cancel();
            mx.aimTask = null;
        }
        mx.barrelPitchDeg = 0.0;
        mx.barrelYawDeg = 0.0;
        updateBarrel(mx, 0.0, 0.0);
        if (mx.seat != null) {
            mx.seat.eject();
            if (mx.seat.isValid()) mx.seat.remove();
            mx.seat = null;
        }
    }

    private void pickup(Player owner, Maxim mx) {
        removeMaxim(mx);
        ItemStack item = plugin.items().create(Weapons.MAXIM);
        owner.getInventory().addItem(item).values()
                .forEach(left -> owner.getWorld().dropItemNaturally(owner.getLocation(), left));
        owner.sendActionBar(Txt.t("Machine gun picked up", NamedTextColor.GREEN));
        owner.getWorld().playSound(owner.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
    }

    private void removeMaxim(Maxim mx) {
        unman(mx);
        for (Entity e : mx.parts) if (e.isValid()) e.remove();
        if (mx.hitbox != null && mx.hitbox.isValid()) mx.hitbox.remove();
        maxims.remove(mx);
    }

    // ------------------------------------------------------------------
    //  Tick from the shared Ticker, every 10 ticks.
    // ------------------------------------------------------------------

    public void tick() {
        tickParity++;
        boolean damageTick = tickParity % 2 == 0; // about once per second
        long now = System.currentTimeMillis();

        Iterator<Barbed> bit = barbed.iterator();
        while (bit.hasNext()) {
            Barbed b = bit.next();
            if (now >= b.expireAt || !b.anyValid()) {
                b.removeAll();
                bit.remove();
                continue;
            }
            double r = w().barbedRadius;
            double r2 = r * r;
            World world = b.centers.get(0).getWorld();
            for (Location c : b.centers) {
                world.spawnParticle(Particle.CRIT, c, 1, 0.3, 0.2, 0.3, 0.02);
            }
            for (Entity e : world.getNearbyEntities(centerOf(b), r + b.centers.size(), 1.5, r + b.centers.size())) {
                if (!(e instanceof LivingEntity le)) continue;
                if (e instanceof ArmorStand) continue;
                if (e.getUniqueId().equals(b.owner)) continue;
                boolean inWire = false;
                for (Location c : b.centers) {
                    if (le.getLocation().distanceSquared(c) <= r2) { inWire = true; break; }
                }
                if (!inWire) continue;
                Player owner = plugin.getServer().getPlayer(b.owner);
                if (owner != null && !TeamRules.canDamage(owner, le)) continue;
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30,
                        w().barbedSlowAmplifier, true, false, false));
                if (damageTick && w().barbedTickDamage > 0) {
                    if (owner != null) le.damage(w().barbedTickDamage, owner);
                    else le.damage(w().barbedTickDamage);
                }
            }
            if (damageTick && w().barbedTickDamage > 0) {
                double vehicleDamage = w().barbedTickDamage * w().vehicleBulletDamageMultiplier;
                if (vehicleDamage > 0) {
                    for (var hit : plugin.core().combat().vehiclesNear(centerOf(b),
                            r + b.centers.size(), null)) {
                        boolean inWire = false;
                        for (Location c : b.centers) {
                            if (hit.point().distanceSquared(c) <= r2) {
                                inWire = true;
                                break;
                            }
                        }
                        if (inWire && plugin.core().combat().directDamage(hit.vehicle(), vehicleDamage)) {
                            world.spawnParticle(Particle.CRIT, hit.point(), 4, 0.25, 0.18, 0.25, 0.02);
                        }
                    }
                }
            }
        }

        for (Maxim mx : new ArrayList<>(maxims)) {
            if (mx.hitbox == null || !mx.hitbox.isValid() || !anyPartValid(mx)) {
                removeMaxim(mx);
                continue;
            }
            if (mx.mannedBy != null) {
                Player p = plugin.getServer().getPlayer(mx.mannedBy);
                if (p == null || !p.isOnline() || mx.seat == null || !mx.seat.isValid()
                        || !mx.seat.getPassengers().contains(p)) {
                    unman(mx);
                } else {
                    updateBarrelFor(p, mx);
                }
            }
            if (!mx.auto && mx.heat > 0 && !mx.overheated) {
                mx.heat = Math.max(0, mx.heat - 6);
            }
            if (mx.overheated && mx.seat != null && mx.seat.isValid()) {
                mx.loc.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                        mx.loc.clone().add(0, 1.0, 0), 3, 0.1, 0.1, 0.1, 0.01);
            }
        }
    }

    private boolean anyPartValid(Maxim mx) {
        for (Entity e : mx.parts) if (e.isValid()) return true;
        return false;
    }

    private Location centerOf(Barbed b) {
        double x = 0, y = 0, z = 0;
        for (Location c : b.centers) { x += c.getX(); y += c.getY(); z += c.getZ(); }
        int n = b.centers.size();
        return new Location(b.centers.get(0).getWorld(), x / n, y / n, z / n);
    }

    public void onPlayerGone(UUID uuid) {
        Maxim mx = null;
        for (Maxim m : maxims) if (uuid.equals(m.mannedBy)) { mx = m; break; }
        if (mx != null) unman(mx);
    }

    public void cleanupAll() {
        for (Maxim mx : new ArrayList<>(maxims)) removeMaxim(mx);
        for (Barbed b : barbed) b.removeAll();
        barbed.clear();
        maxims.clear();
    }
}
