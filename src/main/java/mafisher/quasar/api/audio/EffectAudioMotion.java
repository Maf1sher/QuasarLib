package mafisher.quasar.api.audio;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/**
 * How a cue's authored world position follows the owning effect. {@link Anchored} keeps the
 * authored position. {@link FollowEffect} translates it by the effect's movement since the plan
 * started, which lets continuous sounds track moving effects without per-tick packets. {@link Path}
 * interpolates between two offsets over an absolute effect-tick window, which lets looping sources
 * descend or sweep without per-tick packets.
 */
public sealed interface EffectAudioMotion
    permits EffectAudioMotion.Anchored, EffectAudioMotion.FollowEffect, EffectAudioMotion.Path {

    /** Keep the authored world position. */
    EffectAudioMotion ANCHORED = new Anchored();

    /** Follow the owning effect's translation. */
    EffectAudioMotion FOLLOW_EFFECT = new FollowEffect();

    /**
     * Resolves the world position for one cue at effect age zero.
     *
     * @param authoredPosition position authored in the effect spec
     * @param effectTranslation current effect origin minus the origin captured when the plan started
     */
    default Vec3 resolve(Vec3 authoredPosition, Vec3 effectTranslation) {
        return resolve(authoredPosition, effectTranslation, 0);
    }

    /**
     * Resolves the world position for one cue at an effect age.
     *
     * @param authoredPosition position authored in the effect spec
     * @param effectTranslation current effect origin minus the origin captured when the plan started
     * @param ageTicks effect-local ticks since the plan started
     */
    Vec3 resolve(Vec3 authoredPosition, Vec3 effectTranslation, int ageTicks);

    record Anchored() implements EffectAudioMotion {
        @Override
        public Vec3 resolve(Vec3 authoredPosition, Vec3 effectTranslation, int ageTicks) {
            return Objects.requireNonNull(authoredPosition, "authoredPosition");
        }
    }

    record FollowEffect() implements EffectAudioMotion {
        @Override
        public Vec3 resolve(Vec3 authoredPosition, Vec3 effectTranslation, int ageTicks) {
            return Objects.requireNonNull(authoredPosition, "authoredPosition")
                .add(Objects.requireNonNull(effectTranslation, "effectTranslation"));
        }
    }

    /**
     * Moves between two offsets relative to the authored position over an absolute effect-tick
     * window. Before {@code startTick} the start offset applies; at or after {@code endTick} the end
     * offset applies; inside the window the offsets are interpolated linearly. Path motion is only
     * valid for looping cues because one-shot sounds are positioned once when they start. The
     * offsets are relative to the authored world position and do not follow the owning effect's
     * translation.
     */
    record Path(Vec3 startOffset, Vec3 endOffset, int startTick, int endTick)
        implements EffectAudioMotion {

        public Path {
            requireFinite(startOffset, "startOffset");
            requireFinite(endOffset, "endOffset");
            if (startTick < 0) {
                throw new IllegalArgumentException("Effect audio path start tick cannot be negative");
            }
            if (endTick <= startTick) {
                throw new IllegalArgumentException(
                    "Effect audio path end tick must be after its start tick"
                );
            }
        }

        @Override
        public Vec3 resolve(Vec3 authoredPosition, Vec3 effectTranslation, int ageTicks) {
            Objects.requireNonNull(authoredPosition, "authoredPosition");
            double progress;
            if (ageTicks <= this.startTick) {
                progress = 0.0;
            } else if (ageTicks >= this.endTick) {
                progress = 1.0;
            } else {
                progress = (double)(ageTicks - this.startTick) / (this.endTick - this.startTick);
            }
            return authoredPosition.add(this.startOffset.lerp(this.endOffset, progress));
        }

        /** Rebases this window by {@code deltaTicks}, used when a phase cue is shifted. */
        Path shiftedBy(int deltaTicks) {
            long shiftedStart = (long)this.startTick + deltaTicks;
            long shiftedEnd = (long)this.endTick + deltaTicks;
            if (shiftedStart < 0L || shiftedEnd > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                    "Effect audio path window cannot shift outside the effect timeline"
                );
            }
            return new Path(this.startOffset, this.endOffset, (int)shiftedStart, (int)shiftedEnd);
        }

        private static void requireFinite(Vec3 offset, String label) {
            Objects.requireNonNull(offset, label);
            if (!Double.isFinite(offset.x) || !Double.isFinite(offset.y)
                || !Double.isFinite(offset.z)) {
                throw new IllegalArgumentException("Effect audio path " + label + " must be finite");
            }
        }
    }
}
