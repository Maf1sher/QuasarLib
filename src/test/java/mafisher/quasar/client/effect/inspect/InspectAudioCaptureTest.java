package mafisher.quasar.client.effect.inspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.audio.EffectAudioAttenuation;
import mafisher.quasar.api.audio.EffectAudioCue;
import mafisher.quasar.api.audio.EffectAudioEnvelope;
import mafisher.quasar.api.audio.EffectAudioMotion;
import mafisher.quasar.api.audio.EffectAudioPlan;
import mafisher.quasar.client.effect.audio.EffectAudioDiagnostics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class InspectAudioCaptureTest {
    @Test
    void capturesDirectionalConeAndListenerGain() {
        EffectAudioPlan plan = plan(new EffectAudioCue(
            0,
            Vec3.ZERO,
            ResourceLocation.withDefaultNamespace("test"),
            SoundSource.MASTER,
            1.0F,
            1.0F,
            EffectAudioAttenuation.directional(new Vec3(0, 0, 1), 30.0F, 60.0F, 0.2F, 20.0F),
            EffectAudioEnvelope.ONE_SHOT,
            EffectAudioMotion.ANCHORED
        ));
        InspectFrame frame = new InspectFrame();
        frame.begin(0L, 0L, 1L);

        InspectAudioCapture.capture(
            frame,
            new EffectAudioDiagnostics(plan, 10, 0, 0, 0, Vec3.ZERO),
            new Vec3(0, 0, 10)
        );

        assertEquals(1, frame.audioPlanCount());
        assertEquals(1, frame.audioCueCount());
        InspectFrame.AudioCueSlot cue = frame.audioCue(0);
        assertTrue(cue.directional);
        assertTrue(cue.played);
        assertEquals(0.5F, cue.listenerGain, 1.0E-6F);
        assertEquals(20.0F, cue.maxDistance, 1.0E-6F);
    }

    @Test
    void vanillaCuesAreSkippedAndDistanceCuesGetARing() {
        EffectAudioPlan plan = plan(
            new EffectAudioCue(
                0,
                Vec3.ZERO,
                ResourceLocation.withDefaultNamespace("test"),
                SoundSource.MASTER,
                1.0F,
                1.0F
            ),
            new EffectAudioCue(
                1,
                new Vec3(0, 0, 2),
                ResourceLocation.withDefaultNamespace("test"),
                SoundSource.MASTER,
                1.0F,
                1.0F,
                EffectAudioAttenuation.distance(10.0F)
            )
        );
        InspectFrame frame = new InspectFrame();
        frame.begin(0L, 0L, 1L);

        InspectAudioCapture.capture(
            frame,
            new EffectAudioDiagnostics(plan, 10, 1, 0, 0, Vec3.ZERO),
            new Vec3(0, 0, 0)
        );

        assertEquals(1, frame.audioCueCount());
        assertFalse(frame.audioCue(0).directional);
        assertEquals(10.0F, frame.audioCue(0).maxDistance, 1.0E-6F);
    }

    private static EffectAudioPlan plan(EffectAudioCue... cues) {
        return new EffectAudioPlan(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            ResourceLocation.withDefaultNamespace("overworld"),
            Vec3.ZERO,
            0L,
            100,
            List.of(cues)
        );
    }
}
