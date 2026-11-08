package mafisher.quasar.content;

import com.mojang.blaze3d.pipeline.TextureTarget;
import mafisher.quasar.client.effect.ScreenEffectOperationContext;
import mafisher.quasar.client.effect.ScreenEffectOperationRenderer;
import mafisher.quasar.effect.EffectQualityTier;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Vector3f;

/** Content-owned raymarched black hole with a cheap bloom composite. */
final class ContentBlackHoleOperation implements ScreenEffectOperationRenderer {
    private static final int LINEAR_FILTER = 0x2601;
    private static final float BLOOM_THRESHOLD = 0.65F;

    @Override
    public void render(ScreenEffectOperationContext context) {
        ContentBlackHoleSettings settings = context.settings(ContentBlackHoleSettings.class);
        ShaderInstance shader = ContentShaders.BLACK_HOLE.instance()
            .orElseThrow(() -> new IllegalStateException("Content black-hole shader is not loaded"));
        bindCommonInputs(context, shader, settings);

        float raymarchScale = raymarchScale(context.quality());
        int beamWidth = Math.max(1, Math.round(context.destination().width * raymarchScale));
        int beamHeight = Math.max(1, Math.round(context.destination().height * raymarchScale));
        TextureTarget beam = context.acquireScratchTarget(beamWidth, beamHeight);
        beam.setFilterMode(LINEAR_FILTER);
        try {
            context.setUniform(shader, "Pass", 0.0F);
            context.setUniform(shader, "ViewportWidth", (float)beamWidth);
            context.setUniform(shader, "ViewportHeight", (float)beamHeight);
            context.drawFullscreen(beam, shader);

            context.setUniform(shader, "Pass", 1.0F);
            context.setUniform(shader, "ViewportWidth", (float)context.destination().width);
            context.setUniform(shader, "ViewportHeight", (float)context.destination().height);
            context.setSampler(shader, "EffectSampler", beam.getColorTextureId());
            context.setUniform(shader, "BloomStrength",
                context.quality().atLeast(EffectQualityTier.MEDIUM) ? settings.bloomStrength() : 0.0F);
            context.setUniform(shader, "BloomThreshold", BLOOM_THRESHOLD);
            context.setUniform(shader, "BloomRadiusPixels",
                context.quality().atLeast(EffectQualityTier.HIGH) ? 4.0F : 2.5F);
            context.drawFullscreen(context.destination(), shader);
        } finally {
            context.releaseScratchTarget(beam);
        }
    }

    private static float raymarchScale(EffectQualityTier quality) {
        if (quality == EffectQualityTier.ULTRA) {
            return 1.0F;
        }
        if (quality.atLeast(EffectQualityTier.HIGH)) {
            return 0.85F;
        }
        if (quality == EffectQualityTier.MEDIUM) {
            return 0.7F;
        }
        return 0.55F;
    }

    private static void bindCommonInputs(ScreenEffectOperationContext context, ShaderInstance shader,
        ContentBlackHoleSettings settings) {
        context.setSampler(shader, "SceneSampler", context.sourceColor().getColorTextureId());
        context.setSampler(shader, "MaskSampler", context.maskTarget().getColorTextureId());
        context.setSampler(shader, "DepthSampler", context.maskTarget().getDepthTextureId());

        context.setUniform(shader, "ViewMat", context.camera().modelViewMatrix());
        context.setUniform(shader, "WorldProjMat", context.camera().projectionMatrix());
        context.setUniform(shader, "InvProjMat", context.camera().inverseProjectionMatrix());
        context.setUniform(shader, "InvViewMat", context.camera().inverseViewMatrix());
        context.setUniform(shader, "ViewportWidth", (float)context.destination().width);
        context.setUniform(shader, "ViewportHeight", (float)context.destination().height);
        context.setUniform(shader, "MaskStrength", context.mask().strength());

        var camera = context.camera().position();
        var effect = context.effect().transform().position();
        context.setUniform(
            shader,
            "BlackHoleOffset",
            (float)(effect.x() - camera.x),
            (float)(effect.y() - camera.y),
            (float)(effect.z() - camera.z)
        );

        float effectScale = context.effect().scale();
        context.setUniform(shader, "BlackHoleRadius", effectScale);
        context.setUniform(shader, "DiskInnerRadius", effectScale * settings.diskInnerFactor());
        context.setUniform(shader, "DiskOuterRadius", effectScale * settings.diskOuterFactor());
        context.setUniform(shader, "DiskThickness", effectScale * settings.diskThicknessFactor());

        ContentBlackHoleGeometry.Basis basis = ContentBlackHoleGeometry.basis(
            context.effect().transform().yaw(),
            context.effect().transform().pitch(),
            context.effect().transform().roll()
        );
        setVector(context, shader, "DiskRight", basis.right());
        setVector(context, shader, "DiskForward", basis.forward());
        setVector(context, shader, "DiskNormal", basis.normal());

        context.setUniform(shader, "AgeTicks", context.effect().ageTicks());
        context.setUniform(shader, "Progress", context.effect().progress());
        context.setUniform(shader, "Seed",
            (float)((context.effect().seed() & 0xFFFFL) / 65535.0));
        context.setUniform(shader, "Charge",
            ContentBlackHoleGeometry.chargeForSeed(context.effect().seed(), settings.charge()));
        context.setUniform(shader, "Fade",
            ContentBlackHoleGeometry.fadeEnvelope(context.effect().progress()));
        context.setUniform(shader, "LensStrength", settings.lensStrength());
        context.setUniform(shader, "LensGravity", settings.lensGravity());
        context.setUniform(shader, "RingStrength", settings.ringBrightness());
        context.setUniform(shader, "DiskBrightness", settings.diskBrightness());
        context.setUniform(shader, "Turbulence", settings.turbulence());
        context.setUniform(shader, "StepCount", (float)ContentBlackHoleGeometry.stepCount(
            context.effect().lod().level(), context.quality().level()
        ));
    }

    private static void setVector(ScreenEffectOperationContext context, ShaderInstance shader,
        String name, Vector3f value) {
        context.setUniform(shader, name, value.x, value.y, value.z);
    }
}
