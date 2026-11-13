package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.client.render.VisualEffectRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

class VisualEffectRendererRegistryTest {
    private static final ResourceLocation TEST_ID = ResourceLocation.fromNamespaceAndPath("test", "effect");
    private static final ResourceLocation OTHER_ID = ResourceLocation.fromNamespaceAndPath("test", "other_effect");

    @Test
    void registersAndFindsRenderer() {
        VisualEffectRendererRegistry registry = new VisualEffectRendererRegistry();
        CountingRenderer renderer = new CountingRenderer();

        registry.register(TEST_ID, renderer);

        assertSame(renderer, registry.find(TEST_ID).orElseThrow());
        assertTrue(registry.find(OTHER_ID).isEmpty());
    }

    @Test
    void startsWithoutEngineOwnedRenderers() {
        VisualEffectRendererRegistry registry = new VisualEffectRendererRegistry();

        assertTrue(registry.find(ResourceLocation.fromNamespaceAndPath("quasar_lib", "debug_rune_circle"))
            .isEmpty());
        assertTrue(registry.find(ResourceLocation.fromNamespaceAndPath("quasar_lib", "debug_black_hole"))
            .isEmpty());
    }

    @Test
    void rejectsDuplicateIds() {
        VisualEffectRendererRegistry registry = new VisualEffectRendererRegistry();
        registry.register(TEST_ID, new CountingRenderer());

        assertThrows(IllegalStateException.class, () -> registry.register(TEST_ID, new CountingRenderer()));
    }

    @Test
    void rejectsRegistrationAfterFreeze() {
        VisualEffectRendererRegistry registry = new VisualEffectRendererRegistry();
        registry.freeze();

        assertThrows(IllegalStateException.class, () -> registry.register(TEST_ID, new CountingRenderer()));
    }

    @Test
    void freezePreservesLookupAndIsIdempotent() {
        VisualEffectRendererRegistry registry = new VisualEffectRendererRegistry();
        CountingRenderer renderer = new CountingRenderer();
        registry.register(TEST_ID, renderer);

        registry.freeze();
        registry.freeze();

        assertSame(renderer, registry.find(TEST_ID).orElseThrow());
        assertThrows(IllegalStateException.class, () -> registry.register(OTHER_ID, new CountingRenderer()));
    }

    @Test
    void resourceReloadReachesEveryRenderer() {
        VisualEffectRendererRegistry registry = new VisualEffectRendererRegistry();
        CountingRenderer first = new CountingRenderer();
        CountingRenderer second = new CountingRenderer();
        registry.register(TEST_ID, first);
        registry.register(OTHER_ID, second);

        registry.onResourceReload(null);

        assertEquals(1, first.reloads);
        assertEquals(1, second.reloads);
    }

    private static final class CountingRenderer implements VisualEffectRenderer {
        private int reloads;

        @Override
        public void onResourceReload(ResourceManager resourceManager) {
            this.reloads++;
        }
    }
}
