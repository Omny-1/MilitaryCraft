package me.bibo.militarycraft.core.key;

/**
 * Central registry of {@code CustomModelData} int constants for every placer/gear item
 * across all modules (e.g. TankCraft's placer was 7341). One constant per item; never
 * reuse a value.
 */
public final class ModelData {

    private ModelData() {
    }

    /** TankCraft placer item (source CustomModelData 7341). */
    public static final int TANK = 7341;

    /** Kamaz "Pushinka" placer item (was KamazCraft's 7342). */
    public static final int KAMAZ = 7342;

    /** JetCraft placer item remapped from source 7342 to avoid the Kamaz value. */
    public static final int JET = 7343;

    public static final int HELICOPTER = 7344;

    public static final int AIRSHIP = 7345;

    /** DroneCraft placer item remapped from source 7351 into the CP3c aircraft block. */
    public static final int DRONE = 7346;

    /** MotoCraft placer item remapped from source 7351. */
    public static final int MOTO = 7347;

    /** PickupCraft placer item remapped from source 7342. */
    public static final int PICKUP = 7348;

    /** TrainCraft placer item; the source item did not use custom model data. */
    public static final int TRAIN = 7349;

    /** SvoArtillery placer item; the source item did not use custom model data. */
    public static final int ARTILLERY = 7350;

    /** AntiAirCraft stationary turret placer. */
    public static final int ANTI_AIR = 7352;

    /** TCKBus stationary bus placer. */
    public static final int TCK_BUS = 7353;

    /** Airstrike beacon item. */
    public static final int AIRSTRIKE_BEACON = 7354;

    /** Nuclear strike briefcase item. */
    public static final int NUKE_BRIEFCASE = 7355;

    /** First value in the WarKit gear range; individual items add their enum ordinal. */
    public static final int WARKIT_BASE = 7360;
}
