package mafisher.quasar.client.effect.screen;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import java.util.Objects;
import java.util.function.Consumer;
import mafisher.quasar.api.client.BlackHoleScreenSettings;
import mafisher.quasar.api.client.BloomSettings;
import mafisher.quasar.api.client.SceneBlurSettings;
import mafisher.quasar.api.client.ScreenEffectCameraState;
import mafisher.quasar.api.client.ScreenEffectInputs;
import mafisher.quasar.api.client.ScreenEffectOperation;
import mafisher.quasar.api.client.ScreenEffectOperationContext;
import mafisher.quasar.api.client.ScreenEffectOperationRenderer;
import mafisher.quasar.api.client.WaveDistortionSettings;
import mafisher.quasar.api.effect.EffectQualityTier;
import mafisher.quasar.client.effect.render.EffectShaders;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Engine-owned screen operations, implemented only through the public operation context. */
final class BuiltInScreenEffectOperations {
    private static final int NEAREST_FILTER = 0x2600;
    private static final int LINEAR_FILTER = 0x2601;

    private BuiltInScreenEffectOperations() {
    }

    static void register(ScreenEffectOperationRegistry registry) {
        registry.register(ScreenEffectOperation.DIAGNOSTIC_MASK_PREVIEW,
            operation(ScreenEffectInputs.NONE, BuiltInScreenEffectOperations::renderDiagnostic));
        registry.register(ScreenEffectOperation.MASK_SCOPED_WAVE_DISTORTION,
            operation(ScreenEffectInputs.NONE, BuiltInScreenEffectOperations::renderWaveDistortion));
        registry.register(ScreenEffectOperation.MASK_SCOPED_SCENE_BLUR,
            operation(ScreenEffectInputs.NONE, BuiltInScreenEffectOperations::renderSceneBlur));
        registry.register(ScreenEffectOperation.MASK_SCOPED_BLOOM,
            operation(ScreenEffectInputs.NONE, BuiltInScreenEffectOperations::renderBloom));
        // The lens uses DepthSampler from the mandatory mask depth, but never the optional color or foreground copy.
        registry.register(ScreenEffectOperation.MASK_SCOPED_BLACK_HOLE,
            operation(ScreenEffectInputs.NONE, BuiltInScreenEffectOperations::renderBlackHole));
    }

    private static ScreenEffectOperationRenderer operation(ScreenEffectInputs inputs,
        Consumer<ScreenEffectOperationContext> renderer) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(renderer, "renderer");
        return new ScreenEffectOperationRenderer() {
            @Override
            public void render(ScreenEffectOperationContext context) {
                renderer.accept(context);
            }

            @Override
            public ScreenEffectInputs requiredInputs() {
                return inputs;
            }
        };
    }

    private static void renderDiagnostic(ScreenEffectOperationContext context) {
        ShaderInstance shader = compositeShader();
        context.bindCompositeInputs(shader);
        context.setUniform(shader, "Operation", 0.0F);
        context.drawFullscreen(context.destination(), shader);
    }

    private static void renderWaveDistortion(ScreenEffectOperationContext context) {
        WaveDistortionSettings settings = context.settings(WaveDistortionSettings.class);
        ShaderInstance shader = compositeShader();
        context.bindCompositeInputs(shader);
        context.setUniform(shader, "Operation", 1.0F);
        context.setUniform(shader, "WaveAmplitudePixels",
            settings.amplitudePixels() * context.quality().screenScale());
        context.setUniform(shader, "WaveFrequency", settings.frequencyCycles());
        context.setUniform(shader, "WaveSpeed", settings.speedCyclesPerTick());
        context.setUniform(shader, "WaveTime", context.effect().ageTicks());
        context.setUniform(shader, "ViewportWidth", context.destination().width);
        context.drawFullscreen(context.destination(), shader);
    }

    private static void renderSceneBlur(ScreenEffectOperationContext context) {
        SceneBlurSettings settings = context.settings(SceneBlurSettings.class);
        if (!blurEnabled(context.quality())) {
            context.blitColor(context.sourceColor(), context.destination());
            return;
        }
        TextureTarget blur = context.acquireScratchTarget(
            context.destination().width, context.destination().height
        );
        blur.setFilterMode(NEAREST_FILTER);
        try {
            ShaderInstance shader = compositeShader();
            context.bindCompositeInputs(shader, context.sourceColor());
            context.setUniform(shader, "Operation", 2.0F);
            setBlurUniforms(context, shader, settings, context.sourceColor());
            context.drawFullscreen(blur, shader);

            context.bindCompositeInputs(shader, context.sourceColor());
            context.setSampler(shader, "BlurSampler", blur.getColorTextureId());
            context.setUniform(shader, "Operation", 3.0F);
            setBlurUniforms(context, shader, settings, context.destination());
            context.drawFullscreen(context.destination(), shader);
        } finally {
            context.releaseScratchTarget(blur);
        }
    }

    private static void renderBloom(ScreenEffectOperationContext context) {
        BloomSettings settings = context.settings(BloomSettings.class);
        RenderTarget destination = context.destination();
        int width = bloomDimension(destination.width);
        int height = bloomDimension(destination.height);
        TextureTarget first = context.acquireScratchTarget(width, height);
        TextureTarget second = context.acquireScratchTarget(width, height);
        first.setFilterMode(LINEAR_FILTER);
        second.setFilterMode(LINEAR_FILTER);
        try {
            renderBloomPasses(context, context.sourceColor(), destination, context.mask().strength(),
                settings, first, second, bloomUsesBlur(context.quality()));
        } finally {
            context.releaseScratchTarget(first);
            context.releaseScratchTarget(second);
        }
    }

    private static void renderBlackHole(ScreenEffectOperationContext context) {
        BlackHoleScreenSettings settings = context.settings(BlackHoleScreenSettings.class);
        RenderTarget destination = context.destination();
        TextureTarget lens = null;
        try {
            BlackHoleProjection projection = projectBlackHole(context, settings);
            RenderTarget bloomSource = context.sourceColor();
            if (projection.visible()) {
                lens = context.acquireScratchTarget(destination.width, destination.height);
                lens.setFilterMode(NEAREST_FILTER);
                ShaderInstance shader = compositeShader();
                context.bindCompositeInputs(shader, context.sourceColor());
                context.setUniform(shader, "Operation", 4.0F);
                context.setUniform(shader, "LensCenter", projection.centerX(), projection.centerY());
                context.setUniform(shader, "LensRadiusPixels", projection.radiusPixels());
                context.setUniform(shader, "LensDepth", projection.depth());
                context.setUniform(shader, "LensStrength", settings.lensStrength());
                context.setUniform(shader, "MaxDistortionPixels",
                    settings.maxDistortionPixels() * context.quality().screenScale());
                context.setUniform(shader, "ViewportWidth", destination.width);
                context.setUniform(shader, "ViewportHeight", destination.height);
                context.drawFullscreen(lens, shader);
                bloomSource = lens;
            }

            int width = bloomDimension(destination.width);
            int height = bloomDimension(destination.height);
            TextureTarget first = context.acquireScratchTarget(width, height);
            TextureTarget second = context.acquireScratchTarget(width, height);
            first.setFilterMode(LINEAR_FILTER);
            second.setFilterMode(LINEAR_FILTER);
            try {
                renderBloomPasses(context, bloomSource, destination, context.mask().strength(),
                    settings.bloomSettings(), first, second, bloomUsesBlur(context.quality()));
            } finally {
                context.releaseScratchTarget(first);
                context.releaseScratchTarget(second);
            }
        } finally {
            if (lens != null) {
                context.releaseScratchTarget(lens);
            }
        }
    }

    private static void renderBloomPasses(ScreenEffectOperationContext context, RenderTarget source,
        RenderTarget destination, float maskStrength, BloomSettings settings, TextureTarget first,
        TextureTarget second, boolean blur) {
        ShaderInstance shader = bloomShader();
        bindBloomInputs(context, shader, source);
        setBloomUniforms(context, shader, settings, source, maskStrength);
        context.setUniform(shader, "Pass", 0.0F);
        context.drawFullscreen(first, shader);

        if (blur) {
            context.setSampler(shader, "BloomSampler", first.getColorTextureId());
            context.setUniform(shader, "Pass", 1.0F);
            context.setUniform(shader, "Horizontal", 1.0F);
            context.drawFullscreen(second, shader);

            context.setSampler(shader, "BloomSampler", second.getColorTextureId());
            context.setUniform(shader, "Pass", 2.0F);
            context.setUniform(shader, "Horizontal", 0.0F);
            context.drawFullscreen(first, shader);
        }

        bindBloomInputs(context, shader, source);
        context.setSampler(shader, "BloomSampler", first.getColorTextureId());
        setBloomUniforms(context, shader, settings, source, maskStrength);
        context.setUniform(shader, "Pass", 3.0F);
        context.drawFullscreen(destination, shader);
    }

    private static void bindBloomInputs(ScreenEffectOperationContext context, ShaderInstance shader,
        RenderTarget source) {
        context.setSampler(shader, "SceneSampler", source.getColorTextureId());
        context.setSampler(shader, "MaskSampler", context.maskTarget().getColorTextureId());
        context.setSampler(shader, "BloomSampler", source.getColorTextureId());
    }

    private static void setBloomUniforms(ScreenEffectOperationContext context, ShaderInstance shader,
        BloomSettings settings, RenderTarget viewport, float maskStrength) {
        context.setUniform(shader, "Threshold", settings.threshold());
        context.setUniform(shader, "Intensity", settings.intensity());
        context.setUniform(shader, "RadiusPixels",
            settings.radiusPixels() * context.quality().screenScale());
        context.setUniform(shader, "MaskStrength", maskStrength);
        context.setUniform(shader, "ViewportWidth", viewport.width);
        context.setUniform(shader, "ViewportHeight", viewport.height);
    }

    private static void setBlurUniforms(ScreenEffectOperationContext context, ShaderInstance shader,
        SceneBlurSettings settings, RenderTarget viewport) {
        context.setUniform(shader, "BlurRadiusPixels",
            settings.radiusPixels() * context.quality().screenScale() * blurRadiusScale(context.quality()));
        context.setUniform(shader, "ViewportWidth", viewport.width);
        context.setUniform(shader, "ViewportHeight", viewport.height);
    }

    static boolean bloomUsesBlur(EffectQualityTier quality) {
        return quality.atLeast(EffectQualityTier.HIGH);
    }

    static boolean blurEnabled(EffectQualityTier quality) {
        return quality != EffectQualityTier.LOW;
    }

    static float blurRadiusScale(EffectQualityTier quality) {
        return quality.atLeast(EffectQualityTier.HIGH) ? 1.0F : 0.5F;
    }

    private static BlackHoleProjection projectBlackHole(ScreenEffectOperationContext context,
        BlackHoleScreenSettings settings) {
        ScreenEffectCameraState camera = context.camera();
        Vec3 cameraPosition = camera.position();
        var effectPosition = context.effect().transform().position();
        Vector4f viewPosition = new Vector4f(
            (float)(effectPosition.x() - cameraPosition.x),
            (float)(effectPosition.y() - cameraPosition.y),
            (float)(effectPosition.z() - cameraPosition.z),
            1.0F
        ).mul(camera.modelViewMatrix());
        Matrix4f projectionMatrix = camera.projectionMatrix();
        Vector4f clipPosition = new Vector4f(viewPosition).mul(projectionMatrix);
        if (clipPosition.w() <= 0.0F || viewPosition.z() >= -0.001F
            || !Float.isFinite(clipPosition.x()) || !Float.isFinite(clipPosition.y())
            || !Float.isFinite(clipPosition.z()) || !Float.isFinite(clipPosition.w())) {
            return BlackHoleProjection.INVISIBLE;
        }

        float inverseW = 1.0F / clipPosition.w();
        float centerX = clipPosition.x() * inverseW * 0.5F + 0.5F;
        float centerY = clipPosition.y() * inverseW * 0.5F + 0.5F;
        float worldRadius = context.effect().scale() * settings.lensRadiusScale();
        float radiusPixels = Math.abs(projectionMatrix.m00() * worldRadius / viewPosition.z())
            * camera.viewportWidth() * 0.5F;
        float depth = clipPosition.z() * inverseW * 0.5F + 0.5F;
        if (!Float.isFinite(centerX) || !Float.isFinite(centerY) || !Float.isFinite(radiusPixels)
            || !Float.isFinite(depth) || radiusPixels < 1.0F) {
            return BlackHoleProjection.INVISIBLE;
        }
        return new BlackHoleProjection(centerX, centerY, radiusPixels, depth, true);
    }

    private static int bloomDimension(int size) {
        return Math.max(1, size / 2 + size % 2);
    }

    private static ShaderInstance compositeShader() {
        return Objects.requireNonNull(EffectShaders.SCREEN_COMPOSITE.shaderInstance(),
            "screen composite shader is not registered");
    }

    private static ShaderInstance bloomShader() {
        return Objects.requireNonNull(EffectShaders.SCREEN_BLOOM.shaderInstance(),
            "screen bloom shader is not registered");
    }

    private record BlackHoleProjection(float centerX, float centerY, float radiusPixels, float depth,
        boolean visible) {
        private static final BlackHoleProjection INVISIBLE =
            new BlackHoleProjection(0.5F, 0.5F, 1.0F, 1.0F, false);
    }
}
