package me.bibo.militarycraft.core.placeable;

import me.bibo.militarycraft.core.model.DisplayConfig;
import me.bibo.militarycraft.core.model.Part;
import org.bukkit.Material;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlaceableModelTest {

    @Test
    void rejectsDuplicateStableIdsWithinEachRole() {
        PlaceableModel.PartSpec body = part("body");
        assertThrows(IllegalArgumentException.class, () -> new PlaceableModel(
                List.of(body, part("body")), List.of(), 2.0));

        PlaceableModel.HitboxSpec box = hitbox("body");
        assertThrows(IllegalArgumentException.class, () -> new PlaceableModel(
                List.of(), List.of(box, hitbox("body")), 2.0));
    }

    @Test
    void permitsSameStableIdAcrossDistinctRoles() {
        PlaceableModel model = new PlaceableModel(
                List.of(part("body")), List.of(hitbox("body")), 2.0);
        assertEquals("body", model.parts().getFirst().id());
        assertEquals("body", model.hitboxes().getFirst().id());
    }

    @Test
    void rejectsNonFiniteOrDegenerateGeometry() {
        assertThrows(IllegalArgumentException.class, () -> new PlaceableModel.PartSpec("bad",
                Part.block(0, new Vector3f(Float.NaN, 0.0f, 0.0f),
                        new Vector3f(1.0f), Material.STONE)));
        assertThrows(IllegalArgumentException.class, () -> new PlaceableModel.PartSpec("bad",
                Part.block(0, new Vector3f(), new Vector3f(1.0f, 0.0f, 1.0f), Material.STONE)));
        assertThrows(IllegalArgumentException.class,
                () -> new PlaceableModel.HitboxSpec("bad", new Vector3f(), 65.0f, 1.0f));
        assertThrows(IllegalArgumentException.class,
                () -> new PlaceableModel(List.of(), List.of(), Double.POSITIVE_INFINITY));
    }

    @Test
    void validatesPaperDisplayBoundsWithoutAServer() {
        assertThrows(IllegalArgumentException.class, () -> new PlaceableModel(
                List.of(), List.of(), 1.0, new DisplayConfig(4.0f, 60, 2)));
        assertThrows(IllegalArgumentException.class, () -> new PlaceableModel(
                List.of(), List.of(), 1.0, new DisplayConfig(Float.NaN, 2, 2)));
    }

    @Test
    void hitboxOffsetIsDefensivelyCopied() {
        Vector3f input = new Vector3f(1.0f, 2.0f, 3.0f);
        PlaceableModel.HitboxSpec spec = new PlaceableModel.HitboxSpec("body", input, 2.0f, 2.0f);
        input.set(9.0f);
        Vector3f returned = spec.offset();
        returned.set(7.0f);
        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), spec.offset());
    }

    private static PlaceableModel.PartSpec part(String id) {
        return new PlaceableModel.PartSpec(id,
                Part.block(0, new Vector3f(), new Vector3f(1.0f), Material.STONE));
    }

    private static PlaceableModel.HitboxSpec hitbox(String id) {
        return new PlaceableModel.HitboxSpec(id, new Vector3f(), 2.0f, 2.0f);
    }
}
