package mafisher.quasar.effect.cinematic;

import mafisher.quasar.effect.EffectTimeline;

/** Easing applied to the segment that starts at one keyframe. */
public enum EffectEasing {
    LINEAR {
        @Override
        public float apply(float progress) {
            return clamp(progress);
        }
    },
    EASE_OUT_CUBIC {
        @Override
        public float apply(float progress) {
            return EffectTimeline.easeOutCubic(clamp(progress));
        }
    },
    EASE_IN_OUT {
        @Override
        public float apply(float progress) {
            float value = clamp(progress);
            return value * value * (3.0F - 2.0F * value);
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
