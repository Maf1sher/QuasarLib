package mafisher.quasar.effect.cinematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.cinematic.CinematicCue;
import mafisher.quasar.api.cinematic.CinematicTimeline;
import mafisher.quasar.api.cinematic.EffectEasing;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.budget.EffectPriority;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.scale.SegmentTerrainConform;
import mafisher.quasar.api.scale.SegmentedVisualEffectSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CinematicTimelineTest {
    private static final ResourceLocation TIMELINE_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "test");
    private static final ResourceLocation VISUAL_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "visual");
    private static final SoundEvent SOUND = SoundEvent.createVariableRangeEvent(
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "test_sound")
    );
    private static final EffectTransform TRANSFORM = new EffectTransform(
        new EffectPoint(0.0, 64.0, 0.0), 0.0F, 0.0F, 0.0F
    );

    @Test
    void sortsCuesByTickAndKeepsInsertionOrderForTies() {
        CinematicTimeline timeline = CinematicTimeline.builder(TIMELINE_ID, 100, 7L)
            .sound(40, Vec3.ZERO, SOUND, SoundSource.PLAYERS, 0.4F, 1.0F)
            .sound(0, Vec3.ZERO, SOUND, SoundSource.PLAYERS, 0.5F, 1.0F)
            .sound(20, Vec3.ZERO, SOUND, SoundSource.PLAYERS, 0.6F, 1.0F)
            .sound(20, Vec3.ZERO, SOUND, SoundSource.PLAYERS, 0.7F, 1.0F)
            .build();

        assertEquals(List.of(0, 20, 20, 40), timeline.cues().stream().map(CinematicCue::tick).toList());
        assertEquals(0.6F, ((CinematicCue.SoundCue)timeline.cues().get(1)).volume());
        assertEquals(0.7F, ((CinematicCue.SoundCue)timeline.cues().get(2)).volume());
        assertEquals(7L, timeline.seed());
        assertEquals(TIMELINE_ID, timeline.id());
    }

    @Test
    void keepsSegmentStartsAndTracks() {
        CinematicTimeline timeline = CinematicTimeline.builder(TIMELINE_ID, 100, 1L)
            .segmented("ring", 0, segmentedSpec())
            .transform("ring", 0, TRANSFORM, EffectEasing.LINEAR)
            .transform("ring", 40, TRANSFORM, EffectEasing.EASE_IN_OUT)
            .build();

        assertEquals(1, timeline.tracks().size());
        assertEquals(2, timeline.tracks().iterator().next().keyframes().size());
    }

    @Test
    void rejectsCuesAndKeyframesAtOrAfterDuration() {
        assertThrows(IllegalArgumentException.class, () -> CinematicTimeline.builder(TIMELINE_ID, 10, 1L)
            .sound(10, Vec3.ZERO, SOUND, SoundSource.PLAYERS, 1.0F, 1.0F)
            .build());
        assertThrows(IllegalArgumentException.class, () -> CinematicTimeline.builder(TIMELINE_ID, 10, 1L)
            .visual("ring", 0, visualSpec())
            .transform("ring", 10, TRANSFORM, EffectEasing.LINEAR)
            .build());
    }

    @Test
    void rejectsInvalidStopsAndKeys() {
        assertThrows(IllegalArgumentException.class, () -> CinematicTimeline.builder(TIMELINE_ID, 100, 1L)
            .stopVisual("ghost", 10)
            .build());
        assertThrows(IllegalArgumentException.class, () -> CinematicTimeline.builder(TIMELINE_ID, 100, 1L)
            .visual("ring", 10, visualSpec())
            .stopVisual("ring", 5)
            .build());
        assertThrows(IllegalArgumentException.class, () -> CinematicTimeline.builder(TIMELINE_ID, 100, 1L)
            .visual("ring", 0, visualSpec())
            .stopVisual("ring", 10)
            .stopVisual("ring", 20)
            .build());
        assertThrows(IllegalArgumentException.class, () -> CinematicTimeline.builder(TIMELINE_ID, 100, 1L)
            .visual("ring", 0, visualSpec())
            .stopSegmented("ring", 10)
            .build());
        assertThrows(IllegalArgumentException.class, () -> CinematicTimeline.builder(TIMELINE_ID, 100, 1L)
            .visual("ring", 0, visualSpec())
            .segmented("ring", 0, segmentedSpec()));
    }

    @Test
    void rejectsTracksForUnknownKeys() {
        assertThrows(IllegalArgumentException.class, () -> CinematicTimeline.builder(TIMELINE_ID, 100, 1L)
            .sound(0, Vec3.ZERO, SOUND, SoundSource.PLAYERS, 1.0F, 1.0F)
            .transform("ghost", 0, TRANSFORM, EffectEasing.LINEAR)
            .build());
    }

    @Test
    void requiresAtLeastOneCueAndEnforcesTheCueCap() {
        assertThrows(IllegalStateException.class, () -> CinematicTimeline.builder(TIMELINE_ID, 10, 1L).build());

        CinematicTimeline.Builder builder = CinematicTimeline.builder(TIMELINE_ID, 10_000, 1L);
        for (int index = 0; index < CinematicTimeline.MAX_CUES; index++) {
            builder.visual("key" + index, index, visualSpec());
        }
        assertEquals(CinematicTimeline.MAX_CUES, builder.build().cueCount());
        assertThrows(IllegalStateException.class,
            () -> builder.visual("overflow", 0, visualSpec()));
    }

    @Test
    void validatesTransformUpdateIntervalAndDuration() {
        assertThrows(IllegalArgumentException.class,
            () -> CinematicTimeline.builder(TIMELINE_ID, 0, 1L));
        assertThrows(IllegalArgumentException.class,
            () -> CinematicTimeline.builder(TIMELINE_ID, CinematicTimeline.MAX_DURATION_TICKS + 1, 1L));
        assertThrows(IllegalArgumentException.class,
            () -> CinematicTimeline.builder(TIMELINE_ID, 100, 1L).transformUpdateIntervalTicks(0));
        assertThrows(IllegalArgumentException.class,
            () -> CinematicTimeline.builder(TIMELINE_ID, 100, 1L).transformUpdateIntervalTicks(21));
        assertEquals(5, CinematicTimeline.builder(TIMELINE_ID, 100, 1L)
            .transformUpdateIntervalTicks(5)
            .sound(0, Vec3.ZERO, SOUND, SoundSource.PLAYERS, 1.0F, 1.0F)
            .build()
            .transformUpdateIntervalTicks());
    }

    private static VisualEffectSpec visualSpec() {
        return VisualEffectSpec.builder(VISUAL_ID, Vec3.ZERO, 100, 1L, EffectAudience.nearby(32.0))
            .rotation(0.0F, 0.0F, 0.0F)
            .scale(1.0F)
            .build();
    }

    private static SegmentedVisualEffectSpec segmentedSpec() {
        return SegmentedVisualEffectSpec.builder(VISUAL_ID, Vec3.ZERO, 100, 1L, EffectAudience.nearby(32.0))
            .rotation(0.0F, 0.0F, 0.0F)
            .segmentScale(2.0F)
            .grid(2, 2)
            .spacing(2.0)
            .layers(1, 0.0)
            .audio(EffectAudioSpec.NONE)
            .priority(EffectPriority.NORMAL)
            .terrain(SegmentTerrainConform.NONE)
            .build();
    }
}
