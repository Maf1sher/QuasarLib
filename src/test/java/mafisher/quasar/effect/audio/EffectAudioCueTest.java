package mafisher.quasar.effect.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EffectAudioCueTest {
    private static final ResourceLocation SOUND = ResourceLocation.withDefaultNamespace("test");

    @Test
    void defaultsToVanillaAttenuationAndOneShotMotion() {
        EffectAudioCue cue = new EffectAudioCue(5, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F);

        assertEquals(EffectAudioAttenuation.VANILLA, cue.attenuation());
        assertEquals(EffectAudioEnvelope.ONE_SHOT, cue.envelope());
        assertEquals(EffectAudioMotion.ANCHORED, cue.motion());
        assertEquals(5, cue.tick());
    }

    @Test
    void rejectsLoopsThatStopAtOrBeforeTheirStart() {
        assertThrows(IllegalArgumentException.class, () -> new EffectAudioCue(
            5,
            Vec3.ZERO,
            SOUND,
            SoundSource.MASTER,
            1.0F,
            1.0F,
            EffectAudioAttenuation.VANILLA,
            EffectAudioEnvelope.loopUntil(5, 0, 0),
            EffectAudioMotion.ANCHORED
        ));
    }

    @Test
    void rejectsInvalidCueValues() {
        assertThrows(IllegalArgumentException.class,
            () -> new EffectAudioCue(-1, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectAudioCue(0, Vec3.ZERO, SOUND, SoundSource.MASTER, -1.0F, 1.0F));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectAudioCue(0, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 0.0F));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectAudioCue(0, new Vec3(Double.NaN, 0.0, 0.0), SOUND, SoundSource.MASTER, 1.0F, 1.0F));
        assertThrows(NullPointerException.class,
            () -> new EffectAudioCue(0, Vec3.ZERO, null, SoundSource.MASTER, 1.0F, 1.0F));
        assertThrows(NullPointerException.class,
            () -> new EffectAudioCue(0, Vec3.ZERO, SOUND, null, 1.0F, 1.0F));
    }
}
