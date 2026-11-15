package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import mafisher.quasar.api.client.render.VisualEffectInstance;
import mafisher.quasar.api.client.render.VisualEffectRenderer;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
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

    @Test
    void resourceReloadKeepsActiveInstances() throws ReflectiveOperationException {
        VisualEffectRendererRegistry registry = new VisualEffectRendererRegistry();
        CountingRenderer renderer = new CountingRenderer();
        registry.register(ResourceLocation.fromNamespaceAndPath("test", "reload_effect"), renderer);
        ClientVisualEffectManager manager = new ClientVisualEffectManager(
            registry, mock(ResourceManager.class),
            new ClientDepthOcclusion(new NoopProbeBackend())
        );
        StartVisualEffectPayload payload = new StartVisualEffectPayload(
            ResourceLocation.fromNamespaceAndPath("test", "reload_effect"),
            UUID.randomUUID(),
            ResourceLocation.withDefaultNamespace("overworld"),
            0.0, 64.0, 0.0, 0.0F, 0.0F, 0.0F, 1.0F, 1_200, 1L, 2L
        );
        Field field = ClientVisualEffectManager.class.getDeclaredField("activeEffects");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, VisualEffectInstance> active = (Map<UUID, VisualEffectInstance>) field.get(manager);
        active.put(payload.instanceId(), new VisualEffectInstance(payload));

        manager.onResourceReload(mock(ResourceManager.class));

        assertEquals(1, active.size());
        assertEquals(2, renderer.reloads);
    }

    private static final class CountingRenderer implements VisualEffectRenderer {
        private int reloads;

        @Override
        public void onResourceReload(ResourceManager resourceManager) {
            this.reloads++;
        }
    }

    private static final class NoopProbeBackend implements DepthProbeBackend {
        @Override
        public boolean supported() {
            return false;
        }

        @Override
        public int issue(DepthProbeRequest request) {
            return 0;
        }

        @Override
        public boolean isResultAvailable(int query) {
            return false;
        }

        @Override
        public boolean anySamplesPassed(int query) {
            return true;
        }

        @Override
        public void deleteQuery(int query) {
        }

        @Override
        public void reset() {
        }
    }
}
