package mafisher.quasar.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import mafisher.quasar.client.effect.EffectMesh;
import mafisher.quasar.client.effect.EffectMeshes;
import mafisher.quasar.client.effect.EffectRenderTypeOptions;
import mafisher.quasar.client.effect.EffectShader;
import mafisher.quasar.client.effect.QuasarRenderTypes;
import mafisher.quasar.client.effect.RegisterVisualEffectRenderersEvent;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Compiles from outside the implementation package to pin the public SPI surface. */
class PublicRendererSpiVisibilityTest {
    @Test
    void exposesMeshPrimitivesAndRenderTypes() {
        EffectMesh mesh = EffectMesh.builder(4)
            .vertex(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F)
            .vertex(1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F)
            .vertex(1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F)
            .vertex(0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F)
            .build();
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
            "test", "textures/effect/public_api.png"
        );

        assertEquals(4, mesh.vertexCount());
        assertNotNull(EffectMeshes.horizontalQuad());
        assertNotNull(EffectMeshes.verticalPlane());
        assertNotNull(EffectMeshes.unitSphere());
        assertNotNull(QuasarRenderTypes.worldVisual(texture));
        assertNotNull(QuasarRenderTypes.screenEffectMask(texture));
        assertNotNull(RegisterVisualEffectRenderersEvent.class);
    }

    @Test
    void exposesShaderRenderTypeFactories() {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
            "test", "textures/effect/public_shader.png"
        );
        EffectShader shader = EffectShader.create(
            ResourceLocation.fromNamespaceAndPath("test", "shader"), DefaultVertexFormat.BLOCK
        );

        assertEquals(DefaultVertexFormat.BLOCK, shader.format());
        assertNotNull(QuasarRenderTypes.worldVisual(shader, texture));
        assertNotNull(QuasarRenderTypes.worldVisual(shader, texture,
            EffectRenderTypeOptions.WORLD_DEPTH_WRITING));
        assertNotNull(QuasarRenderTypes.screenEffectMask(shader, texture));
        assertNotNull(QuasarRenderTypes.screenEffectMask(shader, texture,
            EffectRenderTypeOptions.MASK_DEFAULT));
    }
}
