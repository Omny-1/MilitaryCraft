package me.bibo.militarycraft.camera;

/**
 * A vehicle module calls {@link #registerScale} on its own {@code enable(Core)}
 * with its own configured zoom scale; the reconcile loop (owned by {@link CameraModule})
 * applies it to whoever is currently riding that vehicle type.
 */
public interface CameraService {

    void registerScale(String vehicleType, double scale);
}
