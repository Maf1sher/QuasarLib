package mafisher.quasar.effect.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.audio.EffectAudioAttenuation;
import mafisher.quasar.api.audio.EffectAudioCue;
import mafisher.quasar.api.audio.EffectAudioEnvelope;
import mafisher.quasar.api.audio.EffectAudioMotion;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.audio.EffectAudioTrigger;
import mafisher.quasar.api.gameplay.EffectPhase;
import mafisher.quasar.api.gameplay.EffectPhasePlan;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EffectAudioSpecTest {
    private static final ResourceLocation SOUND = ResourceLocation.withDefaultNamespace("test");
    private static final SoundEvent SOUND_EVENT = SoundEvent.createVariableRangeEvent(SOUND);

    @Test
    void sortsCuesByTick() {
        EffectAudioSpec spec = EffectAudioSpec.builder(100)
            .cue(EffectAudioCue.at(40, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F))
            .cue(EffectAudioCue.at(10, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F))
            .build();

        assertEquals(2, spec.cueCount());
        assertEquals(10, spec.cues().get(0).tick());
        assertEquals(40, spec.cues().get(1).tick());
    }

    @Test
    void resolvesPhaseCuesByIndexAndName() {
        EffectPhasePlan phases = EffectPhasePlan.of(
            new EffectPhase("charge", 20),
            new EffectPhase("impact", 30)
        );

        EffectAudioSpec spec = EffectAudioSpec.builder(phases)
            .phaseCue(1, 5, EffectAudioCue.at(0, Vec3.ZERO, SOUND_EVENT, SoundSource.MASTER, 1.0F, 1.0F))
            .phaseCue("impact", 10, EffectAudioCue.at(0, Vec3.ZERO, SOUND_EVENT, SoundSource.MASTER, 1.0F, 1.0F))
            .build();

        assertEquals(2, spec.cueCount());
        assertEquals(25, spec.cues().get(0).tick());
        assertEquals(30, spec.cues().get(1).tick());
    }

    @Test
    void rejectsUnknownAndDuplicatePhaseNames() {
        EffectPhasePlan phases = EffectPhasePlan.of(
            new EffectPhase("same", 10),
            new EffectPhase("same", 10)
        );

        assertThrows(IllegalArgumentException.class, () -> EffectAudioSpec.builder(phases)
            .phaseCue("missing", 0, EffectAudioCue.at(0, Vec3.ZERO, SOUND_EVENT, SoundSource.MASTER, 1.0F, 1.0F)));
        assertThrows(IllegalArgumentException.class, () -> EffectAudioSpec.builder(phases)
            .phaseCue("same", 0, EffectAudioCue.at(0, Vec3.ZERO, SOUND_EVENT, SoundSource.MASTER, 1.0F, 1.0F)));
    }

    @Test
    void rejectsCuesAtOrBeyondDuration() {
        assertThrows(IllegalArgumentException.class, () -> EffectAudioSpec.builder(10)
            .cue(EffectAudioCue.at(10, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F)));
        assertThrows(IllegalArgumentException.class, () -> EffectAudioSpec.builder(10)
            .cue(EffectAudioCue.at(11, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F)));
    }

    @Test
    void capsTheCueCount() {
        EffectAudioSpec.Builder builder = EffectAudioSpec.builder(1000);
        for (int index = 0; index < EffectAudioSpec.MAX_CUES; index++) {
            builder.cue(EffectAudioCue.at(index, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F));
        }

        assertThrows(IllegalArgumentException.class,
            () -> builder.cue(EffectAudioCue.at(EffectAudioSpec.MAX_CUES, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F)));
    }

    @Test
    void noneIsEmpty() {
        assertTrue(EffectAudioSpec.NONE.isEmpty());
        assertEquals(0, EffectAudioSpec.NONE.cueCount());
    }

    @Test
    void validatesCueTicksAgainstDuration() {
        EffectAudioSpec spec = EffectAudioSpec.builder(10)
            .cue(EffectAudioCue.at(9, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F))
            .build();

        assertTrue(spec.fitsWithin(10));
        assertFalse(spec.fitsWithin(9));
        assertThrows(IllegalArgumentException.class, () -> spec.requireFitsWithin(9));
    }

    @Test
    void validatesLoopWindowsAgainstDuration() {
        EffectAudioSpec valid = EffectAudioSpec.builder(100)
            .cue(new EffectAudioCue(10, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F,
                EffectAudioAttenuation.VANILLA,
                EffectAudioEnvelope.loopUntil(50, 5, 10),
                EffectAudioMotion.FOLLOW_EFFECT))
            .build();

        assertEquals(EffectAudioMotion.FOLLOW_EFFECT, valid.cues().getFirst().motion());
        assertEquals(50, valid.cues().getFirst().envelope().stopTick());

        assertThrows(IllegalArgumentException.class, () -> EffectAudioSpec.builder(100)
            .cue(new EffectAudioCue(10, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F,
                EffectAudioAttenuation.VANILLA,
                EffectAudioEnvelope.loopUntil(101, 0, 0),
                EffectAudioMotion.ANCHORED)));
    }

    @Test
    void resolvesPhaseLoopsByShiftingTheWholeWindow() {
        EffectPhasePlan phases = EffectPhasePlan.of(
            new EffectPhase("charge", 20),
            new EffectPhase("impact", 30)
        );
        EffectAudioCue cue = new EffectAudioCue(
            0,
            Vec3.ZERO,
            SOUND,
            SoundSource.MASTER,
            1.0F,
            1.0F,
            EffectAudioAttenuation.VANILLA,
            EffectAudioEnvelope.loopUntil(10, 2, 3),
            EffectAudioMotion.ANCHORED
        );

        EffectAudioSpec spec = EffectAudioSpec.builder(phases)
            .phaseCue("impact", 5, cue)
            .build();

        assertEquals(25, spec.cues().getFirst().tick());
        assertEquals(35, spec.cues().getFirst().envelope().stopTick());
    }

    @Test
    void registersTriggersAndRejectsDuplicates() {
        EffectAudioSpec spec = EffectAudioSpec.builder(100)
            .trigger(new EffectAudioTrigger(
                "impact", Vec3.ZERO, SOUND, SoundSource.PLAYERS, 1.0F, 1.0F
            ))
            .build();

        assertNotNull(spec.triggerOrNull("impact"));
        assertNull(spec.triggerOrNull("missing"));
        assertEquals(1, spec.triggerCount());

        EffectAudioSpec.Builder builder = EffectAudioSpec.builder(100)
            .trigger(new EffectAudioTrigger(
                "impact", Vec3.ZERO, SOUND, SoundSource.PLAYERS, 1.0F, 1.0F
            ));
        assertThrows(IllegalArgumentException.class, () -> builder.trigger(new EffectAudioTrigger(
            "impact", Vec3.ZERO, SOUND, SoundSource.PLAYERS, 1.0F, 1.0F
        )));
    }

    @Test
    void capsTheTriggerCount() {
        EffectAudioSpec.Builder builder = EffectAudioSpec.builder(100);
        for (int index = 0; index < EffectAudioSpec.MAX_TRIGGERS; index++) {
            builder.trigger(new EffectAudioTrigger(
                "trigger_" + index, Vec3.ZERO, SOUND, SoundSource.PLAYERS, 1.0F, 1.0F
            ));
        }

        assertThrows(IllegalArgumentException.class, () -> builder.trigger(new EffectAudioTrigger(
            "overflow", Vec3.ZERO, SOUND, SoundSource.PLAYERS, 1.0F, 1.0F
        )));
    }
}
