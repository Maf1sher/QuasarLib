package mafisher.quasar.effect.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.cinematic.CinematicEffectService;
import mafisher.quasar.api.cinematic.CinematicTimeline;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPriority;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.scale.SegmentTerrainConform;
import mafisher.quasar.api.scale.SegmentedVisualEffectService;
import mafisher.quasar.api.scale.SegmentedVisualEffectSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EffectRuntimeCompositionTest {
    private static final ResourceLocation EFFECT_ID =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "runtime_test");
    private static final ResourceLocation TIMELINE_ID =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "runtime_timeline");

    private ServerLevel level;
    private ServerLevel other;
    private LevelEffectRuntime runtime;
    private LevelEffectRuntime otherRuntime;

    @BeforeEach
    void setUp() {
        this.level = level();
        this.other = level();
        this.runtime = TestEffectRuntime.install(this.level);
        this.otherRuntime = TestEffectRuntime.install(this.other);
    }

    @AfterEach
    void tearDown() {
        TestEffectRuntime.uninstall(this.level);
        TestEffectRuntime.uninstall(this.other);
    }

    @Test
    void keepsVisualStatePerLevel() {
        VisualEffectService.start(this.level, visualSpec());
        VisualEffectService.start(this.other, visualSpec());

        assertEquals(1, this.runtime.visuals().activeCount());
        assertEquals(1, this.otherRuntime.visuals().activeCount());

        this.runtime.close();

        assertEquals(0, this.runtime.visuals().activeCount());
        assertEquals(1, this.otherRuntime.visuals().activeCount());
        assertSame(this.otherRuntime, EffectRuntimeRegistry.peek(this.other));
    }

    @Test
    void cinematicChildrenUseTheInstalledRuntime() {
        CinematicEffectService.start(
            this.level,
            CinematicTimeline.builder(TIMELINE_ID, 100, 1L)
                .visual("ring", 0, visualSpec())
                .build()
        );

        assertEquals(1, this.runtime.cinematics().activeCount());
        assertEquals(1, this.runtime.visuals().activeCount());
        assertEquals(0, otherRuntimeCount(this.other));
    }

    @Test
    void closeDropsEveryDomain() {
        VisualEffectService.start(this.level, visualSpec());
        SegmentedVisualEffectService.start(this.level, segmentedSpec());
        CinematicEffectService.start(
            this.level,
            CinematicTimeline.builder(TIMELINE_ID, 100, 1L)
                .visual("ring", 0, visualSpec())
                .build()
        );

        assertEquals(2, this.runtime.visuals().activeCount());
        assertEquals(1, this.runtime.segmented().activeCount());
        assertEquals(1, this.runtime.cinematics().activeCount());

        this.runtime.close();

        assertEquals(0, this.runtime.visuals().activeCount());
        assertEquals(0, this.runtime.segmented().activeCount());
        assertEquals(0, this.runtime.cinematics().activeCount());
        assertEquals(0, this.runtime.audio().activeCount());
    }

    private static int otherRuntimeCount(ServerLevel other) {
        LevelEffectRuntime runtime = EffectRuntimeRegistry.peek(other);
        return runtime == null ? 0 : runtime.cinematics().activeCount();
    }

    private static ServerLevel level() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.isClientSide()).thenReturn(false);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.players()).thenReturn(List.of());
        when(level.getGameTime()).thenReturn(0L);
        return level;
    }

    private static VisualEffectSpec visualSpec() {
        return VisualEffectSpec.builder(
                EFFECT_ID, Vec3.ZERO, 200, 1L, EffectAudience.dimensionWide()
            )
            .rotation(0.0F, 0.0F, 0.0F)
            .scale(1.0F)
            .build();
    }

    private static SegmentedVisualEffectSpec segmentedSpec() {
        return SegmentedVisualEffectSpec.builder(
                EFFECT_ID, Vec3.ZERO, 200, 1L, EffectAudience.dimensionWide()
            )
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
