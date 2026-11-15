package mafisher.quasar.api.client.particle;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Texture, size, color, and softness for one {@link EffectParticleField}.
 *
 * @param texture standalone RGBA texture sampled by every particle
 * @param blend blending mode used by the engine render type
 * @param size billboard size in local blocks over the particle lifetime
 * @param colorStart packed {@code 0xAARRGGBB} color at birth
 * @param colorEnd packed {@code 0xAARRGGBB} color at death
 * @param softness soft-particle fade thickness in blocks; {@code 0} keeps a hard edge
 */
public record EffectParticleAppearance(
    ResourceLocation texture,
    Blend blend,
    SizeRange size,
    int colorStart,
    int colorEnd,
    double softness
) {
    /** Maximum billboard size in local blocks. */
    public static final double MAX_SIZE = 8.0;
    /** Maximum soft-particle thickness in blocks. */
    public static final double MAX_SOFTNESS = 8.0;

    public EffectParticleAppearance {
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(blend, "blend");
        Objects.requireNonNull(size, "size");
        if (!Double.isFinite(softness) || softness < 0.0 || softness > MAX_SOFTNESS) {
            throw new IllegalArgumentException("Particle softness must be between 0 and "
                + MAX_SOFTNESS);
        }
    }

    /** Larger of the two authored sizes, used for culling bounds. */
    public double maxSize() {
        return Math.max(this.size.start(), this.size.end());
    }

    /** Blending mode for the engine-owned particle render type. */
    public enum Blend {
        /** Standard alpha blending; fields are sorted back to front, particles are not. */
        ALPHA,
        /** Additive glow; draw order does not affect the result. */
        ADDITIVE
    }

    /** Billboard size at birth and death, in local blocks. */
    public record SizeRange(double start, double end) {
        public SizeRange {
            requireSize(start);
            requireSize(end);
        }
    }

    private static void requireSize(double value) {
        if (!Double.isFinite(value) || value <= 0.0 || value > MAX_SIZE) {
            throw new IllegalArgumentException("Particle size must be greater than 0 and at most "
                + MAX_SIZE);
        }
    }
}
