package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.mojang.blaze3d.pipeline.RenderTarget;
import mafisher.quasar.api.client.screen.RegisterScreenEffectOperationsEvent;
import mafisher.quasar.api.client.render.RegisterVisualEffectRenderersEvent;
import mafisher.quasar.api.client.screen.ScreenEffectOperation;
import mafisher.quasar.api.client.render.VisualEffectRenderer;
import mafisher.quasar.api.compat.CompatRegistry;
import mafisher.quasar.api.compat.CompatTestReset;
import mafisher.quasar.api.compat.RegisterCompatProvidersEvent;
import mafisher.quasar.api.compat.SceneDepthProvider;
import mafisher.quasar.api.compat.SceneDepthSource;
import mafisher.quasar.testing.GlobalStateTest;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class ClientEffectRegistriesTest {
    private static final ResourceLocation EFFECT_ID = ResourceLocation.fromNamespaceAndPath("test", "setup_effect");
    private static final ScreenEffectOperation OPERATION = ScreenEffectOperation.create(
        ResourceLocation.fromNamespaceAndPath("test", "setup_operation")
    );
    private static final ResourceLocation PROVIDER_ID = ResourceLocation.fromNamespaceAndPath("test", "setup_depth");

    private final RegistrationListener listener = new RegistrationListener();

    @BeforeEach
    void setUp() {
        CompatTestReset.reset();
        NeoForge.EVENT_BUS.register(this.listener);
    }

    @AfterEach
    void tearDown() {
        NeoForge.EVENT_BUS.unregister(this.listener);
        CompatTestReset.reset();
    }

    @Test
    void initializesRegistriesAndFreezesThemOnce() {
        ClientEffectRegistries registries = ClientEffectRegistries.initialize();

        assertEquals(1, this.listener.operationEvents);
        assertEquals(1, this.listener.rendererEvents);
        assertEquals(1, this.listener.compatEvents);
        assertTrue(registries.operations().find(OPERATION).isPresent());
        assertTrue(registries.renderers().find(EFFECT_ID).isPresent());
        assertTrue(CompatRegistry.environment(256.0).activeProviders().contains(PROVIDER_ID));
        assertThrows(IllegalStateException.class,
            () -> registries.operations().register(OPERATION, context -> { }));
        assertThrows(IllegalStateException.class,
            () -> registries.renderers().register(EFFECT_ID, new VisualEffectRenderer() { }));
        assertThrows(IllegalStateException.class,
            () -> CompatRegistry.registerSceneDepthProvider(depthProvider()));
    }

    private static SceneDepthProvider depthProvider() {
        RenderTarget target = mock(RenderTarget.class);
        return new SceneDepthProvider() {
            @Override
            public ResourceLocation id() {
                return PROVIDER_ID;
            }

            @Override
            public int priority() {
                return 1;
            }

            @Override
            public RenderTarget depthTarget() {
                return target;
            }

            @Override
            public SceneDepthSource source() {
                return SceneDepthSource.HORIZON_LOD;
            }
        };
    }

    private static final class RegistrationListener {
        private int operationEvents;
        private int rendererEvents;
        private int compatEvents;

        @SubscribeEvent
        public void onOperations(RegisterScreenEffectOperationsEvent event) {
            this.operationEvents++;
            event.register(OPERATION, context -> { });
        }

        @SubscribeEvent
        public void onRenderers(RegisterVisualEffectRenderersEvent event) {
            this.rendererEvents++;
            event.register(EFFECT_ID, new VisualEffectRenderer() { });
        }

        @SubscribeEvent
        public void onCompat(RegisterCompatProvidersEvent event) {
            this.compatEvents++;
            event.registerSceneDepthProvider(depthProvider());
        }
    }
}
