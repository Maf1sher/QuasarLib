package mafisher.quasar.content;

import com.mojang.blaze3d.pipeline.TextureTarget;
import mafisher.quasar.client.effect.ScreenEffectMedium;
import mafisher.quasar.client.effect.ScreenEffectMediumPath;
import mafisher.quasar.client.effect.ScreenEffectMediumState;
import mafisher.quasar.client.effect.ScreenEffectOperationContext;
import mafisher.quasar.client.effect.ScreenEffectInputs;
import mafisher.quasar.client.effect.ScreenEffectOperationRenderer;
import mafisher.quasar.compat.FogPolicy;
import mafisher.quasar.effect.EffectQualityTier;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Vector3f;

/** Content-owned raymarched black hole with a cheap bloom composite. */
final class ContentBlackHoleOperation implements ScreenEffectOperationRenderer {
    private static final int LINEAR_FILTER = 0x2601;
    private static final float BLOOM_THRESHOLD = 0.65F;
    /**
     * The composition is already quality-scaled, so the raymarch pass renders at full composition
     * resolution. Scaling it again compounds the reduction and turns the disk into a blocky smear.
     */
    private static final float RAYMARCH_SCALE = 1.0F;

    /** The full-screen lens/disk must not sample a downscaled scene. */
    @Override
    public float compositionScaleFloor() {
        return 1.0F;
    }

    @Override
    public ScreenEffectInputs requiredInputs() {
        return ScreenEffectInputs.ALL;
    }

    @Override
    public void render(ScreenEffectOperationContext context) {
        ContentBlackHoleSettings settings = context.settings(ContentBlackHoleSettings.class);
        ShaderInstance shader = ContentShaders.BLACK_HOLE.instance()
            .orElseThrow(() -> new IllegalStateException("Content black-hole shader is not loaded"));
        bindCommonInputs(context, shader, settings);

        int beamWidth = Math.max(1, Math.round(context.destination().width * RAYMARCH_SCALE));
        int beamHeight = Math.max(1, Math.round(context.destination().height * RAYMARCH_SCALE));
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

    private static void bindCommonInputs(ScreenEffectOperationContext context, ShaderInstance shader,
        ContentBlackHoleSettings settings) {
        // The lens bends the accumulated composition, so later black holes see
        // the image of the ones behind them. The pristine scene is not sampled.
        context.setSampler(shader, "SceneSampler", context.sourceColor().getColorTextureId());
        context.setSampler(shader, "RawSceneSampler", context.rawSceneColor().getColorTextureId());
        context.setSampler(shader, "MaskSampler", context.maskTarget().getColorTextureId());
        context.setSampler(shader, "DepthSampler", context.maskTarget().getDepthTextureId());
        // Pass 0 never samples the raymarch texture, but the shader still declares it.
        // Bind a live texture so ShaderInstance.apply() never touches a released scratch ID.
        context.setSampler(shader, "EffectSampler", context.sourceColor().getColorTextureId());
        context.setSampler(shader, "BloomSampler", context.sourceColor().getColorTextureId());
        context.bindForegroundInputs(shader);

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
        var path = context.mediumPath();
        boolean suppressEffect = suppressAboveWater(medium);
        context.setUniform(shader, "CameraSubmerged", medium.cameraSubmerged() ? 1.0F : 0.0F);
        context.setUniform(shader, "EffectSubmerged", medium.effectSubmerged() ? 1.0F : 0.0F);
        context.setUniform(shader, "SuppressAboveWater", suppressEffect ? 1.0F : 0.0F);
        // The fog follows every submerged span, so a lake between two air endpoints
        // still tints the effect. The shader falls back to the traced entry when the
        // path is empty and an endpoint is submerged (powder snow, missing level).
        context.setUniform(shader, "MediumPathLength", (float)path.submergedLength());
        context.setUniform(shader, "ForegroundDepthValid", context.hasForegroundDepth() ? 1.0F : 0.0F);
        context.setUniform(shader, "OpaqueColorValid", context.hasOpaqueColor() ? 1.0F : 0.0F);
        MediumSurface surface = mediumSurface(
            path, context.camera().position().y, suppressEffect);
        context.setUniform(shader, "SurfaceY", surface.surfaceY());
        context.setUniform(shader, "SurfaceActive", surface.active() ? 1.0F : 0.0F);
    }

    /**
     * Selects the refraction plane from the nearest water crossing instead of the
     * endpoint blocks. A lake between two air endpoints still changes the image, and
     * an exiting camera uses the plane it actually crosses.
     */
    static MediumSurface mediumSurface(ScreenEffectMediumPath path, double cameraY,
        boolean suppressed) {
        if (suppressed || path == null) {
            return MediumSurface.NONE;
        }
        var crossing = path.firstOpenWaterCrossing().orElse(null);
        if (crossing == null || !Float.isFinite(crossing.surfaceY())) {
            return MediumSurface.NONE;
        }
        float relative = crossing.surfaceY() - (float)cameraY;
        if (!Float.isFinite(relative)) {
            return MediumSurface.NONE;
        }
        return new MediumSurface(true, relative);
    }

    /** The active water plane, relative to the camera. */
    record MediumSurface(boolean active, float surfaceY) {
        static final MediumSurface NONE = new MediumSurface(false, 0.0F);
    }

    /**
     * A screen-space refraction of an above-water effect is not a stable reflection:
     * it is misplaced by the screen projection and slides with the camera. Hide the
     * effect instead when the camera is underwater and the effect is in air.
     */
    static boolean suppressAboveWater(ScreenEffectMediumState medium) {
        return medium.cameraMedium() == ScreenEffectMedium.WATER
            && medium.effectMedium() != ScreenEffectMedium.WATER;
    }

    private static void setVector(ScreenEffectOperationContext context, ShaderInstance shader,
        String name, Vector3f value) {
        context.setUniform(shader, name, value.x, value.y, value.z);
    }
}
