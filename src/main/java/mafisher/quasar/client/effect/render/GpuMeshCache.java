package mafisher.quasar.client.effect.render;

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
import java.util.Objects;
import mafisher.quasar.api.client.render.EffectMesh;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-owned cache of static GPU meshes used by instanced and single-mesh world passes. Meshes are keyed by format,
 * mode, mesh identity, baked tint, overlay, and light, because those vertex values are uploaded once.
 */
final class GpuMeshCache implements AutoCloseable {
    private static final int MAX_MESHES = 256;
    private static final int FULLBRIGHT_LIGHT = 0x00F000F0;
    private static final Logger LOGGER = LoggerFactory.getLogger(GpuMeshCache.class);

    private final Map<GpuMeshKey, GpuMesh> meshes = new LinkedHashMap<>(16, 0.75F, true);

    /** Returns a cached white, fullbright mesh or uploads it. Must run on the render thread. */
    GpuMesh get(RenderType renderType, EffectMesh mesh) {
        return get(renderType, mesh, 1.0F, 1.0F, 1.0F, 1.0F, 0, FULLBRIGHT_LIGHT);
    }

    /** Returns a cached mesh with baked vertex tint, overlay, and light, or uploads it. */
    GpuMesh get(RenderType renderType, EffectMesh mesh, float red, float green, float blue, float alpha,
        int overlay, int light) {
        if (!DefaultVertexFormat.BLOCK.equals(renderType.format())
            || renderType.mode() != VertexFormat.Mode.QUADS) {
            LOGGER.warn("Effect mesh pass {} requires BLOCK/QUADS; skipping", renderType);
            return null;
        }
        GpuMeshKey key = new GpuMeshKey(renderType.format(), renderType.mode(), mesh,
            red, green, blue, alpha, overlay, light);
        GpuMesh existing = this.meshes.get(key);
        if (existing != null) {
            return existing;
        }
        GpuMesh created;
        try {
            created = upload(mesh, key);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not upload effect mesh", exception);
            return null;
        }
        this.meshes.put(key, created);
        this.trim();
        return created;
    }

    int size() {
        return this.meshes.size();
    }

    private static GpuMesh upload(EffectMesh mesh, GpuMeshKey key) {
        return upload(mesh, key, ByteBufferBuilder::new);
    }

    static GpuMesh upload(EffectMesh mesh, GpuMeshKey key, ByteBufferAllocator allocator) {
        Objects.requireNonNull(mesh, "mesh");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(allocator, "allocator");
        VertexFormat format = key.format();
        int capacity = Math.max(1_024, mesh.vertexCount() * format.getVertexSize());
        try (ByteBufferBuilder bytes = allocator.create(capacity)) {
            BufferBuilder builder = new BufferBuilder(bytes, key.mode(), format);
            PoseStack poseStack = new PoseStack();
            mesh.emit(builder, poseStack.last(), key.red(), key.green(), key.blue(), key.alpha(),
                key.overlay(), key.light());
            try (MeshData data = builder.buildOrThrow()) {
                VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                try {
                    buffer.bind();
                    buffer.upload(data);
                } catch (RuntimeException | Error exception) {
                    buffer.close();
                    throw exception;
                }
                return new GpuMesh(buffer, mesh.vertexCount());
            }
        }
    }

    @FunctionalInterface
    interface ByteBufferAllocator {
        ByteBufferBuilder create(int capacity);
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

    record GpuMeshKey(
        VertexFormat format,
        VertexFormat.Mode mode,
        EffectMesh mesh,
        float red,
        float green,
        float blue,
        float alpha,
        int overlay,
        int light
    ) {
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

        /** Draws this static mesh with the supplied matrices and the currently active shader. */
        void drawWithShader(Matrix4f modelView, Matrix4f projection, ShaderInstance shader) {
            this.buffer.drawWithShader(modelView, projection, shader);
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
