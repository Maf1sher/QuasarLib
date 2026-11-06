package mafisher.quasar.effect.cinematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.VisualEffectService;
import mafisher.quasar.effect.VisualEffectSpec;
import mafisher.quasar.effect.scale.SegmentedVisualEffectService;
import mafisher.quasar.effect.scale.SegmentedVisualEffectSpec;
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
    }

    @AfterEach
    void tearDown() {
        CinematicEffectService.stopAll(this.level);
        this.gameTime = 10_000;
        VisualEffectService.tick(this.level);
        SegmentedVisualEffectService.tick(this.level);
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

        assertEquals(4, VisualEffectService.activeCount(this.level));

        this.gameTime = 20;
        CinematicEffectService.tick(this.level);

        assertEquals(0, CinematicEffectService.activeCount(this.level));
        assertEquals(0, VisualEffectService.activeCount(this.level));
    }

    @Test
    void rejectsAStartOverTheSessionCap() {
        for (int index = 0; index < CinematicEffectService.MAX_ACTIVE_SESSIONS_PER_LEVEL; index++) {
            CinematicEffectService.start(this.level, timelineBuilder(100)
                .sound(0, Vec3.ZERO, SOUND, SoundSource.PLAYERS, 1.0F, 1.0F)
                .build());
        }

        assertEquals(CinematicEffectService.MAX_ACTIVE_SESSIONS_PER_LEVEL,
            CinematicEffectService.activeCount(this.level));
        assertThrows(IllegalStateException.class, () -> CinematicEffectService.start(this.level,
            timelineBuilder(100)
                .sound(0, Vec3.ZERO, SOUND, SoundSource.PLAYERS, 1.0F, 1.0F)
                .build()));
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
    void failedSegmentedCueRollsBackTheSession() {
        for (int index = 0; index < VisualEffectService.MAX_ACTIVE_EFFECTS_PER_LEVEL; index++) {
            VisualEffectService.start(this.level, visualSpec(100));
        }

        assertThrows(IllegalStateException.class, () -> CinematicEffectService.start(this.level,
            timelineBuilder(100).segmented("field", 0, segmentedSpec(100)).build()));

        assertEquals(0, CinematicEffectService.activeCount(this.level));
        assertEquals(VisualEffectService.MAX_ACTIVE_EFFECTS_PER_LEVEL,
            VisualEffectService.activeCount(this.level));
    }

    private static CinematicTimeline.Builder timelineBuilder(int durationTicks) {
        return CinematicTimeline.builder(TIMELINE_ID, durationTicks, 1L);
    }

    private static VisualEffectSpec visualSpec(int durationTicks) {
        return new VisualEffectSpec(VISUAL_ID, Vec3.ZERO, 0.0F, 0.0F, 0.0F, 1.0F, durationTicks, 1L,
            EffectAudience.nearby(32.0));
    }

    private static SegmentedVisualEffectSpec segmentedSpec(int durationTicks) {
        return new SegmentedVisualEffectSpec(VISUAL_ID, Vec3.ZERO, 0.0F, 0.0F, 0.0F, 2.0F, 2, 2, 2.0,
            durationTicks, 1L, EffectAudience.nearby(32.0));
    }
}
