package mafisher.quasar.effect.cinematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.VisualEffectSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CinematicAudioPlanTest {
    private static final ResourceLocation TIMELINE_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "audio");
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final SoundEvent SOUND = SoundEvent.createVariableRangeEvent(
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "test_sound")
    );

    @Test
    void copiesOnlySoundCuesInTickOrder() {
        CinematicTimeline timeline = CinematicTimeline.builder(TIMELINE_ID, 100, 1L)
            .visual("ring", 0, new VisualEffectSpec(
                TIMELINE_ID, Vec3.ZERO, 0.0F, 0.0F, 0.0F, 1.0F, 100, 1L,
                EffectAudience.nearby(32.0)
            ))
            .sound(20, new Vec3(1.0, 2.0, 3.0), SOUND, SoundSource.MASTER, 1.0F, 1.0F)
            .sound(5, Vec3.ZERO, SOUND, SoundSource.MASTER, 0.5F, 2.0F)
            .audioMode(CinematicAudioMode.CLIENT)
            .build();

        CinematicAudioPlan plan = CinematicAudioPlan.from(UUID.randomUUID(), timeline, DIMENSION, 1000L);

        assertEquals(2, plan.cueCount());
        assertEquals(5, plan.cues().get(0).tick());
        assertEquals(20, plan.cues().get(1).tick());
        assertEquals(TIMELINE_ID, plan.timelineId());
        assertEquals(DIMENSION, plan.dimensionId());
        assertEquals(1000L, plan.startGameTime());
        assertEquals(100, plan.durationTicks());
    }

    @Test
    void skipsCuesOutsideTheGraceWindow() {
        CinematicAudioPlan plan = plan(new CinematicAudioCue(
            5, Vec3.ZERO, ResourceLocation.withDefaultNamespace("test"), SoundSource.MASTER, 1.0F, 1.0F
        ));

        assertEquals(0, plan.firstCueIndex(0, 2));
        assertEquals(0, plan.firstCueIndex(5, 2));
        assertEquals(0, plan.firstCueIndex(7, 2));
        assertEquals(1, plan.firstCueIndex(8, 2));
        assertEquals(1, plan.firstCueIndex(50, 2));
    }

    @Test
    void capsTheNumberOfScheduledCues() {
        List<CinematicAudioCue> cues = new ArrayList<>();
        for (int index = 0; index < CinematicAudioPlan.MAX_CUES + 1; index++) {
            cues.add(new CinematicAudioCue(
                index,
                Vec3.ZERO,
                ResourceLocation.withDefaultNamespace("test"),
                SoundSource.MASTER,
                1.0F,
                1.0F
            ));
        }

        assertThrows(IllegalArgumentException.class, () -> new CinematicAudioPlan(
            UUID.randomUUID(),
            TIMELINE_ID,
            DIMENSION,
            0L,
            100,
            cues
        ));
    }

    private static CinematicAudioPlan plan(CinematicAudioCue... cues) {
        return new CinematicAudioPlan(UUID.randomUUID(), TIMELINE_ID, DIMENSION, 0L, 100, List.of(cues));
    }
}
