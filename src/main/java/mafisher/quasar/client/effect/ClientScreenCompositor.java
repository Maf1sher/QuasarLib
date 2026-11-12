package mafisher.quasar.client.effect;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import mafisher.quasar.api.client.ScreenEffectCameraState;
import mafisher.quasar.api.client.ScreenEffectFogState;
import mafisher.quasar.api.client.ScreenEffectInputs;
import mafisher.quasar.api.client.ScreenEffectMedium;
import mafisher.quasar.api.client.ScreenEffectMediumPath;
import mafisher.quasar.api.client.ScreenEffectMediumState;
import mafisher.quasar.api.client.ScreenEffectOperationContext;
import mafisher.quasar.api.client.ScreenEffectOperationRenderer;
import mafisher.quasar.api.compat.CompatRegistry;
import mafisher.quasar.api.compat.RenderEnvironment;
import mafisher.quasar.api.effect.EffectQualityTier;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional depth-aware per-effect mask composition for client screen effects. */
final class ClientScreenCompositor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientScreenCompositor.class);
    private static final int NEAREST_FILTER = 0x2600;

    private final ScreenEffectOperationRegistry operationRegistry;
    private final ScreenEffectScratchTargetPool scratchTargets = new ScreenEffectScratchTargetPool();
    private final ShaderUniformCache uniformCache = new ShaderUniformCache();
    private final Set<ResourceLocation> failingOperations = new HashSet<>();
    private TextureTarget rawSceneTarget;
    private TextureTarget sceneColorTarget;
    private TextureTarget compositionTarget;
    private TextureTarget screenMaskTarget;
    private TextureTarget opaqueDepthTarget;
    private TextureTarget opaqueColorTarget;
    private TextureTarget foregroundDepthTarget;
    private ScreenEffectFogState fogState = ScreenEffectFogState.NONE;
    private ScreenEffectInputs requiredInputs = ScreenEffectInputs.NONE;
    private ScreenEffectInputs captureInputs = ScreenEffectInputs.ALL;
    private ScreenEffectInputs frameCaptureInputs = ScreenEffectInputs.ALL;
    private boolean opaqueDepthCaptured;
    private boolean foregroundDepthCaptured;
    private boolean hadScreenEffects;
    private boolean warnedPresentationViewport;
    private boolean warnedDegenerateCamera;
    private boolean loggedDeferredBufferFlush;
    private boolean diagnosticsRequested;
    private int unavailableWidth = -1;
    private int unavailableHeight = -1;

    ClientScreenCompositor(ScreenEffectOperationRegistry operationRegistry) {
        this.operationRegistry = Objects.requireNonNull(operationRegistry, "operationRegistry");
    }

    void render(RenderLevelStageEvent event, ClientVisualEffectManager manager, List<ScreenEffectBatch> batches,
        RenderEnvironment environment) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        boolean opaqueDepthAvailable = this.opaqueDepthCaptured;
        this.opaqueDepthCaptured = false;
        Objects.requireNonNull(environment, "environment");
        if (environment.shadowPass()) {
            // Iris owns the shadow pass; QuasarLib must not compose into it.
            return;
        }

        if (batches.isEmpty()) {
            this.hadScreenEffects = false;
            this.requiredInputs = ScreenEffectInputs.NONE;
            this.captureInputs = ScreenEffectInputs.ALL;
            this.unavailableWidth = -1;
            this.unavailableHeight = -1;
            this.releaseTargetsSafely();
            return;
        }
        if (!this.hadScreenEffects) {
            this.unavailableWidth = -1;
            this.unavailableHeight = -1;
        }
        this.hadScreenEffects = true;
        this.updateCaptureInputs(batches);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || manager == null) {
            this.releaseTargetsSafely();
            return;
        }

        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        int width = mainTarget.width;
        int height = mainTarget.height;
        if (width <= 0 || height <= 0) {
            this.releaseTargetsSafely();
            return;
        }
        if (width != this.unavailableWidth || height != this.unavailableHeight) {
            this.unavailableWidth = -1;
            this.unavailableHeight = -1;
        }
        if (width == this.unavailableWidth && height == this.unavailableHeight) {
            return;
        }

        ScreenGlState previousState = ScreenGlState.capture();
        try {
            ClientQualityController.beginLevelGpu();
            try {
                EffectQualityTier quality = ClientQualityController.tier();
                float compositionScale = quality.screenScale();
                for (ScreenEffectBatch batch : batches) {
                    compositionScale = Math.max(compositionScale,
                        this.operationRegistry.find(batch.settings().operation())
                            .map(ScreenEffectOperationRenderer::compositionScaleFloor)
                            .orElse(0.0F));
                }
                compositionScale = Math.min(1.0F, compositionScale);
                int targetWidth = Math.max(1, Math.round(width * compositionScale));
                int targetHeight = Math.max(1, Math.round(height * compositionScale));

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
                this.scratchTargets.beginFrame();
                RenderTarget depthTarget = CompatRegistry.sceneDepthTarget(mainTarget);
                RenderTarget maskDepthTarget = opaqueDepthAvailable
                    && !environment.hasDepthProvider()
                    && this.opaqueDepthTarget != null
                    && this.opaqueDepthTarget.width == mainTarget.width
                    && this.opaqueDepthTarget.height == mainTarget.height
                    ? this.opaqueDepthTarget : depthTarget;
                if (maskDepthTarget.getDepthTextureId() <= 0
                    || this.screenMaskTarget.getDepthTextureId() <= 0) {
                    throw new IllegalStateException(
                        "The " + environment.depthSource() + " depth source has no sampleable depth attachment");
                }

                this.captureSceneColor(mainTarget, this.rawSceneTarget);
                CompositionFrame frame = new CompositionFrame(
                    event, manager, maskDepthTarget, camera, quality, environment, this.fogState);

                List<ScreenEffectBatch> orderedBatches = ScreenEffectBatch.depthOrdered(batches);
                RenderTarget currentColor = ScreenEffectCompositionLoop.render(
                    orderedBatches,
                    this.rawSceneTarget,
                    this.sceneColorTarget,
                    this.compositionTarget,
                    (batch, source, destination) -> this.renderBatch(frame, batch, source, destination),
                    (batch, source, failedDestination, failure) -> this.reportOperationFailure(
                        batch, source, failedDestination, failure),
                    this::reportOperationSuccess
                );

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
            } catch (RuntimeException exception) {
                this.unavailableWidth = width;
                this.unavailableHeight = height;
                this.releaseTargets();
                LOGGER.warn("Depth-aware screen effects are unavailable at {}x{}; continuing without them.",
                    width, height, exception);
            }
        } finally {
            ClientQualityController.endLevelGpu();
            previousState.restore();
        }
    }

    void onLevelUnload() {
        this.hadScreenEffects = false;
        this.diagnosticsRequested = false;
        this.warnedDegenerateCamera = false;
        this.failingOperations.clear();
        this.fogState = ScreenEffectFogState.NONE;
        this.requiredInputs = ScreenEffectInputs.NONE;
        this.captureInputs = ScreenEffectInputs.ALL;
        this.frameCaptureInputs = ScreenEffectInputs.ALL;
        this.foregroundDepthCaptured = false;
        this.unavailableWidth = -1;
        this.unavailableHeight = -1;
        this.uniformCache.clear();
        this.releaseTargetsForLifecycle();
    }

    void onResourceReload() {
        this.diagnosticsRequested = false;
        this.warnedDegenerateCamera = false;
        this.failingOperations.clear();
        this.fogState = ScreenEffectFogState.NONE;
        this.requiredInputs = ScreenEffectInputs.NONE;
        this.captureInputs = ScreenEffectInputs.ALL;
        this.frameCaptureInputs = ScreenEffectInputs.ALL;
        this.foregroundDepthCaptured = false;
        this.unavailableWidth = -1;
        this.unavailableHeight = -1;
        this.uniformCache.clear();
        this.releaseTargetsForLifecycle();
    }

    /** Logs one frame of presentation GL state and target dimensions on the next composed frame. */
    boolean requestDiagnostics() {
        this.diagnosticsRequested = true;
        return true;
    }

    private void renderBatch(CompositionFrame frame, ScreenEffectBatch batch, RenderTarget sourceColor,
        RenderTarget destination) {
        ScreenGlState batchState = ScreenGlState.capture();
        try {
            this.renderMask(frame.event(), frame.manager(), frame.maskDepthTarget(), batch);
            this.renderOperation(frame, batch, sourceColor, destination);
        } finally {
            this.scratchTargets.releaseOperation();
            batchState.restore();
        }
    }

    private void renderOperation(CompositionFrame frame, ScreenEffectBatch batch, RenderTarget sourceColor,
        RenderTarget destination) {
        ScreenEffectOperationRenderer renderer = this.operationRegistry.find(batch.settings().operation())
            .orElseThrow(() -> new IllegalStateException("No screen effect operation registered for "
                + batch.settings().operation().id()));
        var effectPosition = batch.operationContext().transform().position();
        MediumCapture medium = captureMedium(
            new Vec3(effectPosition.x(), effectPosition.y(), effectPosition.z()),
            frame.camera().position());
        ScreenEffectOperationContext context = new ScreenEffectOperationContextImpl(
            batch.settings(),
            batch.operationContext(),
            batch.mask(),
            frame.camera(),
            frame.quality(),
            frame.environment(),
            frame.fogState(),
            medium.state(),
            medium.path(),
            this.opaqueColorTarget,
            this.foregroundDepthCaptured ? this.foregroundDepthTarget : null,
            sourceColor,
            this.rawSceneTarget,
            destination,
            this.screenMaskTarget,
            this.scratchTargets,
            this.uniformCache
        );
        renderer.render(context);
    }

    private void reportOperationFailure(ScreenEffectBatch batch, RenderTarget source,
        RenderTarget failedDestination, RuntimeException failure) {
        ResourceLocation operationId = batch.settings().operation().id();
        if (this.failingOperations.add(operationId)) {
            LOGGER.warn("Screen operation {} failed; skipping it and continuing with other screen effects.",
                operationId, failure);
        } else {
            LOGGER.debug("Screen operation {} is still failing; skipping this frame.", operationId);
        }
        try {
            ScreenTargetBlit.color(source, failedDestination);
        } catch (RuntimeException cleanupFailure) {
            LOGGER.debug("Could not reset the destination of failed screen operation {}.",
                operationId, cleanupFailure);
        }
    }

    private void reportOperationSuccess(ScreenEffectBatch batch) {
        ResourceLocation operationId = batch.settings().operation().id();
        if (this.failingOperations.remove(operationId)) {
            LOGGER.info("Screen operation {} recovered.", operationId);
        }
    }

    private void updateCaptureInputs(List<ScreenEffectBatch> batches) {
        ScreenEffectInputs next = this.operationRegistry.requiredInputs(batches);
        this.captureInputs = ScreenEffectCapturePolicy.forNextFrame(this.requiredInputs, next);
        this.requiredInputs = next;
    }

    private static ScreenEffectFogState captureFogState() {
        float start = RenderSystem.getShaderFogStart();
        float end = RenderSystem.getShaderFogEnd();
        float[] color = RenderSystem.getShaderFogColor();
        if (!Float.isFinite(start) || !Float.isFinite(end) || !(end > start)
            || start >= Float.MAX_VALUE || color == null || color.length < 3
            || !Float.isFinite(color[0]) || !Float.isFinite(color[1]) || !Float.isFinite(color[2])) {
            return ScreenEffectFogState.NONE;
        }
        return new ScreenEffectFogState(start, end, new Vec3(color[0], color[1], color[2]));
    }

    private static MediumCapture captureMedium(Vec3 effectPosition, Vec3 cameraPosition) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer == null ? null : minecraft.gameRenderer.getMainCamera();
        FogType fluid = camera == null ? null : camera.getFluidInCamera();
        ScreenEffectMedium cameraMedium = fluid == null
            ? ScreenEffectMedium.NONE : mediumOf(fluid);
        ClientLevel level = minecraft.level;
        ScreenEffectMedium effectMedium = level == null
            ? ScreenEffectMedium.NONE : mediumOf(level.getFluidState(
                BlockPos.containing(effectPosition.x, effectPosition.y, effectPosition.z)));
        BlockPos cameraBlock = BlockPos.containing(
            cameraPosition.x, cameraPosition.y, cameraPosition.z);
        BlockPos effectBlock = BlockPos.containing(
            effectPosition.x, effectPosition.y, effectPosition.z);
        float cameraSurface = waterSurfaceY(level, cameraBlock);
        float effectSurface = waterSurfaceY(level, effectBlock);
        ScreenEffectMediumState state = new ScreenEffectMediumState(
            cameraMedium,
            effectMedium,
            Float.isNaN(cameraSurface) ? Float.NaN : cameraSurface - (float)cameraPosition.y,
            Float.isNaN(effectSurface) ? Float.NaN : effectSurface - (float)cameraPosition.y
        );
        return new MediumCapture(state, captureMediumPath(level, cameraPosition, effectPosition));
    }

    private static ScreenEffectMediumPath captureMediumPath(ClientLevel level,
        Vec3 cameraPosition, Vec3 effectPosition) {
        if (level == null) {
            return ScreenEffectMediumPath.EMPTY;
        }
        ScreenEffectMediumSampler sampler = new ScreenEffectMediumSampler() {
            @Override
            public ScreenEffectMedium mediumAt(double x, double y, double z) {
                return mediumOf(level.getFluidState(BlockPos.containing(x, y, z)));
            }

            @Override
            public float surfaceY(double x, double y, double z) {
                return waterSurfaceY(level, BlockPos.containing(x, y, z));
            }
        };
        return ScreenEffectMediumTracer.trace(
            new Vector3d(cameraPosition.x, cameraPosition.y, cameraPosition.z),
            new Vector3d(effectPosition.x, effectPosition.y, effectPosition.z),
            sampler);
    }

    private record CompositionFrame(RenderLevelStageEvent event, ClientVisualEffectManager manager,
        RenderTarget maskDepthTarget, ScreenEffectCameraState camera, EffectQualityTier quality,
        RenderEnvironment environment, ScreenEffectFogState fogState) {
    }

    private record MediumCapture(ScreenEffectMediumState state, ScreenEffectMediumPath path) {
    }

    private static ScreenEffectMedium mediumOf(FogType type) {
        return switch (type) {
            case WATER -> ScreenEffectMedium.WATER;
            case LAVA -> ScreenEffectMedium.LAVA;
            case POWDER_SNOW -> ScreenEffectMedium.POWDER_SNOW;
            default -> ScreenEffectMedium.NONE;
        };
    }

    private static ScreenEffectMedium mediumOf(FluidState state) {
        if (state.is(FluidTags.WATER)) {
            return ScreenEffectMedium.WATER;
        }
        if (state.is(FluidTags.LAVA)) {
            return ScreenEffectMedium.LAVA;
        }
        return ScreenEffectMedium.NONE;
    }

    /** Returns the top water surface above {@code position}, or {@link Float#NaN} when unknown. */
    private static float waterSurfaceY(ClientLevel level, BlockPos position) {
        if (level == null) {
            return Float.NaN;
        }
        BlockPos.MutableBlockPos cursor = position.mutable();
        if (!isWater(level, cursor)) {
            return Float.NaN;
        }
        for (int step = 0; step < 128; step++) {
            BlockPos above = cursor.above();
            if (!isWater(level, above)) {
                return fluidSurfaceY(level, cursor);
            }
            cursor.set(above);
        }
        return fluidSurfaceY(level, cursor);
    }

    private static float fluidSurfaceY(ClientLevel level, BlockPos position) {
        FluidState state = level.getFluidState(position);
        return position.getY() + state.getHeight(level, position);
    }

    private static boolean isWater(ClientLevel level, BlockPos position) {
        return level.getFluidState(position).is(FluidTags.WATER);
    }

    /**
     * Copies the scene depth before translucent geometry. Glass, water, and ice
     * should not hide a screen effect; the active fog/medium owns their visual
     * contribution instead.
     */
    void captureOpaqueDepth(RenderLevelStageEvent event, ClientVisualEffectManager manager) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        // Capture the active fog while the terrain pass still owns it. LevelRenderer
        // calls FogRenderer.setupNoFog() before AFTER_LEVEL, which would make the
        // captured range inactive and silently disable every medium tint.
        this.fogState = captureFogState();
        if (manager == null || !manager.hasActiveScreenEffects()) {
            return;
        }
        boolean forceAll = manager.consumeScreenEffectStateChange();
        this.captureInputs = forceAll ? ScreenEffectInputs.ALL : this.captureInputs;
        this.frameCaptureInputs = this.captureInputs;
        Minecraft minecraft = Minecraft.getInstance();
        // Iris batches entity and block-entity geometry and flushes it only when the
        // translucent stage starts, which is after this copy. Flush the deferred source
        // once here so the opaque depth copy contains entities without submitting the
        // chunk translucent layers: glass and water must still not hide screen effects.
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        if (bufferSource.getClass() != MultiBufferSource.BufferSource.class) {
            bufferSource.endBatch();
            if (!this.loggedDeferredBufferFlush) {
                this.loggedDeferredBufferFlush = true;
                LOGGER.debug("Flushed deferred {} buffers before the opaque depth copy",
                    bufferSource.getClass().getSimpleName());
            }
        }
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        int width = mainTarget.width;
        int height = mainTarget.height;
        if (width <= 0 || height <= 0 || mainTarget.getDepthTextureId() <= 0) {
            return;
        }
        // The pre-translucent depth copy is mandatory for mask visibility (D3), independent of the operation.
        this.opaqueDepthTarget = this.ensureTarget(this.opaqueDepthTarget, width, height, true);
        if (this.opaqueDepthTarget.getDepthTextureId() <= 0) {
            return;
        }
        if (this.frameCaptureInputs.opaqueColor()) {
            this.opaqueColorTarget = this.ensureTarget(this.opaqueColorTarget, width, height, false);
            if (this.opaqueColorTarget.getColorTextureId() > 0) {
                ScreenTargetBlit.color(mainTarget, this.opaqueColorTarget);
            }
        } else if (this.opaqueColorTarget != null) {
            this.releaseTarget(this.opaqueColorTarget);
            this.opaqueColorTarget = null;
        }
        ScreenTargetBlit.depth(mainTarget, this.opaqueDepthTarget);
        this.opaqueDepthCaptured = true;
    }

    /**
     * Copies the scene depth that includes translucent terrain, so effects can
     * distinguish glass and water in front of them from opaque geometry. Glass must
     * still not hard-occlude the effect; the copy is only used to gate lens warps
     * and to tint the effect behind a translucent layer.
     */
    void captureForegroundDepth(RenderLevelStageEvent event, ClientVisualEffectManager manager) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        this.foregroundDepthCaptured = false;
        if (manager == null || !manager.hasActiveScreenEffects()) {
            return;
        }
        if (!this.frameCaptureInputs.foregroundDepth()) {
            if (this.foregroundDepthTarget != null) {
                this.releaseTarget(this.foregroundDepthTarget);
                this.foregroundDepthTarget = null;
            }
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget source = minecraft.getMainRenderTarget();
        if (Minecraft.useShaderTransparency() && minecraft.levelRenderer != null) {
            RenderTarget translucent = minecraft.levelRenderer.getTranslucentTarget();
            if (translucent != null && translucent.getDepthTextureId() > 0) {
                source = translucent;
            }
        }
        int width = source.width;
        int height = source.height;
        if (width <= 0 || height <= 0 || source.getDepthTextureId() <= 0) {
            return;
        }
        this.foregroundDepthTarget = this.ensureTarget(this.foregroundDepthTarget, width, height, true);
        if (this.foregroundDepthTarget.getDepthTextureId() <= 0) {
            return;
        }
        // The translucent layer pass continues on this target in Fabulous mode; the
        // blit helper restores the previous framebuffer and viewport.
        ScreenTargetBlit.depth(source, this.foregroundDepthTarget);
        this.foregroundDepthCaptured = true;
    }

    private void renderMask(RenderLevelStageEvent event, ClientVisualEffectManager manager, RenderTarget depthTarget,
        ScreenEffectBatch batch) {
        RenderSystem.disableScissor();
        this.screenMaskTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        this.screenMaskTarget.clear(false);
        this.screenMaskTarget.copyDepthFrom(depthTarget);
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
        ScreenGlState state = ScreenGlState.capture();
        boolean shaderApplied = false;
        try {
            EffectRenderState.bindTarget(mainTarget);
            RenderSystem.disableScissor();
            RenderSystem.colorMask(true, true, true, false);
            RenderSystem.depthMask(false);
            RenderSystem.disableDepthTest();
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
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
            state.restore();
        }
    }

    private void captureSceneColor(RenderTarget mainTarget, RenderTarget captureTarget) {
        ScreenTargetBlit.color(mainTarget, captureTarget);
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

    /** Releases compositor targets without leaking the framebuffer state they change. */
    private void releaseTargetsSafely() {
        ScreenGlState state = ScreenGlState.capture();
        try {
            this.releaseTargets();
        } finally {
            state.restore();
        }
    }

    /** Releases targets from a lifecycle callback, where destruction may already be deferred. */
    private void releaseTargetsForLifecycle() {
        if (RenderSystem.isOnRenderThreadOrInit()) {
            this.releaseTargetsSafely();
        } else {
            this.releaseTargets();
        }
    }

    private void releaseTargets() {
        TextureTarget rawScene = this.rawSceneTarget;
        TextureTarget sceneColor = this.sceneColorTarget;
        TextureTarget composition = this.compositionTarget;
        TextureTarget mask = this.screenMaskTarget;
        TextureTarget opaqueDepth = this.opaqueDepthTarget;
        TextureTarget opaqueColor = this.opaqueColorTarget;
        TextureTarget foregroundDepth = this.foregroundDepthTarget;
        this.rawSceneTarget = null;
        this.sceneColorTarget = null;
        this.compositionTarget = null;
        this.screenMaskTarget = null;
        this.opaqueDepthTarget = null;
        this.opaqueColorTarget = null;
        this.foregroundDepthTarget = null;
        this.opaqueDepthCaptured = false;
        this.foregroundDepthCaptured = false;
        this.warnedPresentationViewport = false;
        this.scratchTargets.releaseAll();
        this.releaseTarget(rawScene);
        this.releaseTarget(sceneColor);
        this.releaseTarget(composition);
        this.releaseTarget(mask);
        this.releaseTarget(opaqueDepth);
        this.releaseTarget(opaqueColor);
        this.releaseTarget(foregroundDepth);
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
