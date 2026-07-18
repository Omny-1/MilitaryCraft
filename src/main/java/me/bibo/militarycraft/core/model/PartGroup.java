package me.bibo.militarycraft.core.model;

/**
 * Documentation only (§5.5): {@link Part#group} is a plain {@code int} because
 * articulation channels are vehicle-defined (tank: hull/turret/barrel; kamaz: hull
 * only; jet: one rigid body sharing a single orientation). The base ({@code core.vehicle})
 * never switches on a group value — only the owning vehicle's own {@code transformFor}
 * does — so this is deliberately not an enum, just the one channel every vehicle shares.
 */
public final class PartGroup {

    private PartGroup() {
    }

    /** Fixed to the body: follows only the vehicle's overall position, no independent articulation. */
    public static final int STATIC = 0;
}
