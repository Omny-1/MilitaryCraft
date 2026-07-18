package me.bibo.militarycraft.weapons.artillery;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/** One stationary artillery installation, addressed by its barrier block. */
public final class Artillery {

    private final UUID id;
    private final UUID worldId;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;

    private float yaw;
    private int ammo;
    private long lastShotMillis;
    private int health;
    private transient UUID operator;

    Artillery(UUID id, UUID worldId, String worldName, int x, int y, int z,
              float yaw, int ammo, long lastShotMillis, int health) {
        this.id = id;
        this.worldId = worldId;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.ammo = ammo;
        this.lastShotMillis = lastShotMillis;
        this.health = health;
    }

    public UUID id() {
        return id;
    }

    public UUID worldId() {
        return worldId;
    }

    public String worldName() {
        return worldName;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    void setYaw(float yaw) {
        this.yaw = normalizeYaw(yaw);
    }

    public int ammo() {
        return ammo;
    }

    public int health() {
        return health;
    }

    public long lastShotMillis() {
        return lastShotMillis;
    }

    public UUID operator() {
        return operator;
    }

    void setOperator(UUID operator) {
        this.operator = operator;
    }

    boolean hasAmmo() {
        return ammo > 0;
    }

    long cooldownRemaining(long cooldownMillis, long now) {
        if (cooldownMillis <= 0L) {
            return 0L;
        }
        long elapsed = now >= lastShotMillis ? now - lastShotMillis : 0L;
        return Math.max(0L, cooldownMillis - elapsed);
    }

    void consumeSalvo(long now) {
        if (ammo <= 0) {
            throw new IllegalStateException("Artillery has no ammo");
        }
        ammo--;
        lastShotMillis = now;
    }

    void restoreFiringState(int ammo, long lastShotMillis) {
        this.ammo = ammo;
        this.lastShotMillis = lastShotMillis;
    }

    void hit() {
        if (health > 0) {
            health--;
        }
    }

    void restoreHealth(int health) {
        this.health = health;
    }

    boolean wrecked() {
        return health <= 0;
    }

    boolean clampState(ArtillerySettings settings) {
        int oldAmmo = ammo;
        int oldHealth = health;
        float oldYaw = yaw;
        long oldLastShot = lastShotMillis;
        ammo = Math.max(0, Math.min(ammo, settings.maxAmmo));
        health = Math.max(0, Math.min(health, settings.maxHits));
        yaw = normalizeYaw(yaw);
        long now = System.currentTimeMillis();
        lastShotMillis = Math.max(0L, Math.min(lastShotMillis, now));
        return ammo != oldAmmo || health != oldHealth
                || Float.compare(yaw, oldYaw) != 0 || lastShotMillis != oldLastShot;
    }

    public World world() {
        World world = Bukkit.getWorld(worldId);
        return world != null ? world : Bukkit.getWorld(worldName);
    }

    public Location blockLocation() {
        World world = world();
        return world == null ? null : new Location(world, x, y, z);
    }

    String positionKey() {
        return positionKey(worldId, x, y, z);
    }

    static String positionKey(Location location) {
        return positionKey(location.getWorld().getUID(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private static String positionKey(UUID worldId, int x, int y, int z) {
        return worldId + ";" + x + ";" + y + ";" + z;
    }

    private static float normalizeYaw(float yaw) {
        if (!Float.isFinite(yaw)) {
            return 0.0f;
        }
        float normalized = yaw % 360.0f;
        if (normalized <= -180.0f) {
            normalized += 360.0f;
        } else if (normalized > 180.0f) {
            normalized -= 360.0f;
        }
        return normalized;
    }
}
