package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.client.effect.EffectRenderTypeOptions.Transparency;
import org.junit.jupiter.api.Test;

class EffectRenderTypeOptionsTest {
    @Test
    void presetsMatchEngineDefaults() {
        assertEquals(Transparency.TRANSLUCENT, EffectRenderTypeOptions.WORLD_DEFAULT.transparency());
        assertTrue(EffectRenderTypeOptions.WORLD_DEFAULT.depthTest());
        assertTrue(EffectRenderTypeOptions.WORLD_DEFAULT.writeColor());
        assertFalse(EffectRenderTypeOptions.WORLD_DEFAULT.writeDepth());
        assertTrue(EffectRenderTypeOptions.WORLD_DEFAULT.cull());
        assertTrue(EffectRenderTypeOptions.WORLD_DEFAULT.lightmap());
        assertTrue(EffectRenderTypeOptions.WORLD_DEFAULT.sort());

        assertEquals(Transparency.NONE, EffectRenderTypeOptions.WORLD_DEPTH_WRITING.transparency());
        assertTrue(EffectRenderTypeOptions.WORLD_DEPTH_WRITING.writeDepth());
        assertFalse(EffectRenderTypeOptions.WORLD_DEPTH_WRITING.cull());
        assertFalse(EffectRenderTypeOptions.WORLD_DEPTH_WRITING.sort());

        assertEquals(Transparency.TRANSLUCENT, EffectRenderTypeOptions.MASK_DEFAULT.transparency());
        assertFalse(EffectRenderTypeOptions.MASK_DEFAULT.lightmap());
    }

    @Test
    void rejectsNullTransparency() {
        assertThrows(NullPointerException.class, () -> new EffectRenderTypeOptions(
            null, true, true, false, true, false, true
        ));
    }

    @Test
    void comparesByValue() {
        assertEquals(
            new EffectRenderTypeOptions(Transparency.ADDITIVE, true, true, false, false, true, true),
            new EffectRenderTypeOptions(Transparency.ADDITIVE, true, true, false, false, true, true)
        );
    }
}
