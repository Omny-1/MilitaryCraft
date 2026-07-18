package me.bibo.militarycraft.gear.warkit.listener;

import me.bibo.militarycraft.core.vehicle.VehicleHandle;
import me.bibo.militarycraft.gear.warkit.Settings;
import me.bibo.militarycraft.gear.warkit.SpectatorBlock;
import me.bibo.militarycraft.gear.warkit.Txt;
import me.bibo.militarycraft.gear.warkit.WarItems;
import me.bibo.militarycraft.gear.warkit.WarKitRuntime;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.Set;

/** Right-click item activation plus safeguards against placing/crafting/brewing custom items. */
public final class UseListener implements Listener {

    private static final Set<String> USABLE = Set.of(
            WarItems.MEDKIT, WarItems.PAINKILLER, WarItems.CAMO_CLOAK,
            WarItems.RATION, WarItems.REPAIR_KIT, WarItems.MARKER);

    /** Interactive blocks: do not intercept right-clicks on them unless the player is sneaking. */
    private static final Set<Material> INTERACTABLE = Set.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST, Material.BARREL,
            Material.CRAFTING_TABLE, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.BREWING_STAND, Material.ENCHANTING_TABLE, Material.ANVIL,
            Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL, Material.SMITHING_TABLE,
            Material.GRINDSTONE, Material.STONECUTTER, Material.CARTOGRAPHY_TABLE,
            Material.LOOM, Material.LECTERN, Material.BEACON, Material.HOPPER,
            Material.DISPENSER, Material.DROPPER, Material.LEVER, Material.COMPOSTER,
            Material.BELL, Material.RESPAWN_ANCHOR);

    private final WarKitRuntime plugin;

    public UseListener(WarKitRuntime plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        String id = plugin.items().id(item);
        if (id == null) return;

        Player p = e.getPlayer();
        if (SpectatorBlock.deny(p)) {
            e.setCancelled(true);
            return;
        }
        if (!USABLE.contains(id)) return;

        // Let chests, doors, and similar blocks open when the player is not sneaking.
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && !p.isSneaking()
                && e.getClickedBlock() != null && isInteractable(e.getClickedBlock())) {
            return;
        }
        e.setCancelled(true); // Prevent custom items from placing blocks or triggering vanilla use.
        if (e.getHand() != EquipmentSlot.HAND) return;

        switch (id) {
            case WarItems.MEDKIT -> useMedkit(p, item);
            case WarItems.PAINKILLER -> usePainkiller(p, item);
            case WarItems.RATION -> useRation(p, item);
            case WarItems.REPAIR_KIT -> useRepairKit(p, item);
            case WarItems.CAMO_CLOAK -> useCamo(p, item);
            case WarItems.MARKER ->
                    p.sendActionBar(Txt.t("Hit an enemy with the marker to tag them", NamedTextColor.YELLOW));
        }
    }

    private boolean isInteractable(org.bukkit.block.Block block) {
        Material type = block.getType();
        return INTERACTABLE.contains(type)
                || Tag.DOORS.isTagged(type) || Tag.TRAPDOORS.isTagged(type)
                || Tag.BUTTONS.isTagged(type) || Tag.FENCE_GATES.isTagged(type)
                || Tag.SHULKER_BOXES.isTagged(type) || Tag.BEDS.isTagged(type);
    }

    // ---------- Medkit ----------

    private void useMedkit(Player p, ItemStack item) {
        if (plugin.channels().isChanneling(p)) return;
        if (onCooldown(p, item)) return;
        Settings s = plugin.settings();

        AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? 20.0 : maxHealth.getValue();
        boolean wounded = p.getHealth() < max - 0.01 || p.getFireTicks() > 0 || hasHarmfulEffect(p);
        if (!wounded) {
            p.sendActionBar(Txt.t("You are not wounded", NamedTextColor.YELLOW));
            return;
        }

        int channelTicks = (int) Math.round(s.medkitChannelSeconds * 20) + 10;
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, channelTicks, 1,
                true, false, true));
        plugin.channels().start(p, WarItems.MEDKIT, s.medkitChannelSeconds, "Bandaging",
                Sound.ITEM_ARMOR_EQUIP_LEATHER, s.medkitCooldownSeconds * 20,
                pl -> pl.removePotionEffect(PotionEffectType.SLOWNESS),
                pl -> {
                    pl.removePotionEffect(PotionEffectType.SLOWNESS);
                    AttributeInstance mh = pl.getAttribute(Attribute.MAX_HEALTH);
                    pl.setHealth(mh == null ? 20.0 : mh.getValue());
                    pl.setFireTicks(0);
                    for (PotionEffectType type : EffectListener.HARMFUL) {
                        pl.removePotionEffect(type);
                    }
                    pl.playSound(pl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
                    pl.getWorld().spawnParticle(Particle.HEART,
                            pl.getLocation().add(0, 1.2, 0), 8, 0.4, 0.5, 0.4);
                    pl.sendActionBar(Txt.t("Health restored", NamedTextColor.GREEN));
                });
    }

    private boolean hasHarmfulEffect(Player p) {
        for (PotionEffectType type : EffectListener.HARMFUL) {
            if (p.hasPotionEffect(type)) return true;
        }
        return false;
    }

    // ---------- Painkiller ----------

    private void usePainkiller(Player p, ItemStack item) {
        if (onCooldown(p, item)) return;
        p.setCooldown(item, plugin.settings().painkillerCooldownSeconds * 20);
        item.setAmount(item.getAmount() - 1);
        plugin.painkiller().apply(p);
    }

    // ---------- Ration pack ----------

    private void useRation(Player p, ItemStack item) {
        if (onCooldown(p, item)) return;
        p.setCooldown(item, Math.max(1, plugin.settings().rationCooldownSeconds) * 20);
        item.setAmount(item.getAmount() - 1);

        give(p, named(Material.RABBIT_STEW, 1, "Army Stew"));
        give(p, named(Material.COOKED_RABBIT, 2, "Field Rations"));
        give(p, named(Material.GOLDEN_APPLE, 2, "Golden Apple"));

        p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 1f, 0.9f);
        p.getWorld().spawnParticle(Particle.ITEM, p.getEyeLocation(), 10,
                0.3, 0.3, 0.3, 0.08, ItemStack.of(Material.COOKED_RABBIT));
        p.sendActionBar(Txt.t("Ration unpacked: stew, field rations x2, golden apple x2",
                NamedTextColor.GOLD));
    }

    private ItemStack named(Material material, int amount, String name) {
        ItemStack it = ItemStack.of(material, amount);
        it.editMeta(m -> m.displayName(Txt.t(name, NamedTextColor.GOLD)));
        return it;
    }

    private void give(Player p, ItemStack item) {
        p.getInventory().addItem(item).values()
                .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
    }

    // ---------- Repair kit ----------

    private void useRepairKit(Player p, ItemStack item) {
        if (plugin.channels().isChanneling(p)) return;
        if (onCooldown(p, item)) return;
        if (countDamaged(p) == 0) {
            p.sendActionBar(Txt.t("Nothing to repair", NamedTextColor.YELLOW));
            return;
        }
        Settings s = plugin.settings();
        plugin.channels().start(p, WarItems.REPAIR_KIT, s.repairChannelSeconds, "Repairing",
                Sound.BLOCK_ANVIL_USE, s.repairCooldownSeconds * 20, null,
                pl -> {
                    RepairResult repaired = repairAll(pl);
                    pl.playSound(pl.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.1f);
                    pl.getWorld().spawnParticle(Particle.WAX_ON,
                            pl.getLocation().add(0, 1.0, 0), 20, 0.4, 0.6, 0.4);
                    pl.sendActionBar(Txt.t("Repair complete: " + repaired, NamedTextColor.GREEN));
                });
    }

    private static final EquipmentSlot[] REPAIR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.OFF_HAND
    };

    private int countDamaged(Player p) {
        int count = 0;
        PlayerInventory inv = p.getInventory();
        for (EquipmentSlot slot : REPAIR_SLOTS) {
            if (isDamaged(inv.getItem(slot))) count++;
        }
        for (int i = 0; i < 9; i++) {
            if (isDamaged(inv.getItem(i))) count++;
        }
        if (vehicleToRepair(p) != null) count++;
        return count;
    }

    private RepairResult repairAll(Player p) {
        int repaired = 0;
        PlayerInventory inv = p.getInventory();
        for (EquipmentSlot slot : REPAIR_SLOTS) {
            ItemStack it = inv.getItem(slot);
            if (isDamaged(it)) {
                it.editMeta(Damageable.class, dm -> dm.setDamage(0));
                inv.setItem(slot, it);
                repaired++;
            }
        }
        for (int i = 0; i < 9; i++) {
            ItemStack it = inv.getItem(i);
            if (isDamaged(it)) {
                it.editMeta(Damageable.class, dm -> dm.setDamage(0));
                inv.setItem(i, it);
                repaired++;
            }
        }
        return new RepairResult(repaired, repairVehicle(p));
    }

    private double repairVehicle(Player p) {
        VehicleHandle vehicle = vehicleToRepair(p);
        if (vehicle == null) {
            return 0.0;
        }
        Settings settings = plugin.settings();
        double amount = Math.max(settings.repairVehicleMinHealth,
                vehicle.maxHealth() * settings.repairVehicleHealthFraction);
        return plugin.core().combat().repair(vehicle, amount);
    }

    private VehicleHandle vehicleToRepair(Player p) {
        VehicleHandle vehicle = plugin.core().vehicles().riddenBy(p);
        if (vehicle == null || !vehicle.isActive()) {
            return null;
        }
        double max = vehicle.maxHealth();
        return Double.isFinite(max) && max > 0.0 && vehicle.health() < max - 0.01 ? vehicle : null;
    }

    private record RepairResult(int items, double vehicleHealth) {
        @Override
        public String toString() {
            if (vehicleHealth <= 0.0) {
                return "Repaired items: " + items;
            }
            return "Repaired items: " + items + ", vehicle: +"
                    + String.format(Locale.ROOT, "%.1f", vehicleHealth) + " HP";
        }
    }

    private boolean isDamaged(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        if (WarItems.REPAIR_KIT.equals(plugin.items().id(it))) return false;
        return it.getItemMeta() instanceof Damageable dm && dm.hasDamage() && dm.getDamage() > 0;
    }

    // ---------- Camouflage cloak ----------

    @SuppressWarnings("deprecation") // Preserve the original server-side grounded check for camouflage.
    private void useCamo(Player p, ItemStack item) {
        if (plugin.camo().isDisguised(p)) {
            p.sendActionBar(Txt.t("You are already camouflaged - moving will remove it",
                    NamedTextColor.YELLOW));
            return;
        }
        if (plugin.channels().isChanneling(p)) return;
        if (onCooldown(p, item)) return;
        if (p.isInsideVehicle()) {
            p.sendActionBar(Txt.t("You cannot camouflage inside a vehicle", NamedTextColor.YELLOW));
            return;
        }
        if (!p.isOnGround()) {
            p.sendActionBar(Txt.t("You must be standing on the ground", NamedTextColor.YELLOW));
            return;
        }
        plugin.camo().activate(p);
        item.setAmount(item.getAmount() - 1); // single-use
    }

    // ---------- common ----------

    private boolean onCooldown(Player p, ItemStack item) {
        if (!p.hasCooldown(item)) return false;
        int seconds = (p.getCooldown(item) + 19) / 20;
        p.sendActionBar(Txt.t("Cooldown: " + seconds + " sec", NamedTextColor.YELLOW));
        return true;
    }

    // ---------- custom item safeguards ----------

    /** Safety net: no WarKit item can be placed as a block. */
    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (plugin.items().id(e.getItemInHand()) != null) e.setCancelled(true);
    }

    /** Prevent markers dyeing sheep, medkits feeding entities, and similar vanilla interactions. */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        guardEntityInteract(e.getPlayer(), e.getHand(), e);
    }

    @EventHandler
    public void onInteractAtEntity(PlayerInteractAtEntityEvent e) {
        guardEntityInteract(e.getPlayer(), e.getHand(), e);
    }

    private void guardEntityInteract(Player p, EquipmentSlot hand,
                                     org.bukkit.event.Cancellable e) {
        ItemStack item = hand == EquipmentSlot.HAND
                ? p.getInventory().getItemInMainHand()
                : p.getInventory().getItemInOffHand();
        String id = plugin.items().id(item);
        if (id != null) {
            e.setCancelled(true);
            SpectatorBlock.deny(p);
        }
    }

    /** Custom items cannot participate in crafting recipes. */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (plugin.items().id(it) != null) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    /** Medkits and painkillers cannot be used as brewing ingredients. */
    @EventHandler(ignoreCancelled = true)
    public void onBrewSlot(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory() instanceof BrewerInventory)) return;
        boolean clickedTop = e.getClickedInventory() instanceof BrewerInventory;
        if (clickedTop && plugin.items().id(e.getCursor()) != null) {
            e.setCancelled(true);
            return;
        }
        if (clickedTop && e.getClick() == ClickType.NUMBER_KEY
                && plugin.items().id(e.getWhoClicked().getInventory().getItem(e.getHotbarButton())) != null) {
            e.setCancelled(true);
            return;
        }
        if (!clickedTop && e.isShiftClick() && plugin.items().id(e.getCurrentItem()) != null) {
            e.setCancelled(true);
        }
    }

    /** Same guard for dragging items into brewing stand slots. */
    @EventHandler(ignoreCancelled = true)
    public void onBrewDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory() instanceof BrewerInventory)) return;
        if (plugin.items().id(e.getOldCursor()) == null) return;
        int topSize = e.getView().getTopInventory().getSize();
        for (int rawSlot : e.getRawSlots()) {
            if (rawSlot < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    /** If a hopper inserts an item anyway, brewing still will not finish. */
    @EventHandler(ignoreCancelled = true)
    public void onBrew(BrewEvent e) {
        if (plugin.items().id(e.getContents().getIngredient()) != null) {
            e.setCancelled(true);
        }
    }

    /** The ration barrel cannot be used as furnace fuel. */
    @EventHandler(ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent e) {
        if (plugin.items().id(e.getFuel()) != null) {
            e.setCancelled(true);
        }
    }

    /** Grindstones strip custom attributes, so WarKit items cannot be inserted there. */
    @EventHandler
    public void onGrindstone(PrepareGrindstoneEvent e) {
        for (ItemStack it : e.getInventory().getContents()) {
            if (plugin.items().id(it) != null) {
                e.setResult(null);
                return;
            }
        }
    }
}
