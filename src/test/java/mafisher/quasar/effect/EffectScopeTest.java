package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.effect.audio.EffectAudioService;
import mafisher.quasar.effect.persistence.EffectPersistenceService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EffectScopeTest {
    private static final ResourceLocation EFFECT_ID =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "scope_test");

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
        VisualEffectService.clear(this.level);
        EffectAudioService.clear(this.level);
        EffectBudgetService.clear(this.level);
        VisualEffectService.resetBudgetForTesting();
    }

    @Test
    void prunesFinishedVisualsFromTheLedger() {
        try (EffectScope scope = new EffectScope(this.level)) {
            UUID visualId = scope.playVisual(spec(2, EffectPriority.NORMAL)).orElseThrow();
            assertEquals(1, scope.activeVisualCount());

            this.gameTime = 2L;
            VisualEffectService.tick(this.level);
            scope.updateVisual(visualId, transform());

            assertEquals(0, scope.activeVisualCount());
        }
    }

    @Test
    void prunesCulledVisualsFromTheLedger() {
        VisualEffectService.overrideBudgetForTesting(1);
        try (EffectScope scope = new EffectScope(this.level)) {
            UUID backgroundId = scope.playVisual(spec(200, EffectPriority.BACKGROUND)).orElseThrow();
            UUID normalId = scope.playVisual(spec(200, EffectPriority.NORMAL)).orElseThrow();

            assertFalse(VisualEffectService.isActive(this.level, new VisualEffectHandle(backgroundId)));
            assertTrue(VisualEffectService.isActive(this.level, new VisualEffectHandle(normalId)));

            scope.updateVisual(normalId, transform());

            assertEquals(1, scope.activeVisualCount());
        }
    }

    @Test
    void doesNotTrackAVisualCulledAtStart() {
        VisualEffectService.overrideBudgetForTesting(1);
        VisualEffectService.start(this.level, spec(200, EffectPriority.HIGH));
        try (EffectScope scope = new EffectScope(this.level)) {
            assertTrue(scope.playVisual(spec(200, EffectPriority.BACKGROUND)).isEmpty());
            assertEquals(0, scope.activeVisualCount());
        }
    }

    @Test
    void closeKeepsDescriptorsWhenThePolicySaysSo() {
        try (var persistence = mockStatic(EffectPersistenceService.class)) {
            try (EffectScope scope = new EffectScope(this.level)) {
                scope.playVisual(spec(200, EffectPriority.NORMAL)).orElseThrow();
                scope.close(EffectRemovalPolicy.KEEP_FOR_RESTORE);
            }

            persistence.verify(
                () -> EffectPersistenceService.recordStop(any(), any()),
                never()
            );
        }
    }

    @Test
    void closeDeletesDescriptorsByDefault() {
        try (var persistence = mockStatic(EffectPersistenceService.class)) {
            try (EffectScope scope = new EffectScope(this.level)) {
                scope.playVisual(spec(200, EffectPriority.NORMAL)).orElseThrow();
            }

            persistence.verify(
                () -> EffectPersistenceService.recordStop(any(), any()),
                times(1)
            );
        }
    }

    @Test
    void duplicateAdoptKeepsOneLedgerEntry() {
        try (EffectScope scope = new EffectScope(this.level)) {
            UUID visualId = scope.playVisual(spec(200, EffectPriority.NORMAL)).orElseThrow();
            scope.adoptVisual(visualId, new VisualEffectHandle(visualId));

            assertEquals(1, scope.activeVisualCount());
        }
        assertEquals(0, VisualEffectService.activeCount(this.level));
    }

    private static VisualEffectSpec spec(int durationTicks, EffectPriority priority) {
        return new VisualEffectSpec(
            EFFECT_ID,
            Vec3.ZERO,
            0.0F,
            0.0F,
            0.0F,
            1.0F,
            durationTicks,
            42L,
            EffectAudience.dimensionWide(),
            mafisher.quasar.effect.audio.EffectAudioSpec.NONE,
            priority
        );
    }

    private static EffectTransform transform() {
        return new EffectTransform(new EffectPoint(1.0, 64.0, 1.0), 0.0F, 0.0F, 0.0F);
    }
}
