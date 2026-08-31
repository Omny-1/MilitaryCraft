package me.bibo.militarycraft.vehicles.pickup.control;

import me.bibo.militarycraft.vehicles.pickup.PickupRuntime;
import me.bibo.militarycraft.vehicles.pickup.combat.GunManager;
import me.bibo.militarycraft.vehicles.pickup.config.PickupConfig;
import me.bibo.militarycraft.vehicles.pickup.util.MathUtil;
import me.bibo.militarycraft.vehicles.pickup.vehicle.Pickup;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Points the gun where the gunner is looking, within the limits of the mount, and turns their camera
 * into a turret control while they are in the seat.
 */
public final class GunnerController {
    private GunnerController() {
    }

    public static void aim(PickupRuntime plugin, Pickup pickup, Player gunner, PickupConfig cfg) {
        Location eye = gunner.getLocation();
        pickup.setGunYaw(eye.getYaw());
        double targetPitch = MathUtil.clamp(eye.getPitch(), -cfg.gunMaxElevation, cfg.gunMaxDepression);
        pickup.setGunPitch(MathUtil.approach(pickup.gunPitch(), targetPitch, cfg.gunPitchSpeed));
        Input in = gunner.getCurrentInput();
        if (in.isJump()) {
            GunManager.fire(plugin, pickup, gunner);
        }
        if (pickup.world().getFullTime() % (long)cfg.hudInterval == 0L) {
            Hud.send(pickup, gunner);
        }
    }
}

