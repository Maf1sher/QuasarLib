package mafisher.quasar.content.orbital;

import com.mojang.blaze3d.pipeline.RenderTarget;
import mafisher.quasar.api.client.render.EffectParameterBinding;
import mafisher.quasar.api.client.render.EffectUniforms;
import mafisher.quasar.api.client.screen.EmissiveComposite;
import mafisher.quasar.api.client.screen.ScreenEffectInputs;
import mafisher.quasar.api.client.screen.ScreenEffectMediumOptics;
import mafisher.quasar.api.client.screen.ScreenEffectOperationContext;
import mafisher.quasar.api.client.screen.ScreenEffectOperationRenderer;
import mafisher.quasar.content.ContentShaders;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * Content-owned orbital-laser beam and impact screen operation.
 *
 * <p>The operation draws the whole sky-to-ground volume, the marker handoff contact glow, the
 * impact flash, the expanding shock ring, the collapse flash, and the aftermath rim glow in one
 * depth-tested pass. The standard {@link EmissiveComposite} owns bloom and the final scene re-add;
 * this layer writes additive emission with zero coverage alpha, so the captured background is
 * always preserved and no part of the beam replaces or darkens the scene. A depth-confirmed
 * foreground layer only tints the emission.</p>
 */
public final class ContentOrbitalLaserBeamOperation implements ScreenEffectOperationRenderer {
    private static final ContentOrbitalLaserBeamSettings SETTINGS =
        ContentOrbitalLaserBeamSettings.DEFAULT;
    private static final EffectParameterBinding CURVES = EffectParameterBinding
        .builder(ContentOrbitalLaserCinematics.parameters())
        .uniform("BeamTip", "BeamTip")
        .uniform("BeamStrength", "BeamStrength")
        .uniform("BeamWidth", "BeamWidth")
        .uniform("ImpactFlash", "ImpactFlash")
        .uniform("FinalFlash", "FinalFlash")
        .uniform("ShockRadiusScale", "ShockRadiusScale")
        .uniform("ShockStrength", "ShockStrength")
        .uniform("ContactGlow", "ContactGlow")
        .uniform("RimGlow", "RimGlow")
        .uniform("TipDepth", "TipDepth")
        .build();
    private static final EmissiveComposite COMPOSITE = EmissiveComposite
        .builder()
        .emissiveScale(1.0F)
        .bloom(ContentOrbitalLaserBloom.SETTINGS)
        .bloomUniforms(ContentOrbitalLaserBloom::bind)
        .emissionFog(false)
        .solidity(0.0F)
        .compositionFloor(ContentOrbitalLaserSettings.BEAM_COMPOSITION_FLOOR)
        .build();

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
        COMPOSITE.render(context, ContentOrbitalLaserBeamOperation::renderLayer);
    }

    private static void renderLayer(ScreenEffectOperationContext context, RenderTarget target) {
        ShaderInstance shader = shader();
        bindInputs(context, shader);
        context.drawFullscreen(target, shader);
    }

    private static void bindInputs(ScreenEffectOperationContext context, ShaderInstance shader) {
        context.setSampler(shader, "DepthSampler", context.maskTarget().getDepthTextureId());
        context.setSampler(shader, "RawSceneSampler",
            context.rawSceneColor().getColorTextureId());
        context.bindForegroundInputs(shader);
        context.setUniform(shader, "ForegroundDepthValid",
            context.hasForegroundDepth() ? 1.0F : 0.0F);
        context.setUniform(shader, "OpaqueColorValid",
            context.hasOpaqueColor() ? 1.0F : 0.0F);

        EffectUniforms.from(context).bind(context, shader,
            EffectUniforms.Group.CAMERA, EffectUniforms.Group.SPACE,
            EffectUniforms.Group.EFFECT, EffectUniforms.Group.LIGHT,
            EffectUniforms.Group.QUALITY);
        ScreenEffectMediumOptics.from(context).bind(context, shader);

        float scale = context.effect().scale();
        float radius = ContentOrbitalLaserBeamGeometry.effectiveRadius(
            scale,
            ContentOrbitalLaserCinematics.beamRadiusScale(
                context.effect().progress(), context.parameters()
            )
        );
        context.setUniform(shader, "BeamRadius", radius);
        context.setUniform(shader, "BeamTopHeight", SETTINGS.topHeight());
        context.setUniform(shader, "BeamCoreRadius", radius * SETTINGS.coreRadiusFactor());
        context.setUniform(shader, "BeamGlowRadius", radius * SETTINGS.glowRadiusFactor());
        context.setUniform(shader, "ShockMaxRadius", radius * SETTINGS.shockMaxRadiusFactor());
        context.setUniform(shader, "FlashRadius", radius * SETTINGS.flashRadiusFactor());
        context.setUniform(shader, "MarchSteps", (float)ContentOrbitalLaserBeamGeometry.marchSteps(
            context.effect().lod().level(), context.effectiveQuality()
        ));
        CURVES.bind(context, shader, context.effect().progress(), context.parameters());
    }

    private static ShaderInstance shader() {
        return ContentShaders.ORBITAL_LASER_BEAM.instance().orElseThrow(
            () -> new IllegalStateException("Content orbital-laser beam shader is not loaded")
        );
    }
}
