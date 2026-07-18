package me.bibo.militarycraft.vehicles.tank.model;

/**
 * Which moving sub-assembly a part belongs to. Determines how it is rotated
 * each tick relative to the tank origin.
 */
public enum PartGroup {
    /** Fixed to the hull: follows hull yaw only. */
    HULL,
    /** Follows the turret yaw (rotates about the turret ring). */
    TURRET,
    /** Follows the turret yaw and the barrel elevation (rotates about the gun mount). */
    BARREL
}
