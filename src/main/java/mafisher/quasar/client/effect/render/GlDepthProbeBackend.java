package mafisher.quasar.client.effect.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import mafisher.quasar.client.effect.screen.ScreenGlState;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL43C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenGL implementation of the depth-occlusion probe.
 *
 * <p>It draws a unit cube scaled to the effect bounds into a depth-only framebuffer that shares the
 * active scene depth texture. No depth copy, mip chain, or Hi-Z pyramid is created. The query is
 * asynchronous; a missing result always means "draw".</p>
 */
@SuppressWarnings("checkstyle:BooleanExpressionComplexity")
final class GlDepthProbeBackend implements DepthProbeBackend {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlDepthProbeBackend.class);
    private static final float PROBE_INFLATION = 1.0e-3F;

    private final Matrix4f probeMvp = new Matrix4f();
    private final FloatBuffer depthRange = BufferUtils.createFloatBuffer(2);
    private final AtomicBoolean loggedShader = new AtomicBoolean();
    private final DepthProbeFramebuffer framebuffer = new DepthProbeFramebuffer();
    private final DepthProbeCube cube = new DepthProbeCube();
    private Boolean supported;

    @Override
    public boolean supported() {
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            return false;
        }
        if (this.supported == null) {
            this.supported = GL.getCapabilities().OpenGL33;
        }
        return this.supported;
    }

    @Override
    public int issue(DepthProbeRequest request) {
        ShaderInstance shader = EffectShaders.DEPTH_PROBE.shaderInstance();
        RenderTarget target = request.depthTarget();
        if (!this.supported() || shader == null || !target.useDepth || target.getDepthTextureId() <= 0
            || target.width <= 0 || target.height <= 0
            || hasActiveQuery() || hasUnsafeRasterState()) {
            return 0;
        }
        ScreenGlState previousState = ScreenGlState.capture();
        boolean previousStencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        this.depthRange.clear();
        GL11.glGetFloatv(GL11.GL_DEPTH_RANGE, this.depthRange);
        float previousDepthRangeNear = this.depthRange.get(0);
        float previousDepthRangeFar = this.depthRange.get(1);
        try {
            if (!this.framebuffer.bind(target)) {
                return 0;
            }
            configureProbeState(target.width, target.height);
            return this.drawProbe(request, shader, this.cube.buffer());
        } catch (RuntimeException exception) {
            if (this.loggedShader.compareAndSet(false, true)) {
                LOGGER.warn("Could not issue a provider-depth occlusion probe; effects stay visible",
                    exception);
            }
            return 0;
        } finally {
            VertexBuffer.unbind();
            GL11.glDepthRange(previousDepthRangeNear, previousDepthRangeFar);
            if (previousStencil) {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
            } else {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
            previousState.restore();
        }
    }

    @Override
    public boolean isResultAvailable(int query) {
        return query != 0 && GL32C.glGetQueryObjecti(query, GL32C.GL_QUERY_RESULT_AVAILABLE) != 0;
    }

    @Override
    public boolean anySamplesPassed(int query) {
        return query != 0 && GL32C.glGetQueryObjecti(query, GL32C.GL_QUERY_RESULT) != 0;
    }

    @Override
    public void deleteQuery(int query) {
        if (query <= 0) {
            return;
        }
        if (RenderSystem.isOnRenderThreadOrInit()) {
            GL32C.glDeleteQueries(query);
        } else {
            RenderSystem.recordRenderCall(() -> GL32C.glDeleteQueries(query));
        }
    }

    @Override
    public void reset() {
        this.cube.close();
        this.framebuffer.delete();
        this.supported = null;
    }

    private int drawProbe(DepthProbeRequest request, ShaderInstance shader, VertexBuffer cubeBuffer) {
        if (cubeBuffer == null) {
            return 0;
        }
        this.drawMvp(request);
        int queryType = GL.getCapabilities().OpenGL43
            ? GL43C.GL_ANY_SAMPLES_PASSED_CONSERVATIVE : GL33C.GL_ANY_SAMPLES_PASSED;
        int query = GL32C.glGenQueries();
        if (query == 0) {
            return 0;
        }
        boolean success = false;
        try {
            GL32C.glBeginQuery(queryType, query);
        } catch (RuntimeException exception) {
            GL32C.glDeleteQueries(query);
            throw exception;
        }
        try {
            shader.apply();
            if (shader.getUniform("ProbeMVP") == null) {
                if (this.loggedShader.compareAndSet(false, true)) {
                    LOGGER.warn("Depth probe shader does not declare ProbeMVP; provider-depth "
                        + "occlusion is disabled");
                }
                return 0;
            }
            shader.safeGetUniform("ProbeMVP").set(this.probeMvp);
            cubeBuffer.bind();
            cubeBuffer.draw();
            success = true;
            return query;
        } finally {
            GL32C.glEndQuery(queryType);
            shader.clear();
            if (!success) {
                GL32C.glDeleteQueries(query);
            }
        }
    }

    private void drawMvp(DepthProbeRequest request) {
        var bounds = request.bounds();
        double centerX = (bounds.minX + bounds.maxX) * 0.5 - request.cameraX();
        double centerY = (bounds.minY + bounds.maxY) * 0.5 - request.cameraY();
        double centerZ = (bounds.minZ + bounds.maxZ) * 0.5 - request.cameraZ();
        float halfX = Math.max((float)(bounds.maxX - bounds.minX) * 0.5F, PROBE_INFLATION);
        float halfY = Math.max((float)(bounds.maxY - bounds.minY) * 0.5F, PROBE_INFLATION);
        float halfZ = Math.max((float)(bounds.maxZ - bounds.minZ) * 0.5F, PROBE_INFLATION);
        this.probeMvp.set(request.projection());
        this.probeMvp.mul(request.viewRotation());
        this.probeMvp.translate((float)centerX, (float)centerY, (float)centerZ);
        this.probeMvp.scale(halfX + PROBE_INFLATION, halfY + PROBE_INFLATION,
            halfZ + PROBE_INFLATION);
    }

    private static void configureProbeState(int width, int height) {
        EffectRenderState.forceViewport(0, 0, width, height);
        GlStateManager._disableScissorTest();
        GlStateManager._disableBlend();
        GlStateManager._disableCull();
        GlStateManager._disablePolygonOffset();
        GlStateManager._colorMask(false, false, false, false);
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL11.GL_LEQUAL);
        GlStateManager._depthMask(false);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDepthRange(0.0, 1.0);
    }

    private static boolean hasActiveQuery() {
        if (GL32C.glGetQueryi(GL33C.GL_ANY_SAMPLES_PASSED, GL32C.GL_CURRENT_QUERY) != 0
            || GL32C.glGetQueryi(GL33C.GL_TIME_ELAPSED, GL32C.GL_CURRENT_QUERY) != 0
            || GL32C.glGetQueryi(GL15C.GL_SAMPLES_PASSED, GL32C.GL_CURRENT_QUERY) != 0
            || GL32C.glGetQueryi(GL30C.GL_PRIMITIVES_GENERATED, GL32C.GL_CURRENT_QUERY) != 0
            || GL32C.glGetQueryi(GL30C.GL_TRANSFORM_FEEDBACK_PRIMITIVES_WRITTEN,
                GL32C.GL_CURRENT_QUERY) != 0) {
            return true;
        }
        return GL.getCapabilities().OpenGL43
            && GL32C.glGetQueryi(GL43C.GL_ANY_SAMPLES_PASSED_CONSERVATIVE,
                GL32C.GL_CURRENT_QUERY) != 0;
    }

    private static boolean hasUnsafeRasterState() {
        if (GL11.glIsEnabled(GL30C.GL_RASTERIZER_DISCARD)) {
            return true;
        }
        for (int plane = 0; plane < 8; plane++) {
            if (GL11.glIsEnabled(GL30C.GL_CLIP_DISTANCE0 + plane)) {
                return true;
            }
        }
        return false;
    }
}
