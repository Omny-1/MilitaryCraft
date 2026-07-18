package me.bibo.militarycraft.core.model;

import org.bukkit.entity.Display;

/**
 * The standard display setup every vehicle model part uses (§5.5). Bakes in the
 * render-smoothness gotcha: teleport-duration must equal interpolation-duration, or
 * a ridden camera (which snaps every tick via {@code RETAIN_PASSENGERS}) outruns a
 * smoothly-lerped model and it visibly jerks (see {@code display-vehicle-smoothness}).
 * View-range defaults to 4.0 so the model doesn't pop for a zoomed-out (camera-module)
 * spectator.
 */
public record DisplayConfig(float viewRange, int teleportDuration, int interpolationDuration) {

    public static final DisplayConfig STANDARD = new DisplayConfig(4.0f, 2, 2);

    public void apply(Display d) {
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(viewRange);
        d.setTeleportDuration(teleportDuration);
        d.setInterpolationDuration(interpolationDuration);
        d.setPersistent(true);
    }
}
