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
 * A large four-engine heavy bomber built from {@link BlockDisplay} entities.
 * <p>
 * Same rig convention as {@code Su57Model}: authored along local +Z (nose
 * forward), a single Y-heading rotation is baked into both each part's anchor
 * offset and its block orientation, and the whole thing is moved rigidly with
 * {@link #moveTo}. The fuselage is a fat tube whose half-width overlaps the wing
 * roots so the wings visibly join the body; the propeller discs flicker to fake
 * rotation, mirroring the fighter's afterburner flicker.
 */
public class BomberModel {

    /** One block of the model: local centre + scale, plus whether it is a spinning prop disc. */
    private record Part(Material material, float lx, float ly, float lz,
                        float sx, float sy, float sz, boolean prop) {
        Part(Material material, float lx, float ly, float lz, float sx, float sy, float sz) {
            this(material, lx, ly, lz, sx, sy, sz, false);
        }
    }

    private static final List<Part> PARTS = buildParts();

    private static List<Part> buildParts() {
        List<Part> p = new ArrayList<>();

        // ----- fuselage: a fat tube (half-width ~1.1 so wing roots overlap it) -----
        p.add(new Part(Material.LIGHT_GRAY_CONCRETE, 0.0f, 0.0f, 0.0f, 2.2f, 2.1f, 12.0f));
        p.add(new Part(Material.GRAY_CONCRETE, 0.0f, -1.05f, 0.0f, 2.0f, 0.6f, 10.8f));  // belly
        p.add(new Part(Material.WHITE_CONCRETE, 0.0f, 1.0f, 0.0f, 1.5f, 0.5f, 9.5f));     // spine
        p.add(new Part(Material.LIGHT_GRAY_CONCRETE, 0.0f, 0.0f, 6.6f, 1.7f, 1.7f, 1.6f)); // nose cone
        p.add(new Part(Material.WHITE_CONCRETE, 0.0f, 0.0f, 7.7f, 1.0f, 1.0f, 1.0f));       // nose tip
        p.add(new Part(Material.CYAN_STAINED_GLASS, 0.0f, 0.85f, 4.9f, 1.55f, 0.7f, 2.2f)); // cockpit
        p.add(new Part(Material.BLACK_CONCRETE, 0.0f, -1.32f, 0.0f, 1.4f, 0.2f, 3.2f));      // bomb bay

        // ----- wing root fairing: blends the fat belly into the wings -----
        p.add(new Part(Material.GRAY_CONCRETE, 0.0f, -0.35f, 0.4f, 3.0f, 1.0f, 4.2f));

        // ----- wings: inner edge tucked inside the fuselage (x ~0.4 < half-width 1.1) -----
        p.add(new Part(Material.GRAY_CONCRETE, 4.5f, 0.05f, 0.3f, 8.4f, 0.5f, 3.6f));
        p.add(new Part(Material.LIGHT_GRAY_CONCRETE, 8.7f, 0.06f, 0.3f, 1.2f, 0.4f, 2.6f)); // right tip
        p.add(new Part(Material.GRAY_CONCRETE, -4.5f, 0.05f, 0.3f, 8.4f, 0.5f, 3.6f));
        p.add(new Part(Material.LIGHT_GRAY_CONCRETE, -8.7f, 0.06f, 0.3f, 1.2f, 0.4f, 2.6f)); // left tip
        p.add(new Part(Material.RED_CONCRETE, 5.4f, 0.32f, 0.7f, 1.5f, 0.06f, 1.5f)); // roundel
        p.add(new Part(Material.RED_CONCRETE, -5.4f, 0.32f, 0.7f, 1.5f, 0.06f, 1.5f));

        // ----- tail -----
        p.add(new Part(Material.LIGHT_GRAY_CONCRETE, 0.0f, 2.3f, -5.3f, 0.5f, 3.0f, 2.4f)); // vertical fin
        p.add(new Part(Material.GRAY_CONCRETE, 0.0f, 3.7f, -5.3f, 0.55f, 0.5f, 1.6f));        // fin cap
        p.add(new Part(Material.RED_CONCRETE, 0.0f, 2.3f, -6.35f, 0.52f, 2.0f, 0.3f));        // rudder stripe
        p.add(new Part(Material.GRAY_CONCRETE, 2.4f, 0.45f, -5.4f, 4.2f, 0.4f, 1.9f));        // right stab
        p.add(new Part(Material.GRAY_CONCRETE, -2.4f, 0.45f, -5.4f, 4.2f, 0.4f, 1.9f));       // left stab

        // ----- engines (inner + outer, each side), nacelles hugging the wing underside -----
        addEngine(p, 3.2f, -0.55f, 1.35f, 3.2f);
        addEngine(p, 6.2f, -0.50f, 1.2f, 2.9f);
        addEngine(p, -3.2f, -0.55f, 1.35f, 3.2f);
        addEngine(p, -6.2f, -0.50f, 1.2f, 2.9f);

        return p;
    }

    /** Adds a single engine nacelle + cowl + spinning prop disc + hub at the given local X. */
    private static void addEngine(List<Part> p, float x, float y, float diameter, float length) {
        float front = 1.2f + length / 2.0f;   // nacelles sit forward under the wing
        p.add(new Part(Material.GRAY_CONCRETE, x, y, 1.2f, diameter, diameter, length));
        p.add(new Part(Material.BLACK_CONCRETE, x, y, front - 0.25f, diameter + 0.05f, diameter + 0.05f, 0.5f));
        p.add(new Part(Material.LIGHT_GRAY_CONCRETE, x, y, front + 0.15f,
                diameter + 1.9f, diameter + 1.9f, 0.12f, true)); // prop disc
        p.add(new Part(Material.BLACK_CONCRETE, x, y, front + 0.22f, 0.4f, 0.4f, 0.4f)); // hub
    }

    private final World world;
    private final List<BlockDisplay> displays = new ArrayList<>(PARTS.size());
    private final List<BlockDisplay> propDisplays = new ArrayList<>();
    private final float headingCos;
    private final float headingSin;
    private final Quaternionf rotation;
    private Location center;

    public BomberModel(Location center, float headingAngle) {
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
            // Rotate the local offset into world space by the heading (single matrix).
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
            if (part.prop()) {
                propDisplays.add(display);
            }
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

    /** Fakes propeller rotation by flickering the disc blocks between two greys. */
    public void spinProps(boolean bright) {
        BlockData data = (bright ? Material.WHITE_CONCRETE : Material.LIGHT_GRAY_CONCRETE).createBlockData();
        for (BlockDisplay d : propDisplays) {
            if (d.isValid()) {
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
        propDisplays.clear();
    }

    public boolean isValid() {
        return !displays.isEmpty() && displays.get(0).isValid();
    }

    public Location getCenter() {
        return center.clone();
    }
}

