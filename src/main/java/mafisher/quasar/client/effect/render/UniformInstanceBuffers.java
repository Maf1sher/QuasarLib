package mafisher.quasar.client.effect.render;

import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.FloatBuffer;
import java.util.List;
import org.lwjgl.system.MemoryUtil;

/** Reusable uniform-array buffers for the legacy instancing contract. */
final class UniformInstanceBuffers implements AutoCloseable {
    static final int FLOATS_PER_MATRIX = 16;
    static final int FLOATS_PER_COLOR = 4;
    static final int FLOATS_PER_DATA = 4;
    static final int FLOATS_PER_INSTANCE = FLOATS_PER_MATRIX + FLOATS_PER_COLOR + FLOATS_PER_DATA;

    private int capacity = -1;
    private FloatBuffer matrices;
    private FloatBuffer colors;
    private FloatBuffer data;

    void upload(int matricesLocation, int colorsLocation, int dataLocation,
        List<InstancedBatchBuilder.Instance> instances) {
        ensureCapacity(instances.size());
        this.matrices.clear();
        this.colors.clear();
        this.data.clear();
        for (InstancedBatchBuilder.Instance instance : instances) {
            int matrixOffset = this.matrices.position();
            instance.model().get(matrixOffset, this.matrices);
            this.matrices.position(matrixOffset + FLOATS_PER_MATRIX);
            this.colors.put(instance.red()).put(instance.green()).put(instance.blue()).put(instance.alpha());
            this.data.put(instance.effectDataX()).put(instance.effectDataY())
                .put(instance.effectDataZ()).put(instance.effectDataW());
        }
        this.matrices.flip();
        this.colors.flip();
        this.data.flip();
        RenderSystem.glUniformMatrix4(matricesLocation, false, this.matrices);
        RenderSystem.glUniform4(colorsLocation, this.colors);
        RenderSystem.glUniform4(dataLocation, this.data);
    }

    private void ensureCapacity(int instances) {
        if (instances <= this.capacity) {
            return;
        }
        close();
        this.capacity = instances;
        this.matrices = MemoryUtil.memAllocFloat(instances * FLOATS_PER_MATRIX);
        this.colors = MemoryUtil.memAllocFloat(instances * FLOATS_PER_COLOR);
        this.data = MemoryUtil.memAllocFloat(instances * FLOATS_PER_DATA);
    }

    @Override
    public void close() {
        if (this.matrices != null) {
            MemoryUtil.memFree(this.matrices);
            this.matrices = null;
        }
        if (this.colors != null) {
            MemoryUtil.memFree(this.colors);
            this.colors = null;
        }
        if (this.data != null) {
            MemoryUtil.memFree(this.data);
            this.data = null;
        }
        this.capacity = -1;
    }
}
