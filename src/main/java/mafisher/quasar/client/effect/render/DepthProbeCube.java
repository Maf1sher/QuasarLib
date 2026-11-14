package mafisher.quasar.client.effect.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cached unit cube used by every depth probe. The model matrix is a uniform, so one static buffer
 * serves all owners.
 */
final class DepthProbeCube {
    private static final Logger LOGGER = LoggerFactory.getLogger(DepthProbeCube.class);
    private VertexBuffer buffer;

    VertexBuffer buffer() {
        if (this.buffer != null && !this.buffer.isInvalid()) {
            return this.buffer;
        }
        if (this.buffer != null) {
            this.buffer.close();
            this.buffer = null;
        }
        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(
            VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION
        );
        addQuad(builder, -1, -1, -1, 1, -1, -1, 1, -1, 1, -1, -1, 1);
        addQuad(builder, -1, 1, -1, -1, 1, 1, 1, 1, 1, 1, 1, -1);
        addQuad(builder, -1, -1, -1, -1, 1, -1, 1, 1, -1, 1, -1, -1);
        addQuad(builder, -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1);
        addQuad(builder, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1, 1, -1);
        addQuad(builder, 1, -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1);
        MeshData data;
        try {
            data = builder.buildOrThrow();
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not build the depth probe cube; effects stay visible", exception);
            return null;
        }
        VertexBuffer created = new VertexBuffer(VertexBuffer.Usage.STATIC);
        try {
            created.bind();
            created.upload(data);
        } catch (RuntimeException exception) {
            created.close();
            LOGGER.warn("Could not upload the depth probe cube; effects stay visible", exception);
            return null;
        } finally {
            VertexBuffer.unbind();
        }
        this.buffer = created;
        return created;
    }

    void close() {
        if (this.buffer == null) {
            return;
        }
        VertexBuffer current = this.buffer;
        this.buffer = null;
        if (RenderSystem.isOnRenderThreadOrInit()) {
            current.close();
        } else {
            RenderSystem.recordRenderCall(current::close);
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private static void addQuad(BufferBuilder builder, float x0, float y0, float z0, float x1,
        float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
        builder.addVertex(x0, y0, z0);
        builder.addVertex(x1, y1, z1);
        builder.addVertex(x2, y2, z2);
        builder.addVertex(x0, y0, z0);
        builder.addVertex(x2, y2, z2);
        builder.addVertex(x3, y3, z3);
    }
}
