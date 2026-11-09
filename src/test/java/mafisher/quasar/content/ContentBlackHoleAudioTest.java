package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.effect.audio.EffectAudioCue;
import mafisher.quasar.effect.audio.EffectAudioSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ContentBlackHoleAudioTest {
    @Test
    void planFitsTheShowcaseTimeline() {
        EffectAudioSpec audio = ContentBlackHoleAudio.plan(Vec3.ZERO, 1200);

        assertFalse(audio.isEmpty());
        assertEquals(12, audio.cueCount());
        assertTrue(audio.fitsWithin(1200));
        assertEquals(2, audio.cues().stream()
            .filter(cue -> cue.envelope().looping()).count());
        assertTrue(audio.cues().getLast().tick() < 1200);
    }

    @Test
    void everyCueUsesAContentOwnedSound() {
        EffectAudioSpec audio = ContentBlackHoleAudio.plan(Vec3.ZERO, 1200);

        for (EffectAudioCue cue : audio.cues()) {
            assertEquals(ContentBootstrap.NAMESPACE, cue.soundId().getNamespace(),
                cue.soundId().toString());
        }
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "black_hole_ignition"),
            audio.cues().get(2).soundId());
    }

    @Test
    void cuesLandOnTheCinematicBeats() {
        EffectAudioSpec audio = ContentBlackHoleAudio.plan(Vec3.ZERO, 1200);

        assertEquals(48, audio.cues().get(2).tick());
        assertEquals(70, audio.cues().get(3).tick());
        assertEquals(1062, audio.cues().get(9).tick());
        assertEquals(1152, audio.cues().get(10).tick());
        assertEquals(1166, audio.cues().get(11).tick());
    }
}
