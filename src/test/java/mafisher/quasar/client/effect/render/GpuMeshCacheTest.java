package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import mafisher.quasar.api.client.EffectMeshes;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

class GpuMeshCacheTest {
    @Test
    void usesSixIndicesPerQuad() {
        assertEquals(6, GpuMeshCache.quadIndexCount(4));
        assertEquals(12, GpuMeshCache.quadIndexCount(8));
        assertEquals(6_144, GpuMeshCache.quadIndexCount(4_096));
    }

    @Test
    void uploadClosesTheNativeBufferBuilder() {
        TrackingByteBufferBuilder bytes = new TrackingByteBufferBuilder(1_024);
        GpuMeshCache.GpuMeshKey key = new GpuMeshCache.GpuMeshKey(
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            EffectMeshes.horizontalQuad(),
            1.0F,
            1.0F,
            1.0F,
            1.0F,
            0,
            0x00F000F0
        );

        try (MockedConstruction<VertexBuffer> ignored = Mockito.mockConstruction(VertexBuffer.class)) {
            assertNotNull(GpuMeshCache.upload(EffectMeshes.horizontalQuad(), key, capacity -> bytes));
        }
        assertTrue(bytes.closed, "upload must release the native ByteBufferBuilder allocation");
    }

    private static final class TrackingByteBufferBuilder extends ByteBufferBuilder {
        private boolean closed;

        private TrackingByteBufferBuilder(int capacity) {
            super(capacity);
        }

        @Override
        public void close() {
            this.closed = true;
            super.close();
        }
    }
}
