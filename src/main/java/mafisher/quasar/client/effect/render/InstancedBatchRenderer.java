package mafisher.quasar.client.effect.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL31C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws batched instanced world passes. The built-in contract streams camera-relative instance matrices, colors, and
 * data through an RGBA32F buffer texture; shaders that only declare the legacy uniform arrays are split into
 * 128-instance draws. Both paths use the shared sequential quad index buffer and indexed instancing.
 */
@SuppressWarnings("PMD.TooManyMethods")
final class InstancedBatchRenderer implements AutoCloseable {
    static final int MAX_INSTANCES_PER_SHADER = 128;

    private static final int UNIFORM_RESERVE = 64;
    private static final Logger LOGGER = LoggerFactory.getLogger(InstancedBatchRenderer.class);

    private final GpuMeshCache meshes;
    private final InstanceBufferTexture instanceBuffer = new InstanceBufferTexture();
    private final UniformInstanceBuffers uniformBuffers = new UniformInstanceBuffers();
    private final InstanceUniformLocationCache<ShaderInstance> uniformLocations =
        new InstanceUniformLocationCache<>();
    private final Set<String> warnedContracts = new HashSet<>();
    private final Matrix4f cameraRelativeView = new Matrix4f();
    private int maxUniformInstances = -1;
    private int maxInstancesPerDraw = -1;

    InstancedBatchRenderer(GpuMeshCache meshes) {
        this.meshes = Objects.requireNonNull(meshes, "meshes");
    }

    /**
     * Returns the batch ceiling used by the builder. The buffer-texture path is limited by the driver's texture
     * buffer size; the uniform fallback is limited by vertex uniform components and the legacy 128-instance contract.
     */
    int maxInstancesPerDraw() {
        if (this.maxInstancesPerDraw < 0) {
            this.maxInstancesPerDraw = this.instanceBuffer.isSupported()
                ? this.instanceBuffer.maxInstances()
                : maxUniformInstances();
        }
        return this.maxInstancesPerDraw;
    }

    void draw(List<InstancedBatchBuilder.InstancedDraw> draws, Matrix4f modelView, Matrix4f projection) {
        if (draws.isEmpty()) {
            return;
        }
        this.cameraRelativeView.set(modelView).setTranslation(0.0F, 0.0F, 0.0F);
        for (InstancedBatchBuilder.InstancedDraw draw : draws) {
            GpuMeshCache.GpuMesh mesh = this.meshes.get(draw.key().renderType(), draw.key().mesh(),
                1.0F, 1.0F, 1.0F, 1.0F, draw.key().overlay(), draw.key().light());
            if (mesh != null) {
                this.drawResolvedBatch(draw.key(), mesh, draw.instances(), draw.start(),
                    draw.end(), this.cameraRelativeView, projection);
            }
        }
    }

    /** Draws one batch whose GPU mesh was resolved and cached by the caller. */
    void drawResolvedBatch(InstanceBatchDescriptor batch, GpuMeshCache.GpuMesh mesh,
        List<InstancedBatchBuilder.Instance> instances, int start, int end, Matrix4f modelView,
        Matrix4f projection) {
        if (start == end || mesh == null) {
            return;
        }
        this.cameraRelativeView.set(modelView).setTranslation(0.0F, 0.0F, 0.0F);
        float[] shaderColor = RenderSystem.getShaderColor();
        float previousRed = shaderColor[0];
        float previousGreen = shaderColor[1];
        float previousBlue = shaderColor[2];
        float previousAlpha = shaderColor[3];
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        try {
            this.drawBatchInternal(batch, mesh, instances, start, end, this.cameraRelativeView,
                projection);
        } finally {
            RenderSystem.setShaderColor(previousRed, previousGreen, previousBlue, previousAlpha);
        }
    }

    private void drawBatchInternal(InstanceBatchDescriptor batch, GpuMeshCache.GpuMesh mesh,
        List<InstancedBatchBuilder.Instance> instances, int start, int end, Matrix4f modelView,
        Matrix4f projection) {
        ShaderInstance shader = null;
        boolean renderStateSetUp = false;
        try {
            batch.renderType().setupRenderState();
            renderStateSetUp = true;
            shader = RenderSystem.getShader();
            if (shader == null) {
                warnOnce("shader:" + batch.id(),
                    "Instanced pass " + batch.id() + " has no active shader");
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
                warnOnce("contract:" + batch.id(), "Instanced shader for pass " + batch.id()
                    + " is missing InstanceBuffer and the legacy instance arrays");
                return;
            }

            boolean drawn = false;
            if (locations.usesBuffer()) {
                drawn = drawBuffered(batch.id(), mesh, instances, start, end,
                    locations.instanceBuffer());
            }
            if (!drawn && locations.usesUniformArrays()) {
                drawUniform(mesh, instances, start, end, locations);
            } else if (!drawn) {
                warnOnce("buffer:" + batch.id(), "Could not draw instanced pass " + batch.id()
                    + " through the buffer texture or the uniform fallback");
            }
        } finally {
            GpuMeshCache.GpuMesh.unbind();
            if (shader != null) {
                shader.clear();
            }
            if (renderStateSetUp) {
                batch.renderType().clearRenderState();
            }
        }
    }

    /**
     * Draws one batch through the buffer texture. A failure before the first chunk allows the caller to fall back to
     * uniforms; a failure after some chunks completes the path to avoid drawing the same instances twice.
     */
    private boolean drawBuffered(String id, GpuMeshCache.GpuMesh mesh,
        List<InstancedBatchBuilder.Instance> instances, int start, int end, int samplerLocation) {
        int limit = Math.max(1, this.instanceBuffer.maxInstances());
        boolean drawn = false;
        for (int chunkStart = start; chunkStart < end; chunkStart += limit) {
            int chunkEnd = Math.min(chunkStart + limit, end);
            if (!this.instanceBuffer.bind(samplerLocation, instances, chunkStart, chunkEnd, id)) {
                return drawn;
            }
            try {
                drawChunk(mesh, chunkEnd - chunkStart);
                drawn = true;
            } finally {
                this.instanceBuffer.unbind();
            }
        }
        return drawn;
    }

    private void drawUniform(GpuMeshCache.GpuMesh mesh,
        List<InstancedBatchBuilder.Instance> instances, int start, int end,
        InstanceUniformLocationCache.Locations locations) {
        int limit = maxUniformInstances();
        for (int chunkStart = start; chunkStart < end; chunkStart += limit) {
            int chunkEnd = Math.min(chunkStart + limit, end);
            this.uniformBuffers.upload(locations.instances(), locations.colors(), locations.data(),
                instances, chunkStart, chunkEnd);
            drawChunk(mesh, chunkEnd - chunkStart);
        }
    }

    private int maxUniformInstances() {
        if (this.maxUniformInstances < 0) {
            int components = 0;
            try {
                components = GL20C.glGetInteger(GL20C.GL_MAX_VERTEX_UNIFORM_COMPONENTS);
            } catch (RuntimeException ignored) {
                // Fall through to the conservative GL 3.3 minimum.
            }
            if (components <= 0) {
                components = 1_024;
            }
            this.maxUniformInstances = Math.max(1,
                Math.min(MAX_INSTANCES_PER_SHADER,
                    (components - UNIFORM_RESERVE) / UniformInstanceBuffers.FLOATS_PER_INSTANCE));
        }
        return this.maxUniformInstances;
    }

    private static void drawChunk(GpuMeshCache.GpuMesh mesh, int instances) {
        mesh.bind();
        RenderSystem.AutoStorageIndexBuffer indices = mesh.bindSequentialIndices();
        GL31C.glDrawElementsInstanced(VertexFormat.Mode.QUADS.asGLMode, mesh.indexCount(),
            indices.type().asGLType, 0L, instances);
    }

    private void warnOnce(String key, String message) {
        if (this.warnedContracts.add(key)) {
            LOGGER.warn(message);
        }
    }

    /** Drops shader-keyed locations after a resource reload replaces programs. */
    void onResourceReload() {
        this.uniformLocations.clear();
        this.warnedContracts.clear();
    }

    @Override
    public void close() {
        this.uniformBuffers.close();
        this.uniformLocations.clear();
        this.warnedContracts.clear();
        this.instanceBuffer.close();
    }
}
