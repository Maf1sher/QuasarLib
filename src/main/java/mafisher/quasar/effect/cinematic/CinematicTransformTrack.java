package mafisher.quasar.effect.cinematic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import mafisher.quasar.effect.EffectTransform;

/**
 * Ordered transform keyframes for one timeline-local visual key. Sampling is deterministic and independent of wall
 * time; a track only positions a visual that is currently active.
 */
public final class CinematicTransformTrack {
    public static final int MAX_KEYFRAMES = 128;

    private final String key;
    private final List<CinematicKeyframe> keyframes;

    public CinematicTransformTrack(String key, List<CinematicKeyframe> keyframes) {
        this.key = CinematicCue.requireKey(key);
        Objects.requireNonNull(keyframes, "keyframes");
        if (keyframes.isEmpty() || keyframes.size() > MAX_KEYFRAMES) {
            throw new IllegalArgumentException("A cinematic track needs 1 to " + MAX_KEYFRAMES + " keyframes");
        }

        List<CinematicKeyframe> sorted = new ArrayList<>(keyframes);
        for (CinematicKeyframe keyframe : sorted) {
            Objects.requireNonNull(keyframe, "keyframe");
        }
        sorted.sort(Comparator.comparingInt(CinematicKeyframe::tick));
        for (int index = 1; index < sorted.size(); index++) {
            if (sorted.get(index - 1).tick() == sorted.get(index).tick()) {
                throw new IllegalArgumentException("Cinematic keyframe ticks must be unique within a track");
            }
        }
        this.keyframes = List.copyOf(sorted);
    }

    public String key() {
        return this.key;
    }

    public List<CinematicKeyframe> keyframes() {
        return this.keyframes;
    }

    public int firstTick() {
        return this.keyframes.getFirst().tick();
    }

    public int lastTick() {
        return this.keyframes.getLast().tick();
    }

    /** Samples the track at one timeline age. Ages outside the keyframes clamp to the nearest endpoint. */
    public EffectTransform sample(int ageTicks) {
        if (ageTicks < 0) {
            throw new IllegalArgumentException("Cinematic age cannot be negative");
        }
        if (ageTicks <= firstTick()) {
            return this.keyframes.getFirst().transform();
        }
        if (ageTicks >= lastTick()) {
            return this.keyframes.getLast().transform();
        }

        CinematicKeyframe from = this.keyframes.getFirst();
        CinematicKeyframe to = this.keyframes.get(1);
        for (int index = 1; index < this.keyframes.size(); index++) {
            to = this.keyframes.get(index);
            if (ageTicks < to.tick()) {
                from = this.keyframes.get(index - 1);
                break;
            }
            from = to;
        }

        int segmentTicks = to.tick() - from.tick();
        if (segmentTicks <= 0) {
            return to.transform();
        }
        float progress = (ageTicks - from.tick()) / (float)segmentTicks;
        return EffectTransform.interpolate(from.transform(), to.transform(), from.easing().apply(progress));
    }
}
