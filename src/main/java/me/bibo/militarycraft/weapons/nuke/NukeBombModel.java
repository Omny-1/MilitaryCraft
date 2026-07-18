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
 * A big, detailed "Fat Man"-style nuclear bomb built from {@link BlockDisplay}
 * entities.
 * <p>
 * The model is authored lying HORIZONTAL with its long axis along local +Z (nose
 * at +Z, boxed tail fins at -Z), so when released it appears level under the
 * bomber. During the fall {@link #setPitch} rotates it nose-down; the whole rig
 * is a rigid body whose position is set by {@link #moveTo}.
 * <p>
 * Orientation follows the single-matrix rule the other rigs rely on: each tick
 * both a part's anchor offset AND its block orientation are rotated by the same
 * quaternion {@code Q = yaw(heading) * pitch(theta)}, so the model never comes
 * apart as it tips over.
 */
public class NukeBombModel {

    /** Distance from the model centre to the nose tip, along local +Z. */
    public static final double NOSE_Z = 4.15;

    /** One block of the model: local centre + scale, in model space (nose along +Z). */
    private record Part(Material material, float lx, float ly, float lz, float sx, float sy, float sz) {
    }

    private static final List<Part> PARTS = buildParts();

    private static List<Part> buildParts() {
        List<Part> p = new ArrayList<>();

        // ----- nose (dark grey rounded tip) -----
        p.add(new Part(Material.GRAY_CONCRETE, 0.0f, 0.0f, 3.90f, 0.70f, 0.70f, 0.55f));

        // ----- red nose cone, widening toward the body -----
        p.add(new Part(Material.RED_CONCRETE, 0.0f, 0.0f, 3.45f, 1.30f, 1.30f, 0.55f));
        p.add(new Part(Material.RED_CONCRETE, 0.0f, 0.0f, 2.90f, 1.90f, 1.90f, 0.60f));
        p.add(new Part(Material.RED_CONCRETE, 0.0f, 0.0f, 2.35f, 2.40f, 2.40f, 0.55f));

        // ----- olive-green body: a fat egg, widest just aft of the nose -----
        p.add(new Part(Material.GREEN_CONCRETE, 0.0f, 0.0f, 1.60f, 2.90f, 2.90f, 0.90f));
        p.add(new Part(Material.GREEN_CONCRETE, 0.0f, 0.0f, 0.60f, 3.20f, 3.20f, 1.05f)); // fattest
        p.add(new Part(Material.GREEN_CONCRETE, 0.0f, 0.0f, -0.50f, 3.15f, 3.15f, 1.05f));
        p.add(new Part(Material.GREEN_CONCRETE, 0.0f, 0.0f, -1.45f, 2.75f, 2.75f, 0.90f));
        p.add(new Part(Material.GREEN_CONCRETE, 0.0f, 0.0f, -2.20f, 2.15f, 2.15f, 0.70f));
        p.add(new Part(Material.GREEN_CONCRETE, 0.0f, 0.0f, -2.78f, 1.55f, 1.55f, 0.60f));
        p.add(new Part(Material.GREEN_CONCRETE, 0.0f, 0.0f, -3.25f, 1.05f, 1.05f, 0.50f)); // tail cap

        // ----- two yellow bands around the forward shoulder (protrude past the body) -----
        p.add(new Part(Material.YELLOW_CONCRETE, 0.0f, 0.0f, 1.85f, 3.02f, 3.02f, 0.22f));
        p.add(new Part(Material.YELLOW_CONCRETE, 0.0f, 0.0f, 1.35f, 3.28f, 3.28f, 0.22f));

        // ----- dark equatorial seam + a side access hatch -----
        p.add(new Part(Material.BLACK_CONCRETE, 0.0f, 0.0f, 0.05f, 3.26f, 3.26f, 0.12f));
        p.add(new Part(Material.GRAY_CONCRETE, 1.52f, 0.0f, 0.30f, 0.12f, 1.0f, 1.6f)); // hatch
        p.add(new Part(Material.GRAY_CONCRETE, -1.52f, 0.0f, 0.30f, 0.12f, 1.0f, 1.6f));

        // ----- two lifting lugs on the top of the tail body -----
        p.add(new Part(Material.BLACK_CONCRETE, 0.35f, 1.45f, -2.40f, 0.14f, 0.55f, 0.14f));
        p.add(new Part(Material.BLACK_CONCRETE, -0.35f, 1.45f, -2.40f, 0.14f, 0.55f, 0.14f));

        // ----- boxed tail fin assembly (the "crate"): a square tube around the -Z axis -----
        p.add(new Part(Material.GRAY_CONCRETE, 1.25f, 0.0f, -3.85f, 0.16f, 2.55f, 2.20f)); // +X wall
        p.add(new Part(Material.GRAY_CONCRETE, -1.25f, 0.0f, -3.85f, 0.16f, 2.55f, 2.20f)); // -X wall
        p.add(new Part(Material.GRAY_CONCRETE, 0.0f, 1.25f, -3.85f, 2.55f, 0.16f, 2.20f)); // +Y wall
        p.add(new Part(Material.GRAY_CONCRETE, 0.0f, -1.25f, -3.85f, 2.55f, 0.16f, 2.20f)); // -Y wall

        // ----- yellow struts on the four outer edges of the crate -----
        p.add(new Part(Material.YELLOW_CONCRETE, 1.25f, 1.25f, -3.85f, 0.22f, 0.22f, 2.35f));
        p.add(new Part(Material.YELLOW_CONCRETE, 1.25f, -1.25f, -3.85f, 0.22f, 0.22f, 2.35f));
        p.add(new Part(Material.YELLOW_CONCRETE, -1.25f, 1.25f, -3.85f, 0.22f, 0.22f, 2.35f));
        p.add(new Part(Material.YELLOW_CONCRETE, -1.25f, -1.25f, -3.85f, 0.22f, 0.22f, 2.35f));

        // ----- short booms connecting the body to the crate -----
        p.add(new Part(Material.GRAY_CONCRETE, 0.62f, 0.62f, -3.45f, 0.16f, 0.16f, 1.0f));
        p.add(new Part(Material.GRAY_CONCRETE, 0.62f, -0.62f, -3.45f, 0.16f, 0.16f, 1.0f));
        p.add(new Part(Material.GRAY_CONCRETE, -0.62f, 0.62f, -3.45f, 0.16f, 0.16f, 1.0f));
        p.add(new Part(Material.GRAY_CONCRETE, -0.62f, -0.62f, -3.45f, 0.16f, 0.16f, 1.0f));

        // ----- rear hub cap closing the crate -----
        p.add(new Part(Material.GRAY_CONCRETE, 0.0f, 0.0f, -4.55f, 0.5f, 0.5f, 0.35f));

        return p;
    }

    private final World world;
    private final float heading;
    private final List<BlockDisplay> displays = new ArrayList<>(PARTS.size());
    private Location center;
    private Quaternionf orientation;

    public NukeBombModel(Location center, float heading) {
        this.world = center.getWorld();
        this.center = center.clone();
        this.heading = heading;
        this.orientation = new Quaternionf().rotateY(heading); // horizontal, aligned to flight
        spawnDisplays();
    }

    private void spawnDisplays() {
        for (Part part : PARTS) {
            BlockData blockData = part.material().createBlockData();
            Transformation t = transformFor(part, orientation);
            BlockDisplay display = world.spawn(center, BlockDisplay.class, d -> {
                d.setBlock(blockData);
                d.setTransformation(t);
                d.setTeleportDuration(2);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(2);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setShadowRadius(0.0f);
                d.setShadowStrength(0.0f);
                d.setPersistent(false);
                d.setViewRange(5.0f);
            });
            displays.add(display);
        }
    }

    /** Builds a part's transform under orientation {@code q} (rotates anchor + block together). */
    private Transformation transformFor(Part part, Quaternionf q) {
        Vector3f anchor = new Vector3f(
                part.lx() - part.sx() / 2.0f,
                part.ly() - part.sy() / 2.0f,
                part.lz() - part.sz() / 2.0f);
        q.transform(anchor); // rotate the anchor offset into entity space
        Vector3f scale = new Vector3f(part.sx(), part.sy(), part.sz());
        return new Transformation(anchor, new Quaternionf(q), scale, new Quaternionf());
    }

    /**
     * Sets the nose-down pitch, {@code theta} in radians: 0 = level (as authored),
     * {@code PI/2} = straight down. Re-applies every part's transform (interpolated).
     */
    public void setPitch(double theta) {
        this.orientation = new Quaternionf().rotateY(heading).rotateX((float) theta);
        for (int i = 0; i < displays.size(); i++) {
            BlockDisplay d = displays.get(i);
            if (d.isValid()) {
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(2);
                d.setTransformation(transformFor(PARTS.get(i), orientation));
            }
        }
    }

    /** Rigidly moves the whole bomb to a new centre (interpolated by teleportDuration). */
    public void moveTo(Location newCenter) {
        this.center = newCenter.clone();
        for (BlockDisplay display : displays) {
            if (display.isValid()) {
                display.teleport(newCenter);
            }
        }
    }

    /** World position of the nose tip under the current position + orientation. */
    public Location getNoseTip() {
        Vector3f tip = new Vector3f(0.0f, 0.0f, (float) NOSE_Z);
        orientation.transform(tip);
        return center.clone().add(tip.x, tip.y, tip.z);
    }

    public void remove() {
        for (BlockDisplay display : displays) {
            if (display.isValid()) {
                display.remove();
            }
        }
        displays.clear();
    }

    public boolean isValid() {
        return !displays.isEmpty() && displays.get(0).isValid();
    }

    public Location getCenter() {
        return center.clone();
    }
}

