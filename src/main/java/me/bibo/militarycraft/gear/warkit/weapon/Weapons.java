package me.bibo.militarycraft.gear.warkit.weapon;

import me.bibo.militarycraft.gear.warkit.ItemTools;
import me.bibo.militarycraft.gear.warkit.WarKitRuntime;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;
import java.util.function.Supplier;

/** WarKit combat weapon factory plus identification and ammo handling (PDC). */
public final class Weapons {

    public static final String RIFLE = "rifle";
    public static final String PISTOL = "pistol";
    public static final String GRENADE_LAUNCHER = "grenade_launcher";
    public static final String PATRIOT = "patriot";
    public static final String FRAG_GRENADE = "frag_grenade";
    public static final String SMOKE_GRENADE = "smoke_grenade";
    public static final String FLASH_GRENADE = "flash_grenade";
    public static final String IMPULSE_GRENADE = "impulse_grenade";
    public static final String FLAMETHROWER = "flamethrower";
    public static final String CHEMICAL = "chemical_sprayer";
    public static final String MAXIM = "maxim";
    public static final String BARBED_WIRE = "barbed_wire";
    // --- engineering and sabotage kit ---
    public static final String TRENCH_SHOVEL = "trench_shovel";
    public static final String MOLOTOV = "molotov";
    public static final String SUICIDE_VEST = "suicide_vest";
    public static final String TRIPWIRE_TRAP = "tripwire_trap";
    public static final String C4 = "c4";
    public static final String SLEEP_GAS = "sleep_gas";
    public static final String FIRING_WALL = "firing_wall";
    // --- expanded kit: mobility, recon, traps, automatic fire ---
    public static final String GRAPPLING_HOOK = "grappling_hook";
    public static final String JUMP_JET = "jump_jet";
    public static final String COMBAT_STIM = "combat_stim";
    public static final String RECON_SCANNER = "recon_scanner";
    public static final String PROXIMITY_MINE = "proximity_mine";
    public static final String SENTRY_GUN = "sentry_gun";

    public static final Set<String> GUNS = Set.of(RIFLE, PISTOL);
    public static final Set<String> LAUNCHERS = Set.of(GRENADE_LAUNCHER, PATRIOT);
    public static final Set<String> THROWABLES =
            Set.of(FRAG_GRENADE, SMOKE_GRENADE, FLASH_GRENADE, IMPULSE_GRENADE);
    public static final Set<String> SPRAYERS = Set.of(FLAMETHROWER, CHEMICAL);
    public static final Set<String> DEPLOYABLES = Set.of(MAXIM, BARBED_WIRE);
    /** Items used by right-click through WeaponListener (except the wearable vest). */
    public static final Set<String> SPECIAL =
            Set.of(TRENCH_SHOVEL, MOLOTOV, TRIPWIRE_TRAP, C4, SLEEP_GAS, FIRING_WALL);
    /** Expanded kit used by right-click through WeaponListener. */
    public static final Set<String> EXTRA = Set.of(
            GRAPPLING_HOOK, JUMP_JET, COMBAT_STIM, RECON_SCANNER,
            PROXIMITY_MINE, SENTRY_GUN);

    private final WarKitRuntime plugin;
    private final NamespacedKey ammoKey;
    private final NamespacedKey lastUseKey;

    public Weapons(WarKitRuntime plugin) {
        this.plugin = plugin;
        this.ammoKey = new NamespacedKey("warkit", "ammo");
        this.lastUseKey = new NamespacedKey("warkit", "last_use");
    }

    /** Registers every weapon in the shared WarItems registry (give/list/protections). */
    public void registerAll() {
        plugin.items().register(RIFLE, this::rifle);
        plugin.items().register(PISTOL, this::pistol);
        plugin.items().register(GRENADE_LAUNCHER, this::grenadeLauncher);
        plugin.items().register(PATRIOT, this::patriot);
        plugin.items().register(FRAG_GRENADE, this::fragGrenade);
        plugin.items().register(SMOKE_GRENADE, this::smokeGrenade);
        plugin.items().register(FLASH_GRENADE, this::flashGrenade);
        plugin.items().register(IMPULSE_GRENADE, this::impulseGrenade);
        plugin.items().register(FLAMETHROWER, this::flamethrower);
        plugin.items().register(CHEMICAL, this::chemical);
        plugin.items().register(MAXIM, this::maxim);
        plugin.items().register(BARBED_WIRE, this::barbedWire);
        plugin.items().register(TRENCH_SHOVEL, this::trenchShovel);
        plugin.items().register(MOLOTOV, this::molotov);
        plugin.items().register(SUICIDE_VEST, this::suicideVest);
        plugin.items().register(TRIPWIRE_TRAP, this::tripwireTrap);
        plugin.items().register(C4, this::c4);
        plugin.items().register(SLEEP_GAS, this::sleepGas);
        plugin.items().register(FIRING_WALL, this::firingWall);
        plugin.items().register(GRAPPLING_HOOK, this::grapplingHook);
        plugin.items().register(JUMP_JET, this::jumpJet);
        plugin.items().register(COMBAT_STIM, this::combatStim);
        plugin.items().register(RECON_SCANNER, this::reconScanner);
        plugin.items().register(PROXIMITY_MINE, this::proximityMine);
        plugin.items().register(SENTRY_GUN, this::sentryGun);
    }

    private WeaponConfig w() {
        return plugin.weaponConfig();
    }

    // ------------------------------------------------------------------
    //  Ammo in PDC
    // ------------------------------------------------------------------

    /** Magazine/fuel capacity for display and reload logic. */
    public int capacity(String id) {
        WeaponConfig w = w();
        return switch (id) {
            case RIFLE -> w.rifleMag;
            case PISTOL -> w.pistolMag;
            case GRENADE_LAUNCHER -> w.glMag;
            case PATRIOT -> w.patriotMagazine;
            case FLAMETHROWER -> w.flameFuel;
            case CHEMICAL -> w.chemFuel;
            case GRAPPLING_HOOK -> w.hookCharges;
            case JUMP_JET -> w.jetFuel;
            default -> 0;
        };
    }

    public int getAmmo(ItemStack item) {
        if (item == null) return 0;
        ItemMeta m = item.getItemMeta();
        if (m == null) return 0;
        Integer a = m.getPersistentDataContainer().get(ammoKey, PersistentDataType.INTEGER);
        return a == null ? 0 : a;
    }

    public void setAmmo(ItemStack item, int ammo) {
        if (item == null) return;
        item.editMeta(m -> m.getPersistentDataContainer().set(ammoKey, PersistentDataType.INTEGER, ammo));
    }

    public boolean hasAmmoTag(ItemStack item) {
        if (item == null) return false;
        ItemMeta m = item.getItemMeta();
        return m != null && m.getPersistentDataContainer().has(ammoKey, PersistentDataType.INTEGER);
    }

    public long getLastUse(ItemStack item) {
        if (item == null) return 0;
        ItemMeta m = item.getItemMeta();
        if (m == null) return 0;
        Long v = m.getPersistentDataContainer().get(lastUseKey, PersistentDataType.LONG);
        return v == null ? 0 : v;
    }

    public void setLastUse(ItemStack item, long millis) {
        if (item == null) return;
        item.editMeta(m -> m.getPersistentDataContainer().set(lastUseKey, PersistentDataType.LONG, millis));
    }

    // ------------------------------------------------------------------
    //  Guns
    // ------------------------------------------------------------------

    private ItemStack rifle() {
        ItemStack it = ItemStack.of(Material.CROSSBOW);
        it.editMeta(m -> {
            ItemTools.name(m, "🔫 Emka / Assault Rifle", NamedTextColor.GOLD);
            ItemTools.loreAccent(m, "Basic assault weapon", NamedTextColor.YELLOW,
                    "Right-click - single shots in a burst rhythm.",
                    "Damage: " + ItemTools.hearts(w().rifleDamage) + " (x" + ItemTools.fmt(w().headshotMultiplier) + " headshot).",
                    "Range: " + (int) w().rifleRange + " blocks. Magazine: " + w().rifleMag + ".",
                    "Spread grows noticeably while sprinting or jumping.",
                    "Reload: left-click or F, auto when empty (" + ItemTools.fmt(w().rifleReloadSeconds) + " sec).");
            finishGun(m, RIFLE, w().rifleMag);
        });
        return it;
    }

    private ItemStack pistol() {
        ItemStack it = ItemStack.of(Material.WARPED_FUNGUS_ON_A_STICK);
        it.editMeta(m -> {
            ItemTools.name(m, "🔫 Pistol", NamedTextColor.GOLD);
            ItemTools.loreAccent(m, "Light close-to-mid range weapon", NamedTextColor.YELLOW,
                    "Right-click - shoot.",
                    "Damage: " + ItemTools.hearts(w().pistolDamage) + " (x" + ItemTools.fmt(w().headshotMultiplier) + " headshot).",
                    "Range: " + (int) w().pistolRange + " blocks. Magazine: " + w().pistolMag + ".",
                    "More accurate than the rifle, but sprinting and jumping still hurt accuracy.",
                    "Reload: left-click or F, auto when empty (" + ItemTools.fmt(w().pistolReloadSeconds) + " sec).");
            finishGun(m, PISTOL, w().pistolMag);
        });
        return it;
    }

    private ItemStack grenadeLauncher() {
        ItemStack it = ItemStack.of(Material.DIAMOND_HOE);
        it.editMeta(m -> {
            ItemTools.name(m, "💥 Grenade Launcher", NamedTextColor.RED);
            ItemTools.loreAccent(m, "Arcing area damage", NamedTextColor.YELLOW,
                    "Right-click - launches a grenade that bounces off walls",
                    "and explodes after " + ItemTools.fmt(w().glFuseSeconds) + " sec (not on impact).",
                    "Blast radius ~" + ItemTools.fmt(w().glExplosionPower * 1.5) + " blocks, does not break blocks.",
                    "Magazine: " + w().glMag + ". Reload left-click/F (" + ItemTools.fmt(w().glReloadSeconds) + " sec).");
            finishGun(m, GRENADE_LAUNCHER, w().glMag);
        });
        return it;
    }

    private ItemStack patriot() {
        ItemStack it = ItemStack.of(Material.NETHERITE_HOE);
        it.editMeta(m -> {
            ItemTools.name(m, "🚀 Patriot", NamedTextColor.RED);
            ItemTools.loreAccent(m, "Homing missile launcher", NamedTextColor.YELLOW,
                    "Aim at an enemy (<=" + (int) w().patriotLockRange + " blocks) and right-click.",
                    "The missile chases the target and explodes.",
                    w().patriotMagazine + " missiles total; cannot reload.",
                    "Cooldown between launches: " + w().patriotCooldownSeconds + " sec.");
            finishGun(m, PATRIOT, w().patriotMagazine);
        });
        return it;
    }

    // ------------------------------------------------------------------
    //  Grenades (consumables)
    // ------------------------------------------------------------------

    private ItemStack fragGrenade() {
        ItemStack it = ItemStack.of(Material.MAGMA_CREAM);
        it.editMeta(m -> {
            ItemTools.name(m, "💣 Fragmentation Grenade", NamedTextColor.RED);
            ItemTools.loreAccent(m, "High-explosive frag, right-click throw", NamedTextColor.YELLOW,
                    "Explodes after " + ItemTools.fmt(w().fragFuseSeconds) + " sec.",
                    "Up to " + ItemTools.hearts(w().fragDamage) + " at center, radius " + (int) w().fragRadius + " blocks.",
                    "Does not break blocks.");
            finishThrowable(m, FRAG_GRENADE);
        });
        return it;
    }

    private ItemStack smokeGrenade() {
        ItemStack it = ItemStack.of(Material.CLAY_BALL);
        it.editMeta(m -> {
            ItemTools.name(m, "🌫 Smoke Grenade", NamedTextColor.GRAY);
            ItemTools.loreAccent(m, "Smoke screen, right-click throw", NamedTextColor.YELLOW,
                    "Dense smoke with " + (int) w().smokeRadius + " block radius",
                    "for " + (int) w().smokeDurationSeconds + " sec. Deals no damage.",
                    "Blocks sight and blinds inside the cloud.");
            finishThrowable(m, SMOKE_GRENADE);
        });
        return it;
    }

    private ItemStack flashGrenade() {
        ItemStack it = ItemStack.of(Material.GLOWSTONE_DUST);
        it.editMeta(m -> {
            ItemTools.name(m, "✦ Flashbang", NamedTextColor.WHITE);
            ItemTools.loreAccent(m, "Flashbang, right-click throw", NamedTextColor.YELLOW,
                    "Blinds and disorients everyone within " + (int) w().flashRadius + " blocks",
                    "who is looking toward it for " + w().flashBlindSeconds + " sec.",
                    "Deals no damage.");
            finishThrowable(m, FLASH_GRENADE);
        });
        return it;
    }

    private ItemStack impulseGrenade() {
        ItemStack it = ItemStack.of(Material.POPPED_CHORUS_FRUIT);
        it.editMeta(m -> {
            ItemTools.name(m, "🌀 Impulse Grenade", NamedTextColor.AQUA);
            ItemTools.loreAccent(m, "Kinetic shove, right-click throw", NamedTextColor.YELLOW,
                    "Throws everyone within " + (int) w().impulseRadius + " blocks",
                    "far forward and upward. Deals no damage.",
                    "Launched players ignore fall damage for " + w().impulseNoFallSeconds + " sec.");
            finishThrowable(m, IMPULSE_GRENADE);
        });
        return it;
    }

    // ------------------------------------------------------------------
    //  Sprayers
    // ------------------------------------------------------------------

    private ItemStack flamethrower() {
        ItemStack it = ItemStack.of(Material.BLAZE_ROD);
        it.editMeta(m -> {
            ItemTools.name(m, "🔥 Flamethrower", NamedTextColor.GOLD);
            ItemTools.loreAccent(m, "Flame jet, right-click burst (press often)", NamedTextColor.YELLOW,
                    "Cone of fire up to " + (int) w().flameRange + " blocks, ignites enemies",
                    "and leaves real fire on ground and trees.",
                    "Fuel: " + w().flameFuel + " - consumable, does NOT regenerate.");
            finishFuelTool(m, FLAMETHROWER, w().flameFuel);
        });
        return it;
    }

    private ItemStack chemical() {
        ItemStack it = ItemStack.of(Material.FERMENTED_SPIDER_EYE);
        it.editMeta(m -> {
            ItemTools.name(m, "☣ Chemical Sprayer", NamedTextColor.DARK_GREEN);
            ItemTools.loreAccent(m, "Toxic aerosol, right-click cloud burst", NamedTextColor.YELLOW,
                    "Sprays a toxic cloud (poison/weakness).",
                    "Cloud lasts " + w().chemCloudSeconds + " sec, radius " + (int) w().chemRadius + " blocks.",
                    "Gas mask fully protects against it.",
                    "Reagent: " + w().chemFuel + " - consumable, does NOT regenerate.");
            finishFuelTool(m, CHEMICAL, w().chemFuel);
        });
        return it;
    }

    // ------------------------------------------------------------------
    //  Deployables
    // ------------------------------------------------------------------

    private ItemStack maxim() {
        ItemStack it = ItemStack.of(Material.GOLDEN_HOE);
        it.editMeta(m -> {
            ItemTools.name(m, "⚔ Maxim Machine Gun", NamedTextColor.GOLD);
            ItemTools.loreAccent(m, "Mounted machine gun, right-click ground to deploy", NamedTextColor.YELLOW,
                    "Right-click deployed gun to sit at it.",
                    "Seated: right-click toggles fire, Shift stands up.",
                    "Overheats after " + w().maximOverheatShots + " rounds.",
                    "Shift+right-click your gun to pick it back up.");
            finishSimple(m, MAXIM);
            m.setMaxStackSize(1);
        });
        return it;
    }

    private ItemStack barbedWire() {
        ItemStack it = ItemStack.of(Material.LEAD);
        it.editMeta(m -> {
            ItemTools.name(m, "✸ Barbed Wire", NamedTextColor.GRAY);
            ItemTools.loreAccent(m, "Obstacle, right-click ground to deploy", NamedTextColor.YELLOW,
                    "Places a wall of " + w().barbedSegments + " segments across your view.",
                    "Heavily slows and hurts enemies near the wire.",
                    "Ignores teammates. Lasts " + w().barbedLifeSeconds + " sec.",
                    "Limit: " + w().barbedMaxPerPlayer + " walls per player.");
            finishSimple(m, BARBED_WIRE);
        });
        return it;
    }

    // ------------------------------------------------------------------
    //  Engineering and sabotage kit
    // ------------------------------------------------------------------

    private ItemStack trenchShovel() {
        ItemStack it = ItemStack.of(Material.IRON_SHOVEL);
        it.editMeta(m -> {
            ItemTools.name(m, "⛏ Trench Shovel", NamedTextColor.GOLD);
            ItemTools.loreAccent(m, "Knows how to dig. Right-click - dig (" + ItemTools.fmt(w().trenchDigSeconds) + " sec)",
                    NamedTextColor.YELLOW,
                    "Look down - digs a 2x6 trench in the floor.",
                    "Look at a wall - digs a 2x6 trench into the wall.",
                    "Hold Shift while digging - 6x6 pit.",
                    "Only digs dirt/sand, not stone.",
                    "Movement interrupts digging.");
            m.setUnbreakable(true);
            finishTool(m, TRENCH_SHOVEL);
        });
        return it;
    }

    private ItemStack molotov() {
        ItemStack it = ItemStack.of(Material.DRAGON_BREATH);
        it.editMeta(m -> {
            ItemTools.name(m, "🔥 Molotov Cocktail", NamedTextColor.RED);
            ItemTools.loreAccent(m, "Incendiary bottle, right-click throw", NamedTextColor.YELLOW,
                    "Shatters on impact and floods",
                    "the area with fire (radius ~" + (int) w().molotovRadius + " blocks).",
                    "Ignites everyone in the zone.");
            finishThrowable(m, MOLOTOV);
        });
        return it;
    }

    private ItemStack suicideVest() {
        ItemStack it = ItemStack.of(Material.LEATHER_CHESTPLATE);
        it.editMeta(org.bukkit.inventory.meta.ColorableArmorMeta.class,
                m -> m.setColor(org.bukkit.Color.fromRGB(0x5A1A1A)));
        it.editMeta(m -> {
            ItemTools.name(m, "☠ Explosive Vest", NamedTextColor.DARK_RED);
            ItemTools.loreAccent(m, "Wearable chestplate explosive", NamedTextColor.YELLOW,
                    "Near an enemy, double-tap Shift",
                    "to trigger a huge blast that also damages structures.",
                    "Guaranteed to hit nearby targets. You die.");
            m.addAttributeModifier(org.bukkit.attribute.Attribute.ARMOR,
                    new org.bukkit.attribute.AttributeModifier(new NamespacedKey("warkit", "vest_armor_s"),
                            3.0, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                            org.bukkit.inventory.EquipmentSlotGroup.CHEST));
            m.setMaxStackSize(1);
            plugin.items().writeId(m, SUICIDE_VEST);
            applyModel(m, SUICIDE_VEST);
        });
        return it;
    }

    private ItemStack tripwireTrap() {
        ItemStack it = ItemStack.of(Material.STRING);
        it.editMeta(m -> {
            ItemTools.name(m, "🕸 Tripwire", NamedTextColor.GRAY);
            ItemTools.loreAccent(m, "Right-click ground in a passage to stretch it", NamedTextColor.YELLOW,
                    "Thin wire across a passage (up to " + w().tripwireMaxWidth + " blocks).",
                    "Almost invisible. Touching it triggers an explosion",
                    "that can kill and break blocks.");
            finishThrowable(m, TRIPWIRE_TRAP);
        });
        return it;
    }

    private ItemStack c4() {
        ItemStack it = ItemStack.of(Material.TNT);
        it.editMeta(m -> {
            ItemTools.name(m, "🧨 C4", NamedTextColor.RED);
            ItemTools.loreAccent(m, "Right-click a surface - attach charge", NamedTextColor.YELLOW,
                    "Detonate: hold Shift and jump twice.",
                    "Powerful blast. Lasts " + w().c4LifeSeconds + " sec.",
                    "Limit: " + w().c4MaxPerPlayer + " charges.");
            finishThrowable(m, C4);
        });
        return it;
    }

    private ItemStack sleepGas() {
        ItemStack it = ItemStack.of(Material.GHAST_TEAR);
        it.editMeta(m -> {
            ItemTools.name(m, "☁ Sleep Gas", NamedTextColor.AQUA);
            ItemTools.loreAccent(m, "Gas grenade, right-click throw", NamedTextColor.YELLOW,
                    "Gas cloud for " + w().gasDurationSeconds + " sec (radius " + (int) w().gasRadius + " blocks).",
                    "Blindness and fatigue; stay too long",
                    "and you stop moving until it clears.",
                    "Gas mask fully protects against it.");
            finishThrowable(m, SLEEP_GAS);
        });
        return it;
    }

    private ItemStack firingWall() {
        ItemStack it = ItemStack.of(Material.DRIED_KELP_BLOCK);
        it.editMeta(m -> {
            ItemTools.name(m, "▥ Firing Wall", NamedTextColor.DARK_GREEN);
            ItemTools.loreAccent(m, "Right-click ground - place a wall with a firing slit", NamedTextColor.YELLOW,
                    "A " + w().firingWallWidth + "x" + w().firingWallHeight + " kelp wall,",
                    "with a narrow firing slit in the center.",
                    "Easy for you to shoot through, hard to hit you.",
                    "Enemies can break the wall.");
            finishThrowable(m, FIRING_WALL);
        });
        return it;
    }

    // ------------------------------------------------------------------
    //  Expanded kit: mobility, recon, traps, automatic fire
    // ------------------------------------------------------------------

    private ItemStack grapplingHook() {
        ItemStack it = ItemStack.of(Material.FISHING_ROD);
        it.editMeta(m -> {
            ItemTools.name(m, "🪝 Grappling Hook", NamedTextColor.GOLD);
            ItemTools.loreAccent(m, "Mobility hook, right-click to fire", NamedTextColor.YELLOW,
                    "Hooks onto a visible block/enemy",
                    "(up to " + (int) w().hookRange + " blocks) and pulls you to it.",
                    "After the pull, no fall damage for " + w().hookNoFallSeconds + " sec.",
                    "Charges: " + w().hookCharges + ". The hook breaks when spent.",
                    "Cooldown: " + ItemTools.fmt(w().hookCooldownSeconds) + " sec.");
            m.setUnbreakable(true);
            finishFuelTool(m, GRAPPLING_HOOK, w().hookCharges);
        });
        return it;
    }

    private ItemStack jumpJet() {
        ItemStack it = ItemStack.of(Material.BREEZE_ROD);
        it.editMeta(m -> {
            ItemTools.name(m, "🚀 Jump Jet", NamedTextColor.AQUA);
            ItemTools.loreAccent(m, "Mobility burst, right-click thrust", NamedTextColor.YELLOW,
                    "Boosts upward and forward, then dampens fall.",
                    "Fuel: " + w().jetFuel + " (-" + w().jetCostPerBurst + " per burst),",
                    w().jetRefuelSeconds > 0
                            ? "regenerates automatically (1 unit / " + ItemTools.fmt(w().jetRefuelSeconds) + " sec)."
                            : "fuel does not regenerate; the jet breaks when spent.",
                    "No fall damage for " + w().jetNoFallSeconds + " sec after a burst.");
            finishFuelTool(m, JUMP_JET, w().jetFuel);
        });
        return it;
    }

    private ItemStack combatStim() {
        ItemStack it = ItemStack.of(Material.SUGAR);
        it.editMeta(m -> {
            ItemTools.name(m, "💉 Combat Stim", NamedTextColor.RED);
            ItemTools.loreAccent(m, "Injector, right-click to use", NamedTextColor.YELLOW,
                    "Speed " + roman(w().stimSpeedAmplifier) + ", Jump " + roman(w().stimJumpAmplifier)
                            + ", regeneration for " + w().stimBuffSeconds + " sec.",
                    "Afterward: crash with weakness and slowness for " + w().stimCrashSeconds + " sec.",
                    "Single-use. Cooldown: " + w().stimCooldownSeconds + " sec.");
            finishSimple(m, COMBAT_STIM);
        });
        return it;
    }

    private ItemStack reconScanner() {
        ItemStack it = ItemStack.of(Material.RECOVERY_COMPASS);
        it.editMeta(m -> {
            ItemTools.name(m, "📡 Recon Scanner", NamedTextColor.AQUA);
            ItemTools.loreAccent(m, "Player radar, right-click to activate", NamedTextColor.YELLOW,
                    "Shows distance to the nearest",
                    "living player and updates it for " + Math.max(1, w().scannerDurationSeconds / 60) + " min,",
                    "then turns off. Right-click again to extend.");
            m.setUnbreakable(true);
            finishTool(m, RECON_SCANNER);
        });
        return it;
    }

    private ItemStack proximityMine() {
        ItemStack it = ItemStack.of(Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
        it.editMeta(m -> {
            ItemTools.name(m, "💥 Proximity Mine", NamedTextColor.RED);
            ItemTools.loreAccent(m, "Right-click ground - deploy mine", NamedTextColor.YELLOW,
                    "Arms in " + ItemTools.fmt(w().mineArmSeconds) + " sec, then explodes",
                    "when an enemy comes within " + ItemTools.fmt(w().mineTriggerRadius) + " blocks.",
                    "Ignores teammates. Lasts " + w().mineLifeSeconds + " sec.",
                    "Limit: " + w().mineMaxPerPlayer + " mines.");
            finishSimple(m, PROXIMITY_MINE);
        });
        return it;
    }

    private ItemStack sentryGun() {
        ItemStack it = ItemStack.of(Material.DISPENSER);
        it.editMeta(m -> {
            ItemTools.name(m, "🛡 Auto Sentry", NamedTextColor.GOLD);
            ItemTools.loreAccent(m, "Right-click ground - deploy sentry", NamedTextColor.YELLOW,
                    "Automatically shoots enemies within " + (int) w().sentryRange + " blocks in line of sight.",
                    "Ammo: " + w().sentryAmmo + ", works for " + w().sentryLifeSeconds + " sec.",
                    "Health: " + ItemTools.hearts(w().sentryHealth) + ". Can be broken by attacks.",
                    "Cannot be picked back up after deployment.",
                    "Limit: " + w().sentryMaxPerPlayer + " per player.");
            finishSimple(m, SENTRY_GUN);
            m.setMaxStackSize(1);
        });
        return it;
    }

    private static String roman(int amplifier) {
        return switch (amplifier) {
            case 0 -> "I";
            case 1 -> "II";
            case 2 -> "III";
            case 3 -> "IV";
            default -> String.valueOf(amplifier + 1);
        };
    }

    // ------------------------------------------------------------------
    //  Meta assembly
    // ------------------------------------------------------------------

    private void finishTool(ItemMeta m, String id) {
        plugin.items().writeId(m, id);
        m.setMaxStackSize(1);
        applyModel(m, id);
    }

    private void finishGun(ItemMeta m, String id, int mag) {
        plugin.items().writeId(m, id);
        m.getPersistentDataContainer().set(ammoKey, PersistentDataType.INTEGER, mag);
        m.setMaxStackSize(1);
        m.setEnchantmentGlintOverride(false);
        applyModel(m, id);
    }

    private void finishFuelTool(ItemMeta m, String id, int fuel) {
        plugin.items().writeId(m, id);
        m.getPersistentDataContainer().set(ammoKey, PersistentDataType.INTEGER, fuel);
        m.setMaxStackSize(1);
        applyModel(m, id);
    }

    private void finishThrowable(ItemMeta m, String id) {
        plugin.items().writeId(m, id);
        m.setMaxStackSize(16);
        applyModel(m, id);
    }

    private void finishSimple(ItemMeta m, String id) {
        plugin.items().writeId(m, id);
        m.setMaxStackSize(16);
        applyModel(m, id);
    }

    /** Custom 3D model from the warkit resource pack. */
    private void applyModel(ItemMeta m, String id) {
        m.setItemModel(new NamespacedKey("warkit", id));
    }
}
