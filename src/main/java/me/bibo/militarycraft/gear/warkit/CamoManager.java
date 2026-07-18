package me.bibo.militarycraft.gear.warkit;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Camouflage cloak: the player is hidden from everyone (hidePlayer),
 * and a dirt BlockDisplay appears in their place.
 * Any movement, damage, attack or interaction breaks the disguise.
 */
public final class CamoManager {

    private record StoredEffect(PotionEffect effect, long savedAtMillis) {}

    private final WarKitRuntime plugin;
    private final Map<UUID, BlockDisplay> disguised = new HashMap<>();
    private final Map<UUID, BlockData> disguiseBlocks = new HashMap<>();
    private final Map<UUID, StoredEffect> previousInvisibility = new HashMap<>();
    private final Map<UUID, Long> lastHintAt = new HashMap<>();

    public CamoManager(WarKitRuntime plugin) {
        this.plugin = plugin;
    }

    public boolean isDisguised(Player p) {
        return disguised.containsKey(p.getUniqueId());
    }

    public boolean anyDisguised() {
        return !disguised.isEmpty();
    }

    public void activate(Player p) {
        if (isDisguised(p)) return;
        Location base = p.getLocation().getBlock().getLocation();
        BlockData camoBlock = chooseCamouflageBlock(base);
        BlockDisplay display = p.getWorld().spawn(base, BlockDisplay.class, d -> {
            d.setBlock(camoBlock);
            d.setPersistent(false);
        });
        // The block is visible to the player too (F5 feedback);
        // invisibility removes their own body from third-person view.
        PotionEffect previous = p.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (previous != null) {
            previousInvisibility.put(p.getUniqueId(), new StoredEffect(previous, System.currentTimeMillis()));
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                PotionEffect.INFINITE_DURATION, 0, true, false, false));
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(p)) other.hidePlayer(plugin.bukkitPlugin(), p);
        }
        disguised.put(p.getUniqueId(), display);
        disguiseBlocks.put(p.getUniqueId(), camoBlock);

        p.getWorld().spawnParticle(Particle.BLOCK, base.clone().add(0.5, 0.5, 0.5), 30,
                0.3, 0.4, 0.3, camoBlock);
        p.getWorld().playSound(base, Sound.BLOCK_GRASS_PLACE, 1f, 0.8f);
        p.sendActionBar(Txt.t("▦ You are disguised as terrain. Do not move.", NamedTextColor.GOLD));
    }

    /** Remove disguise. cooldown=false for death, quit or plugin disable. */
    public void deactivate(Player p, boolean cooldown, String message) {
        BlockDisplay display = disguised.remove(p.getUniqueId());
        if (display == null) return;
        BlockData camoBlock = disguiseBlocks.remove(p.getUniqueId());
        if (camoBlock == null) camoBlock = Material.GRASS_BLOCK.createBlockData();
        lastHintAt.remove(p.getUniqueId());
        Location at = p.getLocation();
        if (display.isValid()) display.remove();
        restoreInvisibility(p);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(p)) other.showPlayer(plugin.bukkitPlugin(), p);
        }
        if (p.isOnline()) {
            p.getWorld().spawnParticle(Particle.BLOCK, at.clone().add(0, 0.5, 0), 25,
                    0.3, 0.4, 0.3, camoBlock);
            p.getWorld().playSound(at, Sound.BLOCK_GRASS_BREAK, 1f, 0.9f);
            if (cooldown && plugin.settings().camoCooldownSeconds > 0) {
                ItemStack template = plugin.items().create(WarItems.CAMO_CLOAK);
                p.setCooldown(template, plugin.settings().camoCooldownSeconds * 20);
            }
            if (message != null) {
                p.sendActionBar(Txt.t(message, NamedTextColor.YELLOW));
            }
        }
    }

    /** Break disguise because of an external event (movement, damage, attack...). */
    public void breakDisguise(Player p, String reason) {
        if (!isDisguised(p)) return;
        deactivate(p, true, reason);
    }

    public void deactivateAll() {
        for (UUID uuid : List.copyOf(disguised.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                deactivate(p, false, null);
            } else {
                BlockDisplay d = disguised.remove(uuid);
                if (d != null && d.isValid()) d.remove();
                disguiseBlocks.remove(uuid);
                previousInvisibility.remove(uuid);
                lastHintAt.remove(uuid);
            }
        }
        disguised.clear();
        disguiseBlocks.clear();
        previousInvisibility.clear();
        lastHintAt.clear();
    }

    /** Hide all disguised players from a player who just joined. */
    public void hideAllFrom(Player joiner) {
        if (disguised.isEmpty()) return;
        for (UUID uuid : disguised.keySet()) {
            Player d = Bukkit.getPlayer(uuid);
            if (d != null && !d.equals(joiner)) joiner.hidePlayer(plugin.bukkitPlugin(), d);
        }
    }

    /** Called by the shared ticker: reminder plus display-block integrity check. */
    public void tick() {
        if (disguised.isEmpty()) return;
        for (UUID uuid : List.copyOf(disguised.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) {
                BlockDisplay d = disguised.remove(uuid);
                if (d != null && d.isValid()) d.remove();
                disguiseBlocks.remove(uuid);
                previousInvisibility.remove(uuid);
                lastHintAt.remove(uuid);
                continue;
            }
            BlockDisplay d = disguised.get(uuid);
            if (d == null || !d.isValid()) {
                breakDisguise(p, "Disguise broken");
                continue;
            }
            long now = System.currentTimeMillis();
            long last = lastHintAt.getOrDefault(uuid, 0L);
            if (now - last >= 2500L) {
                p.sendActionBar(Txt.t("▦ Disguised. Movement will break the disguise.",
                        NamedTextColor.GOLD));
                lastHintAt.put(uuid, now);
            }
        }
    }

    private void restoreInvisibility(Player p) {
        StoredEffect stored = previousInvisibility.remove(p.getUniqueId());
        PotionEffect current = p.getPotionEffect(PotionEffectType.INVISIBILITY);
        boolean currentLooksLikeCamo = current != null
                && current.getAmplifier() == 0
                && current.getDuration() == PotionEffect.INFINITE_DURATION
                && !current.hasParticles()
                && !current.hasIcon();
        if (currentLooksLikeCamo) {
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
        if (stored == null) return;
        PotionEffect previous = stored.effect();
        int duration = previous.getDuration();
        if (duration != PotionEffect.INFINITE_DURATION) {
            int elapsedTicks = (int) ((System.currentTimeMillis() - stored.savedAtMillis()) / 50L);
            duration -= elapsedTicks;
            if (duration <= 0) return;
        }
        p.addPotionEffect(new PotionEffect(previous.getType(), duration, previous.getAmplifier(),
                previous.isAmbient(), previous.hasParticles(), previous.hasIcon()));
    }

    private BlockData chooseCamouflageBlock(Location base) {
        World world = base.getWorld();
        if (world == null) return Material.GRASS_BLOCK.createBlockData();

        Block below = world.getBlockAt(base.getBlockX(), base.getBlockY() - 1, base.getBlockZ());
        Map<Material, Integer> scores = new HashMap<>();
        Map<Material, BlockData> samples = new HashMap<>();

        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 1; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    Block block = world.getBlockAt(base.getBlockX() + dx, base.getBlockY() + dy, base.getBlockZ() + dz);
                    Material type = block.getType();
                    if (!isCamouflageCandidate(type)) continue;
                    int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    int weight = Math.max(1, 8 - distance);
                    if (dy == -1) weight += 3;
                    scores.merge(type, weight, Integer::sum);
                    samples.putIfAbsent(type, block.getBlockData());
                }
            }
        }

        Material best = null;
        int bestScore = -1;
        for (Map.Entry<Material, Integer> entry : scores.entrySet()) {
            int score = entry.getValue();
            if (score > bestScore || (score == bestScore && entry.getKey() == below.getType())) {
                best = entry.getKey();
                bestScore = score;
            }
        }

        if (best != null) return samples.get(best);
        if (isCamouflageCandidate(below.getType())) return below.getBlockData();
        return Material.GRASS_BLOCK.createBlockData();
    }

    private boolean isCamouflageCandidate(Material type) {
        if (!type.isBlock() || !type.isSolid() || !type.isOccluding()) return false;
        return switch (type) {
            case BARRIER, BEDROCK, COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK,
                 STRUCTURE_BLOCK, STRUCTURE_VOID, JIGSAW, LIGHT, SPAWNER, TRIAL_SPAWNER, VAULT -> false;
            default -> true;
        };
    }
}
