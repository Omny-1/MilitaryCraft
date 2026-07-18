package me.bibo.militarycraft.weapons.airstrike.model;

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
 * A Su-57 fighter built from BlockDisplay entities. The model points along a
 * fixed heading and is moved as a rigid body via {@link #moveTo(Location)}.
 */
public class Su57Model {

    /** One block of the model: local position (centre) + scale, in model space. */
    private record Part(Material material, float lx, float ly, float lz, float sx, float sy, float sz) {
    }

    private static final List<Part> PARTS = buildParts();

    private static List<Part> buildParts() {
        List<Part> p = new ArrayList<>();
        // fuselage
        p.add(new Part(Material.IRON_BLOCK, 0.0f, 0.0f, 0.0f, 0.65f, 0.6f, 8.2f));
        p.add(new Part(Material.LIGHT_GRAY_CONCRETE, 0.0f, 0.28f, 0.5f, 0.42f, 0.12f, 3.2f));
        p.add(new Part(Material.GRAY_CONCRETE, 0.0f, -0.28f, 0.0f, 0.72f, 0.08f, 5.0f));
        // nose
        p.add(new Part(Material.GRAY_CONCRETE, 0.0f, 0.0f, 4.5f, 0.48f, 0.48f, 0.9f));
        p.add(new Part(Material.WHITE_CONCRETE, 0.0f, 0.0f, 5.2f, 0.3f, 0.3f, 0.8f));
        p.add(new Part(Material.QUARTZ_BLOCK, 0.0f, 0.0f, 5.8f, 0.18f, 0.18f, 0.5f));
        // canopy + pitot
        p.add(new Part(Material.CYAN_STAINED_GLASS, 0.0f, 0.4f, 2.0f, 0.4f, 0.24f, 0.95f));
        p.add(new Part(Material.DARK_OAK_LOG, 0.0f, 0.4f, 2.0f, 0.4f, 0.05f, 0.05f));
        // right wing
        p.add(new Part(Material.GRAY_CONCRETE, 2.2f, -0.04f, 0.8f, 3.5f, 0.12f, 4.2f));
        p.add(new Part(Material.LIGHT_GRAY_CONCRETE, 4.3f, -0.02f, 1.3f, 1.6f, 0.09f, 2.0f));
        p.add(new Part(Material.IRON_BLOCK, 3.0f, -0.03f, 2.5f, 2.5f, 0.08f, 0.4f));
        p.add(new Part(Material.IRON_BLOCK, 2.2f, -0.03f, -1.2f, 3.5f, 0.08f, 0.3f));
        // left wing
        p.add(new Part(Material.GRAY_CONCRETE, -2.2f, -0.04f, 0.8f, 3.5f, 0.12f, 4.2f));
        p.add(new Part(Material.LIGHT_GRAY_CONCRETE, -4.3f, -0.02f, 1.3f, 1.6f, 0.09f, 2.0f));
        p.add(new Part(Material.IRON_BLOCK, -3.0f, -0.03f, 2.5f, 2.5f, 0.08f, 0.4f));
        p.add(new Part(Material.IRON_BLOCK, -2.2f, -0.03f, -1.2f, 3.5f, 0.08f, 0.3f));
        // tail planes
        p.add(new Part(Material.GRAY_CONCRETE, 0.9f, -0.04f, -3.2f, 1.1f, 0.1f, 0.95f));
        p.add(new Part(Material.GRAY_CONCRETE, -0.9f, -0.04f, -3.2f, 1.1f, 0.1f, 0.95f));
        // twin vertical stabilisers
        p.add(new Part(Material.LIGHT_GRAY_CONCRETE, 0.45f, 0.62f, -2.6f, 0.13f, 1.45f, 1.4f));
        p.add(new Part(Material.LIGHT_GRAY_CONCRETE, -0.45f, 0.62f, -2.6f, 0.13f, 1.45f, 1.4f));
        p.add(new Part(Material.IRON_BLOCK, 0.45f, 1.35f, -2.4f, 0.12f, 0.28f, 0.55f));
        p.add(new Part(Material.IRON_BLOCK, -0.45f, 1.35f, -2.4f, 0.12f, 0.28f, 0.55f));
        // engine nacelles + afterburner nozzles
        p.add(new Part(Material.BLACK_CONCRETE, 0.4f, -0.08f, -2.5f, 0.58f, 0.54f, 2.0f));
        p.add(new Part(Material.BLACK_CONCRETE, -0.4f, -0.08f, -2.5f, 0.58f, 0.54f, 2.0f));
        p.add(new Part(Material.GRAY_CONCRETE, 0.4f, -0.08f, -1.5f, 0.58f, 0.54f, 0.18f));
        p.add(new Part(Material.GRAY_CONCRETE, -0.4f, -0.08f, -1.5f, 0.58f, 0.54f, 0.18f));
        p.add(new Part(Material.ORANGE_CONCRETE, 0.4f, -0.08f, -3.8f, 0.54f, 0.5f, 0.58f));
        p.add(new Part(Material.ORANGE_CONCRETE, -0.4f, -0.08f, -3.8f, 0.54f, 0.5f, 0.58f));
        p.add(new Part(Material.IRON_BLOCK, 0.4f, -0.08f, -4.2f, 0.58f, 0.54f, 0.22f));
        p.add(new Part(Material.IRON_BLOCK, -0.4f, -0.08f, -4.2f, 0.58f, 0.54f, 0.22f));
        // markings + belly fin
        p.add(new Part(Material.RED_WOOL, 2.3f, -0.01f, -0.2f, 0.55f, 0.14f, 0.55f));
        p.add(new Part(Material.RED_WOOL, -2.3f, -0.01f, -0.2f, 0.55f, 0.14f, 0.55f));
        p.add(new Part(Material.DARK_OAK_LOG, 0.0f, -0.32f, 0.8f, 0.36f, 0.04f, 1.8f));
        return p;
    }

    private final World world;
    private final List<BlockDisplay> displays = new ArrayList<>(PARTS.size());
    private final float headingCos;
    private final float headingSin;
    private final Quaternionf rotation;
    private Location center;

    public Su57Model(Location center, float headingAngle) {
        this.world = center.getWorld();
        this.center = center.clone();
        this.headingCos = (float) Math.cos(headingAngle);
        this.headingSin = (float) Math.sin(headingAngle);
        this.rotation = new Quaternionf().rotateY(headingAngle);
        spawnDisplays();
    }

    private void spawnDisplays() {
        for (Part part : PARTS) {
            // Part anchor (BlockDisplay translation is the block's min corner).
            float ax = part.lx() - part.sx() / 2.0f;
            float ay = part.ly() - part.sy() / 2.0f;
            float az = part.lz() - part.sz() / 2.0f;
            // Rotate the local offset into world space by the heading.
            float tx = ax * headingCos + az * headingSin;
            float tz = -ax * headingSin + az * headingCos;

            BlockData blockData = part.material().createBlockData();
            Vector3f translation = new Vector3f(tx, ay, tz);
            Vector3f scale = new Vector3f(part.sx(), part.sy(), part.sz());
            Quaternionf leftRot = new Quaternionf(rotation);
            Quaternionf rightRot = new Quaternionf();

            BlockDisplay display = world.spawn(center, BlockDisplay.class, d -> {
                d.setBlock(blockData);
                d.setTransformation(new Transformation(translation, leftRot, scale, rightRot));
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

    public void moveTo(Location newCenter) {
        this.center = newCenter.clone();
        for (BlockDisplay display : displays) {
            if (display.isValid()) {
                display.teleport(newCenter);
            }
        }
    }

    public void flickerAfterburners(boolean bright) {
        Material nozzleMat = bright ? Material.YELLOW_CONCRETE : Material.ORANGE_CONCRETE;
        BlockData data = nozzleMat.createBlockData();
        for (BlockDisplay d : displays) {
            if (!d.isValid()) {
                continue;
            }
            Material current = d.getBlock().getMaterial();
            if (current == Material.ORANGE_CONCRETE || current == Material.YELLOW_CONCRETE) {
                d.setBlock(data);
            }
        }
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
