package me.bibo.militarycraft.weapons.artillery;

import java.util.UUID;

/** Live association between an operator and the exact selected artillery. */
record ArtillerySession(UUID playerId, UUID artilleryId) {
}
