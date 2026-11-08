package mafisher.quasar.content;

import java.util.ArrayList;
import java.util.List;
import mafisher.quasar.effect.audio.EffectAudioAttenuation;
import mafisher.quasar.effect.audio.EffectAudioCue;
import mafisher.quasar.effect.audio.EffectAudioEnvelope;
import mafisher.quasar.effect.audio.EffectAudioMotion;
import mafisher.quasar.effect.audio.EffectAudioSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Content-owned ambient audio plan for the black-hole showcase. */
final class ContentBlackHoleAudio {
    private static final float AUDIBLE_RADIUS = 512.0F;
    private static final SoundEvent PORTAL = sound("block.portal.ambient");
    private static final SoundEvent HEARTBEAT = sound("entity.warden.heartbeat");
    private static final SoundEvent CHIME = sound("block.amethyst_block.chime");
    private static final SoundEvent GROWL = sound("entity.ender_dragon.growl");

    private ContentBlackHoleAudio() {
    }

    static EffectAudioSpec plan(Vec3 position, int durationTicks) {
        if (durationTicks < 40) {
            throw new IllegalArgumentException("Black-hole audio needs at least 40 ticks");
        }
        List<EffectAudioCue> cues = new ArrayList<>(5);
        int fade = Math.max(20, durationTicks / 10);
        cues.add(new EffectAudioCue(
            0,
            position,
            PORTAL.getLocation(),
            SoundSource.AMBIENT,
            0.8F,
            0.45F,
            EffectAudioAttenuation.distance(AUDIBLE_RADIUS),
            EffectAudioEnvelope.loop(fade, fade),
            EffectAudioMotion.ANCHORED
        ));
        cues.add(oneShot(durationTicks / 30, position, HEARTBEAT, 0.55F, 0.55F));
        cues.add(oneShot(durationTicks / 5, position, HEARTBEAT, 0.45F, 0.5F));
        cues.add(oneShot(durationTicks / 2, position, CHIME, 0.35F, 0.55F));
        cues.add(oneShot(durationTicks * 3 / 4, position, GROWL, 0.55F, 0.4F));
        return EffectAudioSpec.of(cues);
    }

    private static EffectAudioCue oneShot(int tick, Vec3 position, SoundEvent sound, float volume, float pitch) {
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

    private static SoundEvent sound(String path) {
        return SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace(path));
    }
}
