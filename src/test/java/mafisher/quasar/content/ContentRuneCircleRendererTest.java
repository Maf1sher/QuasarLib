package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.client.EffectInstancedPass;
import mafisher.quasar.api.client.EffectLod;
import mafisher.quasar.api.client.EffectMeshes;
import mafisher.quasar.api.client.EffectRenderContext;
import mafisher.quasar.api.client.ScreenEffectMask;
import mafisher.quasar.api.client.ScreenEffectSettings;
import mafisher.quasar.api.effect.EffectQualityTier;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import mafisher.quasar.testing.TestVisualEffects;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ContentRuneCircleRendererTest {
    @Test
    void declaresOrderedDistinctInstancedPasses() {
        ContentRuneCircleRenderer renderer = new ContentRuneCircleRenderer();

        List<EffectInstancedPass> passes = renderer.instancedPasses(context());

        assertTrue(renderer.renderPasses(context()).isEmpty());
        assertEquals(3, passes.size());
        assertEquals(3, passes.stream().map(EffectInstancedPass::id).distinct().count());
        assertTrue(passes.stream().allMatch(pass -> pass.renderType() != null && pass.mesh() != null));
        assertTrue(passes.stream().allMatch(pass -> pass.localTransform().isFinite()));
    }

    @Test
    void selectsContentOwnedScreenGlow() {
        ContentRuneCircleRenderer renderer = new ContentRuneCircleRenderer();
        ScreenEffectSettings settings = renderer.screenEffectSettings(context());

        assertSame(ContentScreenOperations.RUNE_GLOW, settings.operation());
        assertSame(ContentRuneGlowSettings.DEFAULT,
            settings.settings(ContentRuneGlowSettings.class));
        ScreenEffectMask mask = renderer.screenEffectMask(context());
        assertTrue(mask.strength() > 0.0F);
        assertNotNull(renderer.screenMaskRenderType(context()));
    }

    @Test
    void thinsPassesAtReducedLevelOfDetail() {
        ContentRuneCircleRenderer renderer = new ContentRuneCircleRenderer();
        EffectRenderContext full = context();
        EffectRenderContext reduced = full.withLod(new EffectLod(1, 128.0, 8.0));

        assertEquals(3, renderer.instancedPasses(full).size());
        assertEquals(List.of("outer"),
            renderer.instancedPasses(reduced).stream().map(EffectInstancedPass::id).toList());
    }

    @Test
    void crossFadesReducedLevelOfDetailWithTheFullRepresentation() {
        ContentRuneCircleRenderer renderer = new ContentRuneCircleRenderer();
        EffectRenderContext context = context().withLod(new EffectLod(1, 0, 0.5, 128.0, 8.0));

        List<EffectInstancedPass> passes = renderer.instancedPasses(context);

        assertEquals(List.of("prev_outer", "prev_middle", "prev_inner", "next_outer"),
            passes.stream().map(EffectInstancedPass::id).toList());
        assertEquals(4, passes.stream().map(EffectInstancedPass::id).distinct().count());
    }

    @Test
    void trimsLayersByQualityTier() {
        ContentRuneCircleRenderer renderer = new ContentRuneCircleRenderer();
        EffectRenderContext context = context();

        assertEquals(1, renderer.instancedPasses(context.withQuality(EffectQualityTier.LOW)).size());
        assertEquals(2, renderer.instancedPasses(context.withQuality(EffectQualityTier.MEDIUM)).size());
        assertEquals(3, renderer.instancedPasses(context.withQuality(EffectQualityTier.HIGH)).size());
        assertEquals(3, renderer.instancedPasses(context.withQuality(EffectQualityTier.ULTRA)).size());
    }

    @Test
    void returnsFiniteConservativeBounds() {
        ContentRuneCircleRenderer renderer = new ContentRuneCircleRenderer();

        AABB bounds = renderer.cullingBounds(context());

        assertTrue(Double.isFinite(bounds.minX) && Double.isFinite(bounds.minY) && Double.isFinite(bounds.minZ));
        assertTrue(Double.isFinite(bounds.maxX) && Double.isFinite(bounds.maxY) && Double.isFinite(bounds.maxZ));
        assertTrue(bounds.maxX - bounds.minX > 10.0);
        assertTrue(bounds.maxY - bounds.minY > 10.0);
        assertTrue(bounds.maxZ - bounds.minZ > 10.0);
        assertTrue(renderer.spatialRadius(context()) >= (bounds.maxX - bounds.minX) / 2.0);
    }

    @Test
    void worldAndMaskLayersUseTheSameRotation() {
        ContentRuneCircleRenderer renderer = new ContentRuneCircleRenderer();
        EffectRenderContext context = context().withQuality(EffectQualityTier.LOW);
        EffectInstancedPass pass = renderer.instancedPasses(context).getFirst();

        RecordingVertexConsumer recorder = new RecordingVertexConsumer();
        MultiBufferSource bufferSource = renderType -> recorder;
        renderer.renderScreenMask(context, renderer.screenMaskRenderType(context), new PoseStack(), bufferSource);

        var meshVertex = EffectMeshes.horizontalQuad().vertex(0);
        Vector3f expected = pass.localTransform().transformPosition(
            new Vector3f(meshVertex.x(), meshVertex.y(), meshVertex.z()));
        assertEquals(expected.x(), recorder.x, 1.0E-3F);
        assertEquals(expected.y(), recorder.y, 1.0E-3F);
        assertEquals(expected.z(), recorder.z, 1.0E-3F);
    }

    private static EffectRenderContext context() {
        StartVisualEffectPayload payload = TestVisualEffects.payload(
            ContentEffectIds.RUNE_CIRCLE,
            UUID.randomUUID(),
            Vec3.ZERO,
            5.0F,
            100,
            12_345L
        );
        return TestVisualEffects.context(payload, 10.0F, 0.1F);
    }

    private static final class RecordingVertexConsumer implements VertexConsumer {
        private float x;
        private float y;
        private float z;
        private boolean recorded;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (!this.recorded) {
                this.x = x;
                this.y = y;
                this.z = z;
                this.recorded = true;
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            return this;
        }
    }
}
