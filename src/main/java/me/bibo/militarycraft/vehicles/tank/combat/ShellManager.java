package me.bibo.militarycraft.vehicles.tank.combat;

import me.bibo.militarycraft.vehicles.tank.TankRuntime;
import me.bibo.militarycraft.vehicles.tank.config.TankConfig;
import me.bibo.militarycraft.vehicles.tank.tank.Tank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Owns all in-flight shells and the act of firing one. */
public final class ShellManager {

    private final TankRuntime plugin;
    private final List<Shell> shells = new ArrayList<>();

    public ShellManager(TankRuntime plugin) {
        this.plugin = plugin;
    }

    /** Fire the tank's gun if it is loaded. Returns true if a shot went off. */
    public boolean fire(Tank tank) {
        TankConfig cfg = plugin.config();
        if (tank.weaponLock() > 0) {
            warnLoading(tank);
            return false;
        }
        if (tank.reload() > 0) {
            overheat(tank, cfg); // pressing fire while reloading may overheat (only on real spam)
            return false;
        }
        if (shells.size() >= cfg.maxActiveShells) {
            warnBusy(tank);
            return false;
        }
        tank.setOverheatSwings(0); // a clean shot clears the spam counter
        Location muzzle = tank.muzzleLocation();
        Vector dir = tank.barrelDirection();

        // random inaccuracy cone
        if (cfg.spreadDegrees > 0) {
            java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
            double t = Math.tan(Math.toRadians(cfg.spreadDegrees)) * 0.45;
            dir.add(new Vector(rng.nextGaussian() * t, rng.nextGaussian() * t, rng.nextGaussian() * t));
            if (dir.lengthSquared() > 1e-6) {
                dir.normalize();
            }
        }

        // muzzle flash
        muzzle.getWorld().spawnParticle(Particle.FLAME, muzzle, 18, 0.08, 0.08, 0.08, 0.03);
        muzzle.getWorld().spawnParticle(Particle.LARGE_SMOKE, muzzle, 14, 0.1, 0.1, 0.1, 0.02);
        muzzle.getWorld().spawnParticle(Particle.FLASH, muzzle, 1, 0, 0, 0, 0);
        muzzle.getWorld().spawnParticle(Particle.EXPLOSION, muzzle, 1, 0, 0, 0, 0);
        // Muzzle blast: smoke and sparks pushed along the barrel.
        Location blast = muzzle.clone().add(dir.clone().multiply(0.9));
        muzzle.getWorld().spawnParticle(Particle.CLOUD, blast, 12, 0.18, 0.18, 0.18, 0.06);
        muzzle.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, muzzle, 6, 0.1, 0.1, 0.1, 0.02);
        muzzle.getWorld().spawnParticle(Particle.LAVA, muzzle, 4, 0.12, 0.12, 0.12, 0.0);
        muzzle.getWorld().playSound(muzzle, Sound.ENTITY_GENERIC_EXPLODE, 7.0f, 1.15f);
        muzzle.getWorld().playSound(muzzle, Sound.ITEM_FIRECHARGE_USE, 3.0f, 0.55f);

        // spawn shell a little ahead of the muzzle so it clears the barrel
        Location start = muzzle.clone().add(dir.clone().multiply(0.4));
        shells.add(new Shell(plugin, start, dir, tank));

        // A tiny horizontal recoil bump for feedback. It is capped below reverse
        // speed so firing cannot kick the hull into a jumpy terrain correction.
        double recoil = Math.min(cfg.recoil, cfg.maxReverseSpeed * 0.35);
        if (recoil > 0.0) {
            tank.setSpeed(Math.max(-cfg.maxReverseSpeed * 0.35, tank.speed() - recoil));
        }
        tank.setReload(cfg.reloadTicks);
        return true;
    }

    /**
     * Overheat only triggers on genuine spam. One click sends a few swing events,
     * so we count fire-presses made while reloading and only hurt the tank once the
     * count crosses the threshold - a single shot (or a couple of stray swings)
     * never costs HP. The counter is cleared on a clean shot and when the gun is
     * ready again (see {@code TankManager.tick}).
     */
    private void overheat(Tank tank, TankConfig cfg) {
        tank.setOverheatSwings(tank.overheatSwings() + 1);
        if (cfg.overheatDamagePercent <= 0 || tank.overheatSwings() < cfg.overheatSpamThreshold) {
            return;
        }
        tank.setOverheatSwings(0); // consumed; another full burst is needed to overheat again
        Location m = tank.muzzleLocation();
        m.getWorld().spawnParticle(Particle.LARGE_SMOKE, m, 14, 0.18, 0.18, 0.18, 0.02);
        m.getWorld().spawnParticle(Particle.SMOKE, m, 10, 0.18, 0.18, 0.18, 0.01);
        m.getWorld().playSound(m, Sound.BLOCK_FIRE_EXTINGUISH, 1.4f, 1.5f);
        if (tank.driver() != null) {
            Player driver = Bukkit.getPlayer(tank.driver());
            if (driver != null) {
                driver.sendActionBar(Component.text("⚠ OVERHEATED - stop spamming!", NamedTextColor.RED));
            }
        }
        tank.damage(tank.maxHealth() * cfg.overheatDamagePercent / 100.0);
    }

    /** Tell the driver the gun is still loading right after boarding. */
    private void warnLoading(Tank tank) {
        if (tank.driver() == null) {
            return;
        }
        Player driver = Bukkit.getPlayer(tank.driver());
        if (driver != null) {
            driver.sendActionBar(Component.text("⏳ Loading ammunition...", NamedTextColor.YELLOW));
        }
    }

    private void warnBusy(Tank tank) {
        if (tank.driver() == null) {
            return;
        }
        Player driver = Bukkit.getPlayer(tank.driver());
        if (driver != null) {
            driver.sendActionBar(Component.text("⚠ Too many shells in flight", NamedTextColor.RED));
        }
    }

    public void tick() {
        Iterator<Shell> it = shells.iterator();
        while (it.hasNext()) {
            Shell shell = it.next();
            boolean alive = shell.tick();
            if (!alive || shell.isDead()) {
                it.remove();
            }
        }
    }

    public void clear() {
        shells.clear();
    }

    public int count() {
        return shells.size();
    }
}
