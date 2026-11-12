package mafisher.quasar.api.audio;

/**
 * Read-only phase timing used to resolve phase-relative audio cues without making the audio
 * package depend on the gameplay package.
 */
public interface EffectPhaseTiming {
    int phaseCount();

    String phaseName(int phaseIndex);

    int phaseStartTick(int phaseIndex);

    int totalDurationTicks();
}
