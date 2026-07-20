package me.bibo.militarycraft.core.module;

import me.bibo.militarycraft.core.Core;

/** Contract every game module (tank, antiair, warkit, ...) implements. */
public interface MilitaryModule {

    /** Stable id, e.g. "tank", "antiair", "airstrike", "warkit". Used for config/perm/key namespacing. */
    String id();

    /** Build settings, register manager/listeners/subcommands/perms. */
    void enable(Core core);

    /** Eject riders, persist, cancel tasks. Must NOT delete entities (a reload/disable is not a wipe). */
    void disable();

    /** Rebuild the settings snapshot and push it to live objects. Default: nothing to do. */
    default void reload(Core core) {
    }
}
