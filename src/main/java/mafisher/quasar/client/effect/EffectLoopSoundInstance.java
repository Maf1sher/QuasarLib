package mafisher.quasar.client.effect;

import mafisher.quasar.api.audio.EffectAudioAttenuation;
import mafisher.quasar.api.audio.EffectAudioCue;
import net.minecraft.client.resources.sounds.TickableSoundInstance;

/** Looping, positionable and envelope-controlled sound instance for continuous effect audio. */
final class EffectLoopSoundInstance extends EffectSoundInstance implements TickableSoundInstance {
    private boolean stopped;

    EffectLoopSoundInstance(EffectAudioCue cue) {
        super(cue, cue.attenuation() instanceof EffectAudioAttenuation.Vanilla);
        this.looping = true;
    }

    @Override
    public void tick() {
        // The scheduler updates position and gain each client tick.
    }

    @Override
    public boolean isStopped() {
        return this.stopped;
    }

    @Override
    public boolean canStartSilent() {
        // Loops start at zero gain when they fade in; the engine must not skip them.
        return true;
    }

    void stopNow() {
        this.stopped = true;
    }
}
