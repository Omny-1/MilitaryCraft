package me.bibo.militarycraft.gear.warkit.weapon;

import me.bibo.militarycraft.gear.warkit.Txt;
import me.bibo.militarycraft.gear.warkit.SpectatorBlock;
import me.bibo.militarycraft.gear.warkit.WarKitRuntime;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Main right-click weapon use router. */
public final class WeaponListener implements Listener {

    private final WarKitRuntime plugin;

    public WeaponListener(WarKitRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        boolean left = a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK;
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) return;

        Player p = e.getPlayer();

        // Mounted Maxim fires on any click with any held item.
        if (plugin.deployables().isManning(p)) {
            e.setCancelled(true);
            if (SpectatorBlock.deny(p)) {
                plugin.deployables().dismount(p);
                return;
            }
            plugin.deployables().manualFire(p);
            return;
        }

        ItemStack item = e.getItem();
        String id = plugin.items().id(item);
        if (id == null || !isWeapon(id)) return;

        // Suppress vanilla behavior of the backing item: crossbow load, hoe use, leash use.
        e.setCancelled(true);
        if (SpectatorBlock.deny(p)) return;

        // Left-click with firearms or the grenade launcher starts a manual reload.
        if (left) {
            if (Weapons.GUNS.contains(id) || id.equals(Weapons.GRENADE_LAUNCHER)) {
                plugin.guns().reloadHeld(p);
            }
            return;
        }

        boolean onBlock = a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null;

        if (Weapons.GUNS.contains(id)) {
            plugin.guns().handleGun(p, item, id);
        } else if (id.equals(Weapons.GRENADE_LAUNCHER)) {
            plugin.grenades().fireLauncher(p, item);
        } else if (id.equals(Weapons.PATRIOT)) {
            plugin.grenades().firePatriot(p, item);
        } else if (Weapons.THROWABLES.contains(id)) {
            plugin.grenades().throwGrenade(p, item, id);
        } else if (id.equals(Weapons.FLAMETHROWER)) {
            plugin.spray().handleFlame(p, item);
        } else if (id.equals(Weapons.CHEMICAL)) {
            plugin.spray().handleChemical(p, item);
        } else if (id.equals(Weapons.MAXIM)) {
            if (onBlock) {
                deploy(p, e.getClickedBlock(), true);
            } else {
                p.sendActionBar(Txt.t("Aim at the ground to deploy", NamedTextColor.YELLOW));
            }
        } else if (id.equals(Weapons.BARBED_WIRE)) {
            if (onBlock) {
                deploy(p, e.getClickedBlock(), false);
            } else {
                p.sendActionBar(Txt.t("Aim at the ground to deploy", NamedTextColor.YELLOW));
            }
        } else if (id.equals(Weapons.TRENCH_SHOVEL)) {
            plugin.trench().startDig(p, item);
        } else if (id.equals(Weapons.MOLOTOV)) {
            plugin.grenades().throwMolotov(p, item);
        } else if (id.equals(Weapons.SLEEP_GAS)) {
            plugin.grenades().throwGrenade(p, item, Weapons.SLEEP_GAS);
        } else if (id.equals(Weapons.TRIPWIRE_TRAP)) {
            if (onBlock && plugin.explosives().placeTripwire(p, e.getClickedBlock(), e.getBlockFace())) {
                consumeOne(p, item);
            } else if (!onBlock) {
                p.sendActionBar(Txt.t("Aim at a passage to string the tripwire", NamedTextColor.YELLOW));
            }
        } else if (id.equals(Weapons.C4)) {
            if (onBlock && plugin.explosives().placeC4(p, e.getClickedBlock(), e.getBlockFace())) {
                consumeOne(p, item);
            } else if (!onBlock) {
                p.sendActionBar(Txt.t("Aim at a surface to attach C4", NamedTextColor.YELLOW));
            }
        } else if (id.equals(Weapons.FIRING_WALL)) {
            if (onBlock && plugin.explosives().buildFiringWall(p)) {
                consumeOne(p, item);
            } else if (!onBlock) {
                p.sendActionBar(Txt.t("Aim at the ground to place the wall", NamedTextColor.YELLOW));
            }
        } else if (id.equals(Weapons.GRAPPLING_HOOK)) {
            plugin.gadgets().grapplingHook(p, item);
        } else if (id.equals(Weapons.JUMP_JET)) {
            plugin.gadgets().jumpJet(p, item);
        } else if (id.equals(Weapons.COMBAT_STIM)) {
            plugin.gadgets().combatStim(p, item);
        } else if (id.equals(Weapons.RECON_SCANNER)) {
            plugin.gadgets().reconScan(p, item);
        } else if (id.equals(Weapons.PROXIMITY_MINE)) {
            if (onBlock && plugin.explosives().placeProximityMine(p, e.getClickedBlock(), e.getBlockFace())) {
                consumeOne(p, item);
            } else if (!onBlock) {
                p.sendActionBar(Txt.t("Aim at the ground to place the mine", NamedTextColor.YELLOW));
            }
        } else if (id.equals(Weapons.SENTRY_GUN)) {
            if (onBlock) {
                var at = e.getClickedBlock().getLocation().add(0.5, 1.0, 0.5);
                if (plugin.sentries().deploy(p, at)) consumeOne(p, item);
            } else {
                p.sendActionBar(Txt.t("Aim at the ground to deploy the turret", NamedTextColor.YELLOW));
            }
        }
    }

    private void consumeOne(Player p, ItemStack item) {
        item.setAmount(item.getAmount() - 1);
        p.getInventory().setItemInMainHand(item);
    }

    private void deploy(Player p, Block clicked, boolean maxim) {
        var at = clicked.getLocation().add(0.5, 1.0, 0.5);
        ItemStack hand = p.getInventory().getItemInMainHand();
        boolean ok = maxim ? plugin.deployables().deployMaxim(p, at)
                : plugin.deployables().deployBarbed(p, at);
        if (ok) {
            hand.setAmount(hand.getAmount() - 1);
            p.getInventory().setItemInMainHand(hand);
        }
    }

    /** Slot changes interrupt reloads. */
    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        plugin.guns().cancelReload(e.getPlayer());
    }

    /** F key triggers manual weapon reload instead of an off-hand swap. */
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        String id = plugin.items().id(p.getInventory().getItemInMainHand());
        if (id != null && SpectatorBlock.deny(p)) {
            e.setCancelled(true);
            return;
        }
        if (id != null && (Weapons.GUNS.contains(id) || id.equals(Weapons.GRENADE_LAUNCHER))) {
            e.setCancelled(true);
            plugin.guns().reloadHeld(p);
        }
    }

    /** The grappling hook must not cast the vanilla fishing bobber. */
    @EventHandler(ignoreCancelled = true)
    public void onFish(org.bukkit.event.player.PlayerFishEvent e) {
        if (Weapons.GRAPPLING_HOOK.equals(plugin.items().id(e.getPlayer().getInventory().getItemInMainHand()))) {
            e.setCancelled(true);
            SpectatorBlock.deny(e.getPlayer());
        }
    }

    private boolean isWeapon(String id) {
        return Weapons.GUNS.contains(id) || Weapons.LAUNCHERS.contains(id)
                || Weapons.THROWABLES.contains(id) || Weapons.SPRAYERS.contains(id)
                || Weapons.DEPLOYABLES.contains(id) || Weapons.SPECIAL.contains(id)
                || Weapons.EXTRA.contains(id);
    }
}
