package me.bibo.militarycraft.gear.warkit.weapon;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Temporary fall-damage immunity, mainly for impulse grenade knockback. */
public final class FallImmunity {

    private final Map<UUID, Long> until = new HashMap<>();

    public void grant(UUID uuid, int seconds) {
        until.put(uuid, System.currentTimeMillis() + seconds * 1000L);
    }

    public boolean has(UUID uuid) {
        Long t = until.get(uuid);
        if (t == null) return false;
        if (t < System.currentTimeMillis()) {
            until.remove(uuid);
            return false;
        }
        return true;
    }

    public void clear(UUID uuid) {
        until.remove(uuid);
    }
}
