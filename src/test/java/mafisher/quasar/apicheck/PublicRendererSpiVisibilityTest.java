package mafisher.quasar.apicheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.client.render.EffectInstancedPass;
import mafisher.quasar.api.client.render.EffectLod;
import mafisher.quasar.api.client.render.EffectLodProfile;
import mafisher.quasar.api.client.render.EffectMesh;
import mafisher.quasar.api.client.render.EffectMeshes;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.client.render.EffectRenderTypeOptions;
import mafisher.quasar.api.client.render.EffectShader;
import mafisher.quasar.api.client.render.EffectStaticMeshPass;
import mafisher.quasar.api.client.render.InstancedVisualEffectRenderer;
import mafisher.quasar.api.client.render.QuasarRenderTypes;
import mafisher.quasar.api.client.render.RegisterVisualEffectRenderersEvent;
import mafisher.quasar.api.client.render.StaticMeshVisualEffectRenderer;
import mafisher.quasar.api.client.render.VisualEffectInstance;
import mafisher.quasar.api.client.render.VisualEffectRenderer;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
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
        assertNotNull(QuasarRenderTypes.instancedWorldVisual(texture));
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
        assertEquals(EffectQualityTier.HIGH,
            new EffectRenderContext(instance, payload.transform(), 0.0F, 0.0F, 0.0F).quality());
        assertEquals(EffectQualityTier.LOW,
            new EffectRenderContext(instance, payload.transform(), 0.0F, 0.0F, 0.0F)
                .withQuality(EffectQualityTier.LOW)
                .quality());
        assertEquals(2, new EffectRenderContext(instance, payload.transform(), 0.0F, 0.0F, 0.0F)
            .withLod(new EffectLod(2, 64.0, 8.0))
            .lod()
            .level());
        EffectLod steady = new EffectLod(2, 64.0, 8.0);
        assertEquals(2, steady.previousLevel());
        assertEquals(1.0, steady.blend());
        assertFalse(steady.isTransitioning());
        EffectLod transition = new EffectLod(2, 1, 0.5, 64.0, 8.0);
        assertEquals(1, transition.previousLevel());
        assertEquals(0.5, transition.blend());
        assertTrue(transition.isTransitioning());
    }

    @Test
    void exposesInstancedPasses() {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("test", "textures/effect/instanced.png");
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
        EffectRenderContext context = new EffectRenderContext(
            new VisualEffectInstance(payload), payload.transform(), 0.0F, 0.0F, 0.0F
        );
        InstancedVisualEffectRenderer renderer = new InstancedVisualEffectRenderer() {
            @Override
            public List<EffectInstancedPass> instancedPasses(EffectRenderContext ignored) {
                return List.of(new EffectInstancedPass("core", QuasarRenderTypes.instancedWorldVisual(texture),
                    EffectMeshes.horizontalQuad(), new Matrix4f().scale(2.0F), 1.0F, 1.0F, 1.0F, 1.0F, 0.0F,
                    0.0F));
            }
        };

        List<EffectInstancedPass> passes = renderer.instancedPasses(context);
        assertEquals(1, passes.size());
        assertEquals("core", passes.getFirst().id());
        assertTrue(passes.getFirst().localTransform().isFinite());
    }

    @Test
    void exposesStaticMeshPasses() {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("test", "textures/effect/cached.png");
        EffectStaticMeshPass pass = new EffectStaticMeshPass("core", QuasarRenderTypes.worldVisual(texture),
            EffectMeshes.horizontalQuad(), new Matrix4f().scale(2.0F), 0.0F, 0.0F, 0.0F, 1.0F);

        assertEquals(EffectStaticMeshPass.FULLBRIGHT_LIGHT, pass.light());
        assertEquals(OverlayTexture.NO_OVERLAY, pass.overlay());
        assertTrue(pass.localTransform().isFinite());

        StaticMeshVisualEffectRenderer renderer = new StaticMeshVisualEffectRenderer() {
            @Override
            public List<EffectStaticMeshPass> staticMeshPasses(EffectRenderContext ignored) {
                return List.of(pass);
            }
        };
        assertEquals(List.of(pass), renderer.staticMeshPasses(null));
    }
}
