package mafisher.quasar.client.effect.audio;

import java.util.Objects;
import mafisher.quasar.api.audio.EffectAudioPlan;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/**
 * One immutable audio-plan snapshot for the client inspector. All values are copied so the
 * inspector never retains scheduler state.
 *
 * @param plan active audio plan
 * @param ageTicks current plan age in ticks
 * @param nextCueIndex index of the next cue the scheduler will play
 * @param activeLoops number of playing loop tracks
 * @param activeInstances number of one-shot custom-attenuation sounds still active
 * @param effectTranslation current effect origin minus the plan center
 */
@ApiStatus.Internal
public record EffectAudioDiagnostics(
    EffectAudioPlan plan,
    int ageTicks,
    int nextCueIndex,
    int activeLoops,
    int activeInstances,
    Vec3 effectTranslation
) {
    public EffectAudioDiagnostics {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(effectTranslation, "effectTranslation");
        if (ageTicks < 0 || nextCueIndex < 0 || activeLoops < 0 || activeInstances < 0) {
            throw new IllegalArgumentException("Invalid effect audio diagnostics counters");
        }
        if (!Double.isFinite(effectTranslation.x) || !Double.isFinite(effectTranslation.y)
            || !Double.isFinite(effectTranslation.z)) {
            throw new IllegalArgumentException("Effect audio translation must be finite");
        }
    }
}
