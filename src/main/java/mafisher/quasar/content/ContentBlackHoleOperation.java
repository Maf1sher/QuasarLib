package mafisher.quasar.content;

import com.mojang.blaze3d.pipeline.TextureTarget;
import mafisher.quasar.client.effect.ScreenEffectOperationContext;
import mafisher.quasar.client.effect.ScreenEffectOperationRenderer;
import mafisher.quasar.compat.FogPolicy;
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
        int glowWidth = Math.max(1, beamWidth / 2);
        int glowHeight = Math.max(1, beamHeight / 2);
        TextureTarget glow = context.acquireScratchTarget(glowWidth, glowHeight);
        glow.setFilterMode(LINEAR_FILTER);
        try {
            context.setUniform(shader, "Pass", 0.0F);
            context.setUniform(shader, "EmissiveOnly", 0.0F);
            context.setUniform(shader, "ViewportWidth", (float)beamWidth);
            context.setUniform(shader, "ViewportHeight", (float)beamHeight);
            context.drawFullscreen(beam, shader);

            // Pass 2 writes only the accretion disk, photon ring, and halo. Blooming
            // this layer instead of the lensed background removes the bright daytime
            // halo while keeping the cinematic glow.
            context.setUniform(shader, "EmissiveOnly", 1.0F);
            context.setUniform(shader, "ViewportWidth", (float)glowWidth);
            context.setUniform(shader, "ViewportHeight", (float)glowHeight);
            context.setSampler(shader, "BloomSampler", context.sourceColor().getColorTextureId());
            context.drawFullscreen(glow, shader);

            context.setUniform(shader, "Pass", 1.0F);
            context.setUniform(shader, "EmissiveOnly", 0.0F);
            context.setUniform(shader, "ViewportWidth", (float)context.destination().width);
            context.setUniform(shader, "ViewportHeight", (float)context.destination().height);
            context.setSampler(shader, "EffectSampler", beam.getColorTextureId());
            context.setSampler(shader, "BloomSampler", glow.getColorTextureId());
            context.setUniform(shader, "BloomStrength",
                context.quality().atLeast(EffectQualityTier.MEDIUM) ? settings.bloomStrength() : 0.0F);
            context.setUniform(shader, "BloomThreshold", BLOOM_THRESHOLD);
            context.setUniform(shader, "BloomRadiusPixels",
                context.quality().atLeast(EffectQualityTier.HIGH) ? 4.0F : 2.5F);
            context.drawFullscreen(context.destination(), shader);
        } finally {
            context.releaseScratchTarget(beam);
            context.releaseScratchTarget(glow);
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
        // The lens bends the accumulated composition, so later black holes see
        // the image of the ones behind them. The pristine scene is not sampled.
        context.setSampler(shader, "SceneSampler", context.sourceColor().getColorTextureId());
        context.setSampler(shader, "MaskSampler", context.maskTarget().getColorTextureId());
        context.setSampler(shader, "DepthSampler", context.maskTarget().getDepthTextureId());
        // Pass 0 never samples the raymarch texture, but the shader still declares it.
        // Bind a live texture so ShaderInstance.apply() never touches a released scratch ID.
        context.setSampler(shader, "EffectSampler", context.sourceColor().getColorTextureId());
        context.setSampler(shader, "BloomSampler", context.sourceColor().getColorTextureId());

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

        float baseScale = context.effect().scale();
        ContentBlackHoleCinematics.Frame cinematics =
            ContentBlackHoleCinematics.frame(context.effect().progress());
        float horizonRadius = baseScale * cinematics.horizonScale();
        float diskOuterRadius = baseScale * settings.diskOuterFactor() * cinematics.diskScale();
        float lensRadius = Math.max(
            baseScale * settings.diskOuterFactor() * 1.1F,
            baseScale * 3.0F
        );
        context.setUniform(shader, "BlackHoleRadius", horizonRadius);
        context.setUniform(shader, "LensRadius", lensRadius);
        context.setUniform(shader, "DiskInnerRadius",
            baseScale * settings.diskInnerFactor() * cinematics.diskScale());
        context.setUniform(shader, "DiskOuterRadius", diskOuterRadius);
        context.setUniform(shader, "DiskThickness",
            baseScale * settings.diskThicknessFactor() * cinematics.diskScale());

        ContentBlackHoleGeometry.Basis basis = ContentBlackHoleGeometry.basis(
            context.effect().transform().yaw(),
            context.effect().transform().pitch(),
            context.effect().transform().roll()
        );
        setVector(context, shader, "DiskRight", basis.right());
        setVector(context, shader, "DiskForward", basis.forward());
        setVector(context, shader, "DiskNormal", basis.normal());

        context.setUniform(shader, "AgeTicks",
            ContentBlackHoleCinematics.cinematicAge(
                context.effect().ageTicks(), context.effect().progress()));
        context.setUniform(shader, "Progress", context.effect().progress());
        context.setUniform(shader, "Seed",
            (float)((context.effect().seed() & 0xFFFFL) / 65535.0));
        context.setUniform(shader, "Charge",
            ContentBlackHoleGeometry.chargeForSeed(context.effect().seed(), settings.charge()));
        context.setUniform(shader, "Fade", cinematics.fade());
        context.setUniform(shader, "RingFormation", cinematics.ringFormation());
        context.setUniform(shader, "ShockRadius", baseScale * cinematics.shockRadius());
        context.setUniform(shader, "ShockStrength", cinematics.shockStrength());
        context.setUniform(shader, "ShockAbsorb", cinematics.shockAbsorb());
        context.setUniform(shader, "VortexStrength", cinematics.vortexStrength());
        context.setUniform(shader, "VortexRadius",
            baseScale * cinematics.vortexRadiusScale());
        context.setUniform(shader, "CoreFlash", cinematics.coreFlash());
        context.setUniform(shader, "FlashRadius", baseScale * 1.5F);
        context.setUniform(shader, "OutroJet", cinematics.outroJet());
        context.setUniform(shader, "Darkness", cinematics.darkness());
        context.setUniform(shader, "LensStrength", settings.lensStrength());
        context.setUniform(shader, "LensWarpRadius",
            baseScale * settings.lensWarpFactor() * cinematics.lensWarpScale());
        context.setUniform(shader, "RingStrength",
            settings.ringBrightness() * cinematics.ringStrengthScale());
        context.setUniform(shader, "DiskBrightness",
            settings.diskBrightness() * cinematics.diskBrightnessScale());
        context.setUniform(shader, "Turbulence",
            settings.turbulence() * cinematics.turbulenceScale());
        context.setUniform(shader, "StepCount", (float)ContentBlackHoleGeometry.stepCount(
            context.effect().lod().level(), context.quality().level()
        ));

        var fog = context.fogState();
        context.setUniform(shader, "MediumFogStart", fog.start());
        context.setUniform(shader, "MediumFogEnd", fog.end());
        context.setUniform(shader, "MediumFogColor",
            (float)fog.color().x, (float)fog.color().y, (float)fog.color().z);
        boolean fogAllowed = context.environment().fogPolicy() == FogPolicy.RESPECT;
        context.setUniform(shader, "MediumFogActive", fogAllowed && fog.active() ? 1.0F : 0.0F);
        var medium = context.medium();
        context.setUniform(shader, "CameraSubmerged", medium.cameraSubmerged() ? 1.0F : 0.0F);
        context.setUniform(shader, "EffectSubmerged", medium.effectSubmerged() ? 1.0F : 0.0F);
        float surfaceY = 0.0F;
        boolean surfaceActive = false;
        if (medium.cameraSubmerged() && !medium.effectSubmerged()
            && !Float.isNaN(medium.cameraSurfaceY())) {
            surfaceY = medium.cameraSurfaceY();
            surfaceActive = true;
        } else if (!medium.cameraSubmerged() && medium.effectSubmerged()
            && !Float.isNaN(medium.effectSurfaceY())) {
            surfaceY = medium.effectSurfaceY();
            surfaceActive = true;
        }
        surfaceActive = surfaceActive && fogAllowed;
        context.setUniform(shader, "SurfaceY", surfaceY);
        context.setUniform(shader, "SurfaceActive", surfaceActive ? 1.0F : 0.0F);
    }

    private static void setVector(ScreenEffectOperationContext context, ShaderInstance shader,
        String name, Vector3f value) {
        context.setUniform(shader, name, value.x, value.y, value.z);
    }
}
