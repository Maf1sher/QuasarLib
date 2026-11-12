package mafisher.quasar.client.effect;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.FloatBuffer;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws batched instanced world passes with the engine's uniform-array contract. Passes use {@code BLOCK}/{@code QUADS}
 * meshes, so each draw binds the shared sequential quad index buffer and uses indexed instancing. The instance arrays
 * are sized from {@code GL_MAX_VERTEX_UNIFORM_COMPONENTS}, so minimum-spec drivers split the same batch across several
 * draws.
 */
final class InstancedBatchRenderer implements AutoCloseable {
    static final int MAX_INSTANCES_PER_SHADER = 128;

    private static final int FLOATS_PER_MATRIX = 16;
    private static final int FLOATS_PER_COLOR = 4;
    private static final int FLOATS_PER_DATA = 4;
    private static final int FLOATS_PER_INSTANCE = FLOATS_PER_MATRIX + FLOATS_PER_COLOR + FLOATS_PER_DATA;
    private static final int UNIFORM_RESERVE = 64;
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");

    private final GpuMeshCache meshes;
    private final InstanceUniformLocationCache<ShaderInstance> uniformLocations =
        new InstanceUniformLocationCache<>();
    private int maxInstancesPerDraw = -1;
    private int bufferCapacity = -1;
    private FloatBuffer matrices;
    private FloatBuffer colors;
    private FloatBuffer data;

    InstancedBatchRenderer(GpuMeshCache meshes) {
        this.meshes = Objects.requireNonNull(meshes, "meshes");
    }

    int maxInstancesPerDraw() {
        if (this.maxInstancesPerDraw < 0) {
            int components = 0;
            try {
                components = GL20C.glGetInteger(GL20C.GL_MAX_VERTEX_UNIFORM_COMPONENTS);
            } catch (RuntimeException ignored) {
                // Fall through to the conservative GL 3.3 minimum.
            }
            if (components <= 0) {
                components = 1_024;
            }
            this.maxInstancesPerDraw = Math.max(1,
                Math.min(MAX_INSTANCES_PER_SHADER, (components - UNIFORM_RESERVE) / FLOATS_PER_INSTANCE));
        }
        return this.maxInstancesPerDraw;
    }

    void draw(List<InstancedBatchBuilder.InstancedDraw> draws, Matrix4f modelView, Matrix4f projection) {
        if (draws.isEmpty()) {
            return;
        }
        int limit = this.maxInstancesPerDraw();
        this.ensureCapacity(limit);
        for (InstancedBatchBuilder.InstancedDraw draw : draws) {
            List<InstancedBatchBuilder.Instance> instances = draw.instances();
            for (int start = 0; start < instances.size(); start += limit) {
                int end = Math.min(start + limit, instances.size());
                this.drawChunk(draw.key(), instances.subList(start, end), modelView, projection);
            }
        }
    }

    private void drawChunk(InstancedBatchBuilder.BatchKey key, List<InstancedBatchBuilder.Instance> instances,
        Matrix4f modelView, Matrix4f projection) {
        GpuMeshCache.GpuMesh mesh = this.meshes.get(key.renderType(), key.mesh());
        if (mesh == null) {
            return;
        }

        RenderType renderType = key.renderType();
        ShaderInstance shader = null;
        renderType.setupRenderState();
        try {
            shader = RenderSystem.getShader();
            if (shader == null) {
                LOGGER.warn("Instanced pass {} has no active shader", key.id());
                return;
            }
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection,
                Minecraft.getInstance().getWindow());
            shader.apply();

            InstanceUniformLocationCache.Locations locations = this.uniformLocations.get(
                shader,
                (instance, name) -> GL20C.glGetUniformLocation(instance.getId(), name)
            );
            if (!locations.isComplete()) {
                LOGGER.warn("Instanced shader for pass {} is missing Instances/InstanceColors/InstanceData",
                    key.id());
                return;
            }

            this.fillBuffers(instances);
            RenderSystem.glUniformMatrix4(locations.instances(), false, this.matrices);
            RenderSystem.glUniform4(locations.colors(), this.colors);
            RenderSystem.glUniform4(locations.data(), this.data);

            mesh.bind();
            RenderSystem.AutoStorageIndexBuffer indices = mesh.bindSequentialIndices();
            GL31C.glDrawElementsInstanced(VertexFormat.Mode.QUADS.asGLMode, mesh.indexCount(),
                indices.type().asGLType, 0L, instances.size());
        } finally {
            GpuMeshCache.GpuMesh.unbind();
            if (shader != null) {
                shader.clear();
            }
            renderType.clearRenderState();
        }
    }

    private void ensureCapacity(int instances) {
        if (instances <= this.bufferCapacity) {
            return;
        }
        this.freeBuffers();
        this.bufferCapacity = instances;
        this.matrices = MemoryUtil.memAllocFloat(instances * FLOATS_PER_MATRIX);
        this.colors = MemoryUtil.memAllocFloat(instances * FLOATS_PER_COLOR);
        this.data = MemoryUtil.memAllocFloat(instances * FLOATS_PER_DATA);
    }

    private void fillBuffers(List<InstancedBatchBuilder.Instance> instances) {
        this.matrices.clear();
        this.colors.clear();
        this.data.clear();
        for (InstancedBatchBuilder.Instance instance : instances) {
            instance.model().get(this.matrices);
            this.colors.put(instance.red()).put(instance.green()).put(instance.blue()).put(instance.alpha());
            this.data.put(instance.effectDataX()).put(instance.effectDataY())
                .put(instance.effectDataZ()).put(instance.effectDataW());
        }
        this.matrices.flip();
        this.colors.flip();
        this.data.flip();
    }

    private void freeBuffers() {
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
        this.bufferCapacity = -1;
    }

    /** Drops shader-keyed locations after a resource reload replaces programs. */
    void onResourceReload() {
        this.uniformLocations.clear();
    }

    @Override
    public void close() {
        this.freeBuffers();
        this.uniformLocations.clear();
    }
}
