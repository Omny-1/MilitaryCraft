package me.bibo.militarycraft.weapons.nuke;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

final class NukeTargeting {

    private static final double WORLD_LIMIT = 2.9999984E7;

    private NukeTargeting() {
    }

    static Location resolveTarget(Player player, int maxDist) {
        Location eye = player.getEyeLocation();
        World world = player.getWorld();
        RayTraceResult result = world.rayTraceBlocks(
                eye, eye.getDirection(), maxDist, FluidCollisionMode.NEVER, true);
        if (result != null && result.getHitBlock() != null) {
            return result.getHitBlock().getLocation().add(0.5, 0.0, 0.5);
        }
        Vector dir = eye.getDirection().normalize();
        Location projected = eye.clone().add(dir.multiply(maxDist));
        int blockX = (int) Math.floor(clamp(projected.getX()));
        int blockZ = (int) Math.floor(clamp(projected.getZ()));
        int groundY = world.getHighestBlockYAt(blockX, blockZ);
        return new Location(world, blockX + 0.5, groundY, blockZ + 0.5);
    }

    private static double clamp(double value) {
        return Math.max(-WORLD_LIMIT, Math.min(WORLD_LIMIT, value));
    }
}
