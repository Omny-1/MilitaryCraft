package me.bibo.militarycraft.gear.warkit.weapon;

import me.bibo.militarycraft.gear.warkit.Txt;
import me.bibo.militarycraft.gear.warkit.SpectatorBlock;
import me.bibo.militarycraft.gear.warkit.WarKitRuntime;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Smart trench shovel: channel, then cut a 2x8 trench or 6x8 pit while sneaking. */
public final class TrenchService {

    private final WarKitRuntime plugin;
    private final Map<UUID, BukkitRunnable> active = new HashMap<>();

    public TrenchService(WarKitRuntime plugin) {
        this.plugin = plugin;
    }

    public void startDig(Player p, ItemStack shovel) {
        if (SpectatorBlock.deny(p)) return;
        UUID id = p.getUniqueId();
        if (active.containsKey(id)) return;
        if (!hasTarget(p)) {
            p.sendActionBar(Txt.t("Nothing to dig here", NamedTextColor.YELLOW));
            return;
        }
        WeaponConfig w = plugin.weaponConfig();
        int total = Math.max(10, (int) Math.round(w.trenchDigSeconds * 20));
        int sx = p.getLocation().getBlockX(), sy = p.getLocation().getBlockY(), sz = p.getLocation().getBlockZ();

        BukkitRunnable task = new BukkitRunnable() {
            int t = 0;
            boolean shifted = false;

            @Override public void run() {
                if (!p.isOnline() || p.isDead()) { stop(); return; }
                Location l = p.getLocation();
                if (l.getBlockX() != sx || l.getBlockY() != sy || l.getBlockZ() != sz
                        || !Weapons.TRENCH_SHOVEL.equals(plugin.items().id(p.getInventory().getItemInMainHand()))) {
                    p.sendActionBar(Txt.t("Digging interrupted", NamedTextColor.RED));
                    stop();
                    return;
                }
                if (p.isSneaking()) shifted = true;
                if (t % 4 == 0) {
                    p.getWorld().playSound(p.getLocation(), Sound.BLOCK_GRAVEL_HIT, 0.7f, 0.8f);
                    p.getWorld().spawnParticle(Particle.BLOCK, p.getLocation().add(0, 0.2, 0),
                            6, 0.3, 0.1, 0.3, Material.DIRT.createBlockData());
                }
                t += 2;
                if (t < total) {
                    p.sendActionBar(progress(shifted, t, total));
                    return;
                }
                stop();
                excavate(p, shifted);
            }

            private void stop() {
                active.remove(id);
                cancel();
            }
        };
        task.runTaskTimer(plugin.bukkitPlugin(), 0L, 2L);
        active.put(id, task);
    }

    public void cancel(Player p) {
        BukkitRunnable r = active.remove(p.getUniqueId());
        if (r != null) r.cancel();
    }

    public void cancelAll() {
        for (BukkitRunnable r : active.values()) r.cancel();
        active.clear();
    }

    private Component progress(boolean shift, int t, int total) {
        int filled = Math.min(10, (int) Math.round(10.0 * t / total));
        return Txt.t((shift ? "Pit 6x8 " : "Trench 2x8 ") , NamedTextColor.GOLD)
                .append(Txt.t("▰".repeat(filled), NamedTextColor.YELLOW))
                .append(Txt.t("▱".repeat(10 - filled), NamedTextColor.DARK_GRAY));
    }

    private void excavate(Player p, boolean shift) {
        Vector fwd = forwardOf(p);
        Vector right = new Vector(-fwd.getZ(), 0, fwd.getX());
        Location base = p.getLocation().getBlock().getLocation();
        boolean floor = isFloor(p, base, fwd);
        int cleared = 0;

        if (floor) {
            int length = 8;
            int width = shift ? 6 : 2;
            int depth = 2; // d 0..2 = 3 blocks tall
            for (int l = 1; l <= length; l++) {
                for (int wI = 0; wI < width; wI++) {
                    int wOff = wI - width / 2;
                    for (int d = 0; d <= depth; d++) {
                        cleared += clear(base, fwd, right, l, wOff, -d);
                    }
                }
            }
        } else {
            int depth = 8; // tunnel length into the wall, same height
            int width = shift ? 6 : 2;
            int height = shift ? 3 : 2; // pit is 3 blocks tall
            for (int l = 1; l <= depth; l++) {
                for (int wI = 0; wI < width; wI++) {
                    int wOff = wI - width / 2;
                    for (int h = 0; h < height; h++) {
                        cleared += clear(base, fwd, right, l, wOff, h);
                    }
                }
            }
        }

        Location c = base.clone().add(fwd.clone().multiply(2)).add(0.5, 0.5, 0.5);
        p.getWorld().playSound(c, Sound.BLOCK_GRAVEL_BREAK, 1.2f, 0.7f);
        p.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, c, 10, 1.5, 0.5, 1.5, 0.01);
        p.sendActionBar(Txt.t("Done: removed " + cleared + " blocks", NamedTextColor.GREEN));
    }

    private int clear(Location base, Vector fwd, Vector right, int l, int wOff, int yOff) {
        int x = base.getBlockX() + (int) Math.round(fwd.getX() * l + right.getX() * wOff);
        int y = base.getBlockY() + yOff;
        int z = base.getBlockZ() + (int) Math.round(fwd.getZ() * l + right.getZ() * wOff);
        Block b = base.getWorld().getBlockAt(x, y, z);
        if (b.isEmpty() || b.isLiquid()) return 0;
        // Dirt-like blocks plus sandstone only: no stone, ore, wood, or builds.
        if (!isDiggable(b.getType())) return 0;
        b.setType(Material.AIR, false); // no physics: faster, no cascading updates
        return 1;
    }

    /** Shovel-mineable blocks plus all sandstone variants, which are not in MINEABLE_SHOVEL. */
    private boolean isDiggable(Material type) {
        return Tag.MINEABLE_SHOVEL.isTagged(type) || type.name().contains("SANDSTONE");
    }

    /** Whether there is a solid block ahead at feet or body height. */
    private boolean solidAhead(Location base, Vector fwd) {
        for (int h = 0; h <= 1; h++) {
            Block b = base.getWorld().getBlockAt(
                    base.getBlockX() + (int) fwd.getX(),
                    base.getBlockY() + h,
                    base.getBlockZ() + (int) fwd.getZ());
            if (!b.isPassable()) return true;
        }
        return false;
    }

    private Vector forwardOf(Player p) {
        Vector flat = p.getLocation().getDirection().clone().setY(0);
        if (flat.lengthSquared() < 1e-4) return new Vector(0, 0, 1);
        if (Math.abs(flat.getX()) >= Math.abs(flat.getZ())) return new Vector(Math.signum(flat.getX()), 0, 0);
        return new Vector(0, 0, Math.signum(flat.getZ()));
    }

    /** Dig floor if space ahead is open and the player looks sharply down; otherwise dig forward. */
    private boolean isFloor(Player p, Location base, Vector fwd) {
        return !solidAhead(base, fwd) && p.getLocation().getDirection().getY() < -0.55;
    }

    /** Whether the dig zone contains at least one diggable block. */
    private boolean hasTarget(Player p) {
        Vector fwd = forwardOf(p);
        Vector right = new Vector(-fwd.getZ(), 0, fwd.getX());
        Location base = p.getLocation().getBlock().getLocation();
        boolean floor = isFloor(p, base, fwd);
        if (floor) {
            for (int l = 1; l <= 8; l++)
                for (int d = 0; d <= 2; d++)
                    if (diggable(base, fwd, right, l, 0, -d)) return true;
        } else {
            for (int l = 1; l <= 8; l++)
                for (int h = 0; h < 2; h++)
                    if (diggable(base, fwd, right, l, 0, h)) return true;
        }
        return false;
    }

    private boolean diggable(Location base, Vector fwd, Vector right, int l, int wOff, int yOff) {
        int x = base.getBlockX() + (int) Math.round(fwd.getX() * l + right.getX() * wOff);
        int y = base.getBlockY() + yOff;
        int z = base.getBlockZ() + (int) Math.round(fwd.getZ() * l + right.getZ() * wOff);
        return isDiggable(base.getWorld().getBlockAt(x, y, z).getType());
    }
}
