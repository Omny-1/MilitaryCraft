package me.bibo.militarycraft.weapons.tckbus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The trap logic. A worker's grab freezes a passer-by in a stun; while frozen,
 * if enough workers crowd within the capture radius the victim and the workers are
 * dragged smoothly into the TckBusRig and the victim is "mobilised" (dies). If the window
 * elapses first, the victim breaks free and gets a short sprint to escape.
 *
 * <p>State lives only in memory (a 2-second stun never needs to outlive a restart),
 * and is torn down cleanly on quit / death / world-change / TckBusRig removal so a player
 * can never be left frozen.
 */
public final class TckBusSnatchManager {

    private enum Phase {STUN, CAPTURE}

    private static final class State {
        final UUID player;
        final UUID busId;
        Phase phase = Phase.STUN;
        int ticks;                 // STUN: counts down. CAPTURE: counts up.
        Location lockLoc;          // frozen position during the stun
        float prevWalkSpeed = 0.2f;
        Location startLoc;         // capture: where the player began the drag
        Location doorLoc;          // capture: the TckBusRig door
        final List<UUID> capturedWorkers = new ArrayList<>();
        final List<Location> workerStarts = new ArrayList<>();

        State(UUID player, UUID busId) {
            this.player = player;
            this.busId = busId;
        }
    }

    private final TckBusRuntime plugin;
    private final TckBusManager manager;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, Component> pendingDeath = new HashMap<>();
    // Scripted kills are queued and run AFTER the state iteration: setHealth(0)
    // fires PlayerDeathEvent synchronously, which would otherwise mutate `states`
    // mid-iteration (a ConcurrentModificationException).
    private final List<UUID> pendingKills = new ArrayList<>();
    // Post-stun grace: a player just freed cannot be re-stunned for a short window,
    // so a worker standing on top of them can't loop the stun forever.
    private final Map<UUID, Long> immuneUntil = new HashMap<>();
    // Cooldown so the "run!" warning isn't spammed across buses / at the boundary.
    private final Map<UUID, Long> warnCd = new HashMap<>();

    public TckBusSnatchManager(TckBusRuntime plugin, TckBusManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ----------------------------------------------------------------- queries

    public boolean isHeld(Player p) {
        return p != null && states.containsKey(p.getUniqueId());
    }

    /** A player who may be targeted and freshly snatched by this TckBusRig. */
    public boolean isEligible(Player p, TckBusRig TckBusRig) {
        TckBusSettings cfg = plugin.config();
        if (p == null || !p.isOnline() || p.isDead()) {
            return false;
        }
        if (states.containsKey(p.getUniqueId())) {
            return false; // already held
        }
        Long until = immuneUntil.get(p.getUniqueId());
        if (until != null) {
            if (System.currentTimeMillis() < until) {
                return false; // still in the post-stun grace window
            }
            immuneUntil.remove(p.getUniqueId()); // expired — prune lazily
        }
        if (TckBusRig != null && p.getWorld() != TckBusRig.world()) {
            return false;
        }
        if (cfg.ignoreCreative && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)) {
            return false;
        }
        if (p.hasPermission("tckbus.immune")) {
            return false;
        }
        if (cfg.ignoreOwner && TckBusRig != null && TckBusRig.owner() != null && TckBusRig.owner().equals(p.getUniqueId())) {
            return false;
        }
        return true;
    }

    /** The player a given TckBusRig is currently stunning/capturing, if any. */
    public UUID activeTargetOf(UUID busId) {
        for (State st : states.values()) {
            if (st.busId.equals(busId)) {
                return st.player;
            }
        }
        return null;
    }

    public Component takeDeathMessage(UUID player) {
        return pendingDeath.remove(player);
    }

    /** Flash the "run!" warning to a player who just entered a TckBusRig's danger zone. */
    public void warn(Player p) {
        warn(p, null);
    }

    /** Flash the "run!" warning to a player who just entered a TckBusRig's danger zone. */
    public void warn(Player p, TckBusRig TckBusRig) {
        TckBusSettings cfg = plugin.config();
        if (p == null || !p.isOnline() || p.isDead() || states.containsKey(p.getUniqueId())) {
            return;
        }
        if (cfg.ignoreCreative && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)) {
            return;
        }
        if (p.hasPermission("tckbus.immune")) {
            return;
        }
        long now = System.currentTimeMillis();
        Long until = warnCd.get(p.getUniqueId());
        if (until != null && now < until) {
            return;
        }
        warnCd.put(p.getUniqueId(), now + cfg.warnCooldownTicks * 50L);
        String busName = TckBusRig != null ? TckBusRig.skin().busName : plugin.config().defaultSkin().busName;
        p.showTitle(Title.title(
                LegacyComponentSerializer.legacyAmpersand().deserialize(cfg.warnTitle),
                Component.text(busName + " nearby - run!", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1500), Duration.ofMillis(500))));
        p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1.5f, 1.0f);
    }

    // ----------------------------------------------------------------- entry point

    /** A worker reached the victim and grabbed them — start the stun. */
    public void onGrab(Player p, TckBusRig TckBusRig, double hitDamage) {
        TckBusSettings cfg = plugin.config();
        if (!cfg.snatchEnabled || isHeld(p) || !isEligible(p, TckBusRig)) {
            return;
        }
        if (hitDamage > 0) {
            p.damage(hitDamage);
            if (p.isDead() || p.getHealth() <= 0) {
                return; // the token hit finished a near-dead player; nothing to freeze
            }
        }
        State st = new State(p.getUniqueId(), TckBusRig.id());
        st.ticks = cfg.stunTicks;
        st.lockLoc = p.getLocation().clone();
        st.prevWalkSpeed = p.getWalkSpeed();
        states.put(p.getUniqueId(), st);

        applyStunEffects(p);
        p.setWalkSpeed(0f);
        p.setSprinting(false);
        p.showTitle(Title.title(
                Component.text("✋ STOP!", NamedTextColor.RED),
                Component.text(TckBusRig.skin().displayName + " wants to talk to you...", NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(400))));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.2f, 0.7f);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
    }

    // ----------------------------------------------------------------- tick

    public void tick() {
        if (states.isEmpty()) {
            return;
        }
        TckBusSettings cfg = plugin.config();
        Iterator<Map.Entry<UUID, State>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            State st = it.next().getValue();
            Player p = Bukkit.getPlayer(st.player);
            TckBusRig TckBusRig = manager.byId(st.busId);
            if (p == null || !p.isOnline() || p.isDead() || TckBusRig == null || !TckBusRig.isActive()
                    || (st.lockLoc != null && p.getWorld() != st.lockLoc.getWorld())) {
                end(it, st, false);
                continue;
            }
            if (st.phase == Phase.STUN) {
                if (!tickStun(st, p, TckBusRig, cfg)) {
                    end(it, st, true); // window elapsed -> escape
                }
            } else {
                if (tickCapture(st, p, TckBusRig, cfg)) {
                    end(it, st, false); // capture finished
                }
            }
        }
        runPendingKills();
    }

    /** Run scripted executions after the iterator is done (see {@link #pendingKills}). */
    private void runPendingKills() {
        if (pendingKills.isEmpty()) {
            return;
        }
        for (UUID id : pendingKills) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && !p.isDead()) {
                p.setHealth(0.0);
            }
        }
        pendingKills.clear();
    }

    /** @return false when the stun window has elapsed without a capture (escape). */
    private boolean tickStun(State st, Player p, TckBusRig TckBusRig, TckBusSettings cfg) {
        freeze(p, st);
        if ((st.ticks % 4) == 0) {
            p.getWorld().spawnParticle(Particle.SOUL, p.getLocation().add(0, 1.0, 0),
                    6, 0.35, 0.6, 0.35, 0.01);
        }
        double frac = Math.max(0.0, st.ticks / (double) cfg.stunTicks);
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§6Stunned " + bar(frac)));

        int near = manager.countWorkersNear(p.getWorld(), p.getLocation(), cfg.captureRadius);
        if (near >= cfg.requiredWorkers) {
            beginCapture(st, p, TckBusRig, cfg);
            return true;
        }
        return --st.ticks > 0;
    }

    private void beginCapture(State st, Player p, TckBusRig TckBusRig, TckBusSettings cfg) {
        st.phase = Phase.CAPTURE;
        st.ticks = 0;
        st.startLoc = p.getLocation().clone();
        st.doorLoc = TckBusRig.doorWorld();
        TckBusRig.setCaptureControl(true);

        // grab the TckBusRig's two nearest alive workers to escort the victim in
        List<Mob> alive = new ArrayList<>();
        for (Mob w : TckBusRig.workers()) {
            if (w != null && w.isValid() && !w.isDead()) {
                alive.add(w);
            }
        }
        alive.sort((a, b) -> Double.compare(
                a.getLocation().distanceSquared(p.getLocation()),
                b.getLocation().distanceSquared(p.getLocation())));
        for (int i = 0; i < Math.min(2, alive.size()); i++) {
            st.capturedWorkers.add(alive.get(i).getUniqueId());
            st.workerStarts.add(alive.get(i).getLocation().clone());
        }

        p.showTitle(Title.title(
                Component.text("🚐 " + TckBusRig.skin().displayName.toUpperCase(Locale.ROOT) + " IS TAKING YOU", NamedTextColor.DARK_RED),
                Component.text("Resistance is useless", NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(400))));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.2f, 0.8f);
    }

    /** @return true when the drag has finished. */
    private boolean tickCapture(State st, Player p, TckBusRig TckBusRig, TckBusSettings cfg) {
        st.ticks++;
        float f = ease(Math.min(1f, st.ticks / (float) cfg.pullTicks));

        Location dest = lerp(st.startLoc, st.doorLoc, f);
        lookAt(dest, st.doorLoc);
        p.setVelocity(new Vector(0, 0, 0));
        p.teleport(dest);

        for (int i = 0; i < st.capturedWorkers.size(); i++) {
            Entity e = Bukkit.getEntity(st.capturedWorkers.get(i));
            if (e instanceof Mob w && w.isValid() && !w.isDead()) {
                Location wDest = lerp(st.workerStarts.get(i), TckBusRig.flankWorld(i == 1), f);
                w.teleport(wDest);
            }
        }

        p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1.0, 0), 12, 0.4, 0.7, 0.4, 0.2);
        if ((st.ticks % 6) == 0) {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.7f, 1.1f);
        }

        if (st.ticks >= cfg.pullTicks) {
            finalizeCapture(p, TckBusRig, cfg);
            return true;
        }
        return false;
    }

    private void finalizeCapture(Player p, TckBusRig TckBusRig, TckBusSettings cfg) {
        Location door = TckBusRig.doorWorld();
        p.getWorld().playSound(door, Sound.BLOCK_IRON_DOOR_CLOSE, 1.4f, 0.6f);
        p.getWorld().playSound(door, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.8f);
        p.getWorld().spawnParticle(Particle.LARGE_SMOKE, door, 30, 0.6, 0.8, 0.6, 0.03);

        if (cfg.killPlayer) {
            String raw = cfg.deathMessage
                    .replace("%player%", p.getName())
                    .replace("%bus%", TckBusRig.skin().displayName);
            pendingDeath.put(p.getUniqueId(),
                    LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
            // Queue the kill; end() restores walk-speed/effects first, then the
            // execution runs after the iterator finishes. setHealth(0) bypasses
            // totems by design (a scripted death, not damage).
            pendingKills.add(p.getUniqueId());
        }
        // workers are released (resume wandering) by end() clearing captureControl
    }

    // ----------------------------------------------------------------- teardown

    /** Remove a state, restoring the player; {@code escaped} grants the sprint-away. */
    private void end(Iterator<Map.Entry<UUID, State>> it, State st, boolean escaped) {
        it.remove();
        finish(st, escaped);
    }

    private void finish(State st, boolean escaped) {
        TckBusRig TckBusRig = manager.byId(st.busId);
        if (TckBusRig != null) {
            TckBusRig.setCaptureControl(false);
        }
        int immune = plugin.config().immunityTicks;
        if (immune > 0) {
            immuneUntil.put(st.player, System.currentTimeMillis() + immune * 50L);
        }
        Player p = Bukkit.getPlayer(st.player);
        if (p == null) {
            return;
        }
        p.setWalkSpeed(clampWalk(st.prevWalkSpeed));
        removeStunEffects(p);
        if (escaped) {
            int t = plugin.config().escapeSpeedTicks;
            if (t > 0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, t, 1, false, true, true));
            }
            String captor = TckBusRig != null ? TckBusRig.skin().displayName : plugin.config().defaultSkin().displayName;
            p.sendActionBar(Component.text("Escaped from " + captor + "! Run!", NamedTextColor.GREEN));
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        }
    }

    /** Forcibly free a player (quit / death / world-change / admin). */
    public void forceRelease(UUID playerId) {
        State st = states.remove(playerId);
        if (st != null) {
            finish(st, false);
        }
        pendingDeath.remove(playerId);
    }

    /** Free any victim tied to a TckBusRig that is being unloaded or removed. */
    public void onBusUnload(TckBusRig TckBusRig) {
        List<UUID> drop = new ArrayList<>();
        for (State st : states.values()) {
            if (st.busId.equals(TckBusRig.id())) {
                drop.add(st.player);
            }
        }
        for (UUID id : drop) {
            forceRelease(id);
        }
        TckBusRig.setCaptureControl(false);
    }

    public void releaseAll() {
        for (UUID id : new ArrayList<>(states.keySet())) {
            forceRelease(id);
        }
        states.clear();
        pendingDeath.clear();
        immuneUntil.clear();
        warnCd.clear();
    }

    // ----------------------------------------------------------------- helpers

    private void applyStunEffects(Player p) {
        int dur = plugin.config().stunTicks + plugin.config().pullTicks + 40;
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur, plugin.config().freezeSlowness, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, dur, 128, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, dur, 0, false, false, true));
    }

    private void removeStunEffects(Player p) {
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.removePotionEffect(PotionEffectType.JUMP_BOOST);
        p.removePotionEffect(PotionEffectType.DARKNESS);
    }

    private void freeze(Player p, State st) {
        Location cur = p.getLocation();
        if (cur.distanceSquared(st.lockLoc) > 0.0009) {
            Location back = st.lockLoc.clone();
            back.setYaw(cur.getYaw());
            back.setPitch(cur.getPitch());
            p.teleport(back);
        }
        p.setVelocity(new Vector(0, 0, 0));
        p.setSprinting(false);
    }

    private static float clampWalk(float v) {
        if (Float.isNaN(v) || v <= 0f) {
            return 0.2f;
        }
        return Math.min(1f, v);
    }

    private static float ease(float t) {
        return t * t * (3 - 2 * t); // smoothstep
    }

    private static Location lerp(Location a, Location b, float f) {
        return new Location(a.getWorld(),
                a.getX() + (b.getX() - a.getX()) * f,
                a.getY() + (b.getY() - a.getY()) * f,
                a.getZ() + (b.getZ() - a.getZ()) * f);
    }

    private static void lookAt(Location from, Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        if (dir.lengthSquared() < 1e-6) {
            return;
        }
        from.setDirection(dir);
    }

    private static String bar(double frac) {
        int total = 10;
        int filled = (int) Math.round(frac * total);
        StringBuilder sb = new StringBuilder("§7[§6");
        for (int i = 0; i < total; i++) {
            if (i == filled) {
                sb.append("§8");
            }
            sb.append('|');
        }
        sb.append("§7]");
        return sb.toString();
    }
}


