package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.content.ContentClientEffects;
import mafisher.quasar.content.ContentEffectIds;
import org.junit.jupiter.api.Test;

/** Pins that content registers through the public SPI without engine changes. */
class ContentSpiRegistrationTest {
    @Test
    void registersContentRendererThroughTheEvent() {
        VisualEffectRendererRegistry registry = new VisualEffectRendererRegistry();
        RegisterVisualEffectRenderersEvent event = new RegisterVisualEffectRenderersEvent(registry);

        ContentClientEffects.registerRenderers(event);

        assertTrue(registry.find(ContentEffectIds.RUNE_CIRCLE).isPresent());
    }
}
