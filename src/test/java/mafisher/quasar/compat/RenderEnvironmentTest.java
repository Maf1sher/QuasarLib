package mafisher.quasar.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class RenderEnvironmentTest {
    @Test
    void exposesVanillaFallback() {
        assertEquals(SceneDepthSource.MAIN_TARGET, RenderEnvironment.VANILLA.depthSource());
        assertEquals(ShaderPackState.INACTIVE, RenderEnvironment.VANILLA.shaderPackState());
        assertFalse(RenderEnvironment.VANILLA.hasHorizonProvider());
        assertFalse(RenderEnvironment.VANILLA.hasDepthProvider());
        assertTrue(RenderEnvironment.VANILLA.activeProviders().isEmpty());
        assertTrue(RenderEnvironment.VANILLA.screenCompositionAllowed());
    }

    @Test
    void rejectsInvalidDistances() {
        assertThrows(IllegalArgumentException.class, () -> new RenderEnvironment(
            0.0, 256.0, false, SceneDepthSource.MAIN_TARGET, ShaderPackState.INACTIVE,
            FogPolicy.RESPECT, false, false, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RenderEnvironment(
            Double.NaN, 256.0, false, SceneDepthSource.MAIN_TARGET, ShaderPackState.INACTIVE,
            FogPolicy.RESPECT, false, false, List.of()));
    }

    @Test
    void copiesTheProviderList() {
        List<ResourceLocation> providers = new ArrayList<>();
        providers.add(ResourceLocation.fromNamespaceAndPath("test", "one"));
        RenderEnvironment environment = new RenderEnvironment(
            1024.0, 1024.0, true, SceneDepthSource.MAIN_TARGET, ShaderPackState.INACTIVE,
            FogPolicy.RESPECT, false, false, providers);

        providers.clear();

        assertEquals(1, environment.activeProviders().size());
        assertThrows(UnsupportedOperationException.class, () -> environment.activeProviders().clear());
    }

    @Test
    void reportsShaderPackState() {
        RenderEnvironment active = new RenderEnvironment(
            256.0, 256.0, false, SceneDepthSource.EXTERNAL_SHADERPACK, ShaderPackState.ACTIVE,
            FogPolicy.RESPECT, false, false, List.of());
        RenderEnvironment shadow = new RenderEnvironment(
            256.0, 256.0, false, SceneDepthSource.EXTERNAL_SHADERPACK, ShaderPackState.SHADOW_PASS,
            FogPolicy.RESPECT, false, false, List.of());

        assertTrue(active.shaderPackActive());
        assertFalse(active.shadowPass());
        assertTrue(shadow.shadowPass());
        assertFalse(shadow.screenCompositionAllowed());
    }
}
