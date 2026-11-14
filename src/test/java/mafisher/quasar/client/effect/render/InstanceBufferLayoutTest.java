package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class InstanceBufferLayoutTest {
    @Test
    void packsSixTexelsPerInstance() {
        assertEquals(24, InstanceBufferLayout.FLOATS_PER_INSTANCE);
        assertEquals(96, InstanceBufferLayout.BYTES_PER_INSTANCE);
        assertEquals(48, InstanceBufferLayout.floatCount(2));
        assertEquals(192L, InstanceBufferLayout.byteSize(2));
    }

    @Test
    void writesMatrixColorAndDataInOrder() {
        FloatBuffer buffer = directBuffer();
        Matrix4f model = new Matrix4f().translate(1.0F, 2.0F, 3.0F);

        InstanceBufferLayout.write(buffer, instance(model, 0.25F, 0.5F, 0.75F, 1.0F,
            4.0F, 5.0F, 6.0F, 7.0F));
        buffer.flip();

        assertEquals(24, buffer.limit());
        assertEquals(1.0F, buffer.get(12));
        assertEquals(2.0F, buffer.get(13));
        assertEquals(3.0F, buffer.get(14));
        assertEquals(1.0F, buffer.get(15));
        assertEquals(0.25F, buffer.get(16));
        assertEquals(0.5F, buffer.get(17));
        assertEquals(0.75F, buffer.get(18));
        assertEquals(1.0F, buffer.get(19));
        assertEquals(4.0F, buffer.get(20));
        assertEquals(5.0F, buffer.get(21));
        assertEquals(6.0F, buffer.get(22));
        assertEquals(7.0F, buffer.get(23));
    }

    @Test
    void rejectsNonFinitePayloads() {
        FloatBuffer buffer = directBuffer();

        assertThrows(IllegalArgumentException.class, () -> InstanceBufferLayout.write(
            buffer, instance(new Matrix4f(), 1.0F, 1.0F, 1.0F, Float.NaN, 0.0F, 0.0F, 0.0F, 0.0F)));
        assertThrows(IllegalArgumentException.class, () -> InstanceBufferLayout.write(
            buffer, instance(new Matrix4f(), 1.0F, 1.0F, 1.0F, 1.0F, Float.NaN, 0.0F, 0.0F, 0.0F)));
    }

    private static InstancedBatchBuilder.Instance instance(Matrix4f model, float red, float green, float blue,
        float alpha, float effectDataX, float effectDataY, float effectDataZ, float effectDataW) {
        return new InstancedBatchBuilder.Instance(model, red, green, blue, alpha, effectDataX, effectDataY,
            effectDataZ, effectDataW, 0.0);
    }

    private static FloatBuffer directBuffer() {
        return ByteBuffer.allocateDirect(InstanceBufferLayout.FLOATS_PER_INSTANCE * Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
    }
}
