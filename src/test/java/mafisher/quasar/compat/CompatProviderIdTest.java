package mafisher.quasar.compat;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CompatProviderIdTest {
    @BeforeEach
    void resetBefore() {
        CompatRegistry.clearForTesting();
    }

    @AfterEach
    void reset() {
        CompatRegistry.clearForTesting();
    }

    @Test
    void rejectsDuplicateIdsAcrossAllProviderKinds() {
        CompatRegistry.registerSceneDepthProvider(CompatTestProviders.depth(
            "shared", 1, mock(RenderTarget.class), SceneDepthSource.HORIZON_LOD));

        assertThrows(IllegalStateException.class, () -> CompatRegistry.registerPhysicsBridge(
            CompatTestProviders.physics("shared", true, new AtomicInteger())));
        assertThrows(IllegalStateException.class, () -> CompatRegistry.registerProtectionBridge(
            CompatTestProviders.protection("shared", true, new AtomicInteger())));
        assertThrows(IllegalStateException.class, () -> CompatRegistry.registerShaderPackProvider(
            CompatTestProviders.shaderPack("shared", 1, ShaderPackState.INACTIVE)));
        assertThrows(IllegalStateException.class, () -> CompatRegistry.registerHorizonProvider(
            CompatTestProviders.horizon("shared", 1024.0, 1024.0)));
    }

    @Test
    void rejectsProviderIdsAgainstRegisteredBridges() {
        CompatRegistry.registerPhysicsBridge(CompatTestProviders.physics(
            "shared", true, new AtomicInteger()));

        assertThrows(IllegalStateException.class, () -> CompatRegistry.registerProtectionBridge(
            CompatTestProviders.protection("shared", true, new AtomicInteger())));
        assertThrows(IllegalStateException.class, () -> CompatRegistry.registerSceneDepthProvider(
            CompatTestProviders.depth(
                "shared", 1, mock(RenderTarget.class), SceneDepthSource.HORIZON_LOD)));
        assertThrows(IllegalStateException.class, () -> CompatRegistry.registerShaderPackProvider(
            CompatTestProviders.shaderPack("shared", 1, ShaderPackState.INACTIVE)));
        assertThrows(IllegalStateException.class, () -> CompatRegistry.registerHorizonProvider(
            CompatTestProviders.horizon("shared", 1024.0, 1024.0)));
    }

    @Test
    void rejectsReservedVanillaIds() {
        assertThrows(IllegalStateException.class, () -> CompatRegistry.registerShaderPackProvider(
            new ShaderPackProvider() {
                @Override
                public ResourceLocation id() {
                    return PhysicsBridge.VANILLA_ID;
                }

                @Override
                public int priority() {
                    return 1;
                }

                @Override
                public ShaderPackState state() {
                    return ShaderPackState.INACTIVE;
                }
            }));
        assertThrows(IllegalStateException.class, () -> CompatRegistry.registerPhysicsBridge(
            new PhysicsBridge() {
                @Override
                public ResourceLocation id() {
                    return PhysicsBridge.VANILLA_ID;
                }

                @Override
                public boolean available() {
                    return true;
                }
            }));
    }
}
