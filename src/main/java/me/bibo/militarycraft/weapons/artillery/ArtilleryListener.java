package me.bibo.militarycraft.weapons.artillery;

import me.bibo.militarycraft.core.Core;
import me.bibo.militarycraft.core.event.ExplosionSink;
import me.bibo.militarycraft.core.event.InteractSink;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Artillery placement/entry plus block, session and chunk lifecycle protection. */
final class ArtilleryListener implements InteractSink, ExplosionSink, Listener {

    private static final long HIT_COOLDOWN_MILLIS = 250L;

    private final Core core;
    private final ArtilleryManager manager;
    private final Map<UUID, Long> lastHit = new HashMap<>();

    ArtilleryListener(Core core, ArtilleryManager manager) {
        this.core = core;
        this.manager = manager;
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (manager.sessions().active(player)) {
            event.setCancelled(true);
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block clicked = event.getClickedBlock();
        Artillery artillery = clicked == null ? null : manager.get(clicked.getLocation());

        if (event.getAction() == Action.LEFT_CLICK_BLOCK && artillery != null) {
            event.setCancelled(true);
            if (player.getGameMode() == GameMode.CREATIVE) {
                return;
            }
            if (!allowed(player, "damage")) {
                ArtilleryMessages.action(player, "You do not have permission to damage artillery.");
                return;
            }
            long now = System.currentTimeMillis();
            long previous = lastHit.getOrDefault(player.getUniqueId(), 0L);
            if (now - previous < HIT_COOLDOWN_MILLIS) {
                return;
            }
            lastHit.put(player.getUniqueId(), now);
            manager.hit(artillery, player);
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || clicked == null) {
            return;
        }
        if (artillery != null) {
            event.setCancelled(true);
            if (!allowed(player, "enter")) {
                ArtilleryMessages.action(player, "You do not have permission to operate artillery.");
                return;
            }
            manager.sessions().open(player, artillery);
            return;
        }

        ItemStack item = event.getItem();
        if (!ArtilleryItem.isItem(core.items(), item)) {
            return;
        }
        event.setCancelled(true);
        if (!allowed(player, "place")) {
            ArtilleryMessages.action(player, "You do not have permission to place artillery.");
            return;
        }
        Block target = clicked.getRelative(event.getBlockFace());
        Location location = target.getLocation();
        float yaw = player.getLocation().getYaw();
        if (!manager.canPlace(location, yaw)) {
            ArtilleryMessages.action(player, "That block cannot hold artillery.");
            return;
        }
        Artillery placed = manager.create(location, yaw);
        if (placed == null) {
            ArtilleryMessages.action(player, "Artillery placement failed.");
            return;
        }
        player.getWorld().playSound(location.clone().add(0.5, 0.5, 0.5),
                Sound.BLOCK_ANVIL_PLACE, 1.0f, 0.7f);
        if (manager.settings().consumeItem && player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
        ArtilleryMessages.send(player, "&a" + ArtilleryMessages.NAME + " placed.");
    }

    @Override
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // The functional interaction surface is the registered barrier block.
    }

    @Override
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> manager.get(block.getLocation()) != null);
    }

    @Override
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> manager.get(block.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Artillery artillery = manager.get(event.getBlock().getLocation());
        if (artillery == null) {
            return;
        }
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            event.setDropItems(false);
            if (manager.remove(artillery, false)) {
                ArtilleryMessages.send(event.getPlayer(), "&aArtillery removed from the registry.");
            } else {
                event.setCancelled(true);
                ArtilleryMessages.send(event.getPlayer(), "&cArtillery removal could not be persisted.");
            }
            return;
        }
        event.setCancelled(true);
        ArtilleryMessages.action(event.getPlayer(), "This artillery block is protected.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (manager.get(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (manager.get(block.getLocation()) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (manager.get(block.getLocation()) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        manager.models().spawnChunk(event.getWorld().getUID(),
                event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        manager.models().despawnChunk(event.getWorld().getUID(),
                event.getChunk().getX(), event.getChunk().getZ());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.sessions().restoreIfPending(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.sessions().onQuit(event.getPlayer());
        lastHit.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        manager.sessions().onDeath(event.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        manager.sessions().onRespawn(event);
    }

    private boolean allowed(Player player, String action) {
        return player.hasPermission("militarycraft.admin")
                || player.hasPermission("militarycraft.artillery." + action);
    }
}
