package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.budget.EffectBudgetReport;
import mafisher.quasar.api.effect.budget.EffectBudgets;
import mafisher.quasar.api.effect.budget.EffectPriority;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.effect.EffectStartStatus;
import mafisher.quasar.api.effect.VisualEffectHandle;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.effect.VisualEffectStartResult;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectPersistence;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.effect.runtime.EffectRuntimeRegistry;
import mafisher.quasar.effect.runtime.RecordingPacketSink;
import mafisher.quasar.effect.runtime.TestBudgetSource;
import mafisher.quasar.effect.runtime.TestEffectRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import mafisher.quasar.testing.GlobalStateTest;

@GlobalStateTest
class VisualEffectBudgetTest {
    private static final ResourceLocation EFFECT_ID =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "budget_test");

    private ServerLevel level;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.players()).thenReturn(List.of());
        when(this.level.getGameTime()).thenReturn(0L);
        TestEffectRuntime.install(
            this.level,
            new RecordingPacketSink(),
            new TestBudgetSource().standaloneVisuals(2)
        );
    }

    @AfterEach
    void tearDown() {
        VisualEffectService.clear(this.level);
        TestEffectRuntime.uninstall(this.level);
    }

    @Test
    void cullsTheLowestPriorityEffectWhenTheBudgetIsFull() {
        VisualEffectHandle background = start(EffectPriority.BACKGROUND);
        VisualEffectHandle normal = start(EffectPriority.NORMAL);
        VisualEffectHandle high = start(EffectPriority.HIGH);

        assertFalse(VisualEffectService.isActive(this.level, background));
        assertTrue(VisualEffectService.isActive(this.level, normal));
        assertTrue(VisualEffectService.isActive(this.level, high));
        assertEquals(2, VisualEffectService.activeCount(this.level));
    }

    @Test
    void cullsALowerPriorityNewcomerInsteadOfAnActiveEffect() {
        VisualEffectHandle first = start(EffectPriority.HIGH);
        VisualEffectHandle second = start(EffectPriority.HIGH);
        VisualEffectHandle background = start(EffectPriority.BACKGROUND);

        assertTrue(VisualEffectService.isActive(this.level, first));
        assertTrue(VisualEffectService.isActive(this.level, second));
        assertFalse(VisualEffectService.isActive(this.level, background));
        assertEquals(2, VisualEffectService.activeCount(this.level));
    }

    @Test
    void cullsTheOldestEffectOnAPriorityTie() {
        VisualEffectHandle oldest = start(EffectPriority.NORMAL);
        VisualEffectHandle middle = start(EffectPriority.NORMAL);
        VisualEffectHandle newest = start(EffectPriority.NORMAL);

        assertFalse(VisualEffectService.isActive(this.level, oldest));
        assertTrue(VisualEffectService.isActive(this.level, middle));
        assertTrue(VisualEffectService.isActive(this.level, newest));
    }

    @Test
    void restoreReportsAnInactiveStatusWhenTheBudgetCullsIt() {
        start(EffectPriority.HIGH);
        start(EffectPriority.HIGH);

        VisualEffectStartResult restored = VisualEffectService.restore(
            this.level,
            spec(EffectPriority.NORMAL),
            UUID.randomUUID(),
            0L,
            null
        );

        assertNotNull(restored.handle());
        assertEquals(EffectStartStatus.RESTORED_INACTIVE, restored.status());
        assertFalse(restored.active());
        assertEquals(2, VisualEffectService.activeCount(this.level));
    }

    @Test
    void startReportsWhenTheNewcomerIsCulled() {
        start(EffectPriority.HIGH);
        start(EffectPriority.HIGH);

        VisualEffectStartResult background = VisualEffectService.start(
            this.level, spec(EffectPriority.BACKGROUND)
        );

        assertEquals(EffectStartStatus.CULLED_ON_START, background.status());
        assertFalse(background.active());
    }

    @Test
    void adoptOwnedByBindsVisualsToTheirGameplayScope() {
        UUID ownerSession = UUID.randomUUID();
        EffectPersistenceOptions options = new EffectPersistenceOptions(
            EffectPersistence.RESUMABLE,
            EffectCapabilities.NONE,
            new CompoundTag(),
            ownerSession
        );
        VisualEffectHandle handle = VisualEffectService.start(
            this.level, spec(EffectPriority.NORMAL), options
        ).handle();

        try (EffectScope scope = new EffectScope(this.level)) {
            VisualEffectService.adoptOwnedBy(this.level, ownerSession, scope);
            scope.stopVisual(handle.instanceId());
        }

        assertFalse(VisualEffectService.isActive(this.level, handle));
    }

    @Test
    void reportCountsCulledEffects() {
        start(EffectPriority.BACKGROUND);
        start(EffectPriority.NORMAL);
        start(EffectPriority.HIGH);

        EffectBudgetReport report = EffectBudgetService.report(this.level);

        assertEquals(2, report.standaloneVisuals());
        assertEquals(1, report.culledEffects());
    }

    @Test
    void reportDoesNotCreateRuntime() {
        ServerLevel untouched = mock(ServerLevel.class);

        EffectBudgetReport report = EffectBudgetService.report(untouched);

        assertEquals(0, report.standaloneVisuals());
        assertEquals(0L, report.culledEffects());
        assertNull(EffectRuntimeRegistry.peek(untouched));
    }

    @Test
    void reportUsesTheRuntimeBudgetSource() {
        EffectBudgetReport report = EffectBudgetService.report(this.level);

        assertEquals(2, report.standaloneVisualBudget());
        assertEquals(EffectBudgets.segments(), report.segmentBudget());
        assertEquals(EffectBudgets.gameplaySessions(), report.gameplaySessionBudget());
        assertEquals(EffectBudgets.cinematicSessions(), report.cinematicSessionBudget());
    }

    private VisualEffectHandle start(EffectPriority priority) {
        return VisualEffectService.start(this.level, spec(priority)).handle();
    }

    private static VisualEffectSpec spec(EffectPriority priority) {
        return new VisualEffectSpec(
            EFFECT_ID,
            new Vec3(0.0, 64.0, 0.0),
            0.0F,
            0.0F,
            0.0F,
            1.0F,
            200,
            42L,
            EffectAudience.dimensionWide(),
            EffectAudioSpec.NONE,
            priority
        );
    }
}
