package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.effect.audio.EffectAudioSpec;
import mafisher.quasar.effect.persistence.EffectCapabilities;
import mafisher.quasar.effect.persistence.EffectPersistence;
import mafisher.quasar.effect.persistence.EffectPersistenceOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VisualEffectBudgetTest {
    private static final ResourceLocation EFFECT_ID =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "budget_test");

    private ServerLevel level;

    @BeforeEach
    void setUp() {
        VisualEffectService.overrideBudgetForTesting(2);
        this.level = mock(ServerLevel.class);
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.players()).thenReturn(List.of());
        when(this.level.getGameTime()).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        VisualEffectService.clear(this.level);
        VisualEffectService.resetBudgetForTesting();
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
    void restoreNeverReturnsNullForACapacityReason() {
        start(EffectPriority.HIGH);
        start(EffectPriority.HIGH);

        VisualEffectHandle restored = VisualEffectService.restore(
            this.level,
            spec(EffectPriority.NORMAL),
            UUID.randomUUID(),
            0L,
            null
        );

        assertNotNull(restored);
        assertFalse(VisualEffectService.isActive(this.level, restored));
        assertEquals(2, VisualEffectService.activeCount(this.level));
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
        );

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

    private VisualEffectHandle start(EffectPriority priority) {
        return VisualEffectService.start(this.level, spec(priority));
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
