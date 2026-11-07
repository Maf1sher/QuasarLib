package mafisher.quasar.effect.audio;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/**
 * How a cue's authored world position follows the owning effect. {@link Anchored} keeps the
 * authored position. {@link FollowEffect} translates it by the effect's movement since the plan
 * started, which lets continuous sounds track moving effects without per-tick packets.
 */
public sealed interface EffectAudioMotion
    permits EffectAudioMotion.Anchored, EffectAudioMotion.FollowEffect {

    /** Keep the authored world position. */
    EffectAudioMotion ANCHORED = new Anchored();

    /** Follow the owning effect's translation. */
    EffectAudioMotion FOLLOW_EFFECT = new FollowEffect();

    /**
     * Resolves the world position for one cue.
     *
     * @param authoredPosition position authored in the effect spec
     * @param effectTranslation current effect origin minus the origin captured when the plan started
     */
    Vec3 resolve(Vec3 authoredPosition, Vec3 effectTranslation);

    record Anchored() implements EffectAudioMotion {
        @Override
        public Vec3 resolve(Vec3 authoredPosition, Vec3 effectTranslation) {
            return Objects.requireNonNull(authoredPosition, "authoredPosition");
        }
    }

    record FollowEffect() implements EffectAudioMotion {
        @Override
        public Vec3 resolve(Vec3 authoredPosition, Vec3 effectTranslation) {
            return Objects.requireNonNull(authoredPosition, "authoredPosition")
                .add(Objects.requireNonNull(effectTranslation, "effectTranslation"));
        }
    }
}
