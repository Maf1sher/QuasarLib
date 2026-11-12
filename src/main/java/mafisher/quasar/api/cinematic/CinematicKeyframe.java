package mafisher.quasar.api.cinematic;

import java.util.Objects;
import mafisher.quasar.api.effect.EffectTransform;

/**
 * One transform sample on a cinematic track. The easing is applied to the segment from this keyframe to the next one;
 * the last keyframe's easing is unused.
 */
public record CinematicKeyframe(int tick, EffectTransform transform, EffectEasing easing) {
    public CinematicKeyframe {
        if (tick < 0) {
            throw new IllegalArgumentException("Cinematic keyframe ticks cannot be negative");
        }
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(easing, "easing");
    }
}
