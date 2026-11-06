package mafisher.quasar.effect.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.VisualEffectService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SegmentedVisualEffectServiceTest {
    private static final ResourceLocation EFFECT_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "test");

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
        SegmentedVisualEffectService.stopAll(this.level);
    }

    @Test
    void startsOneSegmentPerGridCell() {
        SegmentedVisualEffectHandle handle = SegmentedVisualEffectService.start(this.level, spec(2, 2, 200));

        assertEquals(1, SegmentedVisualEffectService.activeCount(this.level));
        assertEquals(4, VisualEffectService.activeCount(this.level));

        SegmentedVisualEffectService.stop(this.level, handle);

        assertEquals(0, SegmentedVisualEffectService.activeCount(this.level));
        assertEquals(0, VisualEffectService.activeCount(this.level));
    }

    @Test
    void rejectsAStartThatDoesNotFitTheSharedEffectCap() {
        SegmentedVisualEffectService.start(this.level, spec(16, 8, 200));

        assertEquals(128, VisualEffectService.activeCount(this.level));
        assertThrows(IllegalStateException.class,
            () -> SegmentedVisualEffectService.start(this.level, spec(1, 1, 200)));
        assertEquals(128, VisualEffectService.activeCount(this.level));
    }

    @Test
    void expiresAndReleasesEverySegment() {
        SegmentedVisualEffectService.start(this.level, spec(2, 2, 5));

        SegmentedVisualEffectService.tick(this.level);
        assertEquals(1, SegmentedVisualEffectService.activeCount(this.level));

        this.gameTime = 5;
        SegmentedVisualEffectService.tick(this.level);

        assertEquals(0, SegmentedVisualEffectService.activeCount(this.level));
        assertEquals(0, VisualEffectService.activeCount(this.level));
    }

    @Test
    void updatesAndStopsAreIdempotent() {
        SegmentedVisualEffectHandle handle = SegmentedVisualEffectService.start(this.level, spec(2, 1, 200));
        EffectTransform transform = new EffectTransform(new EffectPoint(10.0, 70.0, -10.0), 45.0F, 0.0F, 0.0F);

        SegmentedVisualEffectService.update(this.level, handle, transform);
        SegmentedVisualEffectService.stop(this.level, handle);
        SegmentedVisualEffectService.stop(this.level, handle);
        SegmentedVisualEffectService.update(this.level, handle, transform);

        assertEquals(0, VisualEffectService.activeCount(this.level));
    }

    @Test
    void stopAllReleasesEveryCoordinator() {
        SegmentedVisualEffectService.start(this.level, spec(2, 2, 200));
        SegmentedVisualEffectService.start(this.level, spec(1, 1, 200));

        assertEquals(2, SegmentedVisualEffectService.stopAll(this.level));

        assertEquals(0, SegmentedVisualEffectService.activeCount(this.level));
        assertEquals(0, VisualEffectService.activeCount(this.level));
    }

    @Test
    void aSingleCellIsAllowed() {
        SegmentedVisualEffectHandle handle = SegmentedVisualEffectService.start(this.level, spec(1, 1, 200));

        assertEquals(1, VisualEffectService.activeCount(this.level));

        SegmentedVisualEffectService.stop(this.level, handle);
        assertEquals(0, VisualEffectService.activeCount(this.level));
    }

    private static SegmentedVisualEffectSpec spec(int columns, int rows, int durationTicks) {
        return new SegmentedVisualEffectSpec(
            EFFECT_ID,
            new Vec3(0.0, 64.0, 0.0),
            0.0F,
            0.0F,
            0.0F,
            4.0F,
            columns,
            rows,
            columns * rows > 1 ? 8.0 : 0.0,
            durationTicks,
            42L,
            EffectAudience.dimensionWide()
        );
    }
}
