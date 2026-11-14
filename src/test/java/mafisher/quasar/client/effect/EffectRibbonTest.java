package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import mafisher.quasar.api.client.render.EffectRibbon;
import mafisher.quasar.api.effect.EffectCurve;
import mafisher.quasar.api.effect.EffectEasing;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class EffectRibbonTest {
    @Test
    void emitsOneQuadForTwoDistinctPoints() {
        EffectRibbon ribbon = EffectRibbon.builder()
            .subdivisions(1)
            .taper(1.0F, 1.0F)
            .build();
        ribbon.addPoint(0.0, 0.0, 0.0);
        ribbon.addPoint(1.0, 0.0, 0.0);

        RecordingConsumer consumer = emit(ribbon, new PoseStack());

        assertEquals(4, consumer.vertices.size());
        assertEquals(0.0F, consumer.vertices.get(0).u(), 0.0001F);
        assertEquals(0.0F, consumer.vertices.get(1).u(), 0.0001F);
        assertEquals(1.0F, consumer.vertices.get(2).u(), 0.0001F);
        assertEquals(1.0F, consumer.vertices.get(3).u(), 0.0001F);
        assertTrue(consumer.vertices.get(0).z() < consumer.vertices.get(1).z());
        assertTrue(consumer.vertices.get(3).z() < consumer.vertices.get(2).z());
        consumer.assertFinite();
    }

    @Test
    void keepsControlPointsWhenSubdivisionsAreOne() {
        EffectRibbon ribbon = EffectRibbon.builder()
            .subdivisions(1)
            .build();
        ribbon.addPoint(0.0, 0.0, 0.0);
        ribbon.addPoint(1.0, 0.0, 0.0);
        ribbon.addPoint(2.0, 0.0, 0.0);

        RecordingConsumer consumer = emit(ribbon, new PoseStack());

        assertEquals(3, ribbon.ringCount());
        assertEquals(8, ribbon.vertexCount());
        assertEquals(8, consumer.vertices.size());
        assertEquals(2, consumer.maxX(), 0.0001F);
        consumer.assertFinite();
    }

    @Test
    void buildsCameraFacingQuadsFromTheInversePose() {
        EffectRibbon ribbon = EffectRibbon.builder()
            .subdivisions(1)
            .taper(0.4F, 0.4F)
            .build();
        ribbon.addPoint(0.0, 0.0, 0.0);
        ribbon.addPoint(1.0, 0.0, 0.0);
        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0, 0.0, 10.0);

        RecordingConsumer consumer = emit(ribbon, poseStack);

        assertEquals(4, consumer.vertices.size());
        for (RecordingConsumer.Vertex vertex : consumer.vertices) {
            assertEquals(0.0F, vertex.z(), 0.0001F);
            assertTrue(vertex.normalZ() < -0.99F);
        }
        assertTrue(consumer.vertices.get(0).y() < 0.0F);
        assertTrue(consumer.vertices.get(1).y() > 0.0F);
        consumer.assertFinite();
    }

    @Test
    void mergesDuplicatePointsWithoutChangingTheStrip() {
        EffectRibbon duplicate = EffectRibbon.builder()
            .subdivisions(1)
            .build();
        duplicate.addPoint(0.0, 0.0, 0.0);
        duplicate.addPoint(0.0, 0.0, 0.0);
        duplicate.addPoint(1.0, 0.0, 0.0);

        EffectRibbon distinct = EffectRibbon.builder()
            .subdivisions(1)
            .build();
        distinct.addPoint(0.0, 0.0, 0.0);
        distinct.addPoint(1.0, 0.0, 0.0);

        assertEquals(distinct.ringCount(), duplicate.ringCount());
        assertEquals(distinct.vertexCount(), duplicate.vertexCount());
        assertEquals(3, duplicate.pointCount());
    }

    @Test
    void emitsNothingForFewerThanTwoDistinctPoints() {
        EffectRibbon empty = EffectRibbon.builder().build();
        assertEquals(0, empty.ringCount());
        assertEquals(0, empty.vertexCount());
        assertEquals(0, emit(empty, new PoseStack()).vertices.size());

        EffectRibbon single = EffectRibbon.builder().build();
        single.addPoint(1.0, 2.0, 3.0);
        assertEquals(0, single.ringCount());
        assertEquals(0, emit(single, new PoseStack()).vertices.size());

        EffectRibbon duplicates = EffectRibbon.builder().build();
        duplicates.addPoint(1.0, 2.0, 3.0);
        duplicates.addPoint(1.0, 2.0, 3.0);
        assertEquals(0, duplicates.ringCount());
        assertEquals(0, emit(duplicates, new PoseStack()).vertices.size());
    }

    @Test
    void dropsTheOldestPointAtTheHistoryCap() {
        EffectRibbon ribbon = EffectRibbon.builder()
            .maxPoints(3)
            .subdivisions(1)
            .build();
        assertTrue(ribbon.addPoint(0.0, 0.0, 0.0));
        assertTrue(ribbon.addPoint(1.0, 0.0, 0.0));
        assertTrue(ribbon.addPoint(2.0, 0.0, 0.0));
        assertFalse(ribbon.addPoint(3.0, 0.0, 0.0));

        RecordingConsumer consumer = emit(ribbon, new PoseStack());

        assertEquals(3, ribbon.pointCount());
        assertEquals(3, ribbon.ringCount());
        assertEquals(1.0F, consumer.minX(), 0.0001F);
        assertEquals(3.0F, consumer.maxX(), 0.0001F);
    }

    @Test
    void reducesSubdivisionsBeforeDroppingHistory() {
        EffectRibbon ribbon = EffectRibbon.builder()
            .maxPoints(EffectRibbon.MAX_POINTS)
            .subdivisions(EffectRibbon.MAX_SUBDIVISIONS)
            .build();
        for (int index = 0; index < EffectRibbon.MAX_POINTS; index++) {
            ribbon.addPoint(index, 0.0, 0.0);
        }

        assertEquals(EffectRibbon.MAX_POINTS, ribbon.pointCount());
        assertTrue(ribbon.ringCount() <= EffectRibbon.MAX_RINGS);
        assertTrue(ribbon.vertexCount() <= EffectRibbon.MAX_VERTICES);
    }

    @Test
    void appliesTaperAndNormalizedUv() {
        EffectRibbon ribbon = EffectRibbon.builder()
            .subdivisions(1)
            .taper(2.0F, 0.0F)
            .uv(EffectRibbon.UvMode.NORMALIZED, 1.0F)
            .build();
        ribbon.addPoint(0.0, 0.0, 0.0);
        ribbon.addPoint(1.0, 0.0, 0.0);

        RecordingConsumer consumer = emit(ribbon, new PoseStack());

        assertEquals(4, consumer.vertices.size());
        assertEquals(0.0F, consumer.vertices.get(0).u(), 0.0001F);
        assertEquals(1.0F, consumer.vertices.get(2).u(), 0.0001F);
        assertTrue(Math.abs(consumer.vertices.get(0).z()) > Math.abs(consumer.vertices.get(2).z()));
        assertEquals(0.0F, consumer.vertices.get(2).z(), 0.0001F);
        assertEquals(0.0F, consumer.vertices.get(3).z(), 0.0001F);
        consumer.assertFinite();
    }

    @Test
    void tilesUvByArcLength() {
        EffectRibbon ribbon = EffectRibbon.builder()
            .subdivisions(1)
            .uv(EffectRibbon.UvMode.TILED, 0.5F)
            .build();
        ribbon.addPoint(0.0, 0.0, 0.0);
        ribbon.addPoint(2.0, 0.0, 0.0);

        RecordingConsumer consumer = emit(ribbon, new PoseStack());

        assertEquals(0.0F, consumer.vertices.get(0).u(), 0.0001F);
        assertEquals(4.0F, consumer.vertices.get(2).u(), 0.0001F);
    }

    @Test
    void samplesAFloatTaperCurve() {
        EffectCurve curve = EffectCurve.scalar()
            .keyframe(0.0F, 0.0F, EffectEasing.LINEAR)
            .keyframe(1.0F, 4.0F, EffectEasing.LINEAR)
            .build();
        EffectRibbon ribbon = EffectRibbon.builder()
            .subdivisions(1)
            .taper(curve)
            .build();
        ribbon.addPoint(0.0, 0.0, 0.0);
        ribbon.addPoint(1.0, 0.0, 0.0);

        RecordingConsumer consumer = emit(ribbon, new PoseStack());

        assertEquals(0.0F, consumer.vertices.get(0).z(), 0.0001F);
        assertEquals(2.0F, Math.abs(consumer.vertices.get(2).z()), 0.0001F);
    }

    @Test
    void reusesBuffersAfterReset() {
        EffectRibbon ribbon = EffectRibbon.builder()
            .subdivisions(1)
            .build();
        ribbon.addPoint(0.0, 0.0, 0.0);
        ribbon.addPoint(1.0, 0.0, 0.0);
        ribbon.addPoint(2.0, 0.0, 0.0);
        assertEquals(8, emit(ribbon, new PoseStack()).vertices.size());

        ribbon.reset();
        ribbon.addPoint(5.0, 0.0, 0.0);
        ribbon.addPoint(6.0, 0.0, 0.0);
        RecordingConsumer consumer = emit(ribbon, new PoseStack());

        assertEquals(4, consumer.vertices.size());
        assertEquals(5.0F, consumer.minX(), 0.0001F);
        assertEquals(6.0F, consumer.maxX(), 0.0001F);
    }

    @Test
    void staysFiniteAtCoincidentAndParallelDegeneratePoints() {
        EffectRibbon ribbon = EffectRibbon.builder()
            .subdivisions(4)
            .build();
        ribbon.addPoint(0.0, 0.0, -1.0);
        ribbon.addPoint(0.0, 0.0, 0.0);
        ribbon.addPoint(0.0, 0.0, 1.0);

        RecordingConsumer consumer = emit(ribbon, new PoseStack());

        assertTrue(consumer.vertices.size() > 0);
        consumer.assertFinite();
    }

    @Test
    void emitsNothingForASingularPose() {
        EffectRibbon ribbon = EffectRibbon.builder()
            .subdivisions(1)
            .build();
        ribbon.addPoint(0.0, 0.0, 0.0);
        ribbon.addPoint(1.0, 0.0, 0.0);
        PoseStack poseStack = new PoseStack();
        poseStack.scale(0.0F, 0.0F, 0.0F);

        assertEquals(0, emit(ribbon, poseStack).vertices.size());
    }

    @Test
    void reportsConservativeLocalBounds() {
        EffectRibbon ribbon = EffectRibbon.builder()
            .subdivisions(1)
            .taper(2.0F, 2.0F)
            .build();
        ribbon.addPoint(0.0, 0.0, 0.0);
        ribbon.addPoint(1.0, 0.0, 0.0);

        AABB bounds = ribbon.localBounds();

        assertEquals(-1.0, bounds.minX, 0.0001);
        assertEquals(2.0, bounds.maxX, 0.0001);
        assertEquals(-1.0, bounds.minZ, 0.0001);
        assertEquals(1.0, bounds.maxZ, 0.0001);
    }

    @Test
    void rejectsInvalidSettingsAndPoints() {
        assertThrows(IllegalArgumentException.class, () -> EffectRibbon.builder().maxPoints(1).build());
        assertThrows(IllegalArgumentException.class,
            () -> EffectRibbon.builder().maxPoints(EffectRibbon.MAX_POINTS + 1).build());
        assertThrows(IllegalArgumentException.class, () -> EffectRibbon.builder().subdivisions(0).build());
        assertThrows(IllegalArgumentException.class,
            () -> EffectRibbon.builder().subdivisions(EffectRibbon.MAX_SUBDIVISIONS + 1).build());
        assertThrows(IllegalArgumentException.class, () -> EffectRibbon.builder().taper(-1.0F, 1.0F).build());
        assertThrows(IllegalArgumentException.class,
            () -> EffectRibbon.builder().taper(EffectRibbon.MAX_WIDTH + 1.0F, 1.0F).build());
        assertThrows(IllegalArgumentException.class,
            () -> EffectRibbon.builder().uv(EffectRibbon.UvMode.TILED, 0.0F).build());
        EffectCurve vec2 = EffectCurve.vec2()
            .keyframe(0.0F, EffectCurve.Value.vec2(0.0F, 0.0F), EffectEasing.LINEAR)
            .keyframe(1.0F, EffectCurve.Value.vec2(1.0F, 1.0F), EffectEasing.LINEAR)
            .build();
        assertThrows(IllegalArgumentException.class, () -> EffectRibbon.builder().taper(vec2).build());

        EffectRibbon ribbon = EffectRibbon.builder().build();
        assertThrows(IllegalArgumentException.class, () -> ribbon.addPoint(Double.NaN, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> ribbon.addPoint(0.0, Double.POSITIVE_INFINITY, 0.0));
    }

    private static RecordingConsumer emit(EffectRibbon ribbon, PoseStack poseStack) {
        RecordingConsumer consumer = new RecordingConsumer();
        ribbon.emit(consumer, poseStack.last(), 1.0F, 1.0F, 1.0F, 1.0F, 0, 0);
        consumer.finish();
        return consumer;
    }

    private static final class RecordingConsumer implements VertexConsumer {
        private final List<Vertex> vertices = new ArrayList<>();
        private float x;
        private float y;
        private float z;
        private float u;
        private float v;
        private float normalX;
        private float normalY;
        private float normalZ;
        private float red = 1.0F;
        private float green = 1.0F;
        private float blue = 1.0F;
        private float alpha = 1.0F;
        private int overlay;
        private int light;
        private boolean active;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            begin(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer addVertex(PoseStack.Pose pose, float x, float y, float z) {
            begin(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.red = red / 255.0F;
            this.green = green / 255.0F;
            this.blue = blue / 255.0F;
            this.alpha = alpha / 255.0F;
            return this;
        }

        @Override
        public VertexConsumer setColor(float red, float green, float blue, float alpha) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
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
        public VertexConsumer setOverlay(int overlay) {
            this.overlay = overlay;
            return this;
        }

        @Override
        public VertexConsumer setLight(int light) {
            this.light = light;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            this.normalX = normalX;
            this.normalY = normalY;
            this.normalZ = normalZ;
            return this;
        }

        @Override
        public VertexConsumer setNormal(PoseStack.Pose pose, float normalX, float normalY, float normalZ) {
            return setNormal(normalX, normalY, normalZ);
        }

        private void begin(float x, float y, float z) {
            commit();
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = 0.0F;
            this.v = 0.0F;
            this.normalX = 0.0F;
            this.normalY = 0.0F;
            this.normalZ = 0.0F;
            this.overlay = 0;
            this.light = 0;
            this.active = true;
        }

        private void commit() {
            if (!this.active) {
                return;
            }
            this.vertices.add(new Vertex(this.x, this.y, this.z, this.u, this.v,
                this.normalX, this.normalY, this.normalZ, this.red, this.green, this.blue,
                this.alpha, this.overlay, this.light));
            this.active = false;
        }

        private void finish() {
            commit();
        }

        private float minX() {
            float min = Float.POSITIVE_INFINITY;
            for (Vertex vertex : this.vertices) {
                min = Math.min(min, vertex.x());
            }
            return min;
        }

        private float maxX() {
            float max = Float.NEGATIVE_INFINITY;
            for (Vertex vertex : this.vertices) {
                max = Math.max(max, vertex.x());
            }
            return max;
        }

        private void assertFinite() {
            for (Vertex vertex : this.vertices) {
                assertTrue(Float.isFinite(vertex.x()));
                assertTrue(Float.isFinite(vertex.y()));
                assertTrue(Float.isFinite(vertex.z()));
                assertTrue(Float.isFinite(vertex.u()));
                assertTrue(Float.isFinite(vertex.v()));
                assertTrue(Float.isFinite(vertex.normalX()));
                assertTrue(Float.isFinite(vertex.normalY()));
                assertTrue(Float.isFinite(vertex.normalZ()));
            }
        }

        private record Vertex(float x, float y, float z, float u, float v,
            float normalX, float normalY, float normalZ, float red, float green, float blue,
            float alpha, int overlay, int light) {
        }
    }
}
