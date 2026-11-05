package mafisher.quasar.client.effect;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;

/** Optional depth-aware per-effect mask composition for client screen effects. */
final class ClientScreenCompositor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int READ_FRAMEBUFFER = 0x8CA8;
    private static final int DRAW_FRAMEBUFFER = 0x8CA9;
    private static final int COLOR_BUFFER_BIT = 0x00004000;
    private static final int NEAREST_FILTER = 0x2600;
    private static final int LINEAR_FILTER = 0x2601;

    private TextureTarget sceneColorTarget;
    private TextureTarget compositionTarget;
    private TextureTarget screenMaskTarget;
    private TextureTarget blurTarget;
    private TextureTarget bloomTargetA;
    private TextureTarget bloomTargetB;
    private boolean hadScreenEffects;
    private boolean failAfterCompositionOnce;
    private int unavailableWidth = -1;
    private int unavailableHeight = -1;

    void render(RenderLevelStageEvent event, ClientVisualEffectManager manager, List<ScreenEffectFrame> screenEffects) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }

        if (screenEffects.isEmpty()) {
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

        boolean sceneCaptured = false;
        try {
            boolean needsBlurTarget = screenEffects.stream()
                .anyMatch(effect -> effect.settings().operation() == ScreenEffectOperation.MASK_SCOPED_SCENE_BLUR);
            boolean needsBloomTargets = screenEffects.stream()
                .anyMatch(effect -> effect.settings().operation() == ScreenEffectOperation.MASK_SCOPED_BLOOM
                    || effect.settings().operation() == ScreenEffectOperation.MASK_SCOPED_BLACK_HOLE);
            this.ensureTargets(width, height, needsBlurTarget, needsBloomTargets);
            if (mainTarget.getDepthTextureId() <= 0 || this.screenMaskTarget.getDepthTextureId() <= 0) {
                throw new IllegalStateException("The main render target has no sampleable depth attachment");
            }

            this.captureSceneColor(mainTarget, this.sceneColorTarget);
            sceneCaptured = true;

            RenderTarget currentColor = mainTarget;
            for (ScreenEffectFrame screenEffect : screenEffects) {
                this.renderMask(event, manager, mainTarget, screenEffect);
                RenderTarget destination = currentColor == mainTarget ? this.compositionTarget : mainTarget;
                currentColor = this.compositeEffect(currentColor, destination, screenEffect, event);
            }

            if (currentColor != mainTarget) {
                mainTarget.bindWrite(true);
                currentColor.blitToScreen(mainTarget.viewWidth, mainTarget.viewHeight);
            }
            if (this.failAfterCompositionOnce) {
                this.failAfterCompositionOnce = false;
                throw new IllegalStateException("Screen compositor failure injection");
            }
            this.restoreMainTargetState(mainTarget);
        } catch (RuntimeException exception) {
            this.failAfterCompositionOnce = false;
            if (sceneCaptured) {
                this.restoreCapturedScene(mainTarget);
            } else {
                this.restoreMainTargetState(mainTarget);
            }
            this.unavailableWidth = width;
            this.unavailableHeight = height;
            this.releaseTargets();
            LOGGER.warn("Depth-aware screen effects are unavailable at {}x{}; continuing without them.",
                width, height, exception);
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

    private void renderMask(RenderLevelStageEvent event, ClientVisualEffectManager manager, RenderTarget mainTarget,
        ScreenEffectFrame screenEffect) {
        this.screenMaskTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        this.screenMaskTarget.clear(false);
        this.screenMaskTarget.copyDepthFrom(mainTarget);
        this.screenMaskTarget.bindWrite(true);
        try {
            manager.renderScreenMask(event, screenEffect);
        } finally {
            this.screenMaskTarget.unbindWrite();
        }
    }

    private void compositeMask(RenderTarget sourceColor, RenderTarget destination, ScreenEffectMask mask) {
        ShaderInstance shader = Objects.requireNonNull(EffectRenderTypes.screenCompositeShader(),
            "screen composite shader is not registered");

        this.bindCompositeInputs(shader, sourceColor, mask);
        this.setUniform(shader, "Operation", 0.0F);
        this.drawFullscreen(destination, shader);
    }

    private void compositeWaveDistortion(RenderTarget sourceColor, RenderTarget destination,
        ScreenEffectFrame screenEffect) {
        ShaderInstance shader = Objects.requireNonNull(EffectRenderTypes.screenCompositeShader(),
            "screen composite shader is not registered");
        WaveDistortionSettings settings = Objects.requireNonNull(
            screenEffect.settings().waveDistortionSettings(), "wave distortion settings");

        this.bindCompositeInputs(shader, sourceColor, screenEffect.mask());
        this.setUniform(shader, "Operation", 1.0F);
        this.setUniform(shader, "WaveAmplitudePixels", settings.amplitudePixels());
        this.setUniform(shader, "WaveFrequency", settings.frequencyCycles());
        this.setUniform(shader, "WaveSpeed", settings.speedCyclesPerTick());
        this.setUniform(shader, "WaveTime", screenEffect.context().ageTicks());
        this.setUniform(shader, "ViewportWidth", destination.width);
        this.drawFullscreen(destination, shader);
    }

    private void compositeSceneBlur(RenderTarget sourceColor, RenderTarget destination,
        ScreenEffectFrame screenEffect) {
        ShaderInstance shader = Objects.requireNonNull(EffectRenderTypes.screenCompositeShader(),
            "screen composite shader is not registered");
        SceneBlurSettings settings = Objects.requireNonNull(
            screenEffect.settings().sceneBlurSettings(), "scene blur settings");
        TextureTarget blur = Objects.requireNonNull(this.blurTarget, "scene blur target");

        this.bindCompositeInputs(shader, sourceColor, screenEffect.mask());
        this.setUniform(shader, "Operation", 2.0F);
        this.setBlurUniforms(shader, settings, sourceColor);
        this.drawFullscreen(blur, shader);

        this.bindCompositeInputs(shader, sourceColor, screenEffect.mask());
        shader.setSampler("BlurSampler", blur.getColorTextureId());
        this.setUniform(shader, "Operation", 3.0F);
        this.setBlurUniforms(shader, settings, destination);
        this.drawFullscreen(destination, shader);
    }

    private void compositeBloom(RenderTarget sourceColor, RenderTarget destination, ScreenEffectFrame screenEffect) {
        this.compositeBloom(sourceColor, destination, screenEffect.mask().strength(),
            Objects.requireNonNull(screenEffect.settings().bloomSettings(), "bloom settings"));
    }

    private void compositeBloom(RenderTarget sourceColor, RenderTarget destination, float maskStrength,
        BloomSettings settings) {
        ShaderInstance shader = Objects.requireNonNull(EffectRenderTypes.screenBloomShader(),
            "screen bloom shader is not registered");
        TextureTarget first = Objects.requireNonNull(this.bloomTargetA, "bloom target A");
        TextureTarget second = Objects.requireNonNull(this.bloomTargetB, "bloom target B");

        this.bindBloomInputs(shader, sourceColor);
        this.setBloomUniforms(shader, settings, sourceColor, maskStrength);
        this.setUniform(shader, "Pass", 0.0F);
        this.drawFullscreen(first, shader);

        shader.setSampler("BloomSampler", first.getColorTextureId());
        this.setUniform(shader, "Pass", 1.0F);
        this.setUniform(shader, "Horizontal", 1.0F);
        this.drawFullscreen(second, shader);

        shader.setSampler("BloomSampler", second.getColorTextureId());
        this.setUniform(shader, "Pass", 2.0F);
        this.setUniform(shader, "Horizontal", 0.0F);
        this.drawFullscreen(first, shader);

        this.bindBloomInputs(shader, sourceColor);
        shader.setSampler("BloomSampler", first.getColorTextureId());
        this.setBloomUniforms(shader, settings, sourceColor, maskStrength);
        this.setUniform(shader, "Pass", 3.0F);
        this.drawFullscreen(destination, shader);
    }

    private void bindBloomInputs(ShaderInstance shader, RenderTarget sourceColor) {
        shader.setSampler("SceneSampler", sourceColor.getColorTextureId());
        shader.setSampler("MaskSampler", this.screenMaskTarget.getColorTextureId());
        shader.setSampler("BloomSampler", sourceColor.getColorTextureId());
    }

    private void setBloomUniforms(ShaderInstance shader, BloomSettings settings, RenderTarget viewport,
        float maskStrength) {
        this.setUniform(shader, "Threshold", settings.threshold());
        this.setUniform(shader, "Intensity", settings.intensity());
        this.setUniform(shader, "RadiusPixels", settings.radiusPixels());
        this.setUniform(shader, "MaskStrength", maskStrength);
        this.setUniform(shader, "ViewportWidth", viewport.width);
        this.setUniform(shader, "ViewportHeight", viewport.height);
    }

    private void setBlurUniforms(ShaderInstance shader, SceneBlurSettings settings, RenderTarget viewport) {
        this.setUniform(shader, "BlurRadiusPixels", settings.radiusPixels());
        this.setUniform(shader, "ViewportWidth", viewport.width);
        this.setUniform(shader, "ViewportHeight", viewport.height);
    }

    private void bindCompositeInputs(ShaderInstance shader, RenderTarget sourceColor, ScreenEffectMask mask) {
        shader.setSampler("SceneSampler", sourceColor.getColorTextureId());
        shader.setSampler("MaskSampler", this.screenMaskTarget.getColorTextureId());
        shader.setSampler("DepthSampler", this.screenMaskTarget.getDepthTextureId());
        shader.setSampler("BlurSampler", sourceColor.getColorTextureId());
        AbstractUniform maskColor = Objects.requireNonNull(shader.getUniform("MaskColor"), "MaskColor uniform");
        maskColor.set(mask.red(), mask.green(), mask.blue());
        this.setUniform(shader, "MaskStrength", mask.strength());
    }

    private void drawFullscreen(RenderTarget destination, ShaderInstance shader) {
        destination.bindWrite(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();

        boolean shaderApplied = false;
        try {
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
        }
    }

    private RenderTarget compositeEffect(RenderTarget sourceColor, RenderTarget destination,
        ScreenEffectFrame screenEffect, RenderLevelStageEvent event) {
        return switch (screenEffect.settings().operation()) {
            case DIAGNOSTIC_MASK_PREVIEW -> {
                this.compositeMask(sourceColor, destination, screenEffect.mask());
                yield destination;
            }
            case MASK_SCOPED_WAVE_DISTORTION -> {
                this.compositeWaveDistortion(sourceColor, destination, screenEffect);
                yield destination;
            }
            case MASK_SCOPED_SCENE_BLUR -> {
                this.compositeSceneBlur(sourceColor, destination, screenEffect);
                yield destination;
            }
            case MASK_SCOPED_BLOOM -> {
                this.compositeBloom(sourceColor, destination, screenEffect);
                yield destination;
            }
            case MASK_SCOPED_BLACK_HOLE -> this.compositeBlackHole(sourceColor, destination, screenEffect, event);
        };
    }

    private RenderTarget compositeBlackHole(RenderTarget sourceColor, RenderTarget destination,
        ScreenEffectFrame screenEffect, RenderLevelStageEvent event) {
        BlackHoleScreenSettings settings = Objects.requireNonNull(
            screenEffect.settings().blackHoleScreenSettings(), "black-hole screen settings");
        BlackHoleProjection projection = this.projectBlackHole(event, screenEffect, destination, settings);
        RenderTarget bloomSource = sourceColor;
        RenderTarget bloomDestination = destination;

        if (projection.visible()) {
            ShaderInstance shader = Objects.requireNonNull(EffectRenderTypes.screenCompositeShader(),
                "screen composite shader is not registered");
            this.bindCompositeInputs(shader, sourceColor, screenEffect.mask());
            this.setUniform(shader, "Operation", 4.0F);
            AbstractUniform lensCenter = Objects.requireNonNull(shader.getUniform("LensCenter"),
                "LensCenter uniform");
            lensCenter.set(projection.centerX(), projection.centerY());
            this.setUniform(shader, "LensRadiusPixels", projection.radiusPixels());
            this.setUniform(shader, "LensDepth", projection.depth());
            this.setUniform(shader, "LensStrength", settings.lensStrength());
            this.setUniform(shader, "MaxDistortionPixels", settings.maxDistortionPixels());
            this.setUniform(shader, "ViewportWidth", destination.width);
            this.setUniform(shader, "ViewportHeight", destination.height);
            this.drawFullscreen(destination, shader);

            // Lens first, then bloom back into the source target so the two passes never sample their destination.
            bloomSource = destination;
            bloomDestination = sourceColor;
        }

        this.compositeBloom(bloomSource, bloomDestination, screenEffect.mask().strength(),
            settings.bloomSettings());
        return bloomDestination;
    }

    private BlackHoleProjection projectBlackHole(RenderLevelStageEvent event, ScreenEffectFrame screenEffect,
        RenderTarget viewport, BlackHoleScreenSettings settings) {
        Vec3 cameraPosition = event.getCamera().getPosition();
        var effectPosition = screenEffect.context().transform().position();
        Vector4f viewPosition = new Vector4f(
            (float)(effectPosition.x() - cameraPosition.x),
            (float)(effectPosition.y() - cameraPosition.y),
            (float)(effectPosition.z() - cameraPosition.z),
            1.0F
        ).mul(new Matrix4f(event.getModelViewMatrix()));
        Matrix4f projectionMatrix = new Matrix4f(event.getProjectionMatrix());
        Vector4f clipPosition = new Vector4f(viewPosition).mul(projectionMatrix);
        if (clipPosition.w() <= 0.0F || viewPosition.z() >= -0.001F
            || !Float.isFinite(clipPosition.x()) || !Float.isFinite(clipPosition.y())
            || !Float.isFinite(clipPosition.z()) || !Float.isFinite(clipPosition.w())) {
            return BlackHoleProjection.INVISIBLE;
        }

        float inverseW = 1.0F / clipPosition.w();
        float centerX = clipPosition.x() * inverseW * 0.5F + 0.5F;
        float centerY = clipPosition.y() * inverseW * 0.5F + 0.5F;
        float worldRadius = screenEffect.context().scale() * settings.lensRadiusScale();
        float radiusPixels = Math.abs(projectionMatrix.m00() * worldRadius / viewPosition.z())
            * viewport.width * 0.5F;
        float depth = clipPosition.z() * inverseW * 0.5F + 0.5F;
        if (!Float.isFinite(centerX) || !Float.isFinite(centerY) || !Float.isFinite(radiusPixels)
            || !Float.isFinite(depth) || radiusPixels < 1.0F) {
            return BlackHoleProjection.INVISIBLE;
        }
        return new BlackHoleProjection(centerX, centerY, radiusPixels, depth, true);
    }

    private record BlackHoleProjection(float centerX, float centerY, float radiusPixels, float depth,
        boolean visible) {
        private static final BlackHoleProjection INVISIBLE = new BlackHoleProjection(0.5F, 0.5F, 1.0F, 1.0F, false);
    }

    private void setUniform(ShaderInstance shader, String name, float value) {
        AbstractUniform uniform = Objects.requireNonNull(shader.getUniform(name), name + " uniform");
        uniform.set(value);
    }

    private void ensureTargets(int width, int height, boolean needsBlurTarget, boolean needsBloomTargets) {
        this.sceneColorTarget = this.ensureTarget(this.sceneColorTarget, width, height, false);
        this.compositionTarget = this.ensureTarget(this.compositionTarget, width, height, false);
        this.screenMaskTarget = this.ensureTarget(this.screenMaskTarget, width, height, true);
        if (needsBlurTarget) {
            this.blurTarget = this.ensureTarget(this.blurTarget, width, height, false);
        } else {
            this.releaseBlurTarget();
        }
        if (needsBloomTargets) {
            int bloomWidth = Math.max(1, width / 2 + width % 2);
            int bloomHeight = Math.max(1, height / 2 + height % 2);
            this.bloomTargetA = this.ensureBloomTarget(this.bloomTargetA, bloomWidth, bloomHeight);
            this.bloomTargetB = this.ensureBloomTarget(this.bloomTargetB, bloomWidth, bloomHeight);
        } else {
            this.releaseBloomTargets();
        }
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

    private TextureTarget ensureBloomTarget(TextureTarget target, int width, int height) {
        target = this.ensureTarget(target, width, height, false);
        target.setFilterMode(LINEAR_FILTER);
        return target;
    }

    private void captureSceneColor(RenderTarget mainTarget, RenderTarget captureTarget) {
        GlStateManager._glBindFramebuffer(READ_FRAMEBUFFER, mainTarget.frameBufferId);
        GlStateManager._glBindFramebuffer(DRAW_FRAMEBUFFER, captureTarget.frameBufferId);
        GlStateManager._glBlitFrameBuffer(
            0, 0, mainTarget.width, mainTarget.height,
            0, 0, captureTarget.width, captureTarget.height,
            COLOR_BUFFER_BIT, NEAREST_FILTER
        );
        mainTarget.bindWrite(false);
    }

    private void restoreCapturedScene(RenderTarget mainTarget) {
        try {
            mainTarget.bindWrite(true);
            if (this.sceneColorTarget != null) {
                this.sceneColorTarget.blitToScreen(mainTarget.viewWidth, mainTarget.viewHeight);
            }
        } catch (RuntimeException recoveryException) {
            LOGGER.warn("Could not restore the captured scene after a compositor failure.", recoveryException);
        } finally {
            this.restoreMainTargetState(mainTarget);
        }
    }

    private void restoreMainTargetState(RenderTarget mainTarget) {
        mainTarget.bindWrite(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void releaseTargets() {
        TextureTarget sceneColor = this.sceneColorTarget;
        TextureTarget composition = this.compositionTarget;
        TextureTarget mask = this.screenMaskTarget;
        this.sceneColorTarget = null;
        this.compositionTarget = null;
        this.screenMaskTarget = null;
        this.releaseBlurTarget();
        this.releaseBloomTargets();
        this.releaseTarget(sceneColor);
        this.releaseTarget(composition);
        this.releaseTarget(mask);
    }

    private void releaseBlurTarget() {
        TextureTarget blur = this.blurTarget;
        this.blurTarget = null;
        this.releaseTarget(blur);
    }

    private void releaseBloomTargets() {
        TextureTarget first = this.bloomTargetA;
        TextureTarget second = this.bloomTargetB;
        this.bloomTargetA = null;
        this.bloomTargetB = null;
        this.releaseTarget(first);
        this.releaseTarget(second);
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
