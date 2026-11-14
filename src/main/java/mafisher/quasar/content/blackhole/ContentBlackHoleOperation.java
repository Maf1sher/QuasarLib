package mafisher.quasar.content.blackhole;

import com.mojang.blaze3d.pipeline.RenderTarget;
import mafisher.quasar.api.client.render.EffectParameterBinding;
import mafisher.quasar.api.client.render.EffectUniforms;
import mafisher.quasar.api.client.screen.EmissiveComposite;
import mafisher.quasar.api.client.screen.ScreenEffectInputs;
import mafisher.quasar.api.client.screen.ScreenEffectMediumOptics;
import mafisher.quasar.api.client.screen.ScreenEffectOperationContext;
import mafisher.quasar.api.client.screen.ScreenEffectOperationRenderer;
import mafisher.quasar.api.effect.EffectParameterSet;
import mafisher.quasar.content.ContentShaders;
import net.minecraft.client.renderer.ShaderInstance;

/** Content-owned raymarched black hole with a shared emissive composite. */
public final class ContentBlackHoleOperation implements ScreenEffectOperationRenderer {
    /**
     * The composition is already quality-scaled, so the raymarch pass renders at full composition
     * resolution. Scaling it again compounds the reduction and turns the disk into a blocky smear.
     */
    private static final float RAYMARCH_SCALE = 1.0F;
    private static final EffectParameterBinding CURVE_UNIFORMS = EffectParameterBinding
        .builder(ContentBlackHoleCinematics.parameters())
        .uniform("Fade", "Fade")
        .uniform("RingFormation", "RingFormation")
        .uniform("ShockStrength", "ShockStrength")
        .uniform("ShockAbsorb", "ShockAbsorb")
        .uniform("VortexStrength", "VortexStrength")
        .uniform("CoreFlash", "CoreFlash")
        .uniform("OutroJet", "OutroJet")
        .uniform("Darkness", "Darkness")
        .build();
    private static final EmissiveComposite COMPOSITE = EmissiveComposite
        .builder(ContentShaders.BLACK_HOLE)
        .emissiveScale(RAYMARCH_SCALE)
        .bloom(ContentBlackHoleBloom.SETTINGS)
        .bloomUniforms(ContentBlackHoleBloom::bind)
        .emissionFog(false)
        .solidity(1.0F)
        .compositionFloor(1.0F)
        .compositeUniforms(ContentBlackHoleOperation::bindCompositeUniforms)
        .build();
    private static final EmissiveComposite.LayerPass EFFECT_PASS =
        ContentBlackHoleOperation::renderEffectLayer;
    private static final EmissiveComposite.LayerPass EMISSION_PASS =
        ContentBlackHoleOperation::renderEmissionLayer;

    /** The full-screen lens/disk must not sample a downscaled scene. */
    @Override
    public float compositionScaleFloor() {
        return COMPOSITE.compositionFloor();
    }

    @Override
    public ScreenEffectInputs requiredInputs() {
        return ScreenEffectInputs.ALL;
    }

    @Override
    public void render(ScreenEffectOperationContext context) {
        COMPOSITE.render(context, EFFECT_PASS, EMISSION_PASS);
    }

    private static void renderEffectLayer(ScreenEffectOperationContext context, RenderTarget target) {
        renderLayer(context, target, false);
    }

    private static void renderEmissionLayer(ScreenEffectOperationContext context,
        RenderTarget target) {
        renderLayer(context, target, true);
    }

    private static void renderLayer(ScreenEffectOperationContext context, RenderTarget target,
        boolean emissionOnly) {
        ContentBlackHoleSettings settings = context.settings(ContentBlackHoleSettings.class);
        ShaderInstance shader = shader();
        bindLayerInputs(context, shader, settings);
        context.setUniform(shader, "Pass", 0.0F);
        context.setUniform(shader, "EmissiveOnly", emissionOnly ? 1.0F : 0.0F);
        context.setUniform(shader, "ViewportWidth", (float) target.width);
        context.setUniform(shader, "ViewportHeight", (float) target.height);
        context.drawFullscreen(target, shader);
    }

    private static void bindLayerInputs(ScreenEffectOperationContext context, ShaderInstance shader,
        ContentBlackHoleSettings settings) {
        // The lens bends the accumulated composition, so later black holes see
        // the image of the ones behind them. The pristine scene is not sampled.
        context.setSampler(shader, "SceneSampler", context.sourceColor().getColorTextureId());
        context.setSampler(shader, "RawSceneSampler",
            context.rawSceneColor().getColorTextureId());
        context.setSampler(shader, "MaskSampler", context.maskTarget().getColorTextureId());
        context.setSampler(shader, "DepthSampler", context.maskTarget().getDepthTextureId());
        // Pass 0 never samples the raymarch texture, but the shader still declares it.
        // Bind a live texture so ShaderInstance.apply() never touches a released scratch ID.
        context.setSampler(shader, "EffectSampler", context.sourceColor().getColorTextureId());
        context.setSampler(shader, "BloomSampler", context.sourceColor().getColorTextureId());
        context.bindForegroundInputs(shader);
        bindEffectUniforms(context, shader, settings);
    }

    private static void bindCompositeUniforms(ScreenEffectOperationContext context,
        ShaderInstance shader) {
        ContentBlackHoleSettings settings = context.settings(ContentBlackHoleSettings.class);
        context.setSampler(shader, "RawSceneSampler",
            context.rawSceneColor().getColorTextureId());
        context.setSampler(shader, "DepthSampler", context.maskTarget().getDepthTextureId());
        context.bindForegroundInputs(shader);
        context.setUniform(shader, "Pass", 1.0F);
        context.setUniform(shader, "EmissiveOnly", 0.0F);
        context.setUniform(shader, "ViewportWidth", (float) context.destination().width);
        context.setUniform(shader, "ViewportHeight", (float) context.destination().height);
        bindEffectUniforms(context, shader, settings);
    }

    private static void bindEffectUniforms(ScreenEffectOperationContext context,
        ShaderInstance shader, ContentBlackHoleSettings settings) {
        EffectUniforms.from(context).bind(context, shader,
            EffectUniforms.Group.CAMERA, EffectUniforms.Group.SPACE,
            EffectUniforms.Group.EFFECT, EffectUniforms.Group.LIGHT);
        context.setUniform(shader, "MaskStrength", context.mask().strength());

        float progress = context.effect().progress();
        EffectParameterSet parameters = ContentBlackHoleCinematics.parameters();
        float horizonScale = parameters.sampleFloat("HorizonScale", progress);
        float diskScale = parameters.sampleFloat("DiskScale", progress);
        float lensWarpScale = parameters.sampleFloat("LensWarpScale", progress);
        float ringFormation = parameters.sampleFloat("RingFormation", progress);
        float turbulenceScale = parameters.sampleFloat("TurbulenceScale", progress);
        float shockRadiusScale = parameters.sampleFloat("ShockRadius", progress);
        float vortexRadiusScale = parameters.sampleFloat("VortexRadiusScale", progress);
        float baseScale = context.effect().scale();
        float horizonRadius = baseScale * horizonScale;
        float diskOuterRadius = baseScale * settings.diskOuterFactor() * diskScale;
        float lensRadius = Math.max(
            baseScale * settings.diskOuterFactor() * 1.1F,
            baseScale * 3.0F
        );
        context.setUniform(shader, "BlackHoleRadius", horizonRadius);
        context.setUniform(shader, "LensRadius", lensRadius);
        context.setUniform(shader, "DiskInnerRadius",
            baseScale * settings.diskInnerFactor() * diskScale);
        context.setUniform(shader, "DiskOuterRadius", diskOuterRadius);
        context.setUniform(shader, "DiskThickness",
            baseScale * settings.diskThicknessFactor() * diskScale);

        // The standard EFFECT group binds raw AgeTicks, Progress, and Seed. The black
        // hole overrides AgeTicks with its cinematic age because it fast-forwards the
        // cinematic clock inside the effect window.
        context.setUniform(shader, "AgeTicks",
            ContentBlackHoleCinematics.cinematicAge(context.effect().ageTicks(), progress));
        context.setUniform(shader, "Charge",
            ContentBlackHoleGeometry.chargeForSeed(context.effect().seed(), settings.charge()));
        CURVE_UNIFORMS.bind(context, shader, progress, null);
        context.setUniform(shader, "ShockRadius", baseScale * shockRadiusScale);
        context.setUniform(shader, "VortexRadius", baseScale * vortexRadiusScale);
        context.setUniform(shader, "FlashRadius", baseScale * 1.5F);
        context.setUniform(shader, "LensStrength", settings.lensStrength());
        context.setUniform(shader, "LensWarpRadius",
            baseScale * settings.lensWarpFactor() * lensWarpScale);
        context.setUniform(shader, "RingStrength",
            settings.ringBrightness() * ringFormation);
        context.setUniform(shader, "DiskBrightness",
            settings.diskBrightness() * ringFormation);
        context.setUniform(shader, "Turbulence",
            settings.turbulence() * turbulenceScale);
        context.setUniform(shader, "StepCount", (float) ContentBlackHoleGeometry.stepCount(
            context.effect().lod().level(), context.quality().level()
        ));

        ScreenEffectMediumOptics.from(context).bind(context, shader);
        context.setUniform(shader, "ForegroundDepthValid",
            context.hasForegroundDepth() ? 1.0F : 0.0F);
        context.setUniform(shader, "OpaqueColorValid", context.hasOpaqueColor() ? 1.0F : 0.0F);
    }

    private static ShaderInstance shader() {
        return ContentShaders.BLACK_HOLE.instance()
            .orElseThrow(() -> new IllegalStateException("Content black-hole shader is not loaded"));
    }
}
