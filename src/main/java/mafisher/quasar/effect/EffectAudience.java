package mafisher.quasar.effect;

/** Network delivery policy for a visual effect. */
public sealed interface EffectAudience permits EffectAudience.Nearby, EffectAudience.DimensionWide {
    static EffectAudience nearby(double radius) {
        return new Nearby(radius);
    }

    static EffectAudience dimensionWide() {
        return DimensionWide.INSTANCE;
    }

    record Nearby(double radius) implements EffectAudience {
        public Nearby {
            if (!Double.isFinite(radius) || radius < 0.0 || radius > 512.0) {
                throw new IllegalArgumentException("Effect audience radius must be between 0 and 512 blocks");
            }
        }
    }

    final class DimensionWide implements EffectAudience {
        private static final DimensionWide INSTANCE = new DimensionWide();

        private DimensionWide() {
        }
    }
}
