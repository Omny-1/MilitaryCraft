package me.bibo.militarycraft.gear.warkit.weapon;

import me.bibo.militarycraft.gear.warkit.Txt;
import me.bibo.militarycraft.gear.warkit.TeamRules;
import me.bibo.militarycraft.gear.warkit.SpectatorBlock;
import me.bibo.militarycraft.gear.warkit.WarItems;
import me.bibo.militarycraft.gear.warkit.WarKitRuntime;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tripwires, C4 charges, proximity mines, suicide vest, and firing wall logic. */
public final class ExplosivesManager {

    private static final long TRIPWIRE_ARM_MS = 1500; // arming delay after placement

    private record BlockKey(UUID world, int x, int y, int z) {}

    private static final class Tripwire {
        final UUID owner;
        final List<Block> webs;
        final long armedAt;
        final long expireAt;
        Tripwire(UUID owner, List<Block> webs, long armedAt, long expireAt) {
            this.owner = owner; this.webs = webs; this.armedAt = armedAt; this.expireAt = expireAt;
        }
        void removeAll() {
            for (Block b : webs) if (b.getType() == Material.TRIPWIRE) b.setType(Material.AIR, false);
        }
        boolean anyExisting() {
            for (Block b : webs) if (b.getType() == Material.TRIPWIRE) return true;
            return false;
        }
    }

    private static final class Charge {
        final UUID owner;
        final Location loc;
        final BlockDisplay display;
        final long expireAt;
        Charge(UUID owner, Location loc, BlockDisplay display, long expireAt) {
            this.owner = owner; this.loc = loc; this.display = display; this.expireAt = expireAt;
        }
    }

    private static final class Mine {
        final UUID owner;
        final Location loc;
        final BlockDisplay display;
        final long armedAt;
        final long expireAt;
        Mine(UUID owner, Location loc, BlockDisplay display, long armedAt, long expireAt) {
            this.owner = owner; this.loc = loc; this.display = display;
            this.armedAt = armedAt; this.expireAt = expireAt;
        }
    }

    private final WarKitRuntime plugin;
    private final List<Tripwire> tripwires = new ArrayList<>();
    private final Map<BlockKey, Tripwire> webIndex = new HashMap<>();
    private final List<Charge> charges = new ArrayList<>();
    private final List<Mine> mines = new ArrayList<>();
    private int tickParity;

    public ExplosivesManager(WarKitRuntime plugin) {
        this.plugin = plugin;
    }

    private WeaponConfig w() {
        return plugin.weaponConfig();
    }

    // ------------------------------------------------------------------
    //  Tripwire
    // ------------------------------------------------------------------

    public boolean placeTripwire(Player p, Block clicked, BlockFace face) {
        if (SpectatorBlock.deny(p)) return false;
        if (tripwires.stream().filter(t -> t.owner.equals(p.getUniqueId())).count() >= w().tripwireMaxPerPlayer) {
            p.sendActionBar(Txt.t("Tripwire limit reached", NamedTextColor.YELLOW));
            return false;
        }
        Vector fwd = cardinal(p);
        Vector right = new Vector(-fwd.getZ(), 0, fwd.getX());
        BlockData wireData = tripwireData(right);
        Block origin = clicked.getRelative(face);
        int width = w().tripwireMaxWidth;
        List<Block> webs = new ArrayList<>();
        for (int k = -(width / 2); webs.size() < width && k <= width; k++) {
            Block b = origin.getWorld().getBlockAt(
                    origin.getX() + (int) Math.round(right.getX() * k),
                    origin.getY(),
                    origin.getZ() + (int) Math.round(right.getZ() * k));
            if (b.isReplaceable() && b.getType() != Material.TRIPWIRE) {
                b.setBlockData(wireData, false); // connected wire in one line
                webs.add(b);
            }
        }
        if (webs.isEmpty()) {
            p.sendActionBar(Txt.t("No passage here for a tripwire", NamedTextColor.YELLOW));
            return false;
        }
        long now = System.currentTimeMillis();
        Tripwire tw = new Tripwire(p.getUniqueId(), webs,
                now + TRIPWIRE_ARM_MS, now + w().tripwireLifeSeconds * 1000L);
        tripwires.add(tw);
        for (Block b : webs) webIndex.put(key(b), tw);
        p.getWorld().playSound(origin.getLocation(), Sound.BLOCK_WOOL_PLACE, 0.6f, 1.4f);
        p.sendActionBar(Txt.t("Tripwire strung (" + webs.size() + ")", NamedTextColor.GRAY));
        return true;
    }

    private void triggerTripwire(Tripwire tw, Location at) {
        World world = at.getWorld();
        // Remove the wire before the explosion so it does not drop as an item.
        clearTripwire(tw);
        world.spawnParticle(Particle.FLASH, at, 1);
        // Real explosion: can injure, kill, and break blocks.
        Player owner = plugin.getServer().getPlayer(tw.owner);
        TeamRules.protectExplosion(owner, at, explosionDamageRadius(w().tripwirePower));
        world.createExplosion(at, (float) w().tripwirePower, false, true, owner);
    }

    private void clearTripwire(Tripwire tw) {
        tw.removeAll();
        for (Block b : tw.webs) webIndex.remove(key(b));
    }

    public boolean hasTripwires() {
        return !webIndex.isEmpty();
    }

    /** Checks whether a moving player stepped onto a tripwire; any player can trigger it. */
    public void checkMove(Player p) {
        if (webIndex.isEmpty()) return;
        if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        Tripwire tw = webIndex.get(key(p.getLocation().getBlock()));
        if (tw == null) return;
        if (System.currentTimeMillis() < tw.armedAt) return; // not armed yet; protects the placer
        triggerTripwire(tw, p.getLocation().add(0, 0.3, 0));
        tripwires.remove(tw);
    }

    private BlockKey key(Block b) {
        return new BlockKey(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
    }

    // ------------------------------------------------------------------
    //  C4 (detonation: sneak + two jumps, see SpecialListener.onJump)
    // ------------------------------------------------------------------

    public boolean placeC4(Player p, Block clicked, BlockFace face) {
        if (SpectatorBlock.deny(p)) return false;
        if (charges.stream().filter(c -> c.owner.equals(p.getUniqueId())).count() >= w().c4MaxPerPlayer) {
            p.sendActionBar(Txt.t("C4 charge limit reached", NamedTextColor.YELLOW));
            return false;
        }
        World world = clicked.getWorld();
        Location loc = clicked.getLocation().add(0.5, 0.5, 0.5)
                .add(face.getModX() * 0.5, face.getModY() * 0.5, face.getModZ() * 0.5);
        float s = 0.5f;
        BlockDisplay d = world.spawn(loc, BlockDisplay.class, bd -> {
            bd.setBlock(Material.TNT.createBlockData());
            bd.setPersistent(false);
            bd.setTransformation(new Transformation(
                    new Vector3f(-s / 2f, -s / 2f, -s / 2f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(s, s, s * 0.6f),
                    new AxisAngle4f(0f, 0f, 0f, 1f)));
        });
        charges.add(new Charge(p.getUniqueId(), loc, d,
                System.currentTimeMillis() + w().c4LifeSeconds * 1000L));
        world.playSound(loc, Sound.BLOCK_GRINDSTONE_USE, 0.7f, 1.5f);
        p.sendActionBar(Txt.t("C4 charge planted - hold sneak and jump twice to detonate",
                NamedTextColor.RED));
        return true;
    }

    /** Whether the player has at least one active C4 charge for the sneak + double-jump trigger. */
    public boolean hasCharge(UUID owner) {
        for (Charge c : charges) if (c.owner.equals(owner)) return true;
        return false;
    }

    public void detonate(Player p) {
        if (SpectatorBlock.deny(p)) return;
        List<Charge> mine = new ArrayList<>();
        for (Charge c : charges) if (c.owner.equals(p.getUniqueId())) mine.add(c);
        if (mine.isEmpty()) {
            p.sendActionBar(Txt.t("No planted C4 charges", NamedTextColor.YELLOW));
            return;
        }
        for (Charge c : mine) {
            if (c.display.isValid()) c.display.remove();
            TeamRules.protectExplosion(p, c.loc, explosionDamageRadius(w().c4Power));
            c.loc.getWorld().createExplosion(c.loc, (float) w().c4Power, false, w().c4BreakBlocks, p);
            charges.remove(c);
        }
        p.sendActionBar(Txt.t("Detonated charges: " + mine.size(), NamedTextColor.RED));
    }

    // ------------------------------------------------------------------
    //  Proximity mine
    // ------------------------------------------------------------------

    public boolean placeProximityMine(Player p, Block clicked, BlockFace face) {
        if (SpectatorBlock.deny(p)) return false;
        if (mines.stream().filter(m -> m.owner.equals(p.getUniqueId())).count() >= w().mineMaxPerPlayer) {
            p.sendActionBar(Txt.t("Mine limit reached", NamedTextColor.YELLOW));
            return false;
        }
        Block support = clicked.getRelative(face);
        if (!support.isReplaceable()) {
            p.sendActionBar(Txt.t("No room to place a mine here", NamedTextColor.YELLOW));
            return false;
        }
        World world = clicked.getWorld();
        Location loc = support.getLocation().add(0.5, 0.02, 0.5);
        float sw = 0.8f, sh = 0.2f;
        BlockDisplay d = world.spawn(loc, BlockDisplay.class, bd -> {
            bd.setBlock(Material.HEAVY_WEIGHTED_PRESSURE_PLATE.createBlockData());
            bd.setPersistent(false);
            bd.setTransformation(new Transformation(
                    new Vector3f(-sw / 2f, 0f, -sw / 2f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(sw, sh, sw),
                    new AxisAngle4f(0f, 0f, 0f, 1f)));
        });
        long now = System.currentTimeMillis();
        mines.add(new Mine(p.getUniqueId(), loc.clone().add(0, 0.15, 0), d,
                now + (long) (w().mineArmSeconds * 1000),
                now + w().mineLifeSeconds * 1000L));
        world.playSound(loc, Sound.BLOCK_GRINDSTONE_USE, 0.7f, 1.8f);
        p.sendActionBar(Txt.t("Proximity mine armed", NamedTextColor.RED));
        return true;
    }

    /** Mine detonation; removal from the mines list is done by the tick iterator. */
    private void triggerMine(Mine mine) {
        World world = mine.loc.getWorld();
        if (mine.display.isValid()) mine.display.remove();
        world.spawnParticle(Particle.FLASH, mine.loc, 1);
        Player owner = plugin.getServer().getPlayer(mine.owner);
        TeamRules.protectExplosion(owner, mine.loc, explosionDamageRadius(w().minePower));
        world.createExplosion(mine.loc, (float) w().minePower, false, w().mineBreakBlocks, owner);
    }

    // ------------------------------------------------------------------
    //  Suicide vest
    // ------------------------------------------------------------------

    public void tryArmSuicide(Player p) {
        if (SpectatorBlock.deny(p)) return;
        if (!Weapons.SUICIDE_VEST.equals(plugin.items().id(p.getInventory().getChestplate()))) return;
        double arm = w().suicideArmRange;
        boolean enemyNear = false;
        for (Entity e : p.getNearbyEntities(arm, arm, arm)) {
            if (GrenadeService.affectable(e) && !TeamRules.sameSvoTeam(p, e)) { enemyNear = true; break; }
        }
        var ownVehicle = plugin.core().vehicles().riddenBy(p);
        if (!enemyNear && plugin.core().combat().vehicleNear(p.getLocation(), arm,
                ownVehicle == null ? null : ownVehicle.id()) != null) {
            enemyNear = true;
        }
        if (!enemyNear) {
            p.sendActionBar(Txt.t("No nearby target for detonation", NamedTextColor.YELLOW));
            return;
        }
        detonateSuicide(p);
    }

    private void detonateSuicide(Player p) {
        World world = p.getWorld();
        Location loc = p.getLocation().add(0, 1, 0);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 3, 1, 1, 1, 0);
        world.spawnParticle(Particle.FLASH, loc, 2);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.6f);

        double radius = w().suicideRadius;
        for (Entity e : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (!GrenadeService.affectable(e)) continue;
            if (e.equals(p)) continue;
            LivingEntity le = (LivingEntity) e;
            double dist = le.getLocation().distance(loc);
            if (dist > radius) continue;
            double falloff = Math.max(0.25, 1.0 - dist / radius);
            if (TeamRules.canDamage(p, le)) {
                le.damage(w().suicideMaxDamage * falloff, p);
            }
            Vector kb = le.getLocation().toVector().subtract(loc.toVector());
            if (kb.lengthSquared() < 1e-4) kb = new Vector(0, 1, 0);
            kb.normalize().multiply(1.4 * falloff).setY(0.6 * falloff + 0.3);
            le.setVelocity(le.getVelocity().add(kb));
        }
        // Strong explosion that breaks the surroundings.
        var ownVehicle = plugin.core().vehicles().riddenBy(p);
        plugin.core().combat().radiusDamage(loc, radius, w().suicideMaxDamage,
                ownVehicle == null ? null : ownVehicle.id(), null);
        TeamRules.protectExplosion(p, loc, explosionDamageRadius(w().suicidePower));
        me.bibo.militarycraft.core.combat.Explosions.createExplosion(
                world, loc, (float) w().suicidePower, false, w().suicideBreakBlocks, p);
        if (!p.isDead()) {
            p.damage(p.getHealth() + 1000.0, p); // death goes through the Bukkit damage pipeline
        }
    }

    // ------------------------------------------------------------------
    //  Firing wall
    // ------------------------------------------------------------------

    public boolean buildFiringWall(Player p) {
        if (SpectatorBlock.deny(p)) return false;
        Vector fwd = cardinal(p);
        Vector right = new Vector(-fwd.getZ(), 0, fwd.getX());
        Location base = p.getLocation().getBlock().getLocation();
        World world = base.getWorld();
        int width = w().firingWallWidth;
        int height = w().firingWallHeight;
        int holeH = Math.min(height - 1, 1); // eye-level firing slit
        int placed = 0;
        for (int wI = 0; wI < width; wI++) {
            int wOff = wI - width / 2;
            for (int h = 0; h < height; h++) {
                if (wOff == 0 && h == holeH) continue; // leave the firing slit open
                Block b = world.getBlockAt(
                        base.getBlockX() + (int) fwd.getX() + (int) Math.round(right.getX() * wOff),
                        base.getBlockY() + h,
                        base.getBlockZ() + (int) fwd.getZ() + (int) Math.round(right.getZ() * wOff));
                if (b.isReplaceable()) {
                    b.setType(Material.DRIED_KELP_BLOCK, false);
                    placed++;
                }
            }
        }
        if (placed == 0) {
            p.sendActionBar(Txt.t("No room to place the wall here", NamedTextColor.YELLOW));
            return false;
        }
        world.playSound(base, Sound.BLOCK_ROOTED_DIRT_PLACE, 1f, 0.8f);
        p.sendActionBar(Txt.t("Firing wall placed", NamedTextColor.GREEN));
        return true;
    }

    // ------------------------------------------------------------------
    //  Tick from the shared Ticker
    // ------------------------------------------------------------------

    public void tick() {
        tickParity++;
        long now = System.currentTimeMillis();

        Iterator<Tripwire> it = tripwires.iterator();
        while (it.hasNext()) {
            Tripwire tw = it.next();
            if (now >= tw.expireAt || !tw.anyExisting()) {
                clearTripwire(tw);
                it.remove();
                continue;
            }
            Location triggerAt = findTripwireTrigger(tw, now);
            if (triggerAt != null) {
                triggerTripwire(tw, triggerAt);
                it.remove();
            }
        }

        // Blinking C4 indicator.
        Iterator<Charge> cit = charges.iterator();
        while (cit.hasNext()) {
            Charge c = cit.next();
            if (!c.display.isValid() || now >= c.expireAt) {
                if (c.display.isValid()) c.display.remove();
                cit.remove();
            }
        }
        if (tickParity % 2 == 0) {
            for (Charge c : charges) {
                if (c.display.isValid()) {
                    c.loc.getWorld().spawnParticle(Particle.DUST, c.loc.clone().add(0, 0.35, 0), 1,
                            0, 0, 0, 0, new Particle.DustOptions(org.bukkit.Color.RED, 0.7f));
                }
            }
        }

        // Proximity mines: indicator plus nearby target checks.
        double mr = w().mineTriggerRadius;
        Iterator<Mine> mit = mines.iterator();
        while (mit.hasNext()) {
            Mine mine = mit.next();
            if (!mine.display.isValid() || now >= mine.expireAt) {
                if (mine.display.isValid()) mine.display.remove();
                mit.remove();
                continue;
            }
            boolean armed = now >= mine.armedAt;
            World world = mine.loc.getWorld();
            if (tickParity % 2 == 0) {
                world.spawnParticle(Particle.DUST, mine.loc.clone().add(0, 0.1, 0), 1, 0, 0, 0, 0,
                        new Particle.DustOptions(armed ? org.bukkit.Color.RED : org.bukkit.Color.YELLOW, 0.6f));
            }
            if (!armed) continue;
            boolean triggered = false;
            BoundingBox triggerBox = BoundingBox.of(mine.loc, mr, Math.max(0.5, mr), mr);
            for (Entity e : world.getNearbyEntities(mine.loc, mr, mr, mr)) {
                if (canTriggerMine(mine, e, triggerBox)) { triggered = true; break; }
            }
            if (!triggered && plugin.core().combat().vehicleNear(mine.loc, mr, null) != null) {
                triggered = true;
            }
            if (triggered) {
                mit.remove();
                triggerMine(mine);
            }
        }
    }

    private boolean canTriggerMine(Mine mine, Entity e, BoundingBox triggerBox) {
        if (!e.isValid() || e.isDead()) return false;
        if (e.equals(mine.display)) return false;
        if (e.getUniqueId().equals(mine.owner)) return false;
        if (e instanceof Item || e instanceof Projectile || e instanceof ExperienceOrb) return false;
        if (e instanceof Player p) {
            var gm = p.getGameMode();
            if (gm == org.bukkit.GameMode.SPECTATOR || gm == org.bukkit.GameMode.CREATIVE) return false;
        }
        return e.getBoundingBox().overlaps(triggerBox);
    }

    public void cleanupAll() {
        for (Tripwire tw : tripwires) tw.removeAll();
        tripwires.clear();
        webIndex.clear();
        for (Charge c : charges) if (c.display.isValid()) c.display.remove();
        charges.clear();
        for (Mine m : mines) if (m.display.isValid()) m.display.remove();
        mines.clear();
    }

    private Location findTripwireTrigger(Tripwire tw, long now) {
        if (now < tw.armedAt) return null;
        for (Block b : tw.webs) {
            if (b.getType() != Material.TRIPWIRE) continue;
            Location center = b.getLocation().add(0.5, 0.35, 0.5);
            for (Entity e : b.getWorld().getNearbyEntities(center, 0.65, 0.9, 0.65)) {
                if (!GrenadeService.affectable(e)) continue;
                return e.getLocation().add(0, 0.3, 0);
            }
            var vehicle = plugin.core().combat().vehicleNear(center, 0.85, null);
            if (vehicle != null) {
                return vehicle.point();
            }
        }
        return null;
    }

    private Vector cardinal(Player p) {
        Vector f = p.getLocation().getDirection();
        f.setY(0);
        if (f.lengthSquared() < 1e-4) return new Vector(0, 0, 1);
        if (Math.abs(f.getX()) >= Math.abs(f.getZ())) return new Vector(Math.signum(f.getX()), 0, 0);
        return new Vector(0, 0, Math.signum(f.getZ()));
    }

    private BlockData tripwireData(Vector line) {
        org.bukkit.block.data.type.Tripwire data =
                (org.bukkit.block.data.type.Tripwire) Material.TRIPWIRE.createBlockData();
        boolean xAxis = Math.abs(line.getX()) >= Math.abs(line.getZ());
        data.setFace(BlockFace.EAST, xAxis);
        data.setFace(BlockFace.WEST, xAxis);
        data.setFace(BlockFace.NORTH, !xAxis);
        data.setFace(BlockFace.SOUTH, !xAxis);
        data.setAttached(true);
        return data;
    }

    private double explosionDamageRadius(double power) {
        return power * 2.6 + 2.0;
    }
}
