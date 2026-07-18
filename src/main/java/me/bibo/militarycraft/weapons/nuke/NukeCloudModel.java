package me.bibo.militarycraft.weapons.nuke;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A mushroom cloud built from {@link BlockDisplay} lumps, shaped like the classic
 * reference: a glowing fireball deep in the crater, a dark grey/black stem
 * climbing up out of the hole, a YELLOW collar ring on the stem, and a wide,
 * full YELLOW cap on top. Lumps overlap generously so the silhouette reads as
 * one connected cloud.
 * <p>
 * The whole rig is authored in a local frame whose {@code y = 0} sits at the
 * CRATER FLOOR ({@code groundOffset} blocks below the impact surface), so the
 * blast visibly starts at the bottom of the crater and rises out of it. The
 * bloom is smooth: the sequence spawns lumps from the ground up via
 * {@link #spawnUpTo}, and each lump puffs from tiny to full size.
 */
public class NukeCloudModel {

    // ---- geometry expressed relative to the GROUND surface (blocks above it) ----
    private static final double STEM_ABOVE = 28.0;
    private static final double RING_ABOVE = 8.0;
    private static final double CAP_BASE_ABOVE = 24.0;
    private static final double CAP_TOP_ABOVE = 44.0;
    public static final double CAP_R = 26.0;
    private static final int MAX_OFFSET = 30;
    private static final int PUFF_TICKS = 11;

    /** One lump of the cloud: local centre + box size + whether it glows (fire). */
    private record Lump(Material material, double x, double y, double z,
                        double sx, double sy, double sz, boolean bright) {
    }

    // deterministic pseudo-noise in 0..1 so the shape is lumpy but stable
    private static double h(double a) {
        double s = Math.sin(a * 12.9898) * 43758.5453;
        return s - Math.floor(s);
    }

    private final World world;
    private final Location base;      // crater floor at local y = 0
    private final int groundOffset;   // ground surface is at local y = groundOffset
    private final double stemTopLocal;
    private final double ringLocal;
    private final double capBaseLocal;
    private final double capTopLocal;
    private final List<Lump> lumps;
    private final List<BlockDisplay> displays;
    private int spawned = 0;

    public NukeCloudModel(Location groundImpact, int craterDepth) {
        this.world = groundImpact.getWorld();
        this.groundOffset = Math.max(0, Math.min(MAX_OFFSET, craterDepth));
        this.base = groundImpact.clone().subtract(0.0, groundOffset, 0.0);
        this.stemTopLocal = groundOffset + STEM_ABOVE;
        this.ringLocal = groundOffset + RING_ABOVE;
        this.capBaseLocal = groundOffset + CAP_BASE_ABOVE;
        this.capTopLocal = groundOffset + CAP_TOP_ABOVE;
        this.lumps = buildLumps();
        this.displays = new ArrayList<>(lumps.size());
    }

    /** Stem radius at local height {@code y} (narrow base, flaring toward the cap). */
    public double stemRadiusAt(double y) {
        double frac = Math.max(0.0, Math.min(1.0, y / stemTopLocal));
        return 4.6 + frac * 3.0 + Math.sin(frac * Math.PI) * 1.2;
    }

    private List<Lump> buildLumps() {
        List<Lump> l = new ArrayList<>();
        double fireTop = groundOffset + 3.0;    // still burning up to just above the crater lip

        // ---------------- stem: glowing in the crater, dark grey/black above ground ----------------
        for (double y = 1.5; y <= stemTopLocal; y += 4.6) {
            double r = stemRadiusAt(y);
            int n = Math.max(5, (int) Math.round(2.0 * Math.PI * r / 6.0));
            boolean fire = y <= fireTop;
            for (int i = 0; i < n; i++) {
                double a = 2.0 * Math.PI * i / n + y * 0.5;
                double jitter = (h(y + i) - 0.5) * 1.4;
                double x = Math.cos(a) * (r + jitter);
                double z = Math.sin(a) * (r + jitter);
                double s = 6.8 + h(x + y) * 1.6;
                Material m = fire ? fireShade(x + z + y) : stemShade(y, x + z + y);
                l.add(new Lump(m, x, y + (h(a) - 0.5), z, s, s, s, fire));
            }
            l.add(new Lump(fire ? Material.ORANGE_CONCRETE : stemShade(y, y + 1),
                    0, y, 0, 7.2, 5.4, 7.2, fire));
        }

        // ---------------- extra fireball volume at the crater bottom ----------------
        for (double r : new double[]{0.0, 3.2, 5.6}) {
            int n = Math.max(1, (int) Math.round(2.0 * Math.PI * r / 3.4));
            for (int i = 0; i < n; i++) {
                double a = 2.0 * Math.PI * i / n + r;
                double x = Math.cos(a) * r;
                double z = Math.sin(a) * r;
                double y = 1.5 + h(x + z) * 3.0;
                double s = 4.6 + h(x * 2 + z) * 1.8;
                l.add(new Lump(fireShade(x - z), x, y, z, s, s, s, true));
            }
        }

        // ---------------- yellow collar ring around the stem ----------------
        double ringR = stemRadiusAt(ringLocal) + 2.2;
        int ringN = Math.max(8, (int) Math.round(2.0 * Math.PI * ringR / 4.4));
        for (int i = 0; i < ringN; i++) {
            double a = 2.0 * Math.PI * i / ringN;
            double x = Math.cos(a) * ringR;
            double z = Math.sin(a) * ringR;
            double y = ringLocal + (h(a) - 0.5) * 1.2;
            l.add(new Lump(Material.YELLOW_CONCRETE, x, y, z, 4.6, 3.4, 4.6, true));
        }

        // ---------------- down-turned skirt (rolled-under rim of the cap) ----------------
        int skirtN = (int) Math.round(2.0 * Math.PI * (CAP_R * 0.92) / 5.8);
        for (int i = 0; i < skirtN; i++) {
            double a = 2.0 * Math.PI * i / skirtN;
            double rr = CAP_R * 0.92;
            double x = Math.cos(a) * rr;
            double z = Math.sin(a) * rr;
            double y = capBaseLocal - 2.5 - h(a) * 1.8;
            l.add(new Lump(Material.ORANGE_CONCRETE, x, y, z, 6.2, 5.2, 6.2, true));
        }

        // ---------------- wide, full yellow domed cap (cauliflower billows) ----------------
        int capLevels = 5;
        for (int lvl = 0; lvl <= capLevels; lvl++) {
            double t = (double) lvl / capLevels;                          // 0 base .. 1 top
            double y = capBaseLocal + t * (capTopLocal - capBaseLocal);
            double rr = CAP_R * Math.pow(Math.cos(t * Math.PI * 0.5 * 0.85), 0.7); // full, then tapering
            if (rr < 2.0) rr = 2.0;
            for (double ring : new double[]{rr, rr * 0.5}) {
                if (ring < 2.5 && ring != rr) continue;
                int n = Math.max(3, (int) Math.round(2.0 * Math.PI * ring / 7.2));
                for (int i = 0; i < n; i++) {
                    double a = 2.0 * Math.PI * i / n + lvl * 0.7;
                    double jitter = (h(a + lvl) - 0.5) * 1.7;
                    double x = Math.cos(a) * (ring + jitter);
                    double z = Math.sin(a) * (ring + jitter);
                    double s = 7.0 + h(x + z + lvl) * 2.0;
                    l.add(new Lump(capShade(t, x + z + lvl), x, y + (h(a * 2) - 0.5) * 1.4, z, s, s * 0.82, s, false));
                }
            }
            l.add(new Lump(capShade(t, y), 0, y, 0, 7.4, 6.0, 7.4, false));
            // a billow poking out for a turbulent, cauliflower surface
            double ba = h(lvl * 3.0) * 2.0 * Math.PI;
            double br = rr * (0.7 + h(ba) * 0.35);
            l.add(new Lump(capShade(t, ba + lvl), Math.cos(ba) * br, y + (h(lvl) - 0.3) * 2.0,
                    Math.sin(ba) * br, 6.4 + h(ba) * 1.5, 5.4, 6.4 + h(ba) * 1.5, false));
        }

        // ---------------- bright billows crowning the very top ----------------
        for (int i = 0; i < 7; i++) {
            double a = 2.0 * Math.PI * i / 7;
            double rr = 6.0 + h(a) * 4.0;
            l.add(new Lump(h(a) > 0.5 ? Material.YELLOW_CONCRETE : Material.ORANGE_CONCRETE,
                    Math.cos(a) * rr, capTopLocal + h(a) * 3.0, Math.sin(a) * rr,
                    6.2, 5.6, 6.2, true));
        }

        // spawn from the ground up
        l.sort((p, q) -> Double.compare(p.y(), q.y()));
        return l;
    }

    private static Material fireShade(double seed) {
        return h(seed) > 0.5 ? Material.YELLOW_CONCRETE : Material.ORANGE_CONCRETE;
    }

    private Material stemShade(double y, double seed) {
        double r = h(seed);
        double aboveFrac = (y - groundOffset) / STEM_ABOVE;   // 0 at ground .. 1 at cap
        if (aboveFrac < 0.4) {
            return r > 0.35 ? Material.BLACK_CONCRETE : Material.GRAY_CONCRETE;  // sooty lower stem
        }
        return r > 0.5 ? Material.GRAY_CONCRETE : Material.BLACK_CONCRETE;
    }

    private static Material capShade(double t, double seed) {
        double r = h(seed);
        if (t < 0.2) return r > 0.5 ? Material.ORANGE_CONCRETE : Material.YELLOW_CONCRETE; // lit underside
        if (t > 0.7) return r > 0.4 ? Material.YELLOW_CONCRETE : Material.YELLOW_TERRACOTTA; // sunlit top
        return r > 0.35 ? Material.YELLOW_CONCRETE : Material.ORANGE_CONCRETE;
    }

    // ---- geometry accessors for the particle layer in the sequence ----
    public double baseY() {
        return base.getY();
    }

    public double groundY() {
        return base.getY() + groundOffset;
    }

    public double stemTopLocal() {
        return stemTopLocal;
    }

    public double ringLocal() {
        return ringLocal;
    }

    public double capBaseLocal() {
        return capBaseLocal;
    }

    public double capTopLocal() {
        return capTopLocal;
    }

    public int totalLumps() {
        return lumps.size();
    }

    /** Spawns lumps until {@code count} of them exist; each puffs from tiny to full size. */
    public void spawnUpTo(int count) {
        int target = Math.min(count, lumps.size());
        while (spawned < target) {
            Lump lump = lumps.get(spawned++);
            BlockData data = lump.material().createBlockData();
            Vector3f fullScale = new Vector3f((float) lump.sx(), (float) lump.sy(), (float) lump.sz());
            Vector3f fullTrans = new Vector3f(
                    (float) (lump.x() - lump.sx() / 2.0),
                    (float) (lump.y() - lump.sy() / 2.0),
                    (float) (lump.z() - lump.sz() / 2.0));
            float seed = 0.12f;
            Vector3f seedScale = new Vector3f(fullScale).mul(seed);
            Vector3f seedTrans = new Vector3f(
                    (float) (lump.x() - lump.sx() * seed / 2.0),
                    (float) (lump.y() - lump.sy() * seed / 2.0),
                    (float) (lump.z() - lump.sz() * seed / 2.0));
            int light = lump.bright() ? 15 : 11;

            BlockDisplay d = world.spawn(base, BlockDisplay.class, disp -> {
                disp.setBlock(data);
                disp.setInterpolationDelay(0);
                disp.setInterpolationDuration(0);
                disp.setTransformation(new Transformation(seedTrans, new Quaternionf(), seedScale, new Quaternionf()));
                disp.setBrightness(new Display.Brightness(light, 15));
                disp.setShadowRadius(0.0f);
                disp.setShadowStrength(0.0f);
                disp.setPersistent(false);
                disp.setViewRange(6.0f); // a towering cloud must be visible from far off
            });
            // second transform starts the smooth tiny -> full puff
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(PUFF_TICKS);
            d.setTransformation(new Transformation(fullTrans, new Quaternionf(), fullScale, new Quaternionf()));
            displays.add(d);
        }
    }

    public void remove() {
        for (BlockDisplay d : displays) {
            if (d.isValid()) {
                d.remove();
            }
        }
        displays.clear();
    }
}

