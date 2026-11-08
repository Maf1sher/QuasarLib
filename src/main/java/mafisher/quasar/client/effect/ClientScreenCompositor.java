package mafisher.quasar.client.effect;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Objects;
import mafisher.quasar.effect.EffectQualityTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

/** Optional depth-aware per-effect mask composition for client screen effects. */
final class ClientScreenCompositor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int READ_FRAMEBUFFER = 0x8CA8;
    private static final int DRAW_FRAMEBUFFER = 0x8CA9;
    private static final int COLOR_BUFFER_BIT = 0x00004000;
    private static final int NEAREST_FILTER = 0x2600;

    private final ScreenEffectOperationRegistry operationRegistry;
    private final ScreenEffectScratchTargetPool scratchTargets = new ScreenEffectScratchTargetPool();
    private TextureTarget rawSceneTarget;
    private TextureTarget sceneColorTarget;
    private TextureTarget compositionTarget;
    private TextureTarget screenMaskTarget;
    private boolean hadScreenEffects;
    private boolean failAfterCompositionOnce;
    private boolean warnedPresentationViewport;
    private boolean warnedDegenerateCamera;
    private boolean diagnosticsRequested;
    private int unavailableWidth = -1;
    private int unavailableHeight = -1;

    ClientScreenCompositor(ScreenEffectOperationRegistry operationRegistry) {
        this.operationRegistry = Objects.requireNonNull(operationRegistry, "operationRegistry");
    }

    void render(RenderLevelStageEvent event, ClientVisualEffectManager manager, List<ScreenEffectBatch> batches) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }

        if (batches.isEmpty()) {
            this.hadScreenEffects = false;
            this.unavailableWidth = -1;
            this.unavailableHeight = -1;
            this.releaseTargets();
            return;
        }
        if (!this.hadScreenEffects) {
            this.unavailableWidth = -1;
            this.unavailableHeight = -1;
        }
        this.hadScreenEffects = true;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || manager == null) {
            this.releaseTargets();
            return;
        }

        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        int width = mainTarget.width;
        int height = mainTarget.height;
        if (width <= 0 || height <= 0) {
            this.releaseTargets();
            return;
        }
        if (width != this.unavailableWidth || height != this.unavailableHeight) {
            this.unavailableWidth = -1;
            this.unavailableHeight = -1;
        }
        if (width == this.unavailableWidth && height == this.unavailableHeight) {
            return;
        }

        ClientQualityController.beginLevelGpu();
        try {
            try {
                EffectQualityTier quality = ClientQualityController.tier();
                int targetWidth = Math.max(1, Math.round(width * quality.screenScale()));
                int targetHeight = Math.max(1, Math.round(height * quality.screenScale()));

                Matrix4f modelViewMatrix = new Matrix4f(event.getModelViewMatrix());
                Matrix4f projectionMatrix = new Matrix4f(event.getProjectionMatrix());
                if (!ScreenEffectCameraState.isUsable(modelViewMatrix, projectionMatrix)) {
                    this.warnDegenerateCameraOnce(projectionMatrix);
                    return;
                }

                ScreenEffectCameraState camera;
                try {
                    camera = new ScreenEffectCameraState(
                        event.getCamera().getPosition(),
                        modelViewMatrix,
                        projectionMatrix,
                        targetWidth,
                        targetHeight,
                        event.getPartialTick().getGameTimeDeltaPartialTick(false)
                    );
                } catch (IllegalArgumentException degenerateCamera) {
                    // A single frame can have a degenerate projection while the
                    // FOV interpolation is still at zero. Skip that frame only;
                    // never disable screen composition for the session.
                    this.warnDegenerateCameraOnce(projectionMatrix);
                    return;
                }
                this.warnedDegenerateCamera = false;

                this.ensureTargets(targetWidth, targetHeight);
                if (mainTarget.getDepthTextureId() <= 0 || this.screenMaskTarget.getDepthTextureId() <= 0) {
                    throw new IllegalStateException("The main render target has no sampleable depth attachment");
                }

                this.captureSceneColor(mainTarget, this.rawSceneTarget);

                RenderTarget currentColor = this.rawSceneTarget;
                RenderTarget alternateColor = this.sceneColorTarget;
                boolean firstBatch = true;
                for (ScreenEffectBatch batch : batches) {
                    this.renderMask(event, manager, mainTarget, batch);
                    this.renderOperation(currentColor, alternateColor, batch, camera, quality);
                    currentColor = alternateColor;
                    if (firstBatch) {
                        firstBatch = false;
                        alternateColor = this.compositionTarget;
                    } else {
                        alternateColor = alternateColor == this.sceneColorTarget
                            ? this.compositionTarget : this.sceneColorTarget;
                    }
                }

                if (this.failAfterCompositionOnce) {
                    this.failAfterCompositionOnce = false;
                    throw new IllegalStateException("Screen compositor failure injection");
                }

                boolean diagnose = this.diagnosticsRequested;
                if (diagnose) {
                    this.diagnosticsRequested = false;
                    this.logPresentationDiagnostics("before present", mainTarget, currentColor, quality,
                        batches.size());
                }
                this.presentToMain(mainTarget, currentColor);
                if (diagnose) {
                    this.logPresentationDiagnostics("after present", mainTarget, currentColor, quality,
                        batches.size());
                }
                this.restoreMainTargetState(mainTarget);
            } catch (RuntimeException exception) {
                this.failAfterCompositionOnce = false;
                this.restoreMainTargetState(mainTarget);
                this.unavailableWidth = width;
                this.unavailableHeight = height;
                this.releaseTargets();
                LOGGER.warn("Depth-aware screen effects are unavailable at {}x{}; continuing without them.",
                    width, height, exception);
            }
        } finally {
            ClientQualityController.endLevelGpu();
        }
    }

    void onLevelUnload() {
        this.hadScreenEffects = false;
        this.failAfterCompositionOnce = false;
        this.diagnosticsRequested = false;
        this.warnedDegenerateCamera = false;
        this.unavailableWidth = -1;
        this.unavailableHeight = -1;
        this.releaseTargets();
    }

    void onResourceReload() {
        this.failAfterCompositionOnce = false;
        this.diagnosticsRequested = false;
        this.warnedDegenerateCamera = false;
        this.unavailableWidth = -1;
        this.unavailableHeight = -1;
        this.releaseTargets();
    }

    boolean armFailureAfterCompositionOnce() {
        this.failAfterCompositionOnce = true;
        return true;
    }

    /** Logs one frame of presentation GL state and target dimensions on the next composed frame. */
    boolean requestDiagnostics() {
        this.diagnosticsRequested = true;
        return true;
    }

    private void renderOperation(RenderTarget sourceColor, RenderTarget destination,
        ScreenEffectBatch batch, ScreenEffectCameraState camera, EffectQualityTier quality) {
        ScreenEffectOperationRenderer renderer = this.operationRegistry.find(batch.settings().operation())
            .orElseThrow(() -> new IllegalStateException("No screen effect operation registered for "
                + batch.settings().operation().id()));
        try {
            ScreenEffectOperationContext context = new ScreenEffectOperationContext(
                batch.settings(),
                batch.operationContext(),
                batch.mask(),
                camera,
                quality,
                sourceColor,
                this.rawSceneTarget,
                destination,
                this.screenMaskTarget,
                this.scratchTargets
            );
            renderer.render(context);
        } finally {
            this.scratchTargets.releaseOperation();
            this.restoreOperationState();
        }
    }

    private void renderMask(RenderLevelStageEvent event, ClientVisualEffectManager manager, RenderTarget mainTarget,
        ScreenEffectBatch batch) {
        RenderSystem.disableScissor();
        this.screenMaskTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        this.screenMaskTarget.clear(false);
        this.screenMaskTarget.copyDepthFrom(mainTarget);
        EffectRenderState.bindTarget(this.screenMaskTarget);
        try {
            manager.renderScreenMask(event, batch);
        } finally {
            this.screenMaskTarget.unbindWrite();
        }
    }

    private void ensureTargets(int width, int height) {
        this.rawSceneTarget = this.ensureTarget(this.rawSceneTarget, width, height, false);
        this.sceneColorTarget = this.ensureTarget(this.sceneColorTarget, width, height, false);
        this.compositionTarget = this.ensureTarget(this.compositionTarget, width, height, false);
        this.screenMaskTarget = this.ensureTarget(this.screenMaskTarget, width, height, true);
    }

    private TextureTarget ensureTarget(TextureTarget target, int width, int height, boolean useDepth) {
        if (target == null) {
            target = new TextureTarget(width, height, useDepth, Minecraft.ON_OSX);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
        }
        target.setFilterMode(NEAREST_FILTER);
        return target;
    }

    /**
     * Presents the quality-scaled composition once at the physical main-target size.
     * The composition itself is smaller than the window at LOW/MEDIUM quality, so its
     * own dimensions must never become the presentation viewport.
     */
    private void presentToMain(RenderTarget mainTarget, RenderTarget composition) {
        int width = mainTarget.width;
        int height = mainTarget.height;
        if (width <= 0 || height <= 0) {
            return;
        }
        if (!this.warnedPresentationViewport
            && (mainTarget.viewWidth != width || mainTarget.viewHeight != height)) {
            this.warnedPresentationViewport = true;
            LOGGER.warn("Main target is {}x{} but its viewport is {}x{}; "
                    + "screen composition will use the physical framebuffer size.",
                width, height, mainTarget.viewWidth, mainTarget.viewHeight);
        }

        ShaderInstance shader = EffectShaders.SCREEN_PRESENT.shaderInstance();
        if (shader == null) {
            // Shader reload in progress; leave the main target untouched.
            return;
        }

        // Bind through the raw framebuffer path and force the viewport instead
        // of relying on the vanilla blit_screen path or Sodium's cached
        // GlStateManager viewport. A stale viewport cache would draw the
        // composition into only the smaller quality-scaled rectangle.
        EffectRenderState.bindTarget(mainTarget);
        RenderSystem.disableScissor();
        RenderSystem.colorMask(true, true, true, false);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        boolean shaderApplied = false;
        try {
            shader.setSampler("DiffuseSampler", composition.getColorTextureId());
            shader.apply();
            shaderApplied = true;
            BufferBuilder builder = RenderSystem.renderThreadTesselator()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            builder.addVertex(0.0F, 0.0F, 0.0F);
            builder.addVertex(1.0F, 0.0F, 0.0F);
            builder.addVertex(1.0F, 1.0F, 0.0F);
            builder.addVertex(0.0F, 1.0F, 0.0F);
            BufferUploader.draw(builder.buildOrThrow());
        } finally {
            if (shaderApplied) {
                shader.clear();
            }
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            EffectRenderState.forceViewport(width, height);
        }
    }

    private void captureSceneColor(RenderTarget mainTarget, RenderTarget captureTarget) {
        RenderSystem.disableScissor();
        GlStateManager._glBindFramebuffer(READ_FRAMEBUFFER, mainTarget.frameBufferId);
        GlStateManager._glBindFramebuffer(DRAW_FRAMEBUFFER, captureTarget.frameBufferId);
        GlStateManager._glBlitFrameBuffer(
            0, 0, mainTarget.width, mainTarget.height,
            0, 0, captureTarget.width, captureTarget.height,
            COLOR_BUFFER_BIT, NEAREST_FILTER
        );
        mainTarget.bindWrite(false);
    }

    private void restoreMainTargetState(RenderTarget mainTarget) {
        EffectRenderState.bindTarget(mainTarget);
        this.restoreOperationState();
    }

    private void restoreOperationState() {
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.disableScissor();
        RenderSystem.enableCull();
    }

    private void warnDegenerateCameraOnce(Matrix4f projection) {
        if (this.warnedDegenerateCamera) {
            return;
        }
        this.warnedDegenerateCamera = true;
        LOGGER.debug("Skipping screen composition for a frame with a degenerate camera matrix; "
                + "projection m00={}, m11={}, m22={}, m32={}, determinant={}, fov={}, window={}x{}. "
                + "Composition retries on the next frame.",
            projection.m00(), projection.m11(), projection.m22(), projection.m32(),
            projection.determinant(), Minecraft.getInstance().options.fov().get(),
            Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight());
    }

    private void logPresentationDiagnostics(String phase, RenderTarget mainTarget, RenderTarget composition,
        EffectQualityTier quality, int batchCount) {
        int errorBefore = GlStateManager._getError();
        int[] viewport = new int[4];
        int[] scissor = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        var window = Minecraft.getInstance().getWindow();
        LOGGER.info("Screen compositor {}: viewport=[{}, {}, {}, {}] scissor=[{}, {}, {}, {}] "
                + "scissorEnabled={} depth={} blend={} cull={} drawFbo={} main={}x{} view={}x{} fbo={} "
                + "composition={}x{} quality={}({}) batches={} window={}x{} glError={}",
            phase,
            viewport[0], viewport[1], viewport[2], viewport[3],
            scissor[0], scissor[1], scissor[2], scissor[3],
            GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
            GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
            GL11.glIsEnabled(GL11.GL_BLEND),
            GL11.glIsEnabled(GL11.GL_CULL_FACE),
            drawFramebuffer,
            mainTarget.width, mainTarget.height, mainTarget.viewWidth, mainTarget.viewHeight,
            mainTarget.frameBufferId,
            composition.width, composition.height,
            quality, quality.screenScale(), batchCount,
            window.getWidth(), window.getHeight(), errorBefore);
    }

    private void releaseTargets() {
        TextureTarget rawScene = this.rawSceneTarget;
        TextureTarget sceneColor = this.sceneColorTarget;
        TextureTarget composition = this.compositionTarget;
        TextureTarget mask = this.screenMaskTarget;
        this.rawSceneTarget = null;
        this.sceneColorTarget = null;
        this.compositionTarget = null;
        this.screenMaskTarget = null;
        this.warnedPresentationViewport = false;
        this.scratchTargets.releaseAll();
        this.releaseTarget(rawScene);
        this.releaseTarget(sceneColor);
        this.releaseTarget(composition);
        this.releaseTarget(mask);
    }

    private void releaseTarget(TextureTarget target) {
        if (target == null) {
            return;
        }
        if (RenderSystem.isOnRenderThreadOrInit()) {
            target.destroyBuffers();
        } else {
            RenderSystem.recordRenderCall(target::destroyBuffers);
        }
    }
}
