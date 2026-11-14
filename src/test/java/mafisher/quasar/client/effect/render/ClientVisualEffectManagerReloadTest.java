package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import mafisher.quasar.api.client.render.VisualEffectRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

class ClientVisualEffectManagerReloadTest {
    @Test
    void initializesRendererResourcesOnConstruction() {
        VisualEffectRendererRegistry registry = new VisualEffectRendererRegistry();
        CountingRenderer renderer = new CountingRenderer();
        registry.register(ResourceLocation.fromNamespaceAndPath("test", "reload_effect"), renderer);

        new ClientVisualEffectManager(registry, mock(ResourceManager.class));

        assertEquals(1, renderer.reloads);
    }

    private static final class CountingRenderer implements VisualEffectRenderer {
        private int reloads;

        @Override
        public void onResourceReload(ResourceManager resourceManager) {
            this.reloads++;
        }
    }
}
