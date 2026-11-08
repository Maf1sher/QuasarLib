package mafisher.quasar.client.effect;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Objects;
import mafisher.quasar.effect.EffectQualityTier;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
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
                this.ensureTargets(targetWidth, targetHeight);
                if (mainTarget.getDepthTextureId() <= 0 || this.screenMaskTarget.getDepthTextureId() <= 0) {
                    throw new IllegalStateException("The main render target has no sampleable depth attachment");
                }

                this.captureSceneColor(mainTarget, this.rawSceneTarget);

                ScreenEffectCameraState camera = new ScreenEffectCameraState(
                    event.getCamera().getPosition(),
                    new Matrix4f(event.getModelViewMatrix()),
                    new Matrix4f(event.getProjectionMatrix()),
                    targetWidth,
                    targetHeight,
                    event.getPartialTick().getGameTimeDeltaPartialTick(false)
                );

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

                this.presentToMain(mainTarget, currentColor);
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
        this.unavailableWidth = -1;
        this.unavailableHeight = -1;
        this.releaseTargets();
    }

    void onResourceReload() {
        this.failAfterCompositionOnce = false;
        this.unavailableWidth = -1;
        this.unavailableHeight = -1;
        this.releaseTargets();
    }

    boolean armFailureAfterCompositionOnce() {
        this.failAfterCompositionOnce = true;
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
        this.screenMaskTarget.bindWrite(true);
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

        mainTarget.bindWrite(true);
        RenderSystem.disableScissor();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        try {
            // blitToScreen sets the viewport from its arguments. Always pass the
            // physical framebuffer size, never viewWidth/viewHeight or the scaled
            // composition size.
            composition.blitToScreen(width, height);
        } finally {
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.viewport(0, 0, width, height);
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
        mainTarget.bindWrite(true);
        RenderSystem.viewport(0, 0, mainTarget.width, mainTarget.height);
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
