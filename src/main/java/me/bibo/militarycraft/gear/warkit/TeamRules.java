package me.bibo.militarycraft.gear.warkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** WarKit team rules. Source of truth for BR mode: scoreboard objective svoteam. */
public final class TeamRules {

    private static final String TEAM_OBJECTIVE = "svoteam";
    private static final long PROTECTED_BLAST_MS = 1500L;
    private static final List<ProtectedBlast> protectedBlasts = new ArrayList<>();

    private record ProtectedBlast(UUID owner, int ownerTeam, Location center, double radius, long expiresAt) {}

    private TeamRules() {}

    public static boolean canDamage(Player attacker, Entity target) {
        return attacker == null || !sameSvoTeam(attacker, target);
    }

    public static boolean sameSvoTeam(Player a, Entity target) {
        if (!(target instanceof Player b)) return false;
        if (a.equals(b)) return false;
        int ta = score(a);
        int tb = score(b);
        return ta > 0 && ta == tb;
    }

    public static void protectExplosion(Player owner, Location center, double radius) {
        if (owner == null || center.getWorld() == null) return;
        int team = score(owner);
        if (team <= 0) return;
        protectedBlasts.add(new ProtectedBlast(owner.getUniqueId(), team, center.clone(), radius,
                System.currentTimeMillis() + PROTECTED_BLAST_MS));
    }

    public static boolean isProtectedExplosionDamage(Player victim) {
        long now = System.currentTimeMillis();
        int victimTeam = score(victim);
        if (victimTeam <= 0) return false;
        Iterator<ProtectedBlast> it = protectedBlasts.iterator();
        while (it.hasNext()) {
            ProtectedBlast blast = it.next();
            if (now > blast.expiresAt()) {
                it.remove();
                continue;
            }
            if (victim.getUniqueId().equals(blast.owner())) continue;
            if (victimTeam != blast.ownerTeam()) continue;
            if (!victim.getWorld().equals(blast.center().getWorld())) continue;
            if (victim.getLocation().distanceSquared(blast.center()) <= blast.radius() * blast.radius()) {
                return true;
            }
        }
        return false;
    }

    private static int score(Player p) {
        Integer score = scoreFrom(p.getScoreboard(), p.getName());
        if (score != null) return score;
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        score = scoreFrom(main, p.getName());
        return score == null ? 0 : score;
    }

    private static Integer scoreFrom(Scoreboard board, String entry) {
        Objective objective = board.getObjective(TEAM_OBJECTIVE);
        if (objective == null) return null;
        Score score = objective.getScore(entry);
        return score.isScoreSet() ? score.getScore() : null;
    }
}
