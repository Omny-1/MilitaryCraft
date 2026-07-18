package me.bibo.militarycraft.vehicles.train.train;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Arc-length parameterised polyline: the exact track the locomotive's nose
 * has travelled (plus a lookahead). Every car reads its position from here at
 * a fixed distance behind the nose, which is what keeps the couplings rigid
 * and makes each wagon repeat the locomotive's path instead of cutting
 * corners.
 */
public final class TrainPath {

    private record Node(double x, double y, double z, double dist) {
    }

    private final List<Node> nodes = new ArrayList<>();

    /** Append a point; consecutive duplicates are dropped. */
    public void append(Vector p) {
        if (nodes.isEmpty()) {
            nodes.add(new Node(p.getX(), p.getY(), p.getZ(), 0.0));
            return;
        }
        Node last = nodes.get(nodes.size() - 1);
        double dx = p.getX() - last.x;
        double dy = p.getY() - last.y;
        double dz = p.getZ() - last.z;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1e-6) {
            return;
        }
        nodes.add(new Node(p.getX(), p.getY(), p.getZ(), last.dist + d));
    }

    public double minDist() {
        return nodes.isEmpty() ? 0.0 : nodes.get(0).dist;
    }

    public double maxDist() {
        return nodes.isEmpty() ? 0.0 : nodes.get(nodes.size() - 1).dist;
    }

    /** Interpolated point at the given arc length (clamped to the path). */
    public Vector pointAt(double d) {
        if (nodes.isEmpty()) {
            return new Vector();
        }
        Node first = nodes.get(0);
        if (d <= first.dist) {
            return new Vector(first.x, first.y, first.z);
        }
        Node last = nodes.get(nodes.size() - 1);
        if (d >= last.dist) {
            return new Vector(last.x, last.y, last.z);
        }
        int lo = 0;
        int hi = nodes.size() - 1;
        while (lo + 1 < hi) {
            int mid = (lo + hi) >>> 1;
            if (nodes.get(mid).dist <= d) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        Node a = nodes.get(lo);
        Node b = nodes.get(hi);
        double t = (d - a.dist) / Math.max(1e-9, b.dist - a.dist);
        return new Vector(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t);
    }

    /** Drop history no car can reach any more (keeps one node before {@code d}). */
    public void trimBefore(double d) {
        int cut = 0;
        while (cut + 1 < nodes.size() && nodes.get(cut + 1).dist < d) {
            cut++;
        }
        if (cut > 0) {
            nodes.subList(0, cut).clear();
        }
    }

    /** Copy of all points in order; used only while assembling the spawn path. */
    public List<Vector> snapshotPoints() {
        List<Vector> out = new ArrayList<>(nodes.size());
        for (Node n : nodes) {
            out.add(new Vector(n.x, n.y, n.z));
        }
        return out;
    }

    public int size() {
        return nodes.size();
    }
}
