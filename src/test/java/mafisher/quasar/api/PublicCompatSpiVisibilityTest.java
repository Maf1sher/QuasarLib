package mafisher.quasar.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.client.effect.EffectRenderContext;
import mafisher.quasar.client.effect.VisualEffectInstance;
import mafisher.quasar.compat.CompatRegistry;
import mafisher.quasar.compat.FogPolicy;
import mafisher.quasar.compat.HorizonProvider;
import mafisher.quasar.compat.PhysicsBridge;
import mafisher.quasar.compat.ProtectionBridge;
import mafisher.quasar.compat.RegisterCompatBridgesEvent;
import mafisher.quasar.compat.RegisterCompatProvidersEvent;
import mafisher.quasar.compat.RenderEnvironment;
import mafisher.quasar.compat.SceneDepthProvider;
import mafisher.quasar.compat.SceneDepthSource;
import mafisher.quasar.compat.ShaderPackProvider;
import mafisher.quasar.compat.ShaderPackState;
import mafisher.quasar.effect.block.BlockProtectionContext;
import mafisher.quasar.effect.network.StartVisualEffectPayload;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Compiles from outside the implementation package to pin the public compat surface. */
class PublicCompatSpiVisibilityTest {
    @Test
    void exposesProviderInterfacesAndEvents() {
        HorizonProvider horizon = new HorizonProvider() {
            @Override
            public ResourceLocation id() {
                return ResourceLocation.fromNamespaceAndPath("test", "horizon");
            }

            @Override
            public int priority() {
                return 1;
            }

            @Override
            public double horizonDistance() {
                return 1024.0;
            }

            @Override
            public double farPlane() {
                return 2048.0;
            }
        };
        ShaderPackProvider shaderPack = new ShaderPackProvider() {
            @Override
            public ResourceLocation id() {
                return ResourceLocation.fromNamespaceAndPath("test", "shaderpack");
            }

            @Override
            public int priority() {
                return 1;
            }

            @Override
            public ShaderPackState state() {
                return ShaderPackState.INACTIVE;
            }
        };
        ProtectionBridge protection = new ProtectionBridge() {
            @Override
            public ResourceLocation id() {
                return ResourceLocation.fromNamespaceAndPath("test", "claims");
            }

            @Override
            public boolean available() {
                return true;
            }

            @Override
            public boolean allows(BlockProtectionContext context) {
                return true;
            }
        };

        assertEquals(1024.0, horizon.horizonDistance());
        assertEquals(ShaderPackState.INACTIVE, shaderPack.state());
        assertTrue(protection.available());
        assertNotNull(SceneDepthProvider.class);
        assertNotNull(PhysicsBridge.class);
        assertNotNull(RegisterCompatProvidersEvent.class);
        assertNotNull(RegisterCompatBridgesEvent.class);
        assertNotNull(FogPolicy.RESPECT);
    }

    @Test
    void exposesFallbackEnvironmentAndRegistry() {
        RenderEnvironment environment = CompatRegistry.environment(256.0);

        assertEquals(SceneDepthSource.MAIN_TARGET, environment.depthSource());
        assertFalse(environment.hasHorizonProvider());
        assertFalse(CompatRegistry.physics().available());
        assertFalse(CompatRegistry.protection().available());
        assertTrue(environment.activeProviders().isEmpty());
        assertTrue(RenderEnvironment.VANILLA.screenCompositionAllowed());
    }

    @Test
    void exposesEnvironmentOnRenderContext() {
        StartVisualEffectPayload payload = new StartVisualEffectPayload(
            ResourceLocation.fromNamespaceAndPath("test", "effect"),
            UUID.randomUUID(),
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
            0.0,
            64.0,
            0.0,
            0.0F,
            0.0F,
            0.0F,
            1.0F,
            100,
            0L,
            1L
        );
        VisualEffectInstance instance = new VisualEffectInstance(payload);
        RenderEnvironment custom = new RenderEnvironment(
            4096.0, 8192.0, true, SceneDepthSource.HORIZON_LOD, ShaderPackState.ACTIVE,
            FogPolicy.RESPECT, false, false, List.of()
        );

        EffectRenderContext context = new EffectRenderContext(
            instance, payload.transform(), 0.0F, 0.0F, 0.0F
        ).withEnvironment(custom);

        assertEquals(custom, context.environment());
        assertEquals(RenderEnvironment.VANILLA,
            new EffectRenderContext(instance, payload.transform(), 0.0F, 0.0F, 0.0F).environment());
    }
}
