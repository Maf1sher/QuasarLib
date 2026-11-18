package mafisher.quasar.content.orbital;

import mafisher.quasar.api.client.screen.BloomSettings;
import mafisher.quasar.api.client.screen.ScreenEffectOperationContext;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import net.minecraft.client.renderer.ShaderInstance;

/** Bloom tuning for the orbital-laser emissive composite. */
final class ContentOrbitalLaserBloom {
    static final float THRESHOLD = 0.64F;
    static final BloomSettings SETTINGS = new BloomSettings(THRESHOLD, 1.0F, 5.5F);
    private static final float DAY_THRESHOLD = 0.52F;
    private static final float DAY_STRENGTH_SCALE = 0.45F;
    private static final float DAY_RADIUS_SCALE = 0.80F;
    private static final float LOW_QUALITY_SCALE = 0.70F;
    private static final float LOD_SCALE_STEP = 0.05F;
    private static final float MIN_LOD_SCALE = 0.85F;

    private ContentOrbitalLaserBloom() {
    }

    /** Returns the bloom profile for the current day/night, quality, and distance LOD. */
    static BloomSettings profile(float daylight, EffectQualityTier quality, int lodLevel) {
        float day = clamp01(daylight);
        float qualityScale = quality.atLeast(EffectQualityTier.MEDIUM) ? 1.0F : LOW_QUALITY_SCALE;
        float lodScale = Math.max(MIN_LOD_SCALE,
            1.0F - Math.max(0, lodLevel) * LOD_SCALE_STEP);
        float scale = qualityScale * lodScale;
        return new BloomSettings(
            lerp(THRESHOLD, DAY_THRESHOLD, day),
            SETTINGS.intensity() * lerp(1.0F, DAY_STRENGTH_SCALE, day) * scale,
            SETTINGS.radiusPixels() * lerp(1.0F, DAY_RADIUS_SCALE, day) * scale
        );
    }

    static void bind(ScreenEffectOperationContext context, ShaderInstance shader) {
        BloomSettings profile = profile(
            context.daylight(), context.effectiveQuality(), context.effect().lod().level()
        );
        float waterScale = ContentOrbitalLaserWaterOptics.bloomScale(
            ContentOrbitalLaserWaterOptics.pathLength(context.mediumPath()));
        context.setUniform(shader, "Threshold", profile.threshold());
        context.setUniform(shader, "RadiusPixels", profile.radiusPixels());
        context.setUniform(shader, "Intensity", profile.intensity() * waterScale);
    }

    private static float lerp(float from, float to, float factor) {
        return from + (to - from) * factor;
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 0.0F;
    }
}
