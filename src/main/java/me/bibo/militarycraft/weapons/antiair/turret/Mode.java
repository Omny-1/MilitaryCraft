package me.bibo.militarycraft.weapons.antiair.turret;

/**
 * The three firing modes. Per the brief, the "survival" family is labelled
 * "normies" modes target mobs, and the battle-royale mode is labelled <b>SVO</b> in all
 * player-facing text.
 */
public enum Mode {
    /** Survival — shoots only phantoms circling overhead (the no-sleep menace). */
    NORMIES_PHANTOMS("Normies: Phantoms", "Shoots down phantoms overhead"),
    /** Survival — shoots every hostile mob in range. */
    NORMIES_HOSTILES("Normies: Hostile Mobs", "Engages hostile mobs"),
    /** Battle-royale — shoots players in range (accuracy drops with distance). */
    SVO("SVO: Players", "Targets players; closer targets are hit more accurately");

    private final String title;
    private final String description;

    Mode(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public boolean isNormies() {
        return this == NORMIES_PHANTOMS || this == NORMIES_HOSTILES;
    }

    public Mode next() {
        Mode[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    public static Mode fromString(String s, Mode fallback) {
        if (s == null) {
            return fallback;
        }
        try {
            return Mode.valueOf(s);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
