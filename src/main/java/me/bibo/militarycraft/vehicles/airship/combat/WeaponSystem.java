package me.bibo.militarycraft.vehicles.airship.combat;

import me.bibo.militarycraft.vehicles.airship.AirshipRuntime;
import me.bibo.militarycraft.vehicles.airship.airship.Airship;
import me.bibo.militarycraft.vehicles.airship.config.AirshipConfig;
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

/** Owns all in-flight bombs and the act of releasing them from the bomb bay. */
public final class WeaponSystem {

    private final AirshipRuntime plugin;
    private final List<Bomb> bombs = new ArrayList<>();

    public WeaponSystem(AirshipRuntime plugin) {
        this.plugin = plugin;
    }

    /** Release one bomb from the gondola belly. Returns true if it dropped. */
    public boolean dropBomb(Airship ship) {
        AirshipConfig cfg = plugin.config();
        if (ship.weaponLock() > 0) {
            warnLoading(ship);
            return false;
        }
        if (ship.bombCooldown() > 0) {
            return false;
        }
        if (ship.bombAmmo() <= 0) {
            emptyClick(ship);
            ship.setBombCooldown(10); // throttle the empty click while held
            return false;
        }
        Location bay = ship.bombBay();

        // the bomb keeps (a fraction of) the airship's velocity, then gravity arcs it down
        Vector vel = ship.forward().multiply(ship.speed() * cfg.bombInheritVelocity);
        vel.setY(vel.getY() + Math.min(0, ship.vSpeed()) - 0.05); // inherit any descent + a small initial drop

        bay.getWorld().spawnParticle(Particle.LARGE_SMOKE, bay, 6, 0.1, 0.1, 0.1, 0.01);
        bay.getWorld().spawnParticle(Particle.CLOUD, bay, 8, 0.15, 0.1, 0.15, 0.04);
        bay.getWorld().playSound(bay, Sound.BLOCK_PISTON_EXTEND, 1.4f, 0.7f);
        bay.getWorld().playSound(bay, Sound.ENTITY_TNT_PRIMED, 0.8f, 1.2f);

        bombs.add(new Bomb(plugin, bay, vel, ship));

        ship.useBomb();
        ship.setBombCooldown(cfg.bombCooldownTicks);
        return true;
    }

    private void emptyClick(Airship ship) {
        Location at = ship.anchor().clone().add(0, 0.6, 0);
        at.getWorld().playSound(at, Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.2f);
    }

    /** Tell the pilot the bomb bay is still loading right after boarding. */
    private void warnLoading(Airship ship) {
        if (ship.driver() == null) {
            return;
        }
        Player p = Bukkit.getPlayer(ship.driver());
        if (p != null) {
            p.sendActionBar(Component.text("⏳ Loading ammunition...", NamedTextColor.YELLOW));
        }
    }

    public void tick() {
        Iterator<Bomb> it = bombs.iterator();
        while (it.hasNext()) {
            Bomb m = it.next();
            boolean alive;
            try {
                alive = m.tick();
            } catch (Exception ex) {
                plugin.getLogger().warning("Bomb tick failed: " + ex);
                m.discard();
                alive = false;
            }
            if (!alive || m.isDead()) {
                it.remove();
            }
        }
    }

    public void clear() {
        for (Bomb b : bombs) {
            b.discard();
        }
        bombs.clear();
    }

    public int count() {
        return bombs.size();
    }
}
