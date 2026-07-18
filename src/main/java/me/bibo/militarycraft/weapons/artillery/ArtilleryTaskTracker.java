package me.bibo.militarycraft.weapons.artillery;

import me.bibo.militarycraft.core.Core;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;

/** Owns every repeating and delayed task created by the artillery module. */
final class ArtilleryTaskTracker {

    private final Core core;
    private final Set<BukkitTask> tasks = new HashSet<>();
    private boolean shuttingDown;

    ArtilleryTaskTracker(Core core) {
        this.core = core;
    }

    BukkitTask later(Runnable action, long delayTicks) {
        if (shuttingDown) {
            return null;
        }
        BukkitTask[] holder = new BukkitTask[1];
        BukkitTask task = core.scheduler().runTaskLater(core.plugin(), () -> {
            try {
                if (!shuttingDown) {
                    action.run();
                }
            } catch (RuntimeException ex) {
                core.logger().warning("Artillery delayed task failed: " + ex.getMessage());
            } finally {
                tasks.remove(holder[0]);
            }
        }, Math.max(0L, delayTicks));
        holder[0] = task;
        tasks.add(task);
        return task;
    }

    BukkitTask repeating(Runnable action, long delayTicks, long periodTicks) {
        if (shuttingDown) {
            return null;
        }
        BukkitTask[] holder = new BukkitTask[1];
        BukkitTask task = core.scheduler().runTaskTimer(core.plugin(), () -> {
            try {
                if (!shuttingDown) {
                    action.run();
                }
            } catch (RuntimeException ex) {
                BukkitTask failed = holder[0];
                if (failed != null) {
                    failed.cancel();
                    tasks.remove(failed);
                }
                core.logger().warning("Artillery repeating task failed and was stopped: " + ex.getMessage());
            }
        },
                Math.max(0L, delayTicks), Math.max(1L, periodTicks));
        holder[0] = task;
        tasks.add(task);
        return task;
    }

    void cancel(BukkitTask task) {
        if (task == null) {
            return;
        }
        task.cancel();
        tasks.remove(task);
    }

    void cancelAll() {
        shuttingDown = true;
        for (BukkitTask task : Set.copyOf(tasks)) {
            task.cancel();
        }
        tasks.clear();
    }
}
