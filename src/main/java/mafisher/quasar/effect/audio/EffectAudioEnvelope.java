package mafisher.quasar.effect.audio;

/**
 * Fade window and stop tick for one audio cue. Looping cues start at their cue tick, fade in,
 * loop until {@code stopTick}, and fade out before stopping. A stop tick of
 * {@link #END_OF_EFFECT} means the cue stops when the owning effect ends.
 */
public record EffectAudioEnvelope(boolean looping, int fadeInTicks, int fadeOutTicks, int stopTick) {
    /** Sentinel stop tick: the cue stops when the effect's plan ends. */
    public static final int END_OF_EFFECT = -1;
    /** A cue that plays once and never loops. */
    public static final EffectAudioEnvelope ONE_SHOT = new EffectAudioEnvelope(false, 0, 0, END_OF_EFFECT);

    public EffectAudioEnvelope {
        if (fadeInTicks < 0 || fadeOutTicks < 0) {
            throw new IllegalArgumentException("Effect audio fade ticks cannot be negative");
        }
        if (stopTick < END_OF_EFFECT) {
            throw new IllegalArgumentException("Effect audio stop tick cannot be below -1");
        }
        if (!looping) {
            if (fadeInTicks != 0 || fadeOutTicks != 0 || stopTick != END_OF_EFFECT) {
                throw new IllegalArgumentException("One-shot audio cues do not carry a fade window or stop tick");
            }
        }
    }

    /** A loop that starts at the cue tick and runs until the effect ends. */
    public static EffectAudioEnvelope loop(int fadeInTicks, int fadeOutTicks) {
        return new EffectAudioEnvelope(true, fadeInTicks, fadeOutTicks, END_OF_EFFECT);
    }

    /** A loop that stops at an absolute effect tick. */
    public static EffectAudioEnvelope loopUntil(int stopTick, int fadeInTicks, int fadeOutTicks) {
        return new EffectAudioEnvelope(true, fadeInTicks, fadeOutTicks, stopTick);
    }

    /** The effective stop tick, substituting the plan duration for {@link #END_OF_EFFECT}. */
    public int stopTickOr(int durationTicks) {
        return this.stopTick == END_OF_EFFECT ? durationTicks : this.stopTick;
    }

    /** True when this envelope describes a valid window for a cue at {@code cueTick}. */
    public boolean isValidFor(int cueTick, int durationTicks) {
        if (!this.looping) {
            return true;
        }
        if (durationTicks < 1 || cueTick < 0) {
            return false;
        }
        int stop = stopTickOr(durationTicks);
        return stop > cueTick && stop <= durationTicks;
    }

    /** Throws when this envelope cannot run inside the cue's effect timeline. */
    public void requireValidFor(int cueTick, int durationTicks) {
        if (!isValidFor(cueTick, durationTicks)) {
            throw new IllegalArgumentException(
                "Looping audio cue at tick " + cueTick + " must stop after its start and by "
                    + durationTicks
            );
        }
    }

    /**
     * Linear fade gain in {@code [0, 1]} at {@code ageTicks} after the cue started. One-shot cues
     * always return {@code 1}. Overlapping fades use the smaller gain.
     */
    public float gainAt(int ageTicks, int effectiveStopTick) {
        if (!this.looping) {
            return 1.0F;
        }
        if (ageTicks < 0 || effectiveStopTick <= 0) {
            return 0.0F;
        }
        int remaining = effectiveStopTick - ageTicks;
        if (remaining <= 0) {
            return 0.0F;
        }
        float gain = 1.0F;
        if (this.fadeInTicks > 0) {
            gain = Math.min(gain, (float)ageTicks / this.fadeInTicks);
        }
        if (this.fadeOutTicks > 0) {
            gain = Math.min(gain, (float)remaining / this.fadeOutTicks);
        }
        return Math.max(0.0F, Math.min(1.0F, gain));
    }
}
