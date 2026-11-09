package mafisher.quasar.content;

import java.util.ArrayList;
import java.util.List;
import mafisher.quasar.effect.audio.EffectAudioAttenuation;
import mafisher.quasar.effect.audio.EffectAudioCue;
import mafisher.quasar.effect.audio.EffectAudioEnvelope;
import mafisher.quasar.effect.audio.EffectAudioMotion;
import mafisher.quasar.effect.audio.EffectAudioSpec;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Content-owned cinematic audio plan for the black-hole showcase. */
final class ContentBlackHoleAudio {
    private static final float AUDIBLE_RADIUS = 1024.0F;
    private static final float IGNITION = ContentBlackHoleCinematics.IGNITION;
    private static final float SHOCKWAVE = 0.058F;
    private static final float ACCRETION = 0.080F;
    private static final float COLLAPSE = ContentBlackHoleCinematics.DESTABILISE + 0.005F;
    private static final float IMPLOSION = ContentBlackHoleCinematics.IMPLOSION;
    private static final float ECHO = ContentBlackHoleCinematics.ECHO;
    private static final float[] PULSES = {0.220F, 0.400F, 0.580F, 0.760F};
    private static final float[] PULSE_PITCH = {0.92F, 1.0F, 1.08F, 0.97F};

    private ContentBlackHoleAudio() {
    }

    static EffectAudioSpec plan(Vec3 position, int durationTicks) {
        if (durationTicks < 40) {
            throw new IllegalArgumentException("Black-hole audio needs at least 40 ticks");
        }
        int fadeOut = Math.min(120, Math.max(1, durationTicks / 10));
        List<EffectAudioCue> cues = new ArrayList<>(14);
        cues.add(loop(0, position, ContentSounds.BLACK_HOLE_DRONE.get(), 0.55F, 1.0F,
            40, fadeOut));
        cues.add(oneShot(0, position, ContentSounds.BLACK_HOLE_DREAD.get(), 0.55F, 1.0F));
        cues.add(oneShot(tick(durationTicks, IGNITION), position,
            ContentSounds.BLACK_HOLE_IGNITION.get(), 1.0F, 1.0F));
        cues.add(oneShot(tick(durationTicks, SHOCKWAVE), position,
            ContentSounds.BLACK_HOLE_SHOCKWAVE.get(), 0.80F, 1.0F));
        cues.add(loop(tick(durationTicks, ACCRETION), position,
            ContentSounds.BLACK_HOLE_ACCRETION.get(), 0.45F, 1.0F, 40, fadeOut));
        for (int index = 0; index < PULSES.length; index++) {
            cues.add(oneShot(tick(durationTicks, PULSES[index]), position,
                ContentSounds.BLACK_HOLE_PULSE.get(), 0.45F, PULSE_PITCH[index]));
        }
        cues.add(oneShot(tick(durationTicks, COLLAPSE), position,
            ContentSounds.BLACK_HOLE_COLLAPSE.get(), 0.90F, 1.0F));
        cues.add(oneShot(tick(durationTicks, IMPLOSION), position,
            ContentSounds.BLACK_HOLE_IMPLOSION.get(), 1.0F, 1.0F));
        cues.add(oneShot(tick(durationTicks, ECHO), position,
            ContentSounds.BLACK_HOLE_ECHO.get(), 0.70F, 1.0F));
        return EffectAudioSpec.of(cues);
    }

    private static int tick(int durationTicks, float fraction) {
        return Math.max(0, Math.min(durationTicks - 1, Math.round(fraction * durationTicks)));
    }

    private static EffectAudioCue loop(int tick, Vec3 position, SoundEvent sound, float volume,
        float pitch, int fadeInTicks, int fadeOutTicks) {
        return new EffectAudioCue(
            tick,
            position,
            sound.getLocation(),
            SoundSource.AMBIENT,
            volume,
            pitch,
            EffectAudioAttenuation.distance(AUDIBLE_RADIUS),
            EffectAudioEnvelope.loop(fadeInTicks, fadeOutTicks),
            EffectAudioMotion.ANCHORED
        );
    }

    private static EffectAudioCue oneShot(int tick, Vec3 position, SoundEvent sound, float volume,
        float pitch) {
        return new EffectAudioCue(
            tick,
            position,
            sound.getLocation(),
            SoundSource.AMBIENT,
            volume,
            pitch,
            EffectAudioAttenuation.distance(AUDIBLE_RADIUS)
        );
    }
}
