package mafisher.quasar.client.effect.render;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.Objects;
import org.joml.Matrix4f;

/**
 * Packs instanced draws into the buffer-texture payload: four model-matrix columns, one RGBA color,
 * and one effect-data vector per instance. This class is pure so the layout is covered by headless tests.
 */
final class InstanceBufferLayout {
    static final int TEXELS_PER_INSTANCE = 6;
    static final int FLOATS_PER_TEXEL = 4;
    static final int FLOATS_PER_INSTANCE = TEXELS_PER_INSTANCE * FLOATS_PER_TEXEL;
    static final int BYTES_PER_INSTANCE = FLOATS_PER_INSTANCE * Float.BYTES;

    private InstanceBufferLayout() {
    }

    static int floatCount(int instances) {
        if (instances < 0) {
            throw new IllegalArgumentException("Instance count must be non-negative");
        }
        return Math.multiplyExact(instances, FLOATS_PER_INSTANCE);
    }

    static long byteSize(int instances) {
        return (long)floatCount(instances) * Float.BYTES;
    }

    static void write(FloatBuffer output, InstancedBatchBuilder.Instance instance) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(instance, "instance");
        requireFinite(instance.model());
        requireFinite(instance.red());
        requireFinite(instance.green());
        requireFinite(instance.blue());
        requireFinite(instance.alpha());
        requireFinite(instance.effectDataX());
        requireFinite(instance.effectDataY());
        requireFinite(instance.effectDataZ());
        requireFinite(instance.effectDataW());

        int matrixOffset = output.position();
        instance.model().get(matrixOffset, output);
        output.position(matrixOffset + 16);
        output.put(instance.red()).put(instance.green()).put(instance.blue()).put(instance.alpha());
        output.put(instance.effectDataX()).put(instance.effectDataY())
            .put(instance.effectDataZ()).put(instance.effectDataW());
    }

    private static void requireFinite(Matrix4f matrix) {
        if (!matrix.isFinite()) {
            throw new IllegalArgumentException("Instance payload contains a non-finite matrix");
        }
    }

    private static void requireFinite(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Instance payload contains a non-finite value");
        }
    }

    static void write(FloatBuffer output, List<InstancedBatchBuilder.Instance> instances) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(instances, "instances");
        for (InstancedBatchBuilder.Instance instance : instances) {
            write(output, instance);
        }
    }
}
