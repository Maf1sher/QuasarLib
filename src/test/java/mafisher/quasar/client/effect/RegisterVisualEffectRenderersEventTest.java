package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import mafisher.quasar.api.client.RegisterVisualEffectRenderersEvent;
import mafisher.quasar.api.client.VisualEffectRenderer;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class RegisterVisualEffectRenderersEventTest {
    private static final ResourceLocation TEST_ID = ResourceLocation.fromNamespaceAndPath("test", "event_effect");

    @Test
    void registersThroughEvent() {
        VisualEffectRendererRegistry registry = new VisualEffectRendererRegistry();
        RegisterVisualEffectRenderersEvent event = new RegisterVisualEffectRenderersEvent(registry::register);
        VisualEffectRenderer renderer = new VisualEffectRenderer() {
        };

        event.register(TEST_ID, renderer);

        assertSame(renderer, registry.find(TEST_ID).orElseThrow());
    }

    @Test
    void surfacesDuplicateRegistration() {
        VisualEffectRendererRegistry registry = new VisualEffectRendererRegistry();
        RegisterVisualEffectRenderersEvent event = new RegisterVisualEffectRenderersEvent(registry::register);
        event.register(TEST_ID, new VisualEffectRenderer() {
        });

        assertThrows(IllegalStateException.class, () -> event.register(TEST_ID, new VisualEffectRenderer() {
        }));
    }

    @Test
    void surfacesFrozenRegistry() {
        VisualEffectRendererRegistry registry = new VisualEffectRendererRegistry();
        registry.freeze();
        RegisterVisualEffectRenderersEvent event = new RegisterVisualEffectRenderersEvent(registry::register);

        assertThrows(IllegalStateException.class, () -> event.register(TEST_ID, new VisualEffectRenderer() {
        }));
    }
}
