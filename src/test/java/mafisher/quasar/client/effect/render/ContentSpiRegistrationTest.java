package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.client.RegisterVisualEffectRenderersEvent;
import mafisher.quasar.content.ContentClientEffects;
import mafisher.quasar.content.ContentEffectIds;
import org.junit.jupiter.api.Test;

/** Pins that content registers through the public SPI without engine changes. */
class ContentSpiRegistrationTest {
    @Test
    void registersEveryContentRendererThroughTheEvent() {
        VisualEffectRendererRegistry registry = new VisualEffectRendererRegistry();
        RegisterVisualEffectRenderersEvent event = new RegisterVisualEffectRenderersEvent(registry::register);

        ContentClientEffects.registerRenderers(event);

        assertTrue(registry.find(ContentEffectIds.RUNE_CIRCLE).isPresent());
        assertTrue(registry.find(ContentEffectIds.WAVE_DISTORTION).isPresent());
        assertTrue(registry.find(ContentEffectIds.SCENE_BLUR).isPresent());
        assertTrue(registry.find(ContentEffectIds.BLOOM).isPresent());
        assertTrue(registry.find(ContentEffectIds.BLACK_HOLE).isPresent());
        assertTrue(registry.find(ContentEffectIds.BLACK_HOLE_MESH).isPresent());
    }
}
