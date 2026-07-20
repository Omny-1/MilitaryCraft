package me.bibo.militarycraft.weapons.antiair.fuel;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

/**
 * Vanilla-like furnace fuel burn times (in ticks). The turret behaves like a
 * furnace: it lights one item from its fuel slot and burns for this long. Only
 * items listed here count as fuel; everything else is rejected from the slot.
 */
public final class FuelTable {

    private FuelTable() {
    }

    /** Burn time of a single item in ticks, or 0 if it isn't a fuel. */
    public static int burnTicks(Material m) {
        if (m == null || m.isAir()) {
            return 0;
        }
        switch (m) {
            case LAVA_BUCKET:
                return 20000;
            case COAL_BLOCK:
                return 16000;
            case DRIED_KELP_BLOCK:
                return 4000;
            case BLAZE_ROD:
                return 2400;
            case COAL:
            case CHARCOAL:
                return 1600;
            case BAMBOO_MOSAIC:
                return 300;
            case STICK:
                return 100;
            case BAMBOO:
                return 50;
            default:
                break;
        }
        // Broad wooden families via tags (matches vanilla 300t for logs/planks/
        // wood, 300t for fences/gates, 150t for slabs, etc. - kept simple at 300).
        if (Tag.LOGS.isTagged(m)
                || Tag.PLANKS.isTagged(m)
                || Tag.WOODEN_STAIRS.isTagged(m)
                || Tag.WOODEN_FENCES.isTagged(m)
                || Tag.FENCE_GATES.isTagged(m)
                || Tag.WOODEN_DOORS.isTagged(m)
                || Tag.WOODEN_TRAPDOORS.isTagged(m)
                || Tag.WOODEN_PRESSURE_PLATES.isTagged(m)
                || Tag.WOODEN_BUTTONS.isTagged(m)
                || Tag.SAPLINGS.isTagged(m)
                || Tag.WOOL.isTagged(m)) {
            return 300;
        }
        if (Tag.WOODEN_SLABS.isTagged(m)) {
            return 150;
        }
        return 0;
    }

    public static boolean isFuel(Material m) {
        return burnTicks(m) > 0;
    }

    public static boolean isFuel(ItemStack item) {
        return item != null && isFuel(item.getType());
    }
}
