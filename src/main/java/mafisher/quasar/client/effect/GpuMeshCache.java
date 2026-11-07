package mafisher.quasar.client.effect;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-owned cache of static GPU meshes used by instanced world passes. */
final class GpuMeshCache implements AutoCloseable {
    private static final int MAX_MESHES = 256;
    private static final int FULLBRIGHT_LIGHT = 0x00F000F0;
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");

    private final Map<GpuMeshKey, GpuMesh> meshes = new LinkedHashMap<>(16, 0.75F, true);

    /** Returns a cached mesh or uploads it. Must run on the render thread. */
    GpuMesh get(RenderType renderType, EffectMesh mesh) {
        if (!DefaultVertexFormat.BLOCK.equals(renderType.format())
            || renderType.mode() != VertexFormat.Mode.QUADS) {
            LOGGER.warn("Instanced pass {} requires BLOCK/QUADS; skipping", renderType);
            return null;
        }
        GpuMeshKey key = new GpuMeshKey(renderType.format(), renderType.mode(), mesh);
        GpuMesh existing = this.meshes.get(key);
        if (existing != null) {
            return existing;
        }
        GpuMesh created;
        try {
            created = upload(mesh, key.format(), key.mode());
        } catch (RuntimeException exception) {
            LOGGER.error("Could not upload instanced effect mesh", exception);
            return null;
        }
        this.meshes.put(key, created);
        this.trim();
        return created;
    }

    int size() {
        return this.meshes.size();
    }

    private static GpuMesh upload(EffectMesh mesh, VertexFormat format, VertexFormat.Mode mode) {
        int capacity = Math.max(1_024, mesh.vertexCount() * format.getVertexSize());
        ByteBufferBuilder bytes = new ByteBufferBuilder(capacity);
        BufferBuilder builder = new BufferBuilder(bytes, mode, format);
        PoseStack poseStack = new PoseStack();
        mesh.emit(builder, poseStack.last(), 1.0F, 1.0F, 1.0F, 1.0F, 0, FULLBRIGHT_LIGHT);
        MeshData data = builder.buildOrThrow();
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        try {
            buffer.bind();
            buffer.upload(data);
        } catch (RuntimeException exception) {
            buffer.close();
            throw exception;
        }
        return new GpuMesh(buffer, mesh.vertexCount());
    }

    private void trim() {
        Iterator<Map.Entry<GpuMeshKey, GpuMesh>> iterator = this.meshes.entrySet().iterator();
        while (this.meshes.size() > MAX_MESHES && iterator.hasNext()) {
            GpuMesh removed = iterator.next().getValue();
            iterator.remove();
            removed.close();
        }
    }

    @Override
    public void close() {
        for (GpuMesh mesh : this.meshes.values()) {
            mesh.close();
        }
        this.meshes.clear();
    }

    private record GpuMeshKey(VertexFormat format, VertexFormat.Mode mode, EffectMesh mesh) {
    }

    static final class GpuMesh implements AutoCloseable {
        private final VertexBuffer buffer;
        private final int vertexCount;

        private GpuMesh(VertexBuffer buffer, int vertexCount) {
            this.buffer = buffer;
            this.vertexCount = vertexCount;
        }

        int indexCount() {
            return quadIndexCount(this.vertexCount);
        }

        /**
         * Binds the shared sequential quad index buffer to the currently bound vertex array. Must run after
         * {@link #bind()}, because the element buffer binding is vertex-array state.
         */
        RenderSystem.AutoStorageIndexBuffer bindSequentialIndices() {
            RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
            indices.bind(this.indexCount());
            return indices;
        }

        void bind() {
            this.buffer.bind();
        }

        static void unbind() {
            VertexBuffer.unbind();
        }

        @Override
        public void close() {
            this.buffer.close();
        }
    }

    static int quadIndexCount(int vertexCount) {
        return VertexFormat.Mode.QUADS.indexCount(vertexCount);
    }
}
