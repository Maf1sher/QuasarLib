package mafisher.quasar.client.effect.render;

import mafisher.quasar.api.effect.EffectPoint;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import org.jetbrains.annotations.ApiStatus;

/** Clamped daylight sampling shared by world and screen effect contexts. */
@ApiStatus.Internal
public final class EffectLighting {
    private static final float SKY_AMBIENT_FLOOR = 0.12F;
    private static final float SKY_AMBIENT_RANGE = 0.78F;
    private static final float BLOCK_LIGHT_WEIGHT = 0.35F;

    private EffectLighting() {
    }

    /**
     * Samples the ambient light at an effect position and returns a smooth 0..1 daylight factor.
     *
     * <p>A missing level is night. The result is always finite and clamped.</p>
     */
    public static float daylight(ClientLevel level, EffectPoint position) {
        if (level == null) {
            return 0.0F;
        }
        BlockPos blockPosition = BlockPos.containing(position.x(), position.y(), position.z());
        float skyBrightness = level.getBrightness(LightLayer.SKY, blockPosition) / 15.0F;
        float skyDarkening = Math.min(1.0F, level.getSkyDarken() / 15.0F);
        float blockBrightness = level.getBrightness(LightLayer.BLOCK, blockPosition) / 15.0F;
        return daylight(skyBrightness, skyDarkening, blockBrightness);
    }

    /** Pure daylight calculation used by the renderer and headless tests. */
    static float daylight(float skyBrightness, float skyDarkening, float blockBrightness) {
        if (!Float.isFinite(skyBrightness) || !Float.isFinite(skyDarkening)
            || !Float.isFinite(blockBrightness)) {
            throw new IllegalArgumentException("Daylight inputs must be finite");
        }
        float sky = clamp01(skyBrightness);
        float darkening = clamp01(skyDarkening);
        float block = clamp01(blockBrightness);
        float ambient = Math.max(sky * (1.0F - darkening), block * BLOCK_LIGHT_WEIGHT);
        float normalized = clamp01((ambient - SKY_AMBIENT_FLOOR) / SKY_AMBIENT_RANGE);
        return normalized * normalized * (3.0F - 2.0F * normalized);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
