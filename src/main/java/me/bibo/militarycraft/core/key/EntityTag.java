package me.bibo.militarycraft.core.key;

import org.bukkit.entity.Entity;

/**
 * Global scoreboard tag + module-stamp PDC put on every entity MilitaryCraft spawns
 * (§3.1). Lets a world sweep (`/mc cleanup`) find and group all of our entities
 * without per-module scoreboard tags.
 */
public final class EntityTag {

    public static final String TAG = "militarycraft";
    private static final String MODULE_PDC_NAME = "module";

    private EntityTag() {
    }

    /** Marks {@code entity} as ours and stamps which module owns it. */
    public static void tag(Entity entity, String moduleId) {
        entity.addScoreboardTag(TAG);
        Pdc.setString(entity.getPersistentDataContainer(), Keys.of("core", MODULE_PDC_NAME), moduleId);
    }

    public static boolean isTagged(Entity entity) {
        return entity.getScoreboardTags().contains(TAG);
    }

    /** Module id stamped on this entity, or null if untagged/foreign. */
    public static String moduleOf(Entity entity) {
        return Pdc.getString(entity.getPersistentDataContainer(), Keys.of("core", MODULE_PDC_NAME), null);
    }
}
