package me.bibo.militarycraft.weapons.tckbus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A single TCK Bus rig: an invisible armor-stand core (the position anchor), a few
 * Interaction hitboxes tiled along its length (clickable / damageable), the
 * block-display model parts, two side "TCK" text signs, and a set of uniformed
 * worker mobs that patrol around it. Position, heading and HP are mirrored onto
 * the core's persistent data so a TckBusRig survives chunk reloads and restarts.
 *
 * <p>The van itself never moves and never animates — the model transforms are set
 * once. The only per-tick work is the worker AI, and that is skipped entirely
 * unless a player is nearby.
 */
public final class TckBusRig {

    private final TckBusRuntime plugin;
    private final UUID id;
    private final World world;
    private final Location anchor;   // footprint centre, on the ground
    private final double yaw;        // heading, degrees
    private final String skinId;
    private double health;
    private UUID owner;

    private ArmorStand core;
    private final List<Interaction> hitboxes = new ArrayList<>();
    private final List<BlockDisplay> displays = new ArrayList<>();
    private final List<TckBusModel.Part> partDefs = new ArrayList<>();
    private final List<TextDisplay> texts = new ArrayList<>();
    private final List<Mob> workers = new ArrayList<>();
    private boolean spawned;
    private int workerCount;   // intended number of workers for this TckBusRig
    private int defeated;      // how many workers a PLAYER has killed (stay dead)

    // worker AI bookkeeping
    private UUID quarry;                                  // currently chased player
    private final Map<UUID, Integer> hitCooldown = new HashMap<>();
    private final Map<UUID, Integer> wanderCooldown = new HashMap<>();
    private final Set<UUID> warnSeen = new HashSet<>();   // players currently inside the warn radius
    private boolean captureControl;                       // true while TckBusSnatchManager drives the workers

    private TckBusRig(TckBusRuntime plugin, UUID id, World world, double x, double y, double z,
                double yaw, double health, String skinId) {
        this.plugin = plugin;
        this.id = id;
        this.world = world;
        this.anchor = new Location(world, x, y, z);
        this.yaw = yaw;
        this.skinId = plugin.config().skin(skinId).id;
        this.health = health;
    }

    // ----------------------------------------------------------------- factories

    public static TckBusRig create(TckBusRuntime plugin, Location at, double yaw, UUID owner, String skinId) {
        TckBusRig b = new TckBusRig(plugin, UUID.randomUUID(), at.getWorld(),
                at.getX(), at.getY(), at.getZ(), yaw, plugin.config().maxHealth, skinId);
        b.owner = owner;
        b.workerCount = Math.max(0, plugin.config().workerCount);
        b.defeated = 0;
        b.spawnEntities();
        b.spawnWorkers(b.workerCount);
        return b;
    }

    /** Rebuild a TckBusRig from its persistent entities found in a chunk. */
    public static TckBusRig rehydrate(TckBusRuntime plugin, UUID id, List<Entity> entities) {
        TckBusSettings cfg = plugin.config();
        List<TckBusModel.Part> parts = TckBusModel.parts(cfg.rounded, cfg.camo);

        ArmorStand core = null;
        Map<Integer, BlockDisplay> partByIndex = new HashMap<>();
        List<BlockDisplay> allParts = new ArrayList<>();
        List<Interaction> foundHitboxes = new ArrayList<>();
        List<TextDisplay> foundTexts = new ArrayList<>();
        List<Mob> foundWorkers = new ArrayList<>();

        for (Entity e : entities) {
            String role = e.getPersistentDataContainer().get(TckBusKeys.ROLE, PersistentDataType.STRING);
            if (role == null) {
                continue;
            }
            Integer idx = e.getPersistentDataContainer().get(TckBusKeys.PART_INDEX, PersistentDataType.INTEGER);
            switch (role) {
                case "core" -> {
                    if (e instanceof ArmorStand a) core = a;
                }
                case "hitbox" -> {
                    if (e instanceof Interaction in) foundHitboxes.add(in);
                }
                case "text" -> {
                    if (e instanceof TextDisplay td) foundTexts.add(td);
                }
                case "worker" -> {
                    if (e instanceof Mob m) foundWorkers.add(m);
                }
                case "part" -> {
                    if (e instanceof BlockDisplay d) {
                        allParts.add(d);
                        if (idx != null && idx >= 0) partByIndex.putIfAbsent(idx, d);
                    }
                }
                default -> {
                }
            }
        }
        if (core == null) {
            return null; // anchor gone — caller drops the orphan group
        }

        PersistentDataContainer pdc = core.getPersistentDataContainer();
        double yaw = pdc.getOrDefault(TckBusKeys.STATE_YAW, PersistentDataType.DOUBLE, 0.0);
        double hp = pdc.getOrDefault(TckBusKeys.STATE_HEALTH, PersistentDataType.DOUBLE, cfg.maxHealth);
        String skinId = pdc.get(TckBusKeys.SKIN, PersistentDataType.STRING);
        Location loc = core.getLocation();

        TckBusRig b = new TckBusRig(plugin, id, core.getWorld(), loc.getX(), loc.getY(), loc.getZ(), yaw, hp, skinId);
        b.core = core;
        b.spawned = true;
        String ownerStr = pdc.get(TckBusKeys.OWNER, PersistentDataType.STRING);
        if (ownerStr != null) {
            try {
                b.owner = UUID.fromString(ownerStr);
            } catch (IllegalArgumentException ignored) {
            }
        }
        // Older buses predate these TckBusKeys — fall back to the configured count / 0 defeated.
        // Clamp PDC-restored counts: a corrupt/tampered STATE_WORKERS must not spawn an entity
        // storm. Workers can't exceed the configured max; defeated can't exceed workers.
        b.workerCount = Math.max(0, Math.min(cfg.workerCount,
                pdc.getOrDefault(TckBusKeys.STATE_WORKERS, PersistentDataType.INTEGER, cfg.workerCount)));
        b.defeated = Math.max(0, Math.min(b.workerCount,
                pdc.getOrDefault(TckBusKeys.STATE_DEFEATED, PersistentDataType.INTEGER, 0)));

        // model displays: adopt if the set matches exactly, else rebuild from scratch
        boolean complete = allParts.size() == parts.size() && partByIndex.size() == parts.size();
        for (int i = 0; complete && i < parts.size(); i++) {
            if (partByIndex.get(i) == null) complete = false;
        }
        if (complete) {
            for (int i = 0; i < parts.size(); i++) {
                b.displays.add(partByIndex.get(i));
                b.partDefs.add(parts.get(i));
            }
        } else {
            for (BlockDisplay d : allParts) {
                if (d != null) d.remove();
            }
            b.buildDisplays();
        }

        if (foundHitboxes.isEmpty()) {
            b.buildHitboxes();
        } else {
            b.hitboxes.addAll(foundHitboxes);
        }
        if (foundTexts.isEmpty() && b.skin().textEnabled) {
            b.buildTexts();
        } else {
            b.texts.addAll(foundTexts);
        }
        b.workers.addAll(foundWorkers);

        b.refreshModel();
        b.persistState();
        return b;
    }

    // ----------------------------------------------------------------- spawning

    private void spawnEntities() {
        core = world.spawn(anchor.clone(), ArmorStand.class, a -> {
            a.setInvisible(true);
            a.setGravity(false);
            a.setMarker(true);
            a.setSmall(true);
            a.setBasePlate(false);
            a.setArms(false);
            a.setSilent(true);
            a.setInvulnerable(true);
            a.setCollidable(false);
            a.setPersistent(true);
            tag(a, "core", -1);
        });
        if (owner != null) {
            core.getPersistentDataContainer().set(TckBusKeys.OWNER, PersistentDataType.STRING, owner.toString());
        }
        buildHitboxes();
        buildDisplays();
        if (skin().textEnabled) {
            buildTexts();
        }
        spawned = true;
        refreshModel();
        persistState();
    }

    private void buildHitboxes() {
        for (int i = 0; i < TckBusModel.HITBOXES.size(); i++) {
            TckBusModel.HitBox hb = TckBusModel.HITBOXES.get(i);
            Vector3f w = TckBusModel.pointToWorld(new Vector3f(hb.center()), yaw);
            Location at = anchor.clone().add(w.x, w.y - hb.height() / 2.0, w.z);
            final int idx = i;
            Interaction in = world.spawn(at, Interaction.class, x -> {
                x.setInteractionWidth(hb.width());
                x.setInteractionHeight(hb.height());
                x.setResponsive(true);
                x.setPersistent(true);
                tag(x, "hitbox", idx);
            });
            hitboxes.add(in);
        }
    }

    private void buildDisplays() {
        TckBusSettings cfg = plugin.config();
        TckBusSettings.Skin skin = skin();
        List<TckBusModel.Part> parts = TckBusModel.parts(cfg.rounded, cfg.camo);
        for (int index = 0; index < parts.size(); index++) {
            TckBusModel.Part part = parts.get(index);
            Material mat = skin.materialFor(part.role);
            final int idx = index;
            BlockDisplay d = world.spawn(anchor.clone(), BlockDisplay.class, b -> {
                b.setBlock(mat.createBlockData());
                b.setBrightness(new Display.Brightness(15, 15));
                b.setViewRange(cfg.viewRange);
                b.setPersistent(true);
                tag(b, "part", idx);
            });
            displays.add(d);
            partDefs.add(part);
        }
    }

    private void buildTexts() {
        TckBusSettings cfg = plugin.config();
        TckBusSettings.Skin skin = skin();
        Component label = Component.text(skin.textContent, skin.textColor);
        for (int i = 0; i < TckBusModel.TEXT_SIDES.size(); i++) {
            TckBusModel.TextSide side = TckBusModel.TEXT_SIDES.get(i);
            Vector3f w = TckBusModel.pointToWorld(new Vector3f(side.pos()), yaw);
            Location at = anchor.clone().add(w.x, w.y, w.z);
            at.setYaw((float) (yaw + side.baseYaw() + cfg.textYawOffset));
            at.setPitch(0f);
            final int idx = i;
            TextDisplay td = world.spawn(at, TextDisplay.class, t -> {
                t.text(label);
                t.setBillboard(Display.Billboard.FIXED);
                t.setDefaultBackground(false);
                t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                t.setSeeThrough(false);
                t.setBrightness(new Display.Brightness(15, 15));
                t.setViewRange(cfg.viewRange);
                t.setPersistent(true);
                Transformation tr = t.getTransformation();
                tr.getScale().set(cfg.textScale);
                t.setTransformation(tr);
                tag(t, "text", idx);
            });
            texts.add(td);
        }
    }

    private static final Vector3f[] WORKER_SLOTS = {
            new Vector3f(1.9f, 0f, 1.2f),
            new Vector3f(1.9f, 0f, -1.6f),
            new Vector3f(-1.9f, 0f, 0.2f),
            new Vector3f(-1.9f, 0f, -2.0f),
            new Vector3f(0f, 0f, 3.0f),
            new Vector3f(0f, 0f, -3.6f),
    };

    private void spawnWorkers(int count) {
        for (int i = 0; i < count; i++) {
            Mob m = spawnWorker(i);
            if (m != null) {
                workers.add(m);
            }
        }
    }

    /** Spawn and configure a single worker at flank slot {@code slotIndex}. */
    private Mob spawnWorker(int slotIndex) {
        Vector3f s = WORKER_SLOTS[Math.floorMod(slotIndex, WORKER_SLOTS.length)];
        Vector3f w = TckBusModel.pointToWorld(s, yaw);
        Location at = anchor.clone().add(w.x, w.y, w.z);
        Entity e = world.spawnEntity(at, plugin.config().workerType);
        if (!(e instanceof Mob m)) {
            e.remove();
            return null;
        }
        configureWorker(m);
        return m;
    }

    /**
     * Re-spawn workers that vanished for any reason OTHER than a player kill —
     * natural death, chunk/entity quirks, a partial world copy, an entity-clearing
     * task, etc. Players who kill a worker bump {@link #defeated}, and those stay
     * dead (that is the "kill both → break the TckBusRig" gate). Runs on the TckBusRig tick, so
     * a TckBusRig self-repairs its guard the moment its chunk is loaded.
     */
    public void healWorkers() {
        if (!spawned || core == null || !core.isValid()) {
            return;
        }
        // Forget dead / invalid references so the alive count is accurate.
        workers.removeIf(w -> w == null || !w.isValid() || w.isDead());
        int target = Math.max(0, workerCount - defeated);
        // Trim any duplicates (e.g. a worker that re-attached from a neighbouring
        // chunk after a top-up already ran) so the count can't drift upward.
        while (workers.size() > target) {
            Mob extra = workers.remove(workers.size() - 1);
            if (extra != null) {
                extra.remove();
            }
        }
        for (int i = workers.size(); i < target; i++) {
            Mob m = spawnWorker(i);
            if (m != null) {
                workers.add(m);
            }
        }
    }

    /**
     * Re-attach a late-loading entity to this already-live TckBusRig. Workers roam and can
     * sit in a neighbouring chunk that loads a tick after the core, so without this
     * they would be dropped as orphans and {@link #healWorkers()} would double them.
     */
    public void adopt(Entity e) {
        if (!(e instanceof Mob m)) {
            return; // parts / hitboxes / texts load together with the core
        }
        String role = e.getPersistentDataContainer().get(TckBusKeys.ROLE, PersistentDataType.STRING);
        if (!"worker".equals(role)) {
            return;
        }
        for (Mob w : workers) {
            if (w != null && w.getUniqueId().equals(m.getUniqueId())) {
                return; // already tracked
            }
        }
        workers.add(m);
    }

    /**
     * Record a worker's death. A player kill counts toward the defeat gate (stays
     * dead); anything else is treated as an accidental vanish and will be re-spawned
     * by {@link #healWorkers()}.
     *
     * @return alive workers still standing between the TckBusRig and being breakable.
     */
    public int onWorkerDeath(UUID workerId, boolean byPlayer) {
        workers.removeIf(w -> w == null || !w.isValid() || w.isDead()
                || w.getUniqueId().equals(workerId));
        if (byPlayer && defeated < workerCount) {
            defeated++;
        }
        persistState();
        return Math.max(0, workerCount - defeated);
    }

    @SuppressWarnings("deprecation")
    private void configureWorker(Mob m) {
        TckBusSettings cfg = plugin.config();
        TckBusSettings.Skin skin = skin();
        m.customName(LegacyComponentSerializer.legacyAmpersand().deserialize(skin.workerName));
        m.setCustomNameVisible(cfg.workerShowName);
        m.setPersistent(true);
        m.setRemoveWhenFarAway(false);
        m.setCanPickupItems(false);

        AttributeInstance hp = m.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(cfg.workerHealth);
            m.setHealth(cfg.workerHealth);
        }

        EntityEquipment eq = m.getEquipment();
        if (eq != null) {
            eq.setItemInMainHand(cfg.handItem != null ? new ItemStack(cfg.handItem) : null);
            eq.setHelmet(armor(cfg.helmet, skin.uniformColor));
            eq.setChestplate(armor(cfg.chestplate, skin.uniformColor));
            eq.setLeggings(armor(cfg.leggings, skin.uniformColor));
            eq.setBoots(armor(cfg.boots, skin.uniformColor));
            eq.setItemInMainHandDropChance(0f);
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
        }
        tag(m, "worker", -1);
    }

    private static ItemStack armor(Material mat, Color color) {
        if (mat == null) {
            return null;
        }
        ItemStack item = new ItemStack(mat);
        if (item.getItemMeta() instanceof LeatherArmorMeta lam) {
            lam.setColor(color);
            item.setItemMeta(lam);
        }
        return item;
    }

    private void tag(Entity e, String role, int index) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        pdc.set(TckBusKeys.BUS_ID, PersistentDataType.STRING, id.toString());
        pdc.set(TckBusKeys.ROLE, PersistentDataType.STRING, role);
        pdc.set(TckBusKeys.SKIN, PersistentDataType.STRING, skinId);
        if (index >= 0) {
            pdc.set(TckBusKeys.PART_INDEX, PersistentDataType.INTEGER, index);
        }
        e.addScoreboardTag(TckBusKeys.SCOREBOARD_TAG);
    }

    public void persistState() {
        if (core == null || !core.isValid()) {
            return;
        }
        PersistentDataContainer pdc = core.getPersistentDataContainer();
        pdc.set(TckBusKeys.SKIN, PersistentDataType.STRING, skinId);
        pdc.set(TckBusKeys.STATE_YAW, PersistentDataType.DOUBLE, yaw);
        pdc.set(TckBusKeys.STATE_HEALTH, PersistentDataType.DOUBLE, health);
        pdc.set(TckBusKeys.STATE_WORKERS, PersistentDataType.INTEGER, workerCount);
        pdc.set(TckBusKeys.STATE_DEFEATED, PersistentDataType.INTEGER, defeated);
    }

    // ----------------------------------------------------------------- rendering

    /** Push every part's transform once (the van never animates after this). */
    public void refreshModel() {
        if (!spawned) {
            return;
        }
        for (int i = 0; i < displays.size(); i++) {
            BlockDisplay d = displays.get(i);
            if (d == null || !d.isValid()) {
                continue;
            }
            d.setTransformation(TckBusModel.forPart(partDefs.get(i), yaw));
        }
    }

    /** Snap the rig back onto its anchor if something shoved the core. */
    public void stabilize() {
        if (!spawned || core == null || !core.isValid() || core.getWorld() != anchor.getWorld()) {
            return;
        }
        Location cl = core.getLocation();
        if (Math.abs(cl.getX() - anchor.getX()) > 0.03
                || Math.abs(cl.getY() - anchor.getY()) > 0.03
                || Math.abs(cl.getZ() - anchor.getZ()) > 0.03) {
            core.teleport(anchor);
        }
    }

    // ----------------------------------------------------------------- worker AI

    /**
     * Per-tick worker behaviour: lock onto a quarry the TckBusRig shares between all its
     * workers (so they gang up and a 2nd reliably arrives during the stun), path
     * toward it, snatch on contact, and otherwise wander near the TckBusRig. Skipped
     * cheaply when no player is nearby, and surrendered while a capture animation
     * owns the workers.
     */
    public void tick(long tickCounter, TckBusSettings cfg) {
        stabilize();
        if (captureControl) {
            return; // TckBusSnatchManager is driving the workers
        }

        Player nearest = nearestPlayer(cfg.activationRange);
        if (nearest == null) {
            quarry = null;
            warnSeen.clear();      // re-warn players when they next approach
            setWorkersAware(false); // dormant: freeze AI so they stand by the TckBusRig
            return;
        }
        setWorkersAware(true);
        warnNearby(cfg);

        // Re-pick the quarry on the retarget cadence (staggered per TckBusRig).
        long phase = Math.floorMod(id.hashCode(), cfg.retargetTicks);
        if (((tickCounter + phase) % cfg.retargetTicks) == 0) {
            chooseQuarry(cfg);
        }
        Player target = quarryPlayer();

        for (Mob w : workers) {
            if (w == null || !w.isValid() || w.isDead()) {
                continue;
            }
            UUID wid = w.getUniqueId();
            hitCooldown.merge(wid, -1, Integer::sum);

            double leashSq = cfg.leashRange * cfg.leashRange;
            if (w.getWorld() != world || w.getLocation().distanceSquared(anchor) > leashSq) {
                returnToBus(w, cfg);
                continue;
            }
            if (target != null) {
                w.getPathfinder().moveTo(target.getLocation(), cfg.workerSpeed);
                trySnatch(w, target, cfg);
            } else {
                wander(w, cfg);
            }
        }
    }

    private void chooseQuarry(TckBusSettings cfg) {
        // Stay locked on a victim this TckBusRig is already stunning/capturing.
        UUID locked = plugin.snatch().activeTargetOf(id);
        if (locked != null) {
            Player p = plugin.getServer().getPlayer(locked);
            if (p != null && p.isOnline()) {
                quarry = locked;
                return;
            }
        }
        Player best = null;
        double bestSq = cfg.aggroRange * cfg.aggroRange;
        for (Player p : world.getPlayers()) {
            if (!plugin.snatch().isEligible(p, this)) {
                continue;
            }
            double dSq = p.getLocation().distanceSquared(anchor);
            if (dSq <= bestSq) {
                bestSq = dSq;
                best = p;
            }
        }
        quarry = best != null ? best.getUniqueId() : null;
    }

    private void trySnatch(Mob w, Player target, TckBusSettings cfg) {
        if (!cfg.snatchEnabled) {
            return;
        }
        if (hitCooldown.getOrDefault(w.getUniqueId(), 0) > 0) {
            return;
        }
        if (w.getLocation().distanceSquared(target.getLocation()) > cfg.meleeRange * cfg.meleeRange) {
            return;
        }
        if (!plugin.snatch().isEligible(target, this)) {
            return;
        }
        // The grab: swing, sound, a token hit, and (if the victim is free) the stun.
        w.swingMainHand();
        w.getWorld().playSound(w.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
        hitCooldown.put(w.getUniqueId(), cfg.hitCooldownTicks);
        plugin.snatch().onGrab(target, this, cfg.hitDamage);
    }

    private void wander(Mob w, TckBusSettings cfg) {
        UUID wid = w.getUniqueId();
        int cd = wanderCooldown.merge(wid, -1, Integer::sum);
        if (cd > 0) {
            return;
        }
        wanderCooldown.put(wid, 40 + ThreadLocalRandom.current().nextInt(80));
        double r = cfg.wanderRadius;
        Location dest = anchor.clone().add(
                ThreadLocalRandom.current().nextDouble(-r, r), 0,
                ThreadLocalRandom.current().nextDouble(-r, r));
        w.getPathfinder().moveTo(dest, cfg.workerSpeed * 0.7);
    }

    private void returnToBus(Mob w, TckBusSettings cfg) {
        if (w.getWorld() != world || w.getLocation().distanceSquared(anchor) > 64 * 64) {
            // Hopelessly far / wrong world — yank it back to a flank slot.
            Vector3f off = TckBusModel.pointToWorld(new Vector3f(2.0f, 0f, 0f), yaw);
            w.teleport(anchor.clone().add(off.x, off.y, off.z));
            return;
        }
        w.getPathfinder().moveTo(anchor, cfg.workerSpeed);
    }

    /** Flash the "run!" warning to players the moment they enter the danger zone. */
    private void warnNearby(TckBusSettings cfg) {
        double rSq = cfg.warnRange * cfg.warnRange;
        Set<UUID> inNow = new HashSet<>();
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(anchor) > rSq) {
                continue;
            }
            inNow.add(p.getUniqueId());
            if (warnSeen.add(p.getUniqueId())) { // newly entered the radius
                plugin.snatch().warn(p, this);
            }
        }
        warnSeen.retainAll(inNow); // those who left can be warned again on return
    }

    private void setWorkersAware(boolean aware) {
        for (Mob w : workers) {
            if (w != null && w.isValid() && w.isAware() != aware) {
                w.setAware(aware);
            }
        }
    }

    private Player nearestPlayer(double range) {
        Player best = null;
        double bestSq = range * range;
        for (Player p : world.getPlayers()) {
            double dSq = p.getLocation().distanceSquared(anchor);
            if (dSq <= bestSq) {
                bestSq = dSq;
                best = p;
            }
        }
        return best;
    }

    private Player quarryPlayer() {
        if (quarry == null) {
            return null;
        }
        Player p = plugin.getServer().getPlayer(quarry);
        return (p != null && p.isOnline() && p.getWorld() == world) ? p : null;
    }

    // ----------------------------------------------------------------- damage / loot

    /** @return true if this hit destroyed the TckBusRig. */
    public boolean damage(double amount) {
        if (amount <= 0 || !spawned) {
            return false;
        }
        if (!breakable()) {
            // Protected until every worker is player-defeated: shrug off the hit.
            Location c = doorWorld();
            world.spawnParticle(Particle.ELECTRIC_SPARK, c, 6, 0.6, 0.6, 0.6, 0.02);
            world.playSound(c, Sound.ITEM_SHIELD_BLOCK, 0.7f, 1.2f);
            return false;
        }
        health -= amount;
        persistState();
        if (health <= 0) {
            destroy(true);
            return true;
        }
        Location c = anchor.clone().add(0, 1.4, 0);
        world.spawnParticle(Particle.CRIT, c, 8, 1.2, 0.8, 1.6, 0.0);
        world.spawnParticle(Particle.LARGE_SMOKE, c, 6, 1.0, 0.8, 1.4, 0.02);
        world.playSound(c, Sound.ENTITY_IRON_GOLEM_HURT, 0.8f, 0.7f);
        return false;
    }

    public void destroy(boolean effects) {
        if (effects && spawned) {
            Location c = anchor.clone().add(0, 1.2, 0);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, c, 2, 1.6, 0.8, 2.6, 0);
            world.spawnParticle(Particle.LARGE_SMOKE, c, 90, 2.0, 1.2, 3.0, 0.05);
            world.spawnParticle(Particle.FLAME, c, 40, 1.6, 1.0, 2.6, 0.05);
            world.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 5f, 0.7f);
            world.playSound(c, Sound.BLOCK_GLASS_BREAK, 3f, 0.8f);
            if (plugin.config().debris) {
                flingDebris(c);
            }
            dropLoot();
            if (plugin.config().dropPlacerOnDestroy) {
                world.dropItemNaturally(doorWorld(), TckBusItem.create(skin()));
            }
        }
        removeEntities();
    }

    private void dropLoot() {
        Location at = doorWorld();
        for (ItemStack loot : plugin.drops().get()) {
            if (loot != null && !loot.getType().isAir()) {
                world.dropItemNaturally(at, loot);
            }
        }
    }

    private void flingDebris(Location c) {
        Material mat = skin().bodyBlock;
        for (int i = 0; i < 14; i++) {
            BlockDisplay chunk = world.spawn(c, BlockDisplay.class, b -> {
                b.setBlock(mat.createBlockData());
                b.setBrightness(new Display.Brightness(15, 15));
                b.setPersistent(false);
                Transformation t = b.getTransformation();
                t.getScale().set(0.5f);
                b.setTransformation(t);
            });
            Vector v = new Vector(ThreadLocalRandom.current().nextGaussian() * 0.45,
                    0.35 + ThreadLocalRandom.current().nextDouble() * 0.5,
                    ThreadLocalRandom.current().nextGaussian() * 0.45);
            new org.bukkit.scheduler.BukkitRunnable() {
                int life = 40;
                final Vector vel = v;

                @Override
                public void run() {
                    if (life-- <= 0 || !chunk.isValid()) {
                        chunk.remove();
                        cancel();
                        return;
                    }
                    vel.setY(vel.getY() - 0.04);
                    chunk.teleport(chunk.getLocation().add(vel));
                }
            }.runTaskTimer(plugin.plugin(), 1, 1);
        }
    }

    /** Delete every entity belonging to this TckBusRig, INCLUDING its workers. */
    public void removeEntities() {
        if (core != null) core.remove();
        for (Interaction in : hitboxes) if (in != null) in.remove();
        for (BlockDisplay d : displays) if (d != null) d.remove();
        for (TextDisplay t : texts) if (t != null) t.remove();
        for (Mob w : workers) if (w != null) w.remove();
        hitboxes.clear();
        displays.clear();
        partDefs.clear();
        texts.clear();
        workers.clear();
        spawned = false;
    }

    /** Smallest distance from {@code loc} to any key point of the van. */
    public double minBlastDistance(Location loc) {
        double best = Double.MAX_VALUE;
        for (Vector3f kp : TckBusModel.BLAST_POINTS) {
            Vector3f w = TckBusModel.pointToWorld(kp, yaw);
            double dx = anchor.getX() + w.x - loc.getX();
            double dy = anchor.getY() + w.y - loc.getY();
            double dz = anchor.getZ() + w.z - loc.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < best) best = d;
        }
        return Math.sqrt(best);
    }

    // ----------------------------------------------------------------- getters

    public UUID id() {
        return id;
    }

    public World world() {
        return world;
    }

    public Location anchor() {
        return anchor.clone();
    }

    public double yaw() {
        return yaw;
    }

    public UUID owner() {
        return owner;
    }

    public TckBusSettings.Skin skin() {
        return plugin.config().skin(skinId);
    }

    public String skinId() {
        return skinId;
    }

    public boolean isActive() {
        return spawned && core != null && core.isValid();
    }

    public List<Mob> workers() {
        return workers;
    }

    public int aliveWorkers() {
        int n = 0;
        for (Mob w : workers) {
            if (w != null && w.isValid() && !w.isDead()) n++;
        }
        return n;
    }

    public boolean breakable() {
        // Breakable only once a player has put down every worker. Accidental deaths
        // (which re-spawn) never expose the TckBusRig.
        return defeated >= workerCount;
    }

    public boolean hasWorker(UUID uuid) {
        for (Mob w : workers) {
            if (w != null && w.getUniqueId().equals(uuid)) return true;
        }
        return false;
    }

    /** The right-hand door mouth, in world space — where captives are dragged. */
    public Location doorWorld() {
        Vector3f w = TckBusModel.pointToWorld(TckBusModel.DOOR, yaw);
        Location d = anchor.clone().add(w.x, w.y, w.z);
        d.setYaw((float) (yaw + 90));
        return d;
    }

    public Location flankWorld(boolean second) {
        Vector3f off = TckBusModel.pointToWorld(second ? TckBusModel.DOOR_FLANK_B : TckBusModel.DOOR_FLANK_A, yaw);
        return anchor.clone().add(off.x, off.y, off.z);
    }

    public void setCaptureControl(boolean v) {
        this.captureControl = v;
    }

    public void messageNearby(Component msg, double radius) {
        double rSq = radius * radius;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(anchor) <= rSq) {
                p.sendActionBar(msg);
            }
        }
    }

    /** Convenience used by the break handler to nudge the player toward the goal. */
    public Component protectedHint() {
        TckBusSettings.Skin skin = skin();
        return Component.text(skin.busName + " is protected while " + skin.workerPlural + " are alive!", NamedTextColor.RED);
    }
}


