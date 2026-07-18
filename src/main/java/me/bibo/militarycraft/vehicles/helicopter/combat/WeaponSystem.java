package me.bibo.militarycraft.vehicles.helicopter.combat;

import me.bibo.militarycraft.vehicles.helicopter.HelicopterRuntime;
import me.bibo.militarycraft.vehicles.helicopter.config.HelicopterConfig;
import me.bibo.militarycraft.vehicles.helicopter.helicopter.Helicopter;
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
import java.util.concurrent.ThreadLocalRandom;

/** Owns all in-flight munitions and the acts of firing rockets and dropping bombs. */
public final class WeaponSystem {

    private final HelicopterRuntime plugin;
    private final List<Projectile> munitions = new ArrayList<>();

    public WeaponSystem(HelicopterRuntime plugin) {
        this.plugin = plugin;
    }

    /** Fire one rocket from the next hardpoint (left-click). Returns true if it went off. */
    public boolean fireRocket(Helicopter heli) {
        HelicopterConfig cfg = plugin.config();
        if (heli.weaponLock() > 0) {
            warnLoading(heli);
            return false;
        }
        if (heli.rocketReload() > 0) {
            return false;
        }
        if (heli.rocketAmmo() <= 0) {
            emptyClick(heli);
            heli.setRocketReload(8); // throttle the empty click while held
            return false;
        }
        if (!hasMunitionSlot(heli)) {
            heli.setRocketReload(8);
            return false;
        }
        Location hp = heli.nextHardpoint();
        Vector dir = heli.forward();

        if (cfg.rocketSpread > 0) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            double t = Math.tan(Math.toRadians(cfg.rocketSpread)) * 0.5;
            dir.add(new Vector(rng.nextGaussian() * t, rng.nextGaussian() * t, rng.nextGaussian() * t));
            if (dir.lengthSquared() > 1e-6) {
                dir.normalize();
            }
        }

        // launch flash at the hardpoint + a smoke puff blown out along the rocket's path
        hp.getWorld().spawnParticle(Particle.FLAME, hp, 12, 0.06, 0.06, 0.06, 0.03);
        hp.getWorld().spawnParticle(Particle.LARGE_SMOKE, hp, 8, 0.08, 0.08, 0.08, 0.02);
        hp.getWorld().spawnParticle(Particle.FLASH, hp, 1, 0, 0, 0, 0);
        hp.getWorld().spawnParticle(Particle.CLOUD, hp.clone().add(dir.clone().multiply(0.7)),
                9, 0.12, 0.12, 0.12, 0.05);
        hp.getWorld().playSound(hp, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2.0f, 1.4f);
        hp.getWorld().playSound(hp, Sound.ITEM_FIRECHARGE_USE, 1.6f, 0.8f);

        // rocket inherits the helicopter's forward speed so it never gets outrun
        double launchSpeed = cfg.rocketSpeed + Math.max(0, heli.speed());
        Vector vel = dir.clone().multiply(launchSpeed);
        Location start = hp.clone().add(dir.clone().multiply(0.6));
        munitions.add(new Projectile(plugin, Projectile.Type.ROCKET, start, vel, heli));

        heli.useRocket();
        heli.setRocketReload(cfg.rocketReloadTicks);
        return true;
    }

    /** Release one bomb from the belly (right-click). Returns true if it dropped. */
    public boolean dropBomb(Helicopter heli) {
        HelicopterConfig cfg = plugin.config();
        if (heli.weaponLock() > 0) {
            warnLoading(heli);
            return false;
        }
        if (heli.bombCooldown() > 0) {
            return false;
        }
        if (heli.bombAmmo() <= 0) {
            emptyClick(heli);
            heli.setBombCooldown(10); // throttle the empty click while held
            return false;
        }
        if (!hasMunitionSlot(heli)) {
            heli.setBombCooldown(10);
            return false;
        }
        Location bay = heli.bombBay();

        // the bomb keeps (a fraction of) the helicopter's velocity, then gravity arcs it down
        Vector vel = heli.forward().multiply(heli.speed() * cfg.bombInheritVelocity);
        vel.setY(vel.getY() + Math.min(0, heli.vSpeed()) - 0.05); // inherit any descent + a small initial drop

        bay.getWorld().spawnParticle(Particle.LARGE_SMOKE, bay, 6, 0.1, 0.1, 0.1, 0.01);
        bay.getWorld().spawnParticle(Particle.CLOUD, bay, 8, 0.15, 0.1, 0.15, 0.04);
        bay.getWorld().playSound(bay, Sound.BLOCK_PISTON_EXTEND, 1.4f, 0.7f);
        bay.getWorld().playSound(bay, Sound.ENTITY_TNT_PRIMED, 0.8f, 1.2f);

        munitions.add(new Projectile(plugin, Projectile.Type.BOMB, bay, vel, heli));

        heli.useBomb();
        heli.setBombCooldown(cfg.bombCooldownTicks);
        return true;
    }

    private void emptyClick(Helicopter heli) {
        Location at = heli.anchor().clone().add(0, 0.6, 0);
        at.getWorld().playSound(at, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.2f);
    }

    private boolean hasMunitionSlot(Helicopter heli) {
        if (munitions.size() < plugin.config().maxActiveMunitions) {
            return true;
        }
        emptyClick(heli);
        if (heli.driver() != null) {
            Player p = Bukkit.getPlayer(heli.driver());
            if (p != null) {
                p.sendActionBar(Component.text("Too many munitions in flight", NamedTextColor.RED));
            }
        }
        return false;
    }

    /** Tell the pilot the weapons are still spooling up right after boarding. */
    private void warnLoading(Helicopter heli) {
        if (heli.driver() == null) {
            return;
        }
        Player p = Bukkit.getPlayer(heli.driver());
        if (p != null) {
            p.sendActionBar(Component.text("⏳ Loading ammunition...", NamedTextColor.YELLOW));
        }
    }

    public void tick() {
        Iterator<Projectile> it = munitions.iterator();
        while (it.hasNext()) {
            Projectile m = it.next();
            boolean alive;
            try {
                alive = m.tick();
            } catch (Exception ex) {
                plugin.getLogger().warning("Munition tick failed: " + ex);
                m.discard();
                alive = false;
            }
            if (!alive || m.isDead()) {
                it.remove();
            }
        }
    }

    public void clear() {
        for (Projectile m : munitions) {
            m.discard();
        }
        munitions.clear();
    }

    public int count() {
        return munitions.size();
    }
}
