package me.bibo.militarycraft.gear.warkit;

import org.bukkit.configuration.ConfigurationSection;

/** Snapshot of config.yml values. Recreated on /warkit reload. */
public final class Settings {

    public final double medkitChannelSeconds;
    public final int medkitCooldownSeconds;

    public final double painkillerReduction;
    public final int painkillerDurationSeconds;
    public final int painkillerCooldownSeconds;

    public final double vestArmor, vestToughness, vestKbRes, vestSpeedPenalty;
    public final int vestMaxDurability;

    public final double kevlarArmor, kevlarToughness;
    public final int kevlarMaxDurability;

    public final double exoArmor, exoToughness, exoAttackBonus, exoSpeedBonus, exoJumpBonus;

    public final double maskArmor;

    public final double padsArmor, padsFallReduction, padsSafeFallBonus;

    public final int camoCooldownSeconds;

    public final double visorMinDistance, visorMaxDistance;

    public final int markerDurationSeconds;

    public final int rationCooldownSeconds;

    public final double repairChannelSeconds;
    public final int repairCooldownSeconds;
    public final double repairVehicleHealthFraction;
    public final double repairVehicleMinHealth;

    public Settings(ConfigurationSection c) {
        medkitChannelSeconds = c.getDouble("medkit.channel-seconds", 4.0);
        medkitCooldownSeconds = c.getInt("medkit.cooldown-seconds", 20);

        painkillerReduction = clamp(c.getDouble("painkiller.damage-reduction", 0.25), 0.0, 0.9);
        painkillerDurationSeconds = c.getInt("painkiller.duration-seconds", 45);
        painkillerCooldownSeconds = c.getInt("painkiller.cooldown-seconds", 90);

        vestArmor = c.getDouble("vest.armor", 8.0);
        vestToughness = c.getDouble("vest.toughness", 6.0);
        vestKbRes = c.getDouble("vest.knockback-resistance", 0.2);
        vestSpeedPenalty = c.getDouble("vest.speed-penalty", 0.06);
        vestMaxDurability = c.getInt("vest.max-durability", 400);

        kevlarArmor = c.getDouble("kevlar.armor", 3.0);
        kevlarToughness = c.getDouble("kevlar.toughness", 2.0);
        kevlarMaxDurability = c.getInt("kevlar.max-durability", 300);

        exoArmor = c.getDouble("exosuit.armor", 4.0);
        exoToughness = c.getDouble("exosuit.toughness", 2.0);
        exoAttackBonus = c.getDouble("exosuit.attack-bonus", 4.0);
        exoSpeedBonus = c.getDouble("exosuit.speed-bonus", 0.15);
        exoJumpBonus = c.getDouble("exosuit.jump-bonus", 0.35);

        maskArmor = c.getDouble("gas-mask.armor", 1.0);

        padsArmor = c.getDouble("pads.armor", 2.0);
        padsFallReduction = clamp(c.getDouble("pads.fall-damage-reduction", 0.6), 0.0, 1.0);
        padsSafeFallBonus = c.getDouble("pads.safe-fall-bonus", 2.0);

        camoCooldownSeconds = c.getInt("camo.cooldown-seconds", 15);

        visorMinDistance = c.getDouble("visor.min-distance", 10.0);
        visorMaxDistance = c.getDouble("visor.max-distance", 80.0);

        markerDurationSeconds = c.getInt("marker.duration-seconds", 300);

        rationCooldownSeconds = c.getInt("ration.cooldown-seconds", 2);

        repairChannelSeconds = c.getDouble("repair.channel-seconds", 4.0);
        repairCooldownSeconds = c.getInt("repair.cooldown-seconds", 60);
        repairVehicleHealthFraction = clamp(c.getDouble("repair.vehicle-health-fraction", 0.25), 0.0, 1.0);
        repairVehicleMinHealth = Math.max(0.0, c.getDouble("repair.vehicle-min-health", 10.0));
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
