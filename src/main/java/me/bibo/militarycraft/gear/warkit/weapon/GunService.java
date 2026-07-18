package me.bibo.militarycraft.gear.warkit.weapon;

import me.bibo.militarycraft.core.combat.VehicleHit;
import me.bibo.militarycraft.gear.warkit.Txt;
import me.bibo.militarycraft.gear.warkit.TeamRules;
import me.bibo.militarycraft.gear.warkit.SpectatorBlock;
import me.bibo.militarycraft.gear.warkit.WarKitRuntime;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/** Hitscan firing: ray trace, spread, tracer, attributed damage, and reloads. */
public final class GunService {

    private static final Particle.DustOptions TRACER =
            new Particle.DustOptions(Color.fromRGB(0xFFE08A), 0.7f);

    private static final class ReloadState {
        final String itemId;
        final String itemUid;
        final String token;
        BukkitTask task;

        ReloadState(String itemId, String itemUid, String token) {
            this.itemId = itemId;
            this.itemUid = itemUid;
            this.token = token;
        }
    }

    private final WarKitRuntime plugin;
    private final Map<UUID, ReloadState> reloads = new HashMap<>();
    private final NamespacedKey reloadItemKey;
    private final NamespacedKey reloadTokenKey;

    public GunService(WarKitRuntime plugin) {
        this.plugin = plugin;
        this.reloadItemKey = new NamespacedKey("warkit", "reload_item_uid");
        this.reloadTokenKey = new NamespacedKey("warkit", "reload_token");
    }

    // ------------------------------------------------------------------
    //  Handheld firearms: rifle and pistol.
    // ------------------------------------------------------------------

    public void handleGun(Player p, ItemStack item, String id) {
        if (SpectatorBlock.deny(p)) return;
        WeaponConfig w = plugin.weaponConfig();
        double damage, range, spread, reloadSec;
        int cooldown, mag;
        Sound sound;
        float pitch;
        switch (id) {
            case Weapons.RIFLE -> {
                damage = w.rifleDamage; range = w.rifleRange; spread = w.rifleSpreadDeg;
                cooldown = w.rifleFireCooldownTicks; reloadSec = w.rifleReloadSeconds; mag = w.rifleMag;
                sound = Sound.ENTITY_GENERIC_EXPLODE; pitch = 1.7f;
            }
            case Weapons.PISTOL -> {
                damage = w.pistolDamage; range = w.pistolRange; spread = w.pistolSpreadDeg;
                cooldown = w.pistolFireCooldownTicks; reloadSec = w.pistolReloadSeconds; mag = w.pistolMag;
                sound = Sound.ENTITY_GENERIC_EXPLODE; pitch = 2.0f;
            }
            default -> {
                return;
            }
        }

        if (reloads.containsKey(p.getUniqueId())) return;
        if (p.hasCooldown(item)) return;

        int ammo = plugin.weapons().getAmmo(item);
        if (ammo <= 0) {
            startReload(p, id, mag, reloadSec);
            return;
        }

        ammo--;
        plugin.weapons().setAmmo(item, ammo);
        p.getInventory().setItemInMainHand(item);
        p.setCooldown(item, cooldown);

        fireBullet(p, damage, range, adjustedSpread(p, spread), w.headshotMultiplier);
        p.getWorld().playSound(p.getLocation(), sound, 0.5f, pitch);
        p.spawnParticle(Particle.SMOKE, p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.8)),
                3, 0.05, 0.05, 0.05, 0.01);
        // Muzzle flash visible to nearby players.
        p.getWorld().spawnParticle(Particle.FLASH,
                p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.9)), 1, 0, 0, 0, 0);
        p.sendActionBar(ammoBar(id, ammo, mag));
    }

    /** One hitscan shot: damage the nearest living target and draw a tracer. */
    public void fireBullet(Player shooter, double damage, double range, double spreadDeg, double headshotMult) {
        fireBulletFrom(shooter, shooter.getEyeLocation(), shooter.getEyeLocation().getDirection(),
                damage, range, spreadDeg, headshotMult);
    }

    /** Hitscan from an arbitrary point for mounted weapons whose bullets originate at model muzzles. */
    public void fireBulletFrom(Player shooter, Location origin, Vector aimDirection,
                               double damage, double range, double spreadDeg, double headshotMult) {
        if (origin.getWorld() == null || aimDirection.lengthSquared() < 1e-6) return;
        Vector dir = applySpread(aimDirection.clone().normalize(), spreadDeg);
        World world = origin.getWorld();
        Predicate<Entity> filter = e -> isValidTarget(e, shooter) && !TeamRules.sameSvoTeam(shooter, e);

        RayTraceResult res = world.rayTrace(origin, dir, range, FluidCollisionMode.NEVER, true, 0.4, filter);
        var ownVehicle = plugin.core().vehicles().riddenBy(shooter);
        VehicleHit vehicleHit = plugin.core().combat().rayTrace(origin, dir, range, 0.35,
                ownVehicle == null ? null : ownVehicle.id());
        double vanillaDistance = res != null && res.getHitPosition() != null
                ? origin.toVector().distance(res.getHitPosition())
                : Double.POSITIVE_INFINITY;

        Vector end;
        if (vehicleHit != null && vehicleHit.distance() <= vanillaDistance) {
            double vehicleDamage = damage * plugin.weaponConfig().vehicleBulletDamageMultiplier;
            if (plugin.core().combat().directDamage(vehicleHit.vehicle(), vehicleDamage)) {
                Location point = vehicleHit.point();
                world.playSound(point, Sound.ITEM_SHIELD_BLOCK, 0.8f, 0.75f);
                world.spawnParticle(Particle.CRIT, point, 6, 0.16, 0.16, 0.16, 0.04);
                shooter.playSound(shooter.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.45f, 0.8f);
            }
            end = vehicleHit.point().toVector();
        } else if (res != null && res.getHitEntity() instanceof LivingEntity le) {
            double dmg = damage;
            Vector hit = res.getHitPosition();
            boolean head = hit != null && hit.getY() >= le.getLocation().getY() + le.getEyeHeight() - 0.25;
            if (head) dmg *= headshotMult;
            if (TeamRules.canDamage(shooter, le)) {
                le.damage(dmg, shooter);
                shooter.playSound(shooter.getLocation(), head ? Sound.BLOCK_NOTE_BLOCK_BELL
                        : Sound.BLOCK_NOTE_BLOCK_HAT, head ? 0.65f : 0.45f, head ? 1.8f : 1.5f);
                if (head) {
                    shooter.playSound(shooter.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.45f, 1.7f);
                }
            }
            end = hit != null ? hit : le.getEyeLocation().toVector();
            world.playSound(le.getLocation(), head ? Sound.ENTITY_PLAYER_ATTACK_CRIT
                    : Sound.ENTITY_ARROW_HIT_PLAYER, 1f, head ? 1.6f : 1.2f);
            if (head) {
                shooter.spawnParticle(Particle.CRIT, le.getEyeLocation(), 8, 0.2, 0.2, 0.2, 0.1);
            }
        } else if (res != null && res.getHitPosition() != null) {
            end = res.getHitPosition();
            if (res.getHitBlock() != null) {
                world.spawnParticle(Particle.BLOCK, end.toLocation(world), 6, 0.05, 0.05, 0.05,
                        res.getHitBlock().getBlockData());
            }
        } else {
            end = origin.toVector().add(dir.multiply(range));
        }
        drawTracer(world, origin.toVector(), end);
    }

    private void drawTracer(World world, Vector start, Vector end) {
        Vector path = end.clone().subtract(start);
        double length = path.length();
        if (length < 0.1) return;
        Vector step = path.normalize().multiply(2.2);
        Vector point = start.clone().add(step); // not in the shooter's face
        int steps = Math.min(28, (int) (length / 2.2));
        for (int i = 0; i < steps; i++) {
            world.spawnParticle(Particle.DUST, point.getX(), point.getY(), point.getZ(),
                    1, 0, 0, 0, 0, TRACER);
            point.add(step);
        }
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

    @SuppressWarnings("deprecation") // Paper exposes no equivalent server-side ground-state query.
    private double adjustedSpread(Player p, double baseSpread) {
        double spread = baseSpread;
        Vector velocity = p.getVelocity();
        double horizontal = velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ();
        if (!p.isOnGround()) {
            spread *= 8.0;
        } else if (p.isSprinting()) {
            spread *= 7.0;
        } else if (horizontal > 0.015) {
            spread *= 2.5;
        }
        return spread;
    }

    /** Whether this entity can be shot. */
    public static boolean isValidTarget(Entity e, Player shooter) {
        if (!(e instanceof LivingEntity)) return false;
        if (e.equals(shooter)) return false;
        if (e instanceof ArmorStand || e instanceof Display || e instanceof Interaction) return false;
        if (e instanceof Player tp) {
            return switch (tp.getGameMode()) {
                case SPECTATOR, CREATIVE -> false;
                default -> true;
            };
        }
        return true;
    }

    // ------------------------------------------------------------------
    //  Reloading
    // ------------------------------------------------------------------

    public boolean isReloading(Player p) {
        return reloads.containsKey(p.getUniqueId());
    }

    /** Manual reload for the weapon in hand, usually the F key. */
    public void reloadHeld(Player p) {
        if (SpectatorBlock.deny(p)) return;
        ItemStack item = p.getInventory().getItemInMainHand();
        String id = plugin.items().id(item);
        if (id == null) return;
        WeaponConfig w = plugin.weaponConfig();
        switch (id) {
            case Weapons.RIFLE -> startReload(p, id, w.rifleMag, w.rifleReloadSeconds);
            case Weapons.PISTOL -> startReload(p, id, w.pistolMag, w.pistolReloadSeconds);
            case Weapons.GRENADE_LAUNCHER -> startReload(p, id, w.glMag, w.glReloadSeconds);
            default -> { }
        }
    }

    public void startReload(Player p, String id, int mag, double reloadSeconds) {
        UUID uuid = p.getUniqueId();
        if (reloads.containsKey(uuid)) return;
        ItemStack item = p.getInventory().getItemInMainHand();
        if (!id.equals(plugin.items().id(item))) return;
        if (plugin.weapons().getAmmo(item) >= mag) {
            p.sendActionBar(Txt.t("Magazine full", NamedTextColor.YELLOW));
            return;
        }
        String itemUid = ensureReloadItemUid(item);
        String token = UUID.randomUUID().toString();
        setReloadToken(item, token);
        p.getInventory().setItemInMainHand(item);

        ReloadState state = new ReloadState(id, itemUid, token);
        p.sendActionBar(Txt.t("Reloading...", NamedTextColor.GOLD));
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.8f, 1.2f);

        int ticks = Math.max(2, (int) Math.round(reloadSeconds * 20));
        state.task = plugin.getServer().getScheduler().runTaskLater(plugin.bukkitPlugin(), () -> {
            if (reloads.get(uuid) != state) return;
            reloads.remove(uuid);
            if (!p.isOnline()) return;
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (!state.itemId.equals(plugin.items().id(hand))) return; // weapon type changed
            if (!state.itemUid.equals(getReloadItemUid(hand))) return; // another instance of the same weapon
            if (!state.token.equals(getReloadToken(hand))) return; // stale task or another instance
            clearReloadToken(hand);
            plugin.weapons().setAmmo(hand, mag);
            p.getInventory().setItemInMainHand(hand);
            p.sendActionBar(ammoBar(id, mag, mag));
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 1f, 1.3f);
        }, ticks);
        reloads.put(uuid, state);
    }

    public void cancelReload(Player p) {
        ReloadState state = reloads.remove(p.getUniqueId());
        if (state == null) return;
        if (state.task != null) state.task.cancel();
        ItemStack hand = p.getInventory().getItemInMainHand();
        clearReloadTokenIfMatches(hand, state);
        p.getInventory().setItemInMainHand(hand);
    }

    public void cancelAllReloads() {
        for (Map.Entry<UUID, ReloadState> entry : Map.copyOf(reloads).entrySet()) {
            ReloadState state = entry.getValue();
            if (state.task != null) state.task.cancel();
            Player p = plugin.getServer().getPlayer(entry.getKey());
            if (p != null) {
                ItemStack hand = p.getInventory().getItemInMainHand();
                clearReloadTokenIfMatches(hand, state);
                p.getInventory().setItemInMainHand(hand);
            }
        }
        reloads.clear();
    }

    private String ensureReloadItemUid(ItemStack item) {
        String existing = getReloadItemUid(item);
        if (existing != null) return existing;
        String created = UUID.randomUUID().toString();
        item.editMeta(m -> m.getPersistentDataContainer().set(reloadItemKey, PersistentDataType.STRING, created));
        return created;
    }

    private String getReloadItemUid(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(reloadItemKey, PersistentDataType.STRING);
    }

    private String getReloadToken(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(reloadTokenKey, PersistentDataType.STRING);
    }

    private void setReloadToken(ItemStack item, String token) {
        item.editMeta(m -> m.getPersistentDataContainer().set(reloadTokenKey, PersistentDataType.STRING, token));
    }

    private void clearReloadTokenIfMatches(ItemStack item, ReloadState state) {
        if (item == null || !state.itemUid.equals(getReloadItemUid(item))
                || !state.token.equals(getReloadToken(item))) {
            return;
        }
        clearReloadToken(item);
    }

    private void clearReloadToken(ItemStack item) {
        if (item == null) return;
        item.editMeta(m -> m.getPersistentDataContainer().remove(reloadTokenKey));
    }

    public net.kyori.adventure.text.Component ammoBar(String id, int ammo, int mag) {
        NamedTextColor color = ammo == 0 ? NamedTextColor.RED
                : ammo <= mag * 0.3 ? NamedTextColor.GOLD : NamedTextColor.GREEN;
        return Txt.t("⁍ " + ammo + " / " + mag, color);
    }
}
