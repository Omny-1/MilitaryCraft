package me.bibo.militarycraft.gear.warkit.weapon;

import me.bibo.militarycraft.gear.warkit.Txt;
import me.bibo.militarycraft.gear.warkit.SpectatorBlock;
import me.bibo.militarycraft.gear.warkit.WarKitRuntime;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Personal gadgets: grappling hook, jump jet, combat stim, and recon scanner. */
public final class GadgetService {

    private final WarKitRuntime plugin;
    /** Active recon scanner holders and their expiry time in milliseconds. */
    private final Map<UUID, Long> scanningUntil = new HashMap<>();

    public GadgetService(WarKitRuntime plugin) {
        this.plugin = plugin;
    }

    private WeaponConfig w() {
        return plugin.weaponConfig();
    }

    // ------------------------------------------------------------------
    //  Grappling hook: pulls the player toward the aimed point.
    // ------------------------------------------------------------------

    public void grapplingHook(Player p, ItemStack item) {
        if (SpectatorBlock.deny(p)) return;
        if (p.hasCooldown(item)) return;
        WeaponConfig w = w();
        int charges = plugin.weapons().hasAmmoTag(item) ? plugin.weapons().getAmmo(item) : w.hookCharges;
        if (charges <= 0) {
            p.sendActionBar(Txt.t("Grappling hook depleted", NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.8f);
            return;
        }
        World world = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();

        RayTraceResult res = world.rayTrace(eye, dir, w.hookRange, FluidCollisionMode.NEVER,
                true, 0.3, e -> GunService.isValidTarget(e, p));
        if (res == null || res.getHitPosition() == null) {
            p.sendActionBar(Txt.t("Nothing to grapple", NamedTextColor.YELLOW));
            p.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 0.7f, 1.4f);
            return;
        }
        Location anchor = res.getHitPosition().toLocation(world);
        p.setCooldown(item, (int) Math.round(w.hookCooldownSeconds * 20));
        charges--;
        if (charges <= 0) {
            item.setAmount(0);
        } else {
            plugin.weapons().setAmmo(item, charges);
        }
        p.getInventory().setItemInMainHand(item);

        Vector to = anchor.toVector().subtract(p.getLocation().toVector());
        double dist = to.length();
        if (dist < 1e-3) return;
        // Farther hooks pull harder, capped for control.
        double speed = Math.min(2.4, 0.55 + dist * 0.06) * w.hookPullStrength;
        Vector vel = to.normalize().multiply(speed);
        vel.setY(vel.getY() + w.hookUpBoost);
        p.setVelocity(vel);
        Vector repeatedVelocity = vel.clone();
        plugin.getServer().getScheduler().runTaskLater(plugin.bukkitPlugin(), () -> {
            if (p.isOnline() && !p.isDead()) p.setVelocity(repeatedVelocity);
        }, 1L);
        plugin.fallImmunity().grant(p.getUniqueId(), w.hookNoFallSeconds);

        drawRope(world, eye.toVector(), anchor.toVector());
        world.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.8f);
        world.playSound(anchor, Sound.BLOCK_CHAIN_PLACE, 1f, 1.2f);
        p.sendActionBar(charges <= 0
                ? Txt.t("Last grapple - hook broke", NamedTextColor.RED)
                : Txt.t("Grappled! Charges left: " + charges, NamedTextColor.GOLD));
    }

    private void drawRope(World world, Vector start, Vector end) {
        Vector path = end.clone().subtract(start);
        double len = path.length();
        if (len < 0.1) return;
        Vector step = path.normalize().multiply(0.6);
        Vector point = start.clone();
        int steps = Math.min(60, (int) (len / 0.6));
        for (int i = 0; i < steps; i++) {
            world.spawnParticle(Particle.CRIT, point.getX(), point.getY(), point.getZ(), 1, 0, 0, 0, 0);
            point.add(step);
        }
    }

    // ------------------------------------------------------------------
    //  Jump jet: fuel-powered burst with optional refuel.
    // ------------------------------------------------------------------

    public void jumpJet(Player p, ItemStack item) {
        if (SpectatorBlock.deny(p)) return;
        WeaponConfig w = w();
        long now = System.currentTimeMillis();
        long refuelMs = (long) (w.jetRefuelSeconds * 1000);
        int max = w.jetFuel;

        int fuel = plugin.weapons().hasAmmoTag(item) ? plugin.weapons().getAmmo(item) : max;
        long last = plugin.weapons().getLastUse(item);
        if (last <= 0) last = now;
        if (refuelMs > 0 && fuel < max) {
            int regen = (int) ((now - last) / refuelMs);
            if (regen > 0) {
                fuel = Math.min(max, fuel + regen);
                last += (long) regen * refuelMs;
            }
        }
        if (fuel >= max) {
            fuel = max;
            last = now;
        }

        if (fuel < w.jetCostPerBurst) {
            plugin.weapons().setAmmo(item, fuel);
            plugin.weapons().setLastUse(item, last);
            p.getInventory().setItemInMainHand(item);
            p.sendActionBar(w.jetRefuelSeconds > 0
                    ? Txt.t("Fuel: " + fuel + "/" + max + " - wait for recharge", NamedTextColor.RED)
                    : Txt.t("Fuel depleted", NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.8f, 1f);
            return;
        }

        fuel -= w.jetCostPerBurst;
        if (fuel <= 0 && w.jetRefuelSeconds <= 0) {
            item.setAmount(0);
        } else {
            plugin.weapons().setAmmo(item, fuel);
            plugin.weapons().setLastUse(item, last);
        }
        p.getInventory().setItemInMainHand(item);

        Vector look = p.getEyeLocation().getDirection();
        Vector thrust = new Vector(look.getX() * w.jetForward, w.jetUp, look.getZ() * w.jetForward);
        p.setVelocity(p.getVelocity().multiply(0.25).add(thrust));
        plugin.fallImmunity().grant(p.getUniqueId(), w.jetNoFallSeconds);

        Location feet = p.getLocation();
        p.getWorld().spawnParticle(Particle.CLOUD, feet, 18, 0.25, 0.05, 0.25, 0.05);
        p.getWorld().spawnParticle(Particle.GUST, feet, 1, 0, 0, 0, 0);
        p.getWorld().playSound(feet, Sound.ENTITY_BREEZE_SHOOT, 1f, 1.1f);
        p.sendActionBar(fuel <= 0 && w.jetRefuelSeconds <= 0
                ? Txt.t("Last burst - jump jet broke", NamedTextColor.RED)
                : Txt.t("Fuel: " + fuel + "/" + max, NamedTextColor.AQUA));
    }

    // ------------------------------------------------------------------
    //  Combat stim: instant buff followed by a crash.
    // ------------------------------------------------------------------

    public void combatStim(Player p, ItemStack item) {
        if (SpectatorBlock.deny(p)) return;
        if (p.hasCooldown(item)) {
            int seconds = (p.getCooldown(item) + 19) / 20;
            p.sendActionBar(Txt.t("Cooldown: " + seconds + " sec", NamedTextColor.YELLOW));
            return;
        }
        WeaponConfig w = w();
        p.setCooldown(item, w.stimCooldownSeconds * 20);

        int buffTicks = w.stimBuffSeconds * 20;
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, buffTicks, w.stimSpeedAmplifier, true, true, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, buffTicks, w.stimJumpAmplifier, true, true, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, w.stimRegenSeconds * 20, 1, true, true, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, w.stimRegenSeconds * 20, 0, true, false, true));

        item.setAmount(item.getAmount() - 1);
        p.getWorld().spawnParticle(Particle.INSTANT_EFFECT, p.getLocation().add(0, 1, 0), 16, 0.4, 0.6, 0.4, 0.1);
        p.playSound(p.getLocation(), Sound.ITEM_HONEY_BOTTLE_DRINK, 1f, 1.6f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 2f);
        p.sendActionBar(Txt.t("Stim injected - move!", NamedTextColor.RED));

        final int crashTicks = w.stimCrashSeconds * 20;
        plugin.getServer().getScheduler().runTaskLater(plugin.bukkitPlugin(), () -> {
            if (!p.isOnline()) return;
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, crashTicks, 0, true, true, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, crashTicks, 0, true, true, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, crashTicks, 0, true, true, true));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 0.8f);
            p.sendActionBar(Txt.t("Stim crash...", NamedTextColor.GRAY));
        }, buffTicks);
    }

    // ------------------------------------------------------------------
    //  Recon scanner: distance to nearest living player, updated for N seconds.
    // ------------------------------------------------------------------

    public void reconScan(Player p, ItemStack item) {
        if (SpectatorBlock.deny(p)) return;
        int dur = w().scannerDurationSeconds;
        boolean wasActive = scanningUntil.containsKey(p.getUniqueId());
        scanningUntil.put(p.getUniqueId(), System.currentTimeMillis() + dur * 1000L);

        Location c = p.getLocation();
        for (int i = 0; i < 30; i++) {
            double ang = Math.toRadians(i * 12);
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    c.getX() + Math.cos(ang) * 1.6, c.getY() + 0.5, c.getZ() + Math.sin(ang) * 1.6,
                    1, 0, 0, 0, 0);
        }
        p.playSound(c, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 2f);
        p.playSound(c, Sound.UI_BUTTON_CLICK, 1f, 1.5f);
        if (!wasActive) {
            p.sendActionBar(Txt.t("Radar online for " + Math.max(1, dur / 60) + " min", NamedTextColor.AQUA));
        } else {
            p.sendActionBar(Txt.t("Radar extended", NamedTextColor.AQUA));
        }
    }

    /** Shared Ticker hook: updates nearest-player distance and expires old scanners. */
    public void scannerTick() {
        if (scanningUntil.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = scanningUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) { it.remove(); continue; }
            if (now >= entry.getValue()) {
                it.remove();
                p.sendActionBar(Txt.t("Radar offline", NamedTextColor.GRAY));
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.2f);
                continue;
            }
            reportNearest(p);
        }
    }

    public void clear(UUID uuid) {
        scanningUntil.remove(uuid);
    }

    public void cleanupAll() {
        scanningUntil.clear();
    }

    private void reportNearest(Player p) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player o : p.getWorld().getPlayers()) {
            if (o.equals(p) || o.isDead() || o.getGameMode() == GameMode.SPECTATOR) continue;
            double d = o.getLocation().distanceSquared(p.getLocation());
            if (d < best) { best = d; nearest = o; }
        }
        if (nearest == null) {
            p.sendActionBar(Txt.t("Radar: no living players nearby", NamedTextColor.AQUA));
        } else {
            p.sendActionBar(Txt.t("Nearest player: " + (int) Math.round(Math.sqrt(best)) + " m",
                    NamedTextColor.AQUA));
        }
    }
}
