package me.bibo.militarycraft.core.model;

import java.util.List;

/**
 * Static shape of a vehicle's model (§5.1/§5.5): its parts, the bounding dims used
 * by ray-trace/melee/projectile-sweep hit tests, seat height, and hitbox layout.
 *
 * <p>{@code hitboxZOffsets} mirrors TankCraft/KamazCraft's row of hitboxes spaced
 * along the body's local Z axis; {@code centerHitboxIndex} is the one whose world
 * location defines the vehicle's anchor on rehydrate (§5.5) — Tank uses the middle
 * of three, Kamaz the middle of five.
 */
public record VehicleModel(
        List<Part> parts,
        float width,
        float height,
        float length,
        double seatHeight,
        float[] hitboxZOffsets,
        int centerHitboxIndex
) {
}
