package mafisher.quasar.content.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.client.render.EffectRenderPass;
import mafisher.quasar.content.ContentEffectIds;
import mafisher.quasar.testing.TestVisualEffects;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ContentRibbonRendererTest {
    @Test
    void declaresOneWorldPass() {
        ContentRibbonRenderer renderer = new ContentRibbonRenderer();

        List<EffectRenderPass> passes = renderer.renderPasses(context());

        assertEquals(1, passes.size());
        assertEquals("ribbon-test", passes.getFirst().id());
    }

    @Test
    void emitsFiniteCameraFacingGeometry() {
        ContentRibbonRenderer renderer = new ContentRibbonRenderer();
        RecordingConsumer recorder = new RecordingConsumer();
        MultiBufferSource bufferSource = renderType -> recorder;

        renderer.render(context(), renderer.renderPasses(context()).getFirst(),
            new PoseStack(), bufferSource);

        assertTrue(recorder.count > 0);
        assertTrue(recorder.finite);
    }

    @Test
    void returnsFiniteConservativeBounds() {
        ContentRibbonRenderer renderer = new ContentRibbonRenderer();

        AABB bounds = renderer.cullingBounds(context());

        assertTrue(Double.isFinite(bounds.minX) && Double.isFinite(bounds.maxX));
        assertTrue(Double.isFinite(bounds.minY) && Double.isFinite(bounds.maxY));
        assertTrue(Double.isFinite(bounds.minZ) && Double.isFinite(bounds.maxZ));
        assertTrue(bounds.maxX - bounds.minX > 5.0);
        assertTrue(renderer.spatialRadius(context()) > 0.0);
        assertTrue(Double.isFinite(renderer.spatialRadius(context())));
    }

    private static EffectRenderContext context() {
        return TestVisualEffects.context(
            ContentEffectIds.RIBBON,
            UUID.randomUUID(),
            Vec3.ZERO,
            5.0F,
            200,
            0.5F
        );
    }

    private static final class RecordingConsumer implements VertexConsumer {
        private int count;
        private boolean finite = true;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.count++;
            this.finite &= Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z);
            return this;
        }

        @Override
        public VertexConsumer addVertex(PoseStack.Pose pose, float x, float y, float z) {
            return this.addVertex(x, y, z);
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.finite &= Float.isFinite(u) && Float.isFinite(v);
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
            this.finite &= Float.isFinite(normalX) && Float.isFinite(normalY)
                && Float.isFinite(normalZ);
            return this;
        }
    }
}
