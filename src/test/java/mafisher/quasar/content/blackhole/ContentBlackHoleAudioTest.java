package mafisher.quasar.content.blackhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Supplier;
import mafisher.quasar.api.audio.EffectAudioCue;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.content.ContentBootstrap;
import mafisher.quasar.content.ContentSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ContentBlackHoleAudioTest {
    private static final int SHOWCASE_DURATION = 1200;

    @Test
    void planFitsEveryShowcaseDuration() {
        for (int duration : List.of(40, 120, 600, SHOWCASE_DURATION, 72_000)) {
            EffectAudioSpec audio = ContentBlackHoleAudio.plan(Vec3.ZERO, duration);

            assertFalse(audio.isEmpty(), "duration " + duration);
            assertTrue(audio.fitsWithin(duration), "duration " + duration);
            assertCuesAreOrderedAndInside(duration, audio);
        }
    }

    @Test
    void cuesScaleWithTheShowDuration() {
        int shortDuration = 600;
        EffectAudioSpec shortAudio = ContentBlackHoleAudio.plan(Vec3.ZERO, shortDuration);
        EffectAudioSpec longAudio = ContentBlackHoleAudio.plan(Vec3.ZERO, SHOWCASE_DURATION);

        assertScaled(shortAudio, longAudio, ContentSounds.BLACK_HOLE_IGNITION);
        assertScaled(shortAudio, longAudio, ContentSounds.BLACK_HOLE_SHOCKWAVE);
        assertScaled(shortAudio, longAudio, ContentSounds.BLACK_HOLE_COLLAPSE);
        assertScaled(shortAudio, longAudio, ContentSounds.BLACK_HOLE_IMPLOSION);
        assertScaled(shortAudio, longAudio, ContentSounds.BLACK_HOLE_ECHO);
    }

    @Test
    void everyCueUsesAContentOwnedSound() {
        EffectAudioSpec audio = ContentBlackHoleAudio.plan(Vec3.ZERO, SHOWCASE_DURATION);

        for (EffectAudioCue cue : audio.cues()) {
            assertEquals(ContentBootstrap.NAMESPACE, cue.soundId().getNamespace(),
                cue.soundId().toString());
        }
        assertEquals(ContentSounds.BLACK_HOLE_IGNITION.get().getLocation(),
            cue(audio, ContentSounds.BLACK_HOLE_IGNITION).soundId());
        assertEquals(ContentSounds.BLACK_HOLE_ECHO.get().getLocation(),
            cue(audio, ContentSounds.BLACK_HOLE_ECHO).soundId());
    }

    @Test
    void cuesFollowTheCinematicBeats() {
        int duration = SHOWCASE_DURATION;
        EffectAudioSpec audio = ContentBlackHoleAudio.plan(Vec3.ZERO, duration);

        int ignition = Math.round(ContentBlackHoleCinematics.IGNITION * duration);
        int shockwave = Math.round(ContentBlackHoleCinematics.HORIZON_END * duration);
        int implosion = Math.round(ContentBlackHoleCinematics.IMPLOSION * duration);
        int echo = Math.round(ContentBlackHoleCinematics.ECHO * duration);
        int collapse = cue(audio, ContentSounds.BLACK_HOLE_COLLAPSE).tick();

        assertEquals(ignition, cue(audio, ContentSounds.BLACK_HOLE_IGNITION).tick());
        assertEquals(shockwave, cue(audio, ContentSounds.BLACK_HOLE_SHOCKWAVE).tick());
        assertEquals(implosion, cue(audio, ContentSounds.BLACK_HOLE_IMPLOSION).tick());
        assertEquals(echo, cue(audio, ContentSounds.BLACK_HOLE_ECHO).tick());
        assertTrue(collapse >= Math.round(ContentBlackHoleCinematics.DESTABILISE * duration));
        assertTrue(collapse < implosion);

        List<EffectAudioCue> pulses = cues(audio, ContentSounds.BLACK_HOLE_PULSE);
        assertFalse(pulses.isEmpty());
        for (EffectAudioCue pulse : pulses) {
            assertTrue(pulse.tick() > shockwave, "pulse " + pulse.tick());
            assertTrue(pulse.tick() < collapse, "pulse " + pulse.tick());
        }
    }

    @Test
    void rejectsDurationsBelowTheMinimum() {
        assertThrows(IllegalArgumentException.class,
            () -> ContentBlackHoleAudio.plan(Vec3.ZERO, 39));
    }

    private static void assertCuesAreOrderedAndInside(int duration, EffectAudioSpec audio) {
        int previousTick = -1;
        for (EffectAudioCue cue : audio.cues()) {
            assertTrue(cue.tick() >= previousTick, "cue order at tick " + cue.tick());
            assertTrue(cue.tick() >= 0 && cue.tick() < duration, "cue tick " + cue.tick());
            previousTick = cue.tick();

            if (cue.envelope().looping()) {
                int stopTick = cue.envelope().stopTickOr(duration);
                assertTrue(stopTick > cue.tick(), "loop stop " + stopTick);
                assertTrue(stopTick <= duration, "loop stop " + stopTick);
                assertTrue(cue.envelope().fadeInTicks() >= 0);
                assertTrue(cue.envelope().fadeOutTicks() >= 0);
            }
        }
    }

    private static void assertScaled(
        EffectAudioSpec shortAudio,
        EffectAudioSpec longAudio,
        Supplier<SoundEvent> sound
    ) {
        int shortTick = cue(shortAudio, sound).tick();
        int longTick = cue(longAudio, sound).tick();

        assertEquals(shortTick * 2.0, longTick, 1.0, sound.get().getLocation().toString());
    }

    private static EffectAudioCue cue(EffectAudioSpec audio, Supplier<SoundEvent> sound) {
        return cues(audio, sound).getFirst();
    }

    private static List<EffectAudioCue> cues(EffectAudioSpec audio, Supplier<SoundEvent> sound) {
        return audio.cues().stream()
            .filter(cue -> cue.soundId().equals(sound.get().getLocation()))
            .toList();
    }
}
