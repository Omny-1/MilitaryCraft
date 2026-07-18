package me.bibo.militarycraft.weapons.artillery;

import me.bibo.militarycraft.core.key.EntityTag;
import me.bibo.militarycraft.core.key.Keys;
import me.bibo.militarycraft.core.key.Pdc;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns the exact 15-part stationary model and visual flying shells. */
final class ArtilleryModelManager {

    static final int MODEL_PARTS = 15;
    static final String TAG_MODEL = "svoart_model";
    static final String TAG_SHELL = "svoart_shell";
    private static final float TILT = (float) Math.toRadians(60.0);
    private static final float BARREL_LENGTH = 4.6f;

    private final ArtilleryManager manager;
    private final Map<UUID, List<BlockDisplay>> models = new HashMap<>();

    ArtilleryModelManager(ArtilleryManager manager) {
        this.manager = manager;
    }

    void start() {
        sweepDisplays(true);
        for (Artillery artillery : manager.all()) {
            spawnSafely(artillery);
        }
    }

    void shutdown() {
        for (List<BlockDisplay> displays : models.values()) {
            remove(displays);
        }
        models.clear();
        sweepDisplays(true);
    }

    void spawnChunk(UUID worldId, int chunkX, int chunkZ) {
        for (Artillery artillery : manager.inChunk(worldId, chunkX, chunkZ)) {
            spawn(artillery);
        }
    }

    void despawnChunk(UUID worldId, int chunkX, int chunkZ) {
        for (Artillery artillery : manager.inChunk(worldId, chunkX, chunkZ)) {
            despawn(artillery);
        }
    }

    void refresh(Artillery artillery) {
        despawn(artillery);
        spawn(artillery);
    }

    void refreshAll() {
        for (List<BlockDisplay> displays : models.values()) {
            remove(displays);
        }
        models.clear();
        sweepDisplays(false);
        for (Artillery artillery : manager.all()) {
            spawnSafely(artillery);
        }
    }

    void spawn(Artillery artillery) {
        World world = artillery.world();
        if (artillery.wrecked() || world == null
                || !world.isChunkLoaded(artillery.x() >> 4, artillery.z() >> 4)) {
            return;
        }
        Material carrier = world.getBlockAt(artillery.x(), artillery.y(), artillery.z()).getType();
        if (carrier.isAir()) {
            return;
        }
        if (carrier != Material.BARRIER) {
            world.getBlockAt(artillery.x(), artillery.y(), artillery.z()).setType(Material.BARRIER, false);
        }
        List<BlockDisplay> existing = models.get(artillery.id());
        if (existing != null) {
            existing.removeIf(display -> display == null || !display.isValid());
            if (existing.size() == MODEL_PARTS) {
                return;
            }
            remove(existing);
            models.remove(artillery.id());
        }

        ArtillerySettings settings = manager.settings();
        Location anchor = new Location(world, artillery.x(), artillery.y(), artillery.z());
        BlockData camo = settings.modelCamo.createBlockData();
        BlockData barrel = settings.modelBarrel.createBlockData();
        BlockData metal = settings.modelMetal.createBlockData();
        BlockData wheel = settings.modelWheel.createBlockData();

        Vector3f forward = directionFromYaw(artillery.yaw());
        Vector3f right = new Vector3f(-forward.z, 0.0f, forward.x);
        float cos = (float) Math.cos(TILT);
        float sin = (float) Math.sin(TILT);
        Vector3f barrelDirection = new Vector3f(forward.x * cos, sin, forward.z * cos).normalize();
        Quaternionf barrelRotation = rotateZTo(barrelDirection);
        Quaternionf yawRotation = rotateZTo(forward);

        List<BlockDisplay> parts = new ArrayList<>(MODEL_PARTS);
        try {
            Vector3f axleCenter = new Vector3f(0.5f, 0.6f, 0.5f);
            Vector3f wheelOffset = new Vector3f(right).mul(0.95f);
            wheel(parts, artillery, anchor, new Vector3f(axleCenter).add(wheelOffset), right, wheel, metal);
            wheel(parts, artillery, anchor, new Vector3f(axleCenter).sub(wheelOffset), right, wheel, metal);
            parts.add(part(artillery, anchor, metal, axleCenter,
                    new Vector3f(0.28f, 0.28f, 2.0f), rotateZTo(right)));

            Vector3f bodyCenter = new Vector3f(0.5f, 1.15f, 0.5f)
                    .add(new Vector3f(forward).mul(0.1f));
            parts.add(part(artillery, anchor, camo, bodyCenter,
                    new Vector3f(1.15f, 0.95f, 1.5f), yawRotation));
            Vector3f breechCenter = new Vector3f(0.5f, 1.48f, 0.5f)
                    .add(new Vector3f(forward).mul(0.62f));
            parts.add(part(artillery, anchor, metal, breechCenter,
                    new Vector3f(0.8f, 0.9f, 0.95f), yawRotation));

            Vector3f pivot = new Vector3f(0.5f, 1.55f, 0.5f)
                    .add(new Vector3f(forward).mul(0.45f));
            Vector3f barrelCenter = new Vector3f(pivot)
                    .add(new Vector3f(barrelDirection).mul(BARREL_LENGTH * 0.5f));
            parts.add(part(artillery, anchor, barrel, barrelCenter,
                    new Vector3f(0.42f, 0.42f, BARREL_LENGTH), barrelRotation));
            Vector3f muzzleCenter = new Vector3f(pivot)
                    .add(new Vector3f(barrelDirection).mul(BARREL_LENGTH - 0.35f));
            parts.add(part(artillery, anchor, metal, muzzleCenter,
                    new Vector3f(0.62f, 0.62f, 0.7f), barrelRotation));

            trail(parts, artillery, anchor, forward, right, 1, camo, metal);
            trail(parts, artillery, anchor, forward, right, -1, camo, metal);
            if (parts.size() != MODEL_PARTS) {
                remove(parts);
                throw new IllegalStateException("Artillery model must contain exactly " + MODEL_PARTS + " parts");
            }
            models.put(artillery.id(), parts);
        } catch (RuntimeException ex) {
            remove(parts);
            throw ex;
        }
    }

    void despawn(Artillery artillery) {
        remove(models.remove(artillery.id()));
    }

    private void spawnSafely(Artillery artillery) {
        try {
            spawn(artillery);
        } catch (RuntimeException ex) {
            manager.core().logger().warning("Could not spawn artillery model " + artillery.id()
                    + ": " + ex.getMessage());
        }
    }

    Location muzzleTip(Artillery artillery) {
        World world = artillery.world();
        if (world == null) {
            return null;
        }
        Vector3f forward = directionFromYaw(artillery.yaw());
        float cos = (float) Math.cos(TILT);
        float sin = (float) Math.sin(TILT);
        Vector3f direction = new Vector3f(forward.x * cos, sin, forward.z * cos).normalize();
        Vector3f pivot = new Vector3f(0.5f, 1.55f, 0.5f)
                .add(new Vector3f(forward).mul(0.45f));
        Vector3f tip = new Vector3f(pivot).add(new Vector3f(direction).mul(BARREL_LENGTH));
        return new Location(world, artillery.x() + tip.x, artillery.y() + tip.y, artillery.z() + tip.z);
    }

    BlockDisplay spawnShell(Location at) {
        ArtillerySettings settings = manager.settings();
        return at.getWorld().spawn(at, BlockDisplay.class, display -> {
            display.setBlock(settings.shellMaterial.createBlockData());
            float size = 0.55f;
            display.setTransformation(new Transformation(
                    new Vector3f(-size / 2.0f, -size / 2.0f, -size / 2.0f), new Quaternionf(),
                    new Vector3f(size, size, size), new Quaternionf()));
            display.setPersistent(false);
            display.setViewRange(8.0f);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setGlowing(settings.shellGlow);
            display.addScoreboardTag(TAG_SHELL);
            EntityTag.tag(display, "artillery");
            Pdc.setString(display.getPersistentDataContainer(), Keys.of("artillery", "role"), "shell");
        });
    }

    private BlockDisplay part(Artillery artillery, Location anchor, BlockData data,
                              Vector3f center, Vector3f scale, Quaternionf rotation) {
        Vector3f half = new Vector3f(scale).mul(0.5f);
        Vector3f translation = new Vector3f(center)
                .sub(new Quaternionf(rotation).transform(half));
        return anchor.getWorld().spawn(anchor, BlockDisplay.class, display -> {
            display.setBlock(data);
            display.setTransformation(new Transformation(
                    translation, new Quaternionf(rotation), new Vector3f(scale), new Quaternionf()));
            display.setPersistent(false);
            display.setViewRange(8.0f);
            display.setBrightness(new Display.Brightness(15, 15));
            display.addScoreboardTag(TAG_MODEL);
            EntityTag.tag(display, "artillery");
            Pdc.setString(display.getPersistentDataContainer(), Keys.of("artillery", "id"), artillery.id().toString());
            Pdc.setString(display.getPersistentDataContainer(), Keys.of("artillery", "role"), "model");
        });
    }

    private void wheel(List<BlockDisplay> parts, Artillery artillery, Location anchor,
                       Vector3f center, Vector3f axle, BlockData tyre, BlockData hub) {
        Quaternionf base = rotateZTo(axle);
        Quaternionf diagonal = new Quaternionf(base).rotateZ((float) Math.toRadians(45.0));
        Vector3f tyreScale = new Vector3f(1.25f, 1.25f, 0.42f);
        parts.add(part(artillery, anchor, tyre, center, tyreScale, base));
        parts.add(part(artillery, anchor, tyre, center, tyreScale, diagonal));
        parts.add(part(artillery, anchor, hub, center,
                new Vector3f(0.5f, 0.5f, 0.5f), base));
    }

    private void trail(List<BlockDisplay> parts, Artillery artillery, Location anchor,
                       Vector3f forward, Vector3f right, int side, BlockData camo, BlockData metal) {
        Vector3f direction = new Vector3f(forward).mul(-1.0f).add(0.0f, -0.35f, 0.0f)
                .add(new Vector3f(right).mul(0.45f * side)).normalize();
        float length = 3.4f;
        Vector3f hinge = new Vector3f(0.5f, 0.95f, 0.5f)
                .add(new Vector3f(forward).mul(-0.3f));
        Vector3f center = new Vector3f(hinge)
                .add(new Vector3f(direction).mul(length * 0.5f));
        parts.add(part(artillery, anchor, camo, center,
                new Vector3f(0.32f, 0.32f, length), rotateZTo(direction)));
        Vector3f end = new Vector3f(hinge).add(new Vector3f(direction).mul(length));
        parts.add(part(artillery, anchor, metal, end,
                new Vector3f(0.5f, 0.5f, 0.5f), rotateZTo(direction)));
    }

    private Quaternionf rotateZTo(Vector3f target) {
        return new Quaternionf().rotationTo(new Vector3f(0.0f, 0.0f, 1.0f), new Vector3f(target));
    }

    private Vector3f directionFromYaw(float yawDegrees) {
        double radians = Math.toRadians(yawDegrees);
        return new Vector3f((float) -Math.sin(radians), 0.0f, (float) Math.cos(radians)).normalize();
    }

    private void sweepDisplays(boolean includeShells) {
        for (World world : manager.core().plugin().getServer().getWorlds()) {
            for (BlockDisplay display : world.getEntitiesByClass(BlockDisplay.class)) {
                boolean legacyModel = display.getScoreboardTags().contains(TAG_MODEL);
                boolean legacyShell = display.getScoreboardTags().contains(TAG_SHELL);
                String role = Pdc.getString(display.getPersistentDataContainer(),
                        Keys.of("artillery", "role"), "");
                boolean unified = "artillery".equals(EntityTag.moduleOf(display));
                if ((legacyModel || (unified && "model".equals(role)))
                        || (includeShells && (legacyShell || (unified && "shell".equals(role))))) {
                    display.remove();
                }
            }
        }
    }

    private void remove(List<BlockDisplay> displays) {
        if (displays == null) {
            return;
        }
        for (BlockDisplay display : displays) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
    }
}
