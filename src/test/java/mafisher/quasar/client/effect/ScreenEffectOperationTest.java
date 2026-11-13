package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import mafisher.quasar.api.client.screen.ScreenEffectOperation;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ScreenEffectOperationTest {
    private static final ResourceLocation CUSTOM_ID = ResourceLocation.fromNamespaceAndPath(
        "test", "custom_operation"
    );

    @Test
    void exposesBuiltInIds() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_lib", "diagnostic_mask_preview"),
            ScreenEffectOperation.DIAGNOSTIC_MASK_PREVIEW.id());
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_lib", "wave_distortion"),
            ScreenEffectOperation.MASK_SCOPED_WAVE_DISTORTION.id());
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_lib", "scene_blur"),
            ScreenEffectOperation.MASK_SCOPED_SCENE_BLUR.id());
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_lib", "bloom"),
            ScreenEffectOperation.MASK_SCOPED_BLOOM.id());
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_lib", "black_hole"),
            ScreenEffectOperation.MASK_SCOPED_BLACK_HOLE.id());
    }

    @Test
    void createsEqualHandlesForTheSameId() {
        ScreenEffectOperation first = ScreenEffectOperation.create(CUSTOM_ID);
        ScreenEffectOperation second = ScreenEffectOperation.create(CUSTOM_ID);

        assertEquals(CUSTOM_ID, first.id());
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void rejectsNullId() {
        assertThrows(NullPointerException.class, () -> ScreenEffectOperation.create(null));
    }

    @Test
    void builtInOperationsAreDistinct() {
        assertEquals(5, Set.of(
            ScreenEffectOperation.DIAGNOSTIC_MASK_PREVIEW,
            ScreenEffectOperation.MASK_SCOPED_WAVE_DISTORTION,
            ScreenEffectOperation.MASK_SCOPED_SCENE_BLUR,
            ScreenEffectOperation.MASK_SCOPED_BLOOM,
            ScreenEffectOperation.MASK_SCOPED_BLACK_HOLE
        ).size());
    }
}
