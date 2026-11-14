package mafisher.quasar.content.blackhole;

import mafisher.quasar.api.client.screen.BloomSettings;
import mafisher.quasar.api.client.screen.ScreenEffectOperationContext;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import net.minecraft.client.renderer.ShaderInstance;

/** Bloom tuning for the content black-hole emissive composite. */
final class ContentBlackHoleBloom {
    static final float THRESHOLD = 0.65F;
    static final BloomSettings SETTINGS = new BloomSettings(THRESHOLD, 1.0F, 4.0F);
    private static final float DAY_THRESHOLD = 0.50F;
    private static final float DAY_STRENGTH_SCALE = 0.35F;
    private static final float DAY_RADIUS_SCALE = 0.75F;

    private ContentBlackHoleBloom() {
    }

    static void bind(ScreenEffectOperationContext context, ShaderInstance shader) {
        ContentBlackHoleSettings settings = context.settings(ContentBlackHoleSettings.class);
        float daylight = context.daylight();
        context.setUniform(shader, "Threshold", lerp(THRESHOLD, DAY_THRESHOLD, daylight));
        context.setUniform(shader, "RadiusPixels",
            lerp(1.0F, DAY_RADIUS_SCALE, daylight)
                * (context.quality().atLeast(EffectQualityTier.HIGH) ? 4.0F : 2.5F));
        context.setUniform(shader, "Intensity",
            context.quality().atLeast(EffectQualityTier.MEDIUM)
                ? settings.bloomStrength() * lerp(1.0F, DAY_STRENGTH_SCALE, daylight)
                : 0.0F);
    }

    private static float lerp(float from, float to, float factor) {
        return from + (to - from) * factor;
    }
}
