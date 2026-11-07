package mafisher.quasar.effect.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EffectAudioPlanTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.withDefaultNamespace("overworld");
    private static final ResourceLocation SOUND = ResourceLocation.withDefaultNamespace("test");

    @Test
    void copiesCuesInTickOrder() {
        EffectAudioPlan plan = plan(
            cue(20, 1.0F),
            cue(5, 0.5F)
        );

        assertEquals(2, plan.cueCount());
        assertEquals(5, plan.cues().get(0).tick());
        assertEquals(20, plan.cues().get(1).tick());
        assertEquals(1000L, plan.startGameTime());
        assertEquals(100, plan.durationTicks());
    }

    @Test
    void skipsCuesOutsideTheGraceWindow() {
        EffectAudioPlan plan = plan(cue(5, 1.0F));

        assertEquals(0, plan.firstCueIndex(0, 2));
        assertEquals(0, plan.firstCueIndex(5, 2));
        assertEquals(0, plan.firstCueIndex(7, 2));
        assertEquals(1, plan.firstCueIndex(8, 2));
        assertEquals(1, plan.firstCueIndex(50, 2));
    }

    @Test
    void reportsPlayableCues() {
        EffectAudioPlan plan = plan(cue(5, 1.0F), cue(50, 1.0F));

        assertTrue(plan.hasPlayableCues(5, 2));
        assertTrue(plan.hasPlayableCues(8, 2));
        assertFalse(plan.hasPlayableCues(100, 2));
    }

    @Test
    void keepsActiveLoopsPlayableAfterTheirStartTick() {
        EffectAudioPlan plan = plan(new EffectAudioCue(
            5,
            Vec3.ZERO,
            SOUND,
            SoundSource.MASTER,
            1.0F,
            1.0F,
            EffectAudioAttenuation.VANILLA,
            EffectAudioEnvelope.loopUntil(80, 0, 10),
            EffectAudioMotion.ANCHORED
        ));

        assertEquals(0, plan.firstCueIndex(50, 2));
        assertTrue(plan.hasPlayableCues(50, 2));
        assertEquals(1, plan.firstCueIndex(80, 2));
        assertFalse(plan.hasPlayableCues(80, 2));
    }

    @Test
    void triggersKeepAPlanPlayableWithoutScheduledCues() {
        EffectAudioPlan plan = new EffectAudioPlan(
            UUID.randomUUID(),
            DIMENSION,
            Vec3.ZERO,
            0L,
            100,
            List.of(),
            List.of(new EffectAudioTrigger(
                "impact", Vec3.ZERO, SOUND, SoundSource.PLAYERS, 1.0F, 1.0F
            ))
        );

        assertEquals(1, plan.triggerCount());
        assertNotNull(plan.triggerOrNull("impact"));
        assertTrue(plan.hasPlayableCues(50, 2));
        assertFalse(plan.hasPlayableCues(100, 2));
    }

    @Test
    void capsTheNumberOfScheduledCues() {
        List<EffectAudioCue> cues = new ArrayList<>();
        for (int index = 0; index < EffectAudioPlan.MAX_CUES + 1; index++) {
            cues.add(cue(index, 1.0F));
        }

        assertThrows(IllegalArgumentException.class, () -> new EffectAudioPlan(
            UUID.randomUUID(), DIMENSION, Vec3.ZERO, 0L, 1000, cues
        ));
    }

    @Test
    void rejectsCuesAtOrBeyondDuration() {
        assertThrows(IllegalArgumentException.class, () -> new EffectAudioPlan(
            UUID.randomUUID(), DIMENSION, Vec3.ZERO, 0L, 10, List.of(cue(10, 1.0F))
        ));
    }

    private static EffectAudioPlan plan(EffectAudioCue... cues) {
        return new EffectAudioPlan(
            UUID.randomUUID(), DIMENSION, Vec3.ZERO, 1000L, 100, List.of(cues)
        );
    }

    private static EffectAudioCue cue(int tick, float volume) {
        return new EffectAudioCue(tick, Vec3.ZERO, SOUND, SoundSource.MASTER, volume, 1.0F);
    }
}
