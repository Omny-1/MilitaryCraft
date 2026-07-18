package me.bibo.militarycraft.vehicles.train.rail;

import me.bibo.militarycraft.vehicles.train.train.TrainPath;
import org.bukkit.block.Block;

/**
 * The train's "scout": walks the rails one block at a time appending each
 * block's polyline to the path. When the track runs out it parks in a
 * dead-end state and can later resume if rails are placed (or a switch is
 * flipped) ahead.
 */
public final class RailCursor {

    private Block block;
    private RailEdge entry;
    private RailTracer.Connection lastExit; // set when dead-ended past `block`
    private boolean deadEnd;

    public RailCursor(Block start, RailEdge entry) {
        this.block = start;
        this.entry = entry;
    }

    /**
     * Cursor positioned just past {@code from}'s exit: on the next rail block
     * if it exists, otherwise dead-ended right there (resumable).
     */
    public static RailCursor afterExit(Block from, RailTracer.Connection exit) {
        Block next = RailTracer.nextBlock(from, exit);
        if (next != null) {
            return new RailCursor(next, exit.edge().opposite());
        }
        RailCursor c = new RailCursor(from, exit.edge().opposite());
        c.lastExit = exit;
        c.deadEnd = true;
        return c;
    }

    public boolean isDeadEnd() {
        return deadEnd;
    }

    /**
     * Append one more rail block's polyline to the path.
     *
     * @return false if nothing was appended (dead end).
     */
    public boolean extend(TrainPath path) {
        if (deadEnd) {
            return false;
        }
        if (!RailTracer.isRail(block)) {
            // The rail we were about to ride got broken: path ends here.
            deadEnd = true;
            return false;
        }
        RailTracer.Traverse t = RailTracer.traverse(block, entry);
        for (org.bukkit.util.Vector p : t.points()) {
            path.append(p);
        }
        Block next = RailTracer.nextBlock(block, t.exit());
        if (next == null) {
            lastExit = t.exit();
            deadEnd = true;
        } else {
            block = next;
            entry = t.exit().edge().opposite();
        }
        return true;
    }

    /**
     * Re-probe a dead end: if track appeared ahead (or the broken rail is
     * back), clear the dead-end state so {@link #extend} can continue.
     */
    public boolean tryResume() {
        if (!deadEnd) {
            return true;
        }
        if (lastExit != null) {
            Block next = RailTracer.nextBlock(block, lastExit);
            if (next != null) {
                block = next;
                entry = lastExit.edge().opposite();
                lastExit = null;
                deadEnd = false;
                return true;
            }
            return false;
        }
        if (RailTracer.isRail(block)) {
            deadEnd = false;
            return true;
        }
        return false;
    }
}
