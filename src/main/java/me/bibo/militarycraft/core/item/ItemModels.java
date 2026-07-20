package me.bibo.militarycraft.core.item;

import me.bibo.militarycraft.core.config.ModuleConfig;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Decides whether an item gets its resource-pack model or keeps the plain vanilla look of
 * the material it is built from.
 *
 * <p>A server cannot tell what a client has loaded. If the plugin writes an
 * {@code item_model} reference and the player has no pack providing it, the item renders
 * as the missing-model placeholder rather than falling back to anything sensible. That
 * makes the reference unsafe to apply unconditionally, so it is off by default and turned
 * on by servers that actually distribute the pack.
 *
 * <p>The switch only affects items at the moment they are created. Items already in
 * inventories keep whatever was written into them, because the reference is stored on the
 * item itself.
 */
public final class ItemModels {

    /** Namespace of the shipped pack. Baked into items, so changing it breaks existing ones. */
    private static final String NAMESPACE = "warkit";

    private static volatile boolean enabled;

    private ItemModels() {
    }

    /** Re-reads {@code resource-pack.models}; called on enable and on every reload. */
    public static void refresh(ModuleConfig config) {
        enabled = config != null && config.getBoolean("resource-pack.models", false);
    }

    /** Whether custom models are being applied to newly created items. */
    public static boolean enabled() {
        return enabled;
    }

    /**
     * Applies the pack model for {@code id} when models are enabled. When they are not,
     * the item is left alone and shows the vanilla appearance of its material.
     */
    public static void apply(ItemMeta meta, String id) {
        if (!enabled || meta == null || id == null || id.isBlank()) {
            return;
        }
        meta.setItemModel(new NamespacedKey(NAMESPACE, id));
    }
}
