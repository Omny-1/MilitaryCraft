package me.bibo.militarycraft.core.combat;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Projectile;

/** Weapon-projectile classification (§6), generalised from TankManager's {@code isWeaponProjectile}. */
public final class Projectiles {

    private Projectiles() {
    }

    public static boolean isWeaponProjectile(Projectile p) {
        return p instanceof AbstractArrow || p instanceof Fireball || p instanceof Firework;
    }
}
