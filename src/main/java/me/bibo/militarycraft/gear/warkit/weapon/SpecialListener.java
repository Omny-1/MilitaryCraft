package me.bibo.militarycraft.gear.warkit.weapon;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import me.bibo.militarycraft.gear.warkit.SpectatorBlock;
import me.bibo.militarycraft.gear.warkit.WarKitRuntime;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Double-sneak detonates the suicide vest; sneak + two jumps detonates planted C4. */
public final class SpecialListener implements Listener {

    private static final long DOUBLE_TAP_MS = 500;
    private static final long C4_JUMP_WINDOW_MS = 1500;

    private final WarKitRuntime plugin;
    private final Map<UUID, Long> lastSneak = new HashMap<>();
    private final Map<UUID, Integer> c4JumpCount = new HashMap<>();
    private final Map<UUID, Long> c4JumpAt = new HashMap<>();

    public SpecialListener(WarKitRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!e.isSneaking()) {
            // Releasing sneak resets the C4 combo.
            c4JumpCount.remove(p.getUniqueId());
            c4JumpAt.remove(p.getUniqueId());
            return;
        }
        if (!Weapons.SUICIDE_VEST.equals(plugin.items().id(p.getInventory().getChestplate()))) return;
        if (SpectatorBlock.deny(p)) return;
        long now = System.currentTimeMillis();
        Long last = lastSneak.get(p.getUniqueId());
        if (last != null && now - last <= DOUBLE_TAP_MS) {
            lastSneak.remove(p.getUniqueId());
            plugin.explosives().tryArmSuicide(p);
        } else {
            lastSneak.put(p.getUniqueId(), now);
        }
    }

    /** Held sneak plus two consecutive jumps detonates all planted C4 charges. */
    @EventHandler
    public void onJump(PlayerJumpEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (!p.isSneaking()) {
            c4JumpCount.remove(id);
            return;
        }
        if (!plugin.explosives().hasCharge(id)) return;
        if (SpectatorBlock.deny(p)) return;
        long now = System.currentTimeMillis();
        Long lastAt = c4JumpAt.get(id);
        int count = (lastAt != null && now - lastAt <= C4_JUMP_WINDOW_MS) ? c4JumpCount.getOrDefault(id, 0) : 0;
        count++;
        c4JumpAt.put(id, now);
        if (count >= 2) {
            c4JumpCount.remove(id);
            c4JumpAt.remove(id);
            plugin.explosives().detonate(p);
        } else {
            c4JumpCount.put(id, count);
        }
    }

    /** Tripwire trigger on stepping onto it. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!plugin.explosives().hasTripwires()) return;
        if (!e.hasChangedBlock()) return;
        plugin.explosives().checkMove(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        lastSneak.remove(id);
        c4JumpCount.remove(id);
        c4JumpAt.remove(id);
    }
}
