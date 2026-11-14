package mafisher.quasar.apicheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.client.render.EffectDepthOcclusion;
import mafisher.quasar.api.client.render.EffectInstancedPass;
import mafisher.quasar.api.client.render.EffectLod;
import mafisher.quasar.api.client.render.EffectLodProfile;
import mafisher.quasar.api.client.render.EffectMesh;
import mafisher.quasar.api.client.render.EffectMeshLod;
import mafisher.quasar.api.client.render.EffectMeshes;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.client.render.EffectRenderTypeOptions;
import mafisher.quasar.api.client.render.EffectRibbon;
import mafisher.quasar.api.client.render.EffectScreenLodProfile;
import mafisher.quasar.api.client.render.EffectScreenMetrics;
import mafisher.quasar.api.client.render.EffectShader;
import mafisher.quasar.api.client.render.EffectStaticMesh;
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
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

/** Compiles from outside the implementation package to pin the public SPI surface. */
class PublicRendererSpiVisibilityTest {
    @Test
    void exposesDepthOcclusionPolicy() {
        VisualEffectRenderer renderer = new VisualEffectRenderer() {
        };
        AABB bounds = new AABB(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0);

        assertEquals(EffectDepthOcclusion.AUTO, renderer.depthOcclusion());
        assertEquals(bounds, EffectDepthOcclusion.bounds(bounds).bounds());
        assertThrows(NullPointerException.class, () -> EffectDepthOcclusion.bounds(null));
    }

    @Test
    void exposesStaticMeshLoadingContract() {
        ResourceLocation asset = ResourceLocation.fromNamespaceAndPath(
            "test", "quasar/meshes/sample.obj"
        );
        EffectStaticMesh mesh = EffectStaticMesh.of(asset);

        assertEquals(asset, mesh.location());
        assertFalse(mesh.loaded());
        assertNull(mesh.mesh());
        assertNull(mesh.localBounds());
        assertTrue(EffectStaticMesh.MAX_VERTICES >= 4);
        assertThrows(IllegalArgumentException.class, () -> EffectStaticMesh.of(
            ResourceLocation.fromNamespaceAndPath("test", "quasar/meshes/sample.txt")
        ));
    }

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
        assertNotNull(EffectMeshes.cylinder());
        assertNotNull(EffectMeshes.cylinder(8, 1));
        assertNotNull(EffectMeshes.cone());
        assertNotNull(EffectMeshes.cone(8));
        assertNotNull(EffectMeshes.torus());
        assertNotNull(EffectMeshes.torus(8, 4));
        assertNotNull(EffectMeshes.hemisphere());
        assertNotNull(EffectMeshes.hemisphere(2));
        assertNotNull(EffectMeshes.icosphere());
        assertNotNull(EffectMeshes.icosphere(2));
        assertNotNull(EffectMeshes.disk());
        assertNotNull(EffectMeshes.disk(2, 8));
        EffectRibbon ribbon = EffectRibbon.builder()
            .maxPoints(8)
            .subdivisions(2)
            .taper(1.0F, 0.0F)
            .uv(EffectRibbon.UvMode.NORMALIZED, 1.0F)
            .build();
        assertTrue(ribbon.addPoint(0.0, 0.0, 0.0));
        assertTrue(ribbon.addPoint(1.0, 0.0, 0.0));
        assertEquals(2, ribbon.pointCount());
        EffectMeshLod meshLod = EffectMeshLod.of(
            new AABB(-1.0, -1.0, -1.0, 1.0, 1.0, 1.0),
            EffectMeshes.icosphere(2), EffectMeshes.icosphere(1)
        );
        assertEquals(2, meshLod.levelCount());
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
    void exposesScreenLodAndPerEffectQuality() {
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
            public EffectScreenLodProfile screenLodProfile() {
                return EffectScreenLodProfile.of(0.5, 0.1);
            }
        };
        EffectScreenMetrics metrics = EffectScreenMetrics.of(0.4, 0.5);
        EffectRenderContext context = new EffectRenderContext(
            instance, payload.transform(), 0.0F, 0.0F, 0.0F
        ).withScreenMetrics(metrics).withEffectiveQuality(EffectQualityTier.MEDIUM);

        assertEquals(3, renderer.screenLodProfile().levelCount());
        assertEquals(1, renderer.screenLodProfile().selectCoverage(0.4));
        assertEquals(EffectScreenLodProfile.FULL,
            new VisualEffectRenderer() {
            }.screenLodProfile());
        assertEquals(metrics, context.screenMetrics());
        assertEquals(0.3, context.qualityScore(), 0.000001);
        assertEquals(EffectQualityTier.MEDIUM, context.effectiveQuality());
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
