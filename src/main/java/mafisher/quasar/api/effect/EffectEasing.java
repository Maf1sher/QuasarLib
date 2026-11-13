package mafisher.quasar.api.effect;

/** Easing applied to the segment that starts at one keyframe. */
public enum EffectEasing {
    LINEAR {
        @Override
        public float apply(float progress) {
            return clamp(progress);
        }
    },
    EASE_IN {
        @Override
        public float apply(float progress) {
            float value = clamp(progress);
            return value * value;
        }
    },
    EASE_OUT {
        @Override
        public float apply(float progress) {
            float value = 1.0F - clamp(progress);
            return 1.0F - value * value;
        }
    },
    EASE_IN_OUT {
        @Override
        public float apply(float progress) {
            float value = clamp(progress);
            return value * value * (3.0F - 2.0F * value);
        }
    },
    EASE_IN_CUBIC {
        @Override
        public float apply(float progress) {
            float value = clamp(progress);
            return value * value * value;
        }
    },
    EASE_OUT_CUBIC {
        @Override
        public float apply(float progress) {
            return EffectTimeline.easeOutCubic(clamp(progress));
        }
    };

    /** Maps raw interpolation progress in {@code [0, 1]} to eased progress in {@code [0, 1]}. */
    public abstract float apply(float progress);

    private static float clamp(float progress) {
        if (!Float.isFinite(progress)) {
            throw new IllegalArgumentException("Easing progress must be finite");
        }
        return Math.max(0.0F, Math.min(1.0F, progress));
    }
}
