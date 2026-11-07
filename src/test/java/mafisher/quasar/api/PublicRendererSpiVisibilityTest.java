package mafisher.quasar.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.util.UUID;
import mafisher.quasar.client.effect.EffectLod;
import mafisher.quasar.client.effect.EffectLodProfile;
import mafisher.quasar.client.effect.EffectMesh;
import mafisher.quasar.client.effect.EffectMeshes;
import mafisher.quasar.client.effect.EffectRenderContext;
import mafisher.quasar.client.effect.EffectRenderTypeOptions;
import mafisher.quasar.client.effect.EffectShader;
import mafisher.quasar.client.effect.QuasarRenderTypes;
import mafisher.quasar.client.effect.RegisterVisualEffectRenderersEvent;
import mafisher.quasar.client.effect.VisualEffectInstance;
import mafisher.quasar.client.effect.VisualEffectRenderer;
import mafisher.quasar.effect.network.StartVisualEffectPayload;
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
        assertNotNull(EffectMeshes.unitSphere(8, 16));
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

    @Test
    void exposesSpatialRadiusWithASafeDefault() {
        ResourceLocation effectId = ResourceLocation.fromNamespaceAndPath("test", "effect");
        StartVisualEffectPayload payload = new StartVisualEffectPayload(
            effectId,
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
        VisualEffectRenderer renderer = new VisualEffectRenderer() {
        };

        assertEquals(Double.POSITIVE_INFINITY, renderer.spatialRadius(
            new EffectRenderContext(instance, payload.transform(), 0.0F, 0.0F, 0.0F)
        ));
    }

    @Test
    void exposesLevelOfDetailWithAFullDefault() {
        ResourceLocation effectId = ResourceLocation.fromNamespaceAndPath("test", "effect");
        StartVisualEffectPayload payload = new StartVisualEffectPayload(
            effectId,
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
        VisualEffectRenderer renderer = new VisualEffectRenderer() {
            @Override
            public EffectLodProfile lodProfile() {
                return EffectLodProfile.of(1.0, 4.0);
            }
        };

        assertEquals(3, renderer.lodProfile().levelCount());
        assertEquals(1, renderer.lodProfile().select(2.0));
        assertEquals(EffectLod.FULL,
            new EffectRenderContext(instance, payload.transform(), 0.0F, 0.0F, 0.0F).lod());
        assertEquals(2, new EffectRenderContext(instance, payload.transform(), 0.0F, 0.0F, 0.0F)
            .withLod(new EffectLod(2, 64.0, 8.0))
            .lod()
            .level());
    }
}
