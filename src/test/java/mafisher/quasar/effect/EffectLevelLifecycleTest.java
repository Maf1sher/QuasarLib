package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectRemovalPolicy;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.gameplay.GameplayEffectEndReason;
import mafisher.quasar.effect.runtime.EffectRuntimeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EffectLevelLifecycleTest {
    private static final ResourceLocation EFFECT_ID =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "lifecycle_test");

    @Test
    void clearDropsVisualsAndBudgetCounters() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.isClientSide()).thenReturn(false);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.players()).thenReturn(List.of());
        when(level.getGameTime()).thenReturn(0L);

        VisualEffectService.start(level, spec());
        EffectBudgetService.recordCull(level);
        assertNotNull(EffectRuntimeRegistry.peek(level));
        assertEquals(1L, EffectBudgetService.report(level).culledEffects());

        EffectLevelLifecycle.clear(level, EffectRemovalPolicy.KEEP_FOR_RESTORE,
            GameplayEffectEndReason.LEVEL_UNLOAD);

        assertEquals(0, VisualEffectService.activeCount(level));
        assertNull(EffectRuntimeRegistry.peek(level));
        assertEquals(0L, EffectBudgetService.report(level).culledEffects());
    }

    private static VisualEffectSpec spec() {
        return VisualEffectSpec.builder(EFFECT_ID, Vec3.ZERO, 200, 42L, EffectAudience.dimensionWide())
            .rotation(0.0F, 0.0F, 0.0F)
            .scale(1.0F)
            .build();
    }
}
