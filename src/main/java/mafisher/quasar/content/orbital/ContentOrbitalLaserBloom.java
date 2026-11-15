package mafisher.quasar.content.orbital;

import mafisher.quasar.api.client.screen.BloomSettings;
import mafisher.quasar.api.client.screen.ScreenEffectOperationContext;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import net.minecraft.client.renderer.ShaderInstance;

/** Bloom tuning for the orbital-laser emissive composite. */
final class ContentOrbitalLaserBloom {
    static final float THRESHOLD = 0.62F;
    static final BloomSettings SETTINGS = new BloomSettings(THRESHOLD, 1.1F, 6.0F);
    private static final float DAY_THRESHOLD = 0.50F;
    private static final float DAY_STRENGTH_SCALE = 0.45F;
    private static final float DAY_RADIUS_SCALE = 0.80F;

    private ContentOrbitalLaserBloom() {
    }

    static void bind(ScreenEffectOperationContext context, ShaderInstance shader) {
        float daylight = context.daylight();
        context.setUniform(shader, "Threshold", lerp(THRESHOLD, DAY_THRESHOLD, daylight));
        float qualityScale = context.effectiveQuality().atLeast(EffectQualityTier.MEDIUM) ? 1.0F : 0.7F;
        context.setUniform(shader, "RadiusPixels",
            lerp(SETTINGS.radiusPixels(), SETTINGS.radiusPixels() * DAY_RADIUS_SCALE, daylight)
                * qualityScale);
        context.setUniform(shader, "Intensity",
            SETTINGS.intensity() * lerp(1.0F, DAY_STRENGTH_SCALE, daylight) * qualityScale);
    }

    private static float lerp(float from, float to, float factor) {
        return from + (to - from) * factor;
    }
}
