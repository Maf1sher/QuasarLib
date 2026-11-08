package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.effect.audio.EffectAudioSpec;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ContentBlackHoleAudioTest {
    @Test
    void planFitsTheShowcaseTimeline() {
        EffectAudioSpec audio = ContentBlackHoleAudio.plan(Vec3.ZERO, 1200);

        assertFalse(audio.isEmpty());
        assertEquals(5, audio.cueCount());
        assertTrue(audio.fitsWithin(1200));
        assertTrue(audio.cues().getFirst().envelope().looping());
        assertTrue(audio.cues().getLast().tick() < 1200);
    }
}
