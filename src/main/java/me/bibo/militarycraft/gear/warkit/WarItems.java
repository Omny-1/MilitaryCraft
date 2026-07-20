package me.bibo.militarycraft.gear.warkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ColorableArmorMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Factory and identification for every WarKit item (by PDC key). */
public final class WarItems {

    public static final String MEDKIT = "medkit";
    public static final String PAINKILLER = "painkiller";
    public static final String VEST = "vest";
    public static final String KEVLAR_HELMET = "kevlar_helmet";
    public static final String EXOSUIT = "exosuit";
    public static final String GAS_MASK = "gas_mask";
    public static final String PADS = "pads";
    public static final String CAMO_CLOAK = "camo_cloak";
    public static final String VISOR_HELMET = "visor_helmet";
    public static final String MARKER = "marker";
    public static final String RATION = "ration";
    public static final String REPAIR_KIT = "repair_kit";

    private final WarKitRuntime plugin;
    private final NamespacedKey idKey;
    private final Map<String, Supplier<ItemStack>> factories = new LinkedHashMap<>();

    public WarItems(WarKitRuntime plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey("warkit", "item_id");
        factories.put(MEDKIT, this::medkit);
        factories.put(PAINKILLER, this::painkiller);
        factories.put(VEST, this::vest);
        factories.put(KEVLAR_HELMET, this::kevlarHelmet);
        factories.put(EXOSUIT, this::exosuit);
        factories.put(GAS_MASK, this::gasMask);
        factories.put(PADS, this::pads);
        factories.put(CAMO_CLOAK, this::camoCloak);
        factories.put(VISOR_HELMET, this::visorHelmet);
        factories.put(MARKER, this::marker);
        factories.put(RATION, this::ration);
        factories.put(REPAIR_KIT, this::repairKit);
    }

    public Set<String> ids() {
        return factories.keySet();
    }

    /** Registers an external factory (weapons) for /warkit give, list and shared protections. */
    public void register(String id, Supplier<ItemStack> factory) {
        factories.put(id, factory);
    }

    /** Writes the shared WarKit id tag into an external item. */
    public void writeId(ItemMeta m, String id) {
        m.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
    }

    public ItemStack create(String id) {
        Supplier<ItemStack> f = factories.get(id);
        return f == null ? null : f.get();
    }

    /** WarKit item id, or null. Does not clone item meta (PDC view). */
    public String id(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        return item.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public boolean isWearingHelmet(Player p, String wantedId) {
        return wantedId.equals(id(p.getInventory().getHelmet()));
    }

    private Settings s() {
        return plugin.settings();
    }

    // ---------- items ----------

    private ItemStack medkit() {
        ItemStack it = ItemStack.of(Material.GLISTERING_MELON_SLICE);
        it.editMeta(m -> {
            name(m, "✚ Medkit", NamedTextColor.RED);
            lore(m,
                    "Right-click - bandage (" + fmt(s().medkitChannelSeconds) + " sec).",
                    "Fully restores health, extinguishes fire",
                    "and removes negative effects.",
                    "Combat damage interrupts bandaging.",
                    "Cooldown: " + s().medkitCooldownSeconds + " sec.");
            m.setMaxStackSize(16);
            tag(m, MEDKIT);
            cooldownGroup(m, MEDKIT, s().medkitCooldownSeconds);
        });
        return it;
    }

    private ItemStack painkiller() {
        ItemStack it = ItemStack.of(Material.BONE_MEAL);
        it.editMeta(m -> {
            name(m, "● Painkiller", NamedTextColor.WHITE);
            int pct = (int) Math.round(s().painkillerReduction * 100);
            lore(m,
                    "Right-click - take the pills.",
                    "-" + pct + "% incoming damage for " + s().painkillerDurationSeconds + " sec.",
                    "Cooldown: " + s().painkillerCooldownSeconds + " sec.");
            m.setMaxStackSize(16);
            tag(m, PAINKILLER);
            cooldownGroup(m, PAINKILLER, s().painkillerCooldownSeconds);
        });
        return it;
    }

    private ItemStack vest() {
        ItemStack it = ItemStack.of(Material.LEATHER_CHESTPLATE);
        it.editMeta(ColorableArmorMeta.class, m -> m.setColor(Color.fromRGB(0x4B5320)));
        it.editMeta(m -> {
            name(m, "⛨ Ballistic Vest", NamedTextColor.DARK_GREEN);
            lore(m,
                    "Heavy torso protection.",
                    "+" + fmt(s().vestArmor) + " armor, +" + fmt(s().vestToughness) + " armor toughness.",
                    "Reduces knockback, but -" + (int) Math.round(s().vestSpeedPenalty * 100) + "% speed.");
            attr(m, Attribute.ARMOR, "vest_armor", s().vestArmor,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST);
            attr(m, Attribute.ARMOR_TOUGHNESS, "vest_toughness", s().vestToughness,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST);
            attr(m, Attribute.KNOCKBACK_RESISTANCE, "vest_kb", s().vestKbRes,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST);
            attr(m, Attribute.MOVEMENT_SPEED, "vest_speed", -s().vestSpeedPenalty,
                    AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.CHEST);
            ((Damageable) m).setMaxDamage(s().vestMaxDurability);
            tag(m, VEST);
        });
        return it;
    }

    private ItemStack kevlarHelmet() {
        ItemStack it = ItemStack.of(Material.LEATHER_HELMET);
        it.editMeta(ColorableArmorMeta.class, m -> m.setColor(Color.fromRGB(0x556B2F)));
        it.editMeta(m -> {
            name(m, "⛨ Kevlar Helmet", NamedTextColor.DARK_GREEN);
            lore(m,
                    "Infantry standard.",
                    "+" + fmt(s().kevlarArmor) + " armor, +" + fmt(s().kevlarToughness) + " armor toughness.");
            attr(m, Attribute.ARMOR, "kevlar_armor", s().kevlarArmor,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD);
            attr(m, Attribute.ARMOR_TOUGHNESS, "kevlar_toughness", s().kevlarToughness,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD);
            ((Damageable) m).setMaxDamage(s().kevlarMaxDurability);
            tag(m, KEVLAR_HELMET);
        });
        return it;
    }

    private ItemStack exosuit() {
        ItemStack it = ItemStack.of(Material.NETHERITE_LEGGINGS);
        it.editMeta(m -> {
            name(m, "⚙ Exosuit", NamedTextColor.AQUA);
            lore(m,
                    "Powered lower-body frame.",
                    "+" + fmt(s().exoAttackBonus) + " melee damage",
                    "(fist, sword, axe - anything).",
                    "+" + (int) Math.round(s().exoSpeedBonus * 100) + "% walking speed.",
                    "+" + (int) Math.round(s().exoJumpBonus * 100) + "% jump height (~2 blocks).",
                    "Equips in the leggings slot.");
            m.setEnchantmentGlintOverride(true);
            attr(m, Attribute.ARMOR, "exo_armor", s().exoArmor,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.LEGS);
            attr(m, Attribute.ARMOR_TOUGHNESS, "exo_toughness", s().exoToughness,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.LEGS);
            attr(m, Attribute.ATTACK_DAMAGE, "exo_attack", s().exoAttackBonus,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.LEGS);
            attr(m, Attribute.MOVEMENT_SPEED, "exo_speed", s().exoSpeedBonus,
                    AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.LEGS);
            attr(m, Attribute.JUMP_STRENGTH, "exo_jump", s().exoJumpBonus,
                    AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.LEGS);
            tag(m, EXOSUIT);
        });
        return it;
    }

    private ItemStack gasMask() {
        ItemStack it = ItemStack.of(Material.LEATHER_HELMET);
        it.editMeta(ColorableArmorMeta.class, m -> {
            m.setColor(Color.fromRGB(0x3B4045));
            m.setTrim(new ArmorTrim(TrimMaterial.COPPER, TrimPattern.SILENCE));
        });
        it.editMeta(m -> {
            name(m, "☣ Gas Mask", NamedTextColor.GREEN);
            lore(m,
                    "Filters external harmful effects:",
                    "splash/lingering potions, tipped arrows,",
                    "and effects from mob or player attacks.",
                    "Does not protect from potions you drink yourself.",
                    "Light head protection (+" + fmt(s().maskArmor) + " armor).");
            attr(m, Attribute.ARMOR, "mask_armor", s().maskArmor,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD);
            tag(m, GAS_MASK);
        });
        return it;
    }

    private ItemStack pads() {
        ItemStack it = ItemStack.of(Material.LEATHER_BOOTS);
        it.editMeta(ColorableArmorMeta.class, m -> m.setColor(Color.fromRGB(0x2F2F2F)));
        it.editMeta(m -> {
            name(m, "⛨ Knee and Elbow Pads", NamedTextColor.GRAY);
            lore(m,
                    "-" + (int) Math.round(s().padsFallReduction * 100) + "% fall damage.",
                    "Safe fall distance increased by "
                            + fmt(s().padsSafeFallBonus) + " blocks.",
                    "+" + fmt(s().padsArmor) + " armor.");
            attr(m, Attribute.ARMOR, "pads_armor", s().padsArmor,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET);
            attr(m, Attribute.FALL_DAMAGE_MULTIPLIER, "pads_fall", -s().padsFallReduction,
                    AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.FEET);
            attr(m, Attribute.SAFE_FALL_DISTANCE, "pads_safe", s().padsSafeFallBonus,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET);
            tag(m, PADS);
        });
        return it;
    }

    private ItemStack camoCloak() {
        ItemStack it = ItemStack.of(Material.GRASS_BLOCK);
        it.editMeta(m -> {
            name(m, "▦ Camouflage Cloak", NamedTextColor.GOLD);
            lore(m,
                    "Right-click - disguise as a grass block.",
                    "Single-use: consumed when activated.",
                    "Movement, damage or attacking breaks disguise.",
                    "Cooldown: " + s().camoCooldownSeconds + " sec.");
            m.setMaxStackSize(16);
            m.setEnchantmentGlintOverride(true);
            tag(m, CAMO_CLOAK);
            cooldownGroup(m, CAMO_CLOAK, s().camoCooldownSeconds);
        });
        return it;
    }

    private ItemStack visorHelmet() {
        ItemStack it = ItemStack.of(Material.LEATHER_HELMET);
        it.editMeta(ColorableArmorMeta.class, m -> {
            m.setColor(Color.fromRGB(0x1C1C1C));
            m.setTrim(new ArmorTrim(TrimMaterial.QUARTZ, TrimPattern.EYE));
        });
        it.editMeta(m -> {
            name(m, "◉ Assault Visor Helmet", NamedTextColor.YELLOW);
            lore(m,
                    "Night vision device: see in the dark",
                    "while the helmet is equipped.",
                    "Weaker protection than the kevlar helmet.");
            attr(m, Attribute.ARMOR, "visor_armor", 2.0,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD);
            attr(m, Attribute.ARMOR_TOUGHNESS, "visor_toughness", 1.0,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD);
            ((Damageable) m).setMaxDamage(250);
            tag(m, VISOR_HELMET);
        });
        return it;
    }

    private ItemStack marker() {
        ItemStack it = ItemStack.of(Material.RED_DYE);
        it.editMeta(m -> {
            name(m, "◎ Target Marker", NamedTextColor.RED);
            lore(m,
                    "Hit an enemy with this marker",
                    "to mark them for " + Txt.mmss(s().markerDurationSeconds) + ".",
                    "You see a beacon over the target from any distance.",
                    "Single-use. The target is notified.");
            m.setMaxStackSize(16);
            tag(m, MARKER);
        });
        return it;
    }

    private ItemStack ration() {
        ItemStack it = ItemStack.of(Material.BARREL);
        it.editMeta(m -> {
            name(m, "▤ Ration Pack", NamedTextColor.GOLD);
            lore(m,
                    "Right-click - unpack a 3-part ration:",
                    "army soup, Bochkarova bites x2, golden apples x2.");
            m.setMaxStackSize(16);
            tag(m, RATION);
            cooldownGroup(m, RATION, Math.max(1, s().rationCooldownSeconds));
        });
        return it;
    }

    private ItemStack repairKit() {
        ItemStack it = ItemStack.of(Material.ANVIL);
        it.editMeta(m -> {
            name(m, "⚒ Repair Kit", NamedTextColor.GRAY);
            lore(m,
                    "Right-click - field repair (" + fmt(s().repairChannelSeconds) + " sec).",
                    "Repairs worn armor, offhand and hotbar items,",
                    "or repairs your current vehicle if you are inside one.",
                    "Combat damage interrupts repair.",
                    "Cooldown: " + s().repairCooldownSeconds + " sec.");
            m.setMaxStackSize(16);
            tag(m, REPAIR_KIT);
            cooldownGroup(m, REPAIR_KIT, s().repairCooldownSeconds);
        });
        return it;
    }

    // ---------- helpers ----------

    private void tag(ItemMeta m, String id) {
        m.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        me.bibo.militarycraft.core.item.ItemModels.apply(m, id);
    }

    /** Per-item cooldown group: native recharge animation. */
    private void cooldownGroup(ItemMeta m, String id, double seconds) {
        if (seconds <= 0) return;
        UseCooldownComponent cd = m.getUseCooldown();
        cd.setCooldownSeconds((float) seconds);
        cd.setCooldownGroup(new NamespacedKey("warkit", "cd_" + id));
        m.setUseCooldown(cd);
    }

    private void attr(ItemMeta m, Attribute attribute, String keyName, double amount,
                      AttributeModifier.Operation op, EquipmentSlotGroup slot) {
        m.addAttributeModifier(attribute,
                new AttributeModifier(new NamespacedKey("warkit", keyName), amount, op, slot));
    }

    private void name(ItemMeta m, String text, NamedTextColor color) {
        m.displayName(Txt.t(text, color));
    }

    private void lore(ItemMeta m, String... lines) {
        List<Component> lore = new ArrayList<>(lines.length);
        for (String line : lines) lore.add(Txt.gray(line));
        m.lore(lore);
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
