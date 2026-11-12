package mafisher.quasar.effect.cinematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.cinematic.CinematicCue;
import mafisher.quasar.api.cinematic.CinematicTimeline;
import mafisher.quasar.api.cinematic.EffectEasing;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectPriority;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.scale.SegmentHeightSource;
import mafisher.quasar.api.scale.SegmentTerrainConform;
import mafisher.quasar.api.scale.SegmentedVisualEffectSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CinematicTimelineAnchorTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "anchor_test");
    private static final ResourceLocation EFFECT_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "visual");
    private static final SoundEvent SOUND = SoundEvent.createVariableRangeEvent(
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "test_sound")
    );

    @Test
    void offsetsCuesTracksAndSegmentedCentersWithoutMutatingTheOriginal() {
        CinematicTimeline timeline = CinematicTimeline.builder(ID, 100, 1L)
            .sound(0, new Vec3(1.0, 2.0, 3.0), SOUND, SoundSource.MASTER, 1.0F, 1.0F)
            .visual("ring", 0, VisualEffectSpec.builder(EFFECT_ID, new Vec3(4.0, 5.0, 6.0), 50, 2L, EffectAudience.nearby(32.0))
                .rotation(0.0F, 0.0F, 0.0F)
                .scale(2.0F)
                .build())
            .segmented("field", 0, SegmentedVisualEffectSpec.builder(EFFECT_ID, new Vec3(7.0, 8.0, 9.0), 50, 3L, EffectAudience.nearby(32.0))
                .rotation(0.0F, 0.0F, 0.0F)
                .segmentScale(2.0F)
                .grid(2, 2)
                .spacing(4.0)
                .layers(1, 0.0)
                .audio(EffectAudioSpec.NONE)
                .priority(EffectPriority.NORMAL)
                .terrain(SegmentTerrainConform.NONE)
                .build())
            .transform("ring", 0, transform(1.0, 2.0, 3.0), EffectEasing.LINEAR)
            .transform("ring", 10, transform(1.0, 5.0, 3.0), EffectEasing.LINEAR)
            .build();

        Vec3 anchor = new Vec3(10.0, 64.0, -5.0);
        CinematicTimeline anchored = timeline.anchored(anchor);

        CinematicCue.SoundCue sound = (CinematicCue.SoundCue)anchored.cues().get(0);
        CinematicCue.VisualStartCue visual = (CinematicCue.VisualStartCue)anchored.cues().get(1);
        CinematicCue.SegmentedStartCue segmented = (CinematicCue.SegmentedStartCue)anchored.cues().get(2);

        assertEquals(new Vec3(11.0, 66.0, -2.0), sound.position());
        assertEquals(new Vec3(14.0, 69.0, 1.0), visual.spec().position());
        assertEquals(new Vec3(17.0, 72.0, 4.0), segmented.spec().center());
        assertEquals(11.0, anchored.tracks().iterator().next().sample(0).position().x());

        CinematicCue.VisualStartCue original = (CinematicCue.VisualStartCue)timeline.cues().get(1);
        assertEquals(new Vec3(4.0, 5.0, 6.0), original.spec().position());
    }

    @Test
    void keepsLayersSpacingAndTerrainWhenTheAnchorMovesTheCenter() {
        SegmentTerrainConform terrain = new SegmentTerrainConform(SegmentHeightSource.WORLD_SURFACE, 1.5);
        SegmentedVisualEffectSpec original = SegmentedVisualEffectSpec.builder(EFFECT_ID, new Vec3(7.0, 8.0, 9.0), 50, 3L, EffectAudience.nearby(32.0))
            .rotation(15.0F, 5.0F, 2.0F)
            .segmentScale(3.0F)
            .grid(2, 3)
            .spacing(4.0)
            .layers(4, 5.0)
            .audio(EffectAudioSpec.NONE)
            .priority(EffectPriority.NORMAL)
            .terrain(terrain)
            .build();
        CinematicTimeline timeline = CinematicTimeline.builder(ID, 100, 1L)
            .segmented("field", 0, original)
            .build();

        CinematicCue.SegmentedStartCue anchored = (CinematicCue.SegmentedStartCue)timeline
            .anchored(new Vec3(10.0, 64.0, -5.0))
            .cues()
            .getFirst();

        assertEquals(new Vec3(17.0, 72.0, 4.0), anchored.spec().center());
        assertEquals(4, anchored.spec().layers());
        assertEquals(5.0, anchored.spec().layerSpacing());
        assertEquals(terrain, anchored.spec().terrain());
        assertEquals(original.segmentCount(), anchored.spec().segmentCount());
    }

    @Test
    void zeroAnchorReturnsTheSameInstance() {
        CinematicTimeline timeline = CinematicTimeline.builder(ID, 10, 1L)
            .sound(0, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F)
            .build();

        assertSame(timeline, timeline.anchored(Vec3.ZERO));
    }

    private static EffectTransform transform(double x, double y, double z) {
        return new EffectTransform(new EffectPoint(x, y, z), 0.0F, 0.0F, 0.0F);
    }
}
