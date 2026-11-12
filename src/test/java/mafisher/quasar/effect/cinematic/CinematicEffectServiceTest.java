package mafisher.quasar.effect.cinematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import mafisher.quasar.api.audio.EffectAudioService;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.cinematic.CinematicAudioMode;
import mafisher.quasar.api.cinematic.CinematicEffectHandle;
import mafisher.quasar.api.cinematic.CinematicEffectService;
import mafisher.quasar.api.cinematic.CinematicEffectStartResult;
import mafisher.quasar.api.cinematic.CinematicTimeline;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectBudgets;
import mafisher.quasar.api.effect.EffectPriority;
import mafisher.quasar.api.effect.EffectStartStatus;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.scale.SegmentTerrainConform;
import mafisher.quasar.api.scale.SegmentedVisualEffectService;
import mafisher.quasar.api.scale.SegmentedVisualEffectSpec;
import mafisher.quasar.effect.EffectBudgetService;
import mafisher.quasar.effect.runtime.RecordingPacketSink;
import mafisher.quasar.effect.runtime.TestBudgetSource;
import mafisher.quasar.effect.runtime.TestEffectRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CinematicEffectServiceTest {
    private static final ResourceLocation TIMELINE_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "test");
    private static final ResourceLocation VISUAL_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "visual");
    private static final SoundEvent SOUND = SoundEvent.createVariableRangeEvent(
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "test_sound")
    );

    private ServerLevel level;
    private long gameTime;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.players()).thenReturn(List.of());
        when(this.level.getGameTime()).thenAnswer(ignored -> this.gameTime);
        TestEffectRuntime.install(this.level, new RecordingPacketSink(), new TestBudgetSource());
    }

    @AfterEach
    void tearDown() {
        CinematicEffectService.stopAll(this.level);
        this.gameTime = 10_000;
        VisualEffectService.tick(this.level);
        SegmentedVisualEffectService.tick(this.level);
        EffectAudioService.clear(this.level);
        EffectBudgetService.clear(this.level);
        TestEffectRuntime.uninstall(this.level);
    }

    @Test
    void startsVisualsOnlyAtTheirCueTick() {
        CinematicEffectService.start(this.level, timelineBuilder(100)
            .visual("ring", 10, visualSpec(100))
            .build());

        assertEquals(0, VisualEffectService.activeCount(this.level));

        this.gameTime = 9;
        CinematicEffectService.tick(this.level);
        assertEquals(0, VisualEffectService.activeCount(this.level));

        this.gameTime = 10;
        CinematicEffectService.tick(this.level);
        assertEquals(1, VisualEffectService.activeCount(this.level));
    }

    @Test
    void playsTickZeroSoundDuringStart() {
        CinematicEffectService.start(this.level, timelineBuilder(100)
            .sound(0, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F)
            .build());

        verify(this.level, times(1)).playSound(any(), anyDouble(), anyDouble(), anyDouble(),
            eq(SOUND), eq(SoundSource.MASTER), eq(1.0F), eq(1.0F));
    }

    @Test
    void clientAudioModeDoesNotPlayServerSound() {
        CinematicEffectService.start(this.level, timelineBuilder(100)
            .audioMode(CinematicAudioMode.CLIENT)
            .sound(0, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F)
            .build());

        verify(this.level, times(0)).playSound(any(), anyDouble(), anyDouble(), anyDouble(),
            eq(SOUND), eq(SoundSource.MASTER), anyFloat(), anyFloat());
        assertEquals(1, EffectAudioService.activeCount(this.level));
    }

    @Test
    void playsEachSoundCueOnce() {
        CinematicEffectService.start(this.level, timelineBuilder(100)
            .sound(2, Vec3.ZERO, SOUND, SoundSource.PLAYERS, 0.8F, 1.0F)
            .build());

        this.gameTime = 1;
        CinematicEffectService.tick(this.level);
        verify(this.level, times(0)).playSound(any(), anyDouble(), anyDouble(), anyDouble(),
            eq(SOUND), eq(SoundSource.PLAYERS), anyFloat(), anyFloat());

        this.gameTime = 2;
        CinematicEffectService.tick(this.level);
        this.gameTime = 20;
        CinematicEffectService.tick(this.level);

        verify(this.level, times(1)).playSound(any(), anyDouble(), anyDouble(), anyDouble(),
            eq(SOUND), eq(SoundSource.PLAYERS), eq(0.8F), eq(1.0F));
    }

    @Test
    void catchesUpMissedCuesExactlyOnce() {
        CinematicEffectService.start(this.level, timelineBuilder(100)
            .visual("first", 5, visualSpec(100))
            .visual("second", 10, visualSpec(100))
            .build());

        this.gameTime = 20;
        CinematicEffectService.tick(this.level);
        assertEquals(2, VisualEffectService.activeCount(this.level));

        CinematicEffectService.tick(this.level);
        assertEquals(2, VisualEffectService.activeCount(this.level));
    }

    @Test
    void stopsVisualsAtTheirStopCue() {
        CinematicEffectService.start(this.level, timelineBuilder(100)
            .visual("ring", 0, visualSpec(100))
            .stopVisual("ring", 10)
            .build());

        assertEquals(1, VisualEffectService.activeCount(this.level));

        this.gameTime = 9;
        CinematicEffectService.tick(this.level);
        assertEquals(1, VisualEffectService.activeCount(this.level));

        this.gameTime = 10;
        CinematicEffectService.tick(this.level);
        assertEquals(0, VisualEffectService.activeCount(this.level));
        assertEquals(1, CinematicEffectService.activeCount(this.level));
    }

    @Test
    void expiresAndReleasesEveryVisual() {
        CinematicEffectService.start(this.level, timelineBuilder(20)
            .visual("ring", 0, visualSpec(100))
            .build());

        assertEquals(1, CinematicEffectService.activeCount(this.level));
        assertEquals(1, VisualEffectService.activeCount(this.level));

        this.gameTime = 20;
        CinematicEffectService.tick(this.level);

        assertEquals(0, CinematicEffectService.activeCount(this.level));
        assertEquals(0, VisualEffectService.activeCount(this.level));
    }

    @Test
    void stopsSegmentedCuesAndReleasesEverySegment() {
        CinematicEffectService.start(this.level, timelineBuilder(20)
            .segmented("field", 0, segmentedSpec(20))
            .build());

        assertEquals(4, SegmentedVisualEffectService.activeSegmentCount(this.level));
        assertEquals(0, VisualEffectService.activeCount(this.level));

        this.gameTime = 20;
        CinematicEffectService.tick(this.level);

        assertEquals(0, CinematicEffectService.activeCount(this.level));
        assertEquals(0, SegmentedVisualEffectService.activeSegmentCount(this.level));
    }

    @Test
    void cullsTheOldestLowestPriorityCinematicAtTheSessionBudget() {
        for (int index = 0; index < EffectBudgets.cinematicSessions(); index++) {
            CinematicEffectService.start(this.level, timelineBuilder(100)
                .priority(EffectPriority.LOW)
                .sound(0, Vec3.ZERO, SOUND, SoundSource.PLAYERS, 1.0F, 1.0F)
                .build());
        }

        CinematicEffectStartResult admitted = CinematicEffectService.start(this.level,
            timelineBuilder(100)
                .priority(EffectPriority.HIGH)
                .sound(0, Vec3.ZERO, SOUND, SoundSource.PLAYERS, 1.0F, 1.0F)
                .build());

        assertTrue(admitted.active());
        assertTrue(CinematicEffectService.isActive(this.level, admitted.handle()));
        assertEquals(EffectBudgets.cinematicSessions(),
            CinematicEffectService.activeCount(this.level));
    }

    @Test
    void cullsALowerPriorityNewcomerWithoutThrowing() {
        for (int index = 0; index < EffectBudgets.cinematicSessions(); index++) {
            CinematicEffectService.start(this.level, timelineBuilder(100)
                .priority(EffectPriority.HIGH)
                .sound(0, Vec3.ZERO, SOUND, SoundSource.PLAYERS, 1.0F, 1.0F)
                .build());
        }

        CinematicEffectStartResult culled = CinematicEffectService.start(this.level,
            timelineBuilder(100)
                .priority(EffectPriority.BACKGROUND)
                .sound(0, Vec3.ZERO, SOUND, SoundSource.PLAYERS, 1.0F, 1.0F)
                .build());

        assertFalse(culled.active());
        assertEquals(EffectStartStatus.CULLED_ON_START, culled.status());
        assertFalse(CinematicEffectService.isActive(this.level, culled.handle()));
        assertEquals(EffectBudgets.cinematicSessions(),
            CinematicEffectService.activeCount(this.level));
    }

    @Test
    void stopAndStopAllAreIdempotent() {
        CinematicEffectService.start(this.level, timelineBuilder(100).visual("a", 0, visualSpec(100)).build());
        CinematicEffectService.start(this.level, timelineBuilder(100).visual("b", 0, visualSpec(100)).build());

        assertEquals(2, CinematicEffectService.stopAll(this.level));
        assertEquals(0, CinematicEffectService.stopAll(this.level));
        assertEquals(0, VisualEffectService.activeCount(this.level));
    }

    @Test
    void segmentedCueDoesNotConsumeTheStandaloneCap() {
        for (int index = 0; index < EffectBudgets.standaloneVisuals(); index++) {
            VisualEffectService.start(this.level, visualSpec(100));
        }

        CinematicEffectService.start(this.level,
            timelineBuilder(100).segmented("field", 0, segmentedSpec(100)).build());

        assertEquals(1, CinematicEffectService.activeCount(this.level));
        assertEquals(1, SegmentedVisualEffectService.activeCount(this.level));
        assertEquals(EffectBudgets.standaloneVisuals(),
            VisualEffectService.activeCount(this.level));
    }

    @Test
    void cullsTheOldestCoordinatorToAdmitACinematicSegment() {
        int fullGrids = EffectBudgets.segments() / 128;
        for (int index = 0; index < fullGrids; index++) {
            SegmentedVisualEffectService.start(this.level, largeSegmentedSpec(100));
        }

        CinematicEffectHandle handle = CinematicEffectService.start(this.level,
            timelineBuilder(100).segmented("field", 0, segmentedSpec(100)).build()).handle();

        assertTrue(CinematicEffectService.isActive(this.level, handle));
        assertEquals(1, CinematicEffectService.activeCount(this.level));
        // One 128-cell coordinator was culled to free room for the four-cell cue.
        assertEquals(
            (fullGrids - 1) * 128 + 4,
            SegmentedVisualEffectService.activeSegmentCount(this.level)
        );
    }

    private static CinematicTimeline.Builder timelineBuilder(int durationTicks) {
        return CinematicTimeline.builder(TIMELINE_ID, durationTicks, 1L);
    }

    private static VisualEffectSpec visualSpec(int durationTicks) {
        return VisualEffectSpec.builder(VISUAL_ID, Vec3.ZERO, durationTicks, 1L, EffectAudience.nearby(32.0))
            .rotation(0.0F, 0.0F, 0.0F)
            .scale(1.0F)
            .build();
    }

    private static SegmentedVisualEffectSpec segmentedSpec(int durationTicks) {
        return SegmentedVisualEffectSpec.builder(VISUAL_ID, Vec3.ZERO, durationTicks, 1L, EffectAudience.nearby(32.0))
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

    private static SegmentedVisualEffectSpec largeSegmentedSpec(int durationTicks) {
        return SegmentedVisualEffectSpec.builder(VISUAL_ID, Vec3.ZERO, durationTicks, 1L, EffectAudience.nearby(32.0))
            .rotation(0.0F, 0.0F, 0.0F)
            .segmentScale(2.0F)
            .grid(16, 8)
            .spacing(2.0)
            .layers(1, 0.0)
            .audio(EffectAudioSpec.NONE)
            .priority(EffectPriority.NORMAL)
            .terrain(SegmentTerrainConform.NONE)
            .build();
    }
}
