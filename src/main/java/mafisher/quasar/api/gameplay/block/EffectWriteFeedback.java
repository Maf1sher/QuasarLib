package mafisher.quasar.api.gameplay.block;

import mafisher.quasar.api.gameplay.world.EffectBlockSample;

/**
 * Callback for one successful block write.
 *
 * <p>The sample carries the position and the old block state, so debris and secondary effects
 * describe the block that was actually removed. The callback is never invoked for protection
 * vetoes, unchanged blocks, air, or failed writes. It runs on the server thread after the write
 * and must stay fast and non-blocking.</p>
 */
@FunctionalInterface
public interface EffectWriteFeedback {
    /** A feedback that ignores every write. */
    EffectWriteFeedback NONE = sample -> {
    };

    void onWritten(EffectBlockSample sample);
}
