package me.bibo.militarycraft.gear.warkit.weapon;

import me.bibo.militarycraft.gear.warkit.Txt;
import me.bibo.militarycraft.gear.warkit.TeamRules;
import me.bibo.militarycraft.gear.warkit.SpectatorBlock;
import me.bibo.militarycraft.gear.warkit.WarItems;
import me.bibo.militarycraft.gear.warkit.WarKitRuntime;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Spray weapons: flamethrower cone and chemical sprayer poison cloud. */
public final class SprayService implements Listener {

    private final WarKitRuntime plugin;
    private final org.bukkit.NamespacedKey chemicalCloudKey;
    private final Map<UUID, BukkitTask> activeFlameBursts = new HashMap<>();

    public SprayService(WarKitRuntime plugin) {
        this.plugin = plugin;
        this.chemicalCloudKey = new org.bukkit.NamespacedKey("warkit", "chemical_cloud");
    }

    private WeaponConfig w() {
        return plugin.weaponConfig();
    }

    /** Fuel after regeneration during downtime. */
    private int regenFuel(ItemStack item, int capacity, double refuelSeconds) {
        int fuel = plugin.weapons().getAmmo(item);
        long last = plugin.weapons().getLastUse(item);
        long now = System.currentTimeMillis();
        if (last > 0 && fuel < capacity && refuelSeconds > 0) {
            int add = (int) ((now - last) / 1000.0 * (capacity / refuelSeconds));
            if (add > 0) fuel = Math.min(capacity, fuel + add);
        }
        return fuel;
    }

    // ------------------------------------------------------------------
    //  Flamethrower
    // ------------------------------------------------------------------

    public void handleFlame(Player p, ItemStack item) {
        if (SpectatorBlock.deny(p)) return;
        if (p.hasCooldown(item)) return;
        UUID uuid = p.getUniqueId();
        if (activeFlameBursts.containsKey(uuid)) return;
        int capacity = w().flameFuel;
        int fuel = regenFuel(item, capacity, w().flameRefuelSeconds);
        if (fuel <= 0) {
            p.sendActionBar(Txt.t("Tank empty - let the flamethrower cool down", NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.5f);
            return;
        }
        int pulsesAllowed = Math.min(fuel, 3);
        p.setCooldown(item, Math.max(5, pulsesAllowed * 2));
        plugin.weapons().setAmmo(item, fuel - pulsesAllowed);
        plugin.weapons().setLastUse(item, System.currentTimeMillis());
        p.getInventory().setItemInMainHand(item);
        p.sendActionBar(fuelBar("🔥", fuel - pulsesAllowed, capacity));

        // One stream is a short pulse sequence.
        BukkitTask task = new BukkitRunnable() {
            int pulses = 0;
            @Override public void run() {
                if (pulses >= pulsesAllowed || !p.isOnline()
                        || !Weapons.FLAMETHROWER.equals(plugin.items().id(p.getInventory().getItemInMainHand()))) {
                    activeFlameBursts.remove(uuid);
                    cancel();
                    return;
                }
                flamePulse(p);
                pulses++;
                if (pulses >= pulsesAllowed) {
                    activeFlameBursts.remove(uuid);
                    cancel();
                }
            }
        }.runTaskTimer(plugin.bukkitPlugin(), 0L, 2L);
        activeFlameBursts.put(uuid, task);
    }

    private void flamePulse(Player p) {
        World world = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        double range = w().flameRange;
        double cosHalf = Math.cos(Math.toRadians(w().flameConeDeg) / 2);

        world.playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.7f, 0.9f);
        // Stream particles.
        for (double d = 0.8; d <= range; d += 0.6) {
            double spread = 0.08 * d;
            Location pt = eye.clone().add(dir.clone().multiply(d));
            world.spawnParticle(Particle.FLAME, pt, 2, spread, spread, spread, 0.01);
            world.spawnParticle(Particle.SMOKE, pt, 1, spread, spread, spread, 0.0);
        }
        // Cone damage.
        for (Entity e : world.getNearbyEntities(eye, range, range, range)) {
            if (!GunService.isValidTarget(e, p)) continue;
            LivingEntity le = (LivingEntity) e;
            Vector to = le.getEyeLocation().toVector().subtract(eye.toVector());
            double dist = to.length();
            if (dist < 1e-3 || dist > range) continue;
            if (dir.dot(to.multiply(1.0 / dist)) < cosHalf) continue;
            if (!p.hasLineOfSight(le)) continue;
            if (!TeamRules.canDamage(p, le)) continue;
            le.setFireTicks(Math.max(le.getFireTicks(), w().flameFireTicks));
            le.damage(w().flameTickDamage, p);
        }
        // Real fire on surfaces: ground, trees, and so on.
        double vehicleDamage = w().flameTickDamage * w().vehicleBulletDamageMultiplier;
        if (vehicleDamage > 0) {
            var ownVehicle = plugin.core().vehicles().riddenBy(p);
            for (var hit : plugin.core().combat().vehiclesNear(eye, range,
                    ownVehicle == null ? null : ownVehicle.id())) {
                Vector to = hit.point().toVector().subtract(eye.toVector());
                double dist = to.length();
                if (dist < 1e-3 || dist > range) continue;
                Vector toVehicle = to.clone().multiply(1.0 / dist);
                if (dir.dot(toVehicle) < cosHalf) continue;
                if (world.rayTraceBlocks(eye, toVehicle, dist, FluidCollisionMode.NEVER, true) != null) continue;
                if (plugin.core().combat().directDamage(hit.vehicle(), vehicleDamage)) {
                    world.spawnParticle(Particle.FLAME, hit.point(), 3, 0.18, 0.18, 0.18, 0.01);
                    world.spawnParticle(Particle.SMOKE, hit.point(), 2, 0.16, 0.16, 0.16, 0.0);
                }
            }
        }
        if (w().flameFireBlocks) {
            RayTraceResult hit = world.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
            if (hit != null && hit.getHitBlock() != null && hit.getHitBlockFace() != null) {
                igniteAround(hit.getHitBlock().getRelative(hit.getHitBlockFace()));
            } else {
                igniteAround(eye.clone().add(dir.clone().multiply(range)).getBlock());
            }
        }
    }

    private static final BlockFace[] SIDES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN};

    private void igniteAround(Block b) {
        tryIgnite(b);
        tryIgnite(b.getRelative(BlockFace.UP));
    }

    private void tryIgnite(Block b) {
        if (b == null || !b.isPassable()) return;
        Material t = b.getType();
        if (t != Material.AIR && t != Material.CAVE_AIR
                && t != Material.SHORT_GRASS && t != Material.TALL_GRASS && t != Material.FERN) return;
        for (BlockFace f : SIDES) {
            if (b.getRelative(f).getType().isSolid()) {
                b.setType(Material.FIRE, true); // applyPhysics: vanilla fire spread
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    //  Chemical sprayer
    // ------------------------------------------------------------------

    public void handleChemical(Player p, ItemStack item) {
        if (SpectatorBlock.deny(p)) return;
        if (p.hasCooldown(item)) return;
        int capacity = w().chemFuel;
        int fuel = regenFuel(item, capacity, w().chemRefuelSeconds);
        int cost = w().chemCost;
        if (fuel < cost) {
            p.sendActionBar(Txt.t("Not enough reagent", NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.5f);
            return;
        }
        p.setCooldown(item, 12);

        World world = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult hit = world.rayTraceBlocks(eye, dir, w().chemRange, FluidCollisionMode.NEVER, true);
        Location at = hit != null && hit.getHitPosition() != null
                ? hit.getHitPosition().toLocation(world)
                : eye.clone().add(dir.clone().multiply(w().chemRange));

        AreaEffectCloud cloud = world.spawn(at, AreaEffectCloud.class, c -> {
            c.setRadius((float) w().chemRadius);
            c.setDuration(w().chemCloudSeconds * 20);
            c.setRadiusPerTick(-(float) (w().chemRadius / (w().chemCloudSeconds * 20)));
            c.setWaitTime(0);
            c.setColor(Color.fromRGB(0x6AAE2E));
            c.setParticle(Particle.ENTITY_EFFECT, Color.fromRGB(0x6AAE2E));
            c.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 80, 0), true);
            c.addCustomEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0), true);
            c.setSource(p);
            c.getPersistentDataContainer().set(chemicalCloudKey, PersistentDataType.BYTE, (byte) 1);
        });
        cloud.setPersistent(false);

        // Reagent stream to the target point.
        for (double d = 0.8; d <= w().chemRange; d += 0.7) {
            Location pt = eye.clone().add(dir.clone().multiply(d));
            world.spawnParticle(Particle.SNEEZE, pt, 2, 0.05, 0.05, 0.05, 0.01);
        }
        world.playSound(at, Sound.ENTITY_BREEZE_SHOOT, 0.8f, 0.7f);

        plugin.weapons().setAmmo(item, fuel - cost);
        plugin.weapons().setLastUse(item, System.currentTimeMillis());
        p.getInventory().setItemInMainHand(item);
        p.sendActionBar(fuelBar("☣", fuel - cost, capacity));
    }

    private net.kyori.adventure.text.Component fuelBar(String icon, int fuel, int capacity) {
        NamedTextColor color = fuel <= 0 ? NamedTextColor.RED
                : fuel <= capacity * 0.3 ? NamedTextColor.GOLD : NamedTextColor.GREEN;
        return Txt.t(icon + " fuel " + fuel + " / " + capacity, color);
    }

    public void cleanupAll() {
        for (BukkitTask task : activeFlameBursts.values()) task.cancel();
        activeFlameBursts.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onChemicalCloudApply(AreaEffectCloudApplyEvent e) {
        AreaEffectCloud cloud = e.getEntity();
        if (!cloud.getPersistentDataContainer().has(chemicalCloudKey, PersistentDataType.BYTE)) return;
        if (!(cloud.getSource() instanceof Player owner)) return;
        e.getAffectedEntities().removeIf(entity -> TeamRules.sameSvoTeam(owner, entity));
    }
}
