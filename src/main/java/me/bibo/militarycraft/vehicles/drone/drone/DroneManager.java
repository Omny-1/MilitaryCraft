package me.bibo.militarycraft.vehicles.drone.drone;

import me.bibo.militarycraft.core.combat.VehicleHit;
import me.bibo.militarycraft.vehicles.drone.DroneRuntime;
import me.bibo.militarycraft.vehicles.drone.combat.Rocket;
import me.bibo.militarycraft.vehicles.drone.config.DroneConfig;
import me.bibo.militarycraft.vehicles.drone.control.DroneController;
import me.bibo.militarycraft.vehicles.drone.util.DriverCloak;
import me.bibo.militarycraft.vehicles.drone.util.Keys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import me.bibo.militarycraft.vehicles.drone.util.PlayerScale;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Registry of all UAVs in loaded chunks, plus the single per-tick update loop.
 * Operators ride an invisible core and the UAV flies itself; this class owns
 * boarding, exit (the UAV flies on), rockets, kamikaze detonation and the
 * lost-signal handover. UAVs persist as entities and rehydrate on chunk load.
 */
public final class DroneManager {

    private final DroneRuntime plugin;
    private final Map<UUID, Drone> drones = new LinkedHashMap<>();
    private final Map<UUID, UUID> driverToDrone = new HashMap<>();
    private BukkitTask task;
    private BukkitTask sweepTask;
    private boolean internalExplosion;
    private UUID munitionImmunePilot;
    private long tickCounter;
    private final java.util.Set<UUID> cloaked = new java.util.HashSet<>();
    private final Map<UUID, Long> meleeCd = new HashMap<>();

    public DroneManager(DroneRuntime plugin) {
        this.plugin = plugin;
    }

    // --------------------------------------------------------------- lifecycle

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin.bukkitPlugin(), this::tick, 1L, 1L);
        sweepTask = Bukkit.getScheduler().runTaskTimer(plugin.bukkitPlugin(), this::sweepLitter, 100L, 100L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
    }

    public int sweepLitter() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            long worldNow = world.getFullTime();
            for (Entity e : world.getEntities()) {
                if (e.getScoreboardTags().contains(Keys.DEBRIS_TAG)) {
                    Long expire = e.getPersistentDataContainer()
                            .get(Keys.DEBRIS_EXPIRE, PersistentDataType.LONG);
                    if (expire == null || worldNow >= expire) {
                        e.remove();
                        removed++;
                    }
                    continue;
                }
                if (e.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
                    String idStr = e.getPersistentDataContainer()
                            .get(Keys.DRONE_ID, PersistentDataType.STRING);
                    if (idStr == null) {
                        e.remove();
                        removed++;
                        continue;
                    }
                    UUID id;
                    try {
                        id = UUID.fromString(idStr);
                    } catch (IllegalArgumentException ex) {
                        e.remove();
                        removed++;
                        continue;
                    }
                    if (!drones.containsKey(id)) {
                        e.remove();
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    public void adoptExisting() {
        for (World world : Bukkit.getWorlds()) {
            onEntitiesLoad(world.getEntities());
        }
        sweepLitter();
    }

    public void shutdown() {
        stop();
        for (Drone drone : new ArrayList<>(drones.values())) {
            UUID d = drone.driver();
            if (d != null) {
                Player p = Bukkit.getPlayer(d);
                if (p != null) {
                    p.setInvisible(false);
                    DriverCloak.show(p);
                    PlayerScale.clear(p);
                }
            }
            drone.removeStand();
            drone.releaseChunkTicket();
            drone.eject();
            drone.persistState();
        }
        drones.clear();
        driverToDrone.clear();
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getScoreboardTags().contains(Keys.DEBRIS_TAG)) {
                    e.remove();
                }
            }
        }
    }

    private void tick() {
        tickCounter++;
        DroneConfig cfg = plugin.config();
        Iterator<Drone> it = drones.values().iterator();
        while (it.hasNext()) {
            Drone drone = it.next();
            if (!drone.isActive()) {
                releaseDriverBookkeeping(drone);
                drone.removeEntities();
                it.remove();
                continue;
            }
            if (cfg.altitudeEnabled && (tickCounter % cfg.altitudeIntervalTicks) == 0) {
                double over = drone.anchor().getY() - cfg.altitudeMaxY;
                if (over > 0) {
                    UUID d = drone.driver();
                    if (d != null) {
                        Player p = Bukkit.getPlayer(d);
                        if (p != null) {
                            p.sendActionBar(Component.text(cfg.altitudeMessage, NamedTextColor.RED));
                        }
                    }
                    double dmg = cfg.altitudeDamage + cfg.altitudeDamagePer10 * (over / 10.0);
                    if (dmg > 0 && drone.damage(dmg)) {
                        releaseDriverBookkeeping(drone);
                        it.remove();
                        continue;
                    }
                }
            }
            drone.tickArmTimer();
            UUID driverId = drone.driver();
            if (driverId != null) {
                Player driver = Bukkit.getPlayer(driverId);
                if (driver == null || !driver.isOnline()) {
                    operatorLost(drone); // disconnected → hand the UAV off, return them later
                } else {
                    org.bukkit.entity.Entity veh = driver.getVehicle();
                    boolean mounted = veh != null && veh.equals(drone.core());
                    if (!mounted) {
                        // The game dropped them mid-flight (fast chunk crossing). If they're
                        // still near the UAV, re-seat them and carry on; only give up (and
                        // send them back to the stand) if they've been separated for good.
                        if (driver.getWorld() == drone.world()
                                && driver.getLocation().distanceSquared(drone.anchor()) <= 2304) {
                            drone.core().addPassenger(driver);
                            mounted = true;
                        } else {
                            operatorLost(drone);
                        }
                    }
                    if (mounted) {
                        try {
                            DroneController.fly(drone, driver, this, plugin.config());
                        } catch (Exception ex) {
                            plugin.getLogger().warning("Flight tick failed: " + ex);
                        }
                    }
                }
            } else if (drone.isUnmanned()) {
                try {
                    DroneController.glide(drone, this, plugin.config());
                } catch (Exception ex) {
                    plugin.getLogger().warning("Glide tick failed: " + ex);
                }
            }
            sweepProjectiles(drone);
        }
        reconcileCloak();
    }

    /** Un-invisible / un-cloak / return-to-stand the ex-driver of a drone that just ended. */
    private void releaseDriverBookkeeping(Drone drone) {
        UUID d = drone.driver();
        if (d == null) {
            drone.removeStand();
            return;
        }
        driverToDrone.remove(d);
        cloaked.remove(d);
        Player p = Bukkit.getPlayer(d);
        if (p != null) {
            p.setInvisible(false);
            DriverCloak.show(p);
            PlayerScale.clear(p);
        }
        returnToStand(drone, p);
        drone.onDriverLost();
    }

    private void reconcileCloak() {
        java.util.Set<UUID> current = new java.util.HashSet<>();
        for (Drone drone : drones.values()) {
            UUID d = drone.driver();
            if (d != null) {
                current.add(d);
            }
        }
        for (Iterator<UUID> i = cloaked.iterator(); i.hasNext(); ) {
            UUID u = i.next();
            if (!current.contains(u)) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    DriverCloak.show(p);
                }
                i.remove();
            }
        }
        boolean periodic = (tickCounter % 20 == 0);
        for (UUID u : current) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) {
                continue;
            }
            boolean firstTime = cloaked.add(u);
            if (firstTime || periodic) {
                DriverCloak.hide(p);
            }
        }
    }

    // --------------------------------------------------------------- chunk (de)hydration

    public void onEntitiesLoad(Collection<Entity> entities) {
        Map<UUID, List<Entity>> groups = new HashMap<>();
        for (Entity e : entities) {
            if (!e.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)) {
                continue;
            }
            String idStr = e.getPersistentDataContainer().get(Keys.DRONE_ID, PersistentDataType.STRING);
            if (idStr == null) {
                continue;
            }
            UUID id;
            try {
                id = UUID.fromString(idStr);
            } catch (IllegalArgumentException ex) {
                e.remove();
                continue;
            }
            if (drones.containsKey(id)) {
                continue;
            }
            groups.computeIfAbsent(id, k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<UUID, List<Entity>> entry : groups.entrySet()) {
            Drone drone = Drone.rehydrate(plugin, entry.getKey(), entry.getValue());
            if (drone != null) {
                drones.put(entry.getKey(), drone);
            } else {
                for (Entity e : entry.getValue()) {
                    e.remove();
                }
            }
        }
    }

    public void onEntitiesUnload(Collection<Entity> entities) {
        for (Entity e : entities) {
            // Only the CORE unloading tears a drone down. The launch stand (parked at
            // the faraway launch point, same DRONE_ID, non-persistent) and the display
            // parts must NOT kill a drone that is still flying with its operator
            // elsewhere — that was the ~373-block "freeze + drop" bug: the launch chunk
            // unloaded a few seconds after take-off, the stand unloaded with it, and this
            // handler used to forget the whole (still-flying) drone.
            String role = e.getPersistentDataContainer().get(Keys.DRONE_PART, PersistentDataType.STRING);
            if (!"core".equals(role)) {
                continue;
            }
            String idStr = e.getPersistentDataContainer().get(Keys.DRONE_ID, PersistentDataType.STRING);
            if (idStr == null) {
                continue;
            }
            Drone drone;
            try {
                drone = drones.get(UUID.fromString(idStr));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (drone == null) {
                continue;
            }
            UUID d = drone.driver();
            if (d != null) {
                Player p = Bukkit.getPlayer(d);
                if (p != null) {
                    p.setInvisible(false);
                    DriverCloak.show(p);
                    PlayerScale.clear(p);
                }
                drone.eject();
            }
            drone.removeStand();
            drone.releaseChunkTicket();
            drone.persistState();
            forget(drone);
        }
    }

    // --------------------------------------------------------------- registry

    public Drone create(Location at, double yaw) {
        Drone drone = Drone.create(plugin, at, yaw);
        drones.put(drone.id(), drone);
        return drone;
    }

    public Drone byId(UUID id) {
        return drones.get(id);
    }

    public Drone byDriver(UUID playerId) {
        UUID droneId = driverToDrone.get(playerId);
        return droneId == null ? null : drones.get(droneId);
    }

    public Drone byEntity(Entity entity) {
        String id = entity.getPersistentDataContainer().get(Keys.DRONE_ID, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        try {
            return drones.get(UUID.fromString(id));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public Collection<Drone> all() {
        return drones.values();
    }

    public int count() {
        return drones.size();
    }

    // --------------------------------------------------------------- riding

    public boolean enter(Drone drone, Player player) {
        if (drone.isOccupied() || byDriver(player.getUniqueId()) != null) {
            return false;
        }
        // The launch point (where the operator stands now) becomes their return spot,
        // marked by a breakable control stand. Captured BEFORE mounting moves them.
        Location standLoc = player.getLocation().clone();
        ArmorStand stand = spawnStand(drone, standLoc, player);
        drone.setStand(stand, standLoc);

        drone.setUnmanned(false);
        drone.mount(player);
        driverToDrone.put(player.getUniqueId(), drone.id());
        player.setInvisible(true);
        cloaked.add(player.getUniqueId());
        DriverCloak.hide(player);
        PlayerScale.apply(player, plugin.config().operatorScale);
        player.getWorld().playSound(drone.anchor(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 2.0f);
        player.sendActionBar(Component.text(
                "UAV airborne - steer with camera. Right-click rocket, left-click detonate, double Shift exits",
                NamedTextColor.AQUA));
        return true;
    }

    /** Spawn the breakable launch stand at the launch point — named after the
     *  operator and wearing their head, so it reads as "their" control post. */
    private ArmorStand spawnStand(Drone drone, Location loc, Player owner) {
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, a -> {
            a.setGravity(false);
            a.setBasePlate(true);
            a.setArms(true);
            a.setPersistent(false); // despawns on unload — the return Location is kept anyway
            a.setCustomNameVisible(true);
            a.customName(Component.text(owner.getName(), NamedTextColor.AQUA));
            a.getPersistentDataContainer().set(Keys.DRONE_ID, PersistentDataType.STRING, drone.id().toString());
            a.getPersistentDataContainer().set(Keys.DRONE_PART, PersistentDataType.STRING, "stand");
            a.addScoreboardTag(Keys.SCOREBOARD_TAG);
        });
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta sm) {
            sm.setOwningPlayer(owner);
            head.setItemMeta(sm);
        }
        if (stand.getEquipment() != null) {
            stand.getEquipment().setHelmet(head);
        }
        return stand;
    }

    /** Break the stand → recall its operator to the launch point (and the UAV flies on). */
    public void recall(Drone drone) {
        if (drone.driver() != null) {
            exitControl(drone);
        } else {
            drone.removeStand();
        }
    }

    /**
     * Send the (ex-)operator back to the launch stand. The teleport is deferred one
     * tick so the vanilla dismount reposition (which drops them at the UAV) can't
     * override it. If the stand is gone, just parachute them down.
     */
    private void returnToStand(Drone drone, Player p) {
        Location stand = drone.standLocation();
        drone.removeStand();
        if (p == null) {
            return;
        }
        if (stand != null && stand.getWorld() != null) {
            final Location dest = stand.clone();
            Bukkit.getScheduler().runTask(plugin.bukkitPlugin(), () -> {
                if (p.isOnline()) {
                    p.teleport(dest);
                }
            });
        } else {
            grantSlowFall(p);
        }
    }

    /** Clean vanilla dismount (the operator pressed Shift to leave). */
    public void handleDismount(Player player) {
        UUID droneId = driverToDrone.remove(player.getUniqueId());
        if (droneId == null) {
            return;
        }
        player.setInvisible(false);
        DriverCloak.show(player);
        PlayerScale.clear(player);
        cloaked.remove(player.getUniqueId());
        Drone drone = drones.get(droneId);
        if (drone != null) {
            drone.onDriverLost();
            drone.setUnmanned(true); // flies on straight (does not fall)
            returnToStand(drone, player); // back to the launch stand
        } else {
            grantSlowFall(player);
        }
    }

    /** Fire one rocket (right click), if armed, loaded and off cooldown. */
    public void fireRocket(Drone drone) {
        DroneConfig cfg = plugin.config();
        UUID d = drone.driver();
        Player driver = d != null ? Bukkit.getPlayer(d) : null;
        if (!drone.isArmed() || drone.rocketReload() > 0) {
            return;
        }
        if (drone.rocketAmmo() <= 0) {
            if (driver != null) {
                driver.sendActionBar(Component.text("Rockets depleted", NamedTextColor.RED));
            }
            return;
        }
        Location hp = drone.nextHardpoint();
        Vector dir = drone.forward();
        applySpread(dir, cfg.rocketSpread);
        new Rocket(plugin, hp, dir, d).launch();
        drone.useRocket();
        drone.setRocketReload(cfg.rocketReloadTicks);
        drone.world().spawnParticle(Particle.SMOKE, hp, 6, 0.05, 0.05, 0.05, 0.02);
    }

    private static void applySpread(Vector dir, double degrees) {
        if (degrees <= 0) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double rad = Math.toRadians(degrees);
        dir.add(new Vector(r.nextGaussian() * rad, r.nextGaussian() * rad, r.nextGaussian() * rad));
        dir.normalize();
    }

    /** Set off the warhead: safely eject the operator (immune to this blast), then detonate. */
    public void detonate(Drone drone, Location impact) {
        UUID d = drone.driver();
        munitionImmunePilot = d;
        forceEject(drone);
        try {
            drone.detonate(impact);
        } finally {
            munitionImmunePilot = null;
        }
    }

    public VehicleHit vehicleImpact(Location center, double radius, UUID excludedDrone) {
        return plugin.core().combat().vehicleNear(center, radius, excludedDrone);
    }

    /** Command exit (/bpla exit): eject the operator; the UAV flies on straight. */
    public void exitControl(Drone drone) {
        forceEject(drone);
        drone.setUnmanned(true);
    }

    /**
     * The operator was genuinely lost (disconnect, or separated from the UAV beyond
     * recovery). Return them to the launch stand and let the UAV fly on unmanned.
     */
    private void operatorLost(Drone drone) {
        UUID d = drone.driver();
        if (d != null) {
            driverToDrone.remove(d);
            cloaked.remove(d);
            Player p = Bukkit.getPlayer(d);
            if (p != null) {
                p.setInvisible(false);
                DriverCloak.show(p);
                PlayerScale.clear(p);
            }
            returnToStand(drone, p);
        }
        drone.onDriverLost();
        drone.setUnmanned(true);
    }

    /** Battery died: eject the operator; the UAV flies on straight (lost signal). */
    public void brownout(Drone drone) {
        UUID d = drone.driver();
        Player p = d != null ? Bukkit.getPlayer(d) : null;
        forceEject(drone);
        if (p != null) {
            p.sendActionBar(Component.text("Battery depleted - signal lost!", NamedTextColor.RED));
        }
        drone.setUnmanned(true);
    }

    /** Remove the rider (clearing the driver maps first so the dismount handler ignores it). */
    private void forceEject(Drone drone) {
        UUID d = drone.driver();
        if (d != null) {
            driverToDrone.remove(d);
            cloaked.remove(d);
            Player p = Bukkit.getPlayer(d);
            drone.eject(); // nulls driver + removes passenger
            if (p != null) {
                p.setInvisible(false);
                DriverCloak.show(p);
                PlayerScale.clear(p);
            }
            returnToStand(drone, p); // teleport back to the launch stand, remove it
        } else {
            drone.eject();
            drone.removeStand();
        }
    }

    private void grantSlowFall(Player p) {
        int sf = plugin.config().ejectSlowFallTicks;
        if (sf > 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, sf, 0, true, false, false));
        }
    }

    // --------------------------------------------------------------- damage

    public void damageDronesFromExplosion(Location loc, double power) {
        DroneConfig cfg = plugin.config();
        for (Drone drone : new ArrayList<>(drones.values())) {
            applyBlastTo(drone, loc, power, cfg);
        }
    }

    public void applyExplosionTo(Drone drone, Location loc, double power) {
        if (drone != null && loc != null && Double.isFinite(power) && power > 0.0) {
            applyBlastTo(drone, loc, power, plugin.config());
        }
    }

    public void damageDronesFromAntiAir(Location loc) {
        DroneConfig cfg = plugin.config();
        for (Drone drone : new ArrayList<>(drones.values())) {
            if (!drone.isActive() || drone.world() != loc.getWorld()) {
                continue;
            }
            if (drone.anchor().distance(loc) <= 10.0) {
                drone.damage(cfg.creeperDamage);
            }
        }
    }

    private void applyBlastTo(Drone drone, Location loc, double power, DroneConfig cfg) {
        if (!drone.isActive() || drone.world() != loc.getWorld()) {
            return;
        }
        Location centre = drone.anchor().clone().add(0, 0.2, 0);
        double dist = centre.distance(loc);
        double contact = 2.0;
        double radius = power * 2.0 + contact;
        if (dist > radius) {
            return;
        }
        double falloff = dist <= contact ? 1.0 : Math.max(0.0, 1.0 - (dist - contact) / (radius - contact));
        double dmg = cfg.creeperDamage * (power / 3.0) * falloff;
        if (dmg > 0) {
            drone.damage(dmg);
        }
    }

    public void setInternalExplosion(boolean value) {
        this.internalExplosion = value;
    }

    public boolean isInternalExplosion() {
        return internalExplosion;
    }

    public void setMunitionImmunePilot(UUID pilot) {
        this.munitionImmunePilot = pilot;
    }

    public UUID munitionImmunePilot() {
        return munitionImmunePilot;
    }

    // --------------------------------------------------------------- weapon damage

    public void meleeFromPlayer(Player attacker) {
        if (byDriver(attacker.getUniqueId()) != null) {
            return;
        }
        DroneConfig cfg = plugin.config();
        World w = attacker.getWorld();
        Location eye = attacker.getEyeLocation();
        Vector dir = eye.getDirection();
        double reach = 4.5;
        Drone best = null;
        double bestDist = reach;
        Vector hitAt = null;
        for (Drone drone : drones.values()) {
            if (!drone.isActive() || drone.world() != w || drone.hitbox() == null || !drone.hitbox().isValid()) {
                continue;
            }
            RayTraceResult r = drone.hitbox().getBoundingBox().expand(0.2).rayTrace(eye.toVector(), dir, reach);
            if (r == null) {
                continue;
            }
            double dd = eye.toVector().distance(r.getHitPosition());
            if (dd < bestDist) {
                bestDist = dd;
                best = drone;
                hitAt = r.getHitPosition();
            }
        }
        if (best == null) {
            return;
        }
        if (w.rayTraceBlocks(eye, dir, Math.max(0.1, bestDist - 0.05), FluidCollisionMode.NEVER, true) != null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = meleeCd.get(attacker.getUniqueId());
        if (last != null && now - last < Math.max(50, cfg.weaponMeleeCooldownMs)) {
            return;
        }
        meleeCd.put(attacker.getUniqueId(), now);
        weaponHitFx(new Location(w, hitAt.getX(), hitAt.getY(), hitAt.getZ()));
        best.damage(best.maxHealth() * cfg.weaponMeleePercent / 100.0);
    }

    private void sweepProjectiles(Drone drone) {
        Interaction hb = drone.hitbox();
        if (hb == null || !hb.isValid()) {
            return;
        }
        DroneConfig cfg = plugin.config();
        UUID driver = drone.driver();
        for (Entity e : drone.world().getNearbyEntities(hb.getBoundingBox().expand(0.25))) {
            if (!(e instanceof Projectile proj) || !isWeaponProjectile(proj)) {
                continue;
            }
            if (driver != null && proj.getShooter() instanceof Player sp
                    && sp.getUniqueId().equals(driver)) {
                continue;
            }
            double pct = (proj instanceof Fireball) ? cfg.weaponFireballPercent : cfg.weaponArrowPercent;
            Location at = proj.getLocation();
            proj.remove();
            weaponHitFx(at);
            if (drone.damage(drone.maxHealth() * pct / 100.0)) {
                return;
            }
        }
    }

    private static boolean isWeaponProjectile(Projectile p) {
        return !(p instanceof org.bukkit.entity.FishHook
                || p instanceof org.bukkit.entity.EnderPearl
                || p instanceof org.bukkit.entity.ThrownExpBottle
                || p instanceof org.bukkit.entity.ThrownPotion);
    }

    private void weaponHitFx(Location at) {
        World w = at.getWorld();
        if (w == null) {
            return;
        }
        w.spawnParticle(Particle.CRIT, at, 6, 0.2, 0.2, 0.2, 0.1);
        w.spawnParticle(Particle.ELECTRIC_SPARK, at, 5, 0.15, 0.15, 0.15, 0.05);
        w.playSound(at, Sound.ENTITY_ITEM_BREAK, 0.7f, 1.4f);
    }

    // --------------------------------------------------------------- removal

    public int[] cleanupAll() {
        int droneCount = drones.size();
        for (Drone drone : new ArrayList<>(drones.values())) {
            UUID d = drone.driver();
            if (d != null) {
                Player p = Bukkit.getPlayer(d);
                if (p != null) {
                    p.setInvisible(false);
                    DriverCloak.show(p);
                    PlayerScale.clear(p);
                }
            }
            drone.removeStand();
            drone.eject();
            drone.removeEntities();
        }
        drones.clear();
        driverToDrone.clear();
        cloaked.clear();
        munitionImmunePilot = null;
        internalExplosion = false;

        int orphans = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getScoreboardTags().contains(Keys.SCOREBOARD_TAG)
                        || e.getScoreboardTags().contains(Keys.DEBRIS_TAG)
                        || e.getPersistentDataContainer().has(Keys.DRONE_ID, PersistentDataType.STRING)) {
                    e.remove();
                    orphans++;
                }
            }
        }
        return new int[]{droneCount, orphans};
    }

    public void remove(Drone drone, boolean effects) {
        UUID d = drone.driver();
        if (d != null) {
            Player p = Bukkit.getPlayer(d);
            if (p != null) {
                p.setInvisible(false);
                DriverCloak.show(p);
            }
        }
        if (effects) {
            drone.destroy(true);
        } else {
            drone.eject();
            drone.removeEntities();
        }
        forget(drone);
    }

    private void forget(Drone drone) {
        drones.remove(drone.id());
        driverToDrone.values().removeIf(id -> id.equals(drone.id()));
    }
}
