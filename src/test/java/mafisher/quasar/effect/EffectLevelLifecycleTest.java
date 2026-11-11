package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import mafisher.quasar.effect.gameplay.GameplayEffectEndReason;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EffectLevelLifecycleTest {
    private static final ResourceLocation EFFECT_ID =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "lifecycle_test");

    @Test
    void clearDropsVisualsAndEveryLevelSlot() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.isClientSide()).thenReturn(false);
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.players()).thenReturn(List.of());
        when(level.getGameTime()).thenReturn(0L);

        VisualEffectService.start(level, spec());
        assertTrue(LevelStateSlot.isTracked(level));

        EffectLevelLifecycle.clear(level, false, GameplayEffectEndReason.LEVEL_UNLOAD);

        assertEquals(0, VisualEffectService.activeCount(level));
        assertFalse(LevelStateSlot.isTracked(level));
    }

    private static VisualEffectSpec spec() {
        return new VisualEffectSpec(
            EFFECT_ID,
            Vec3.ZERO,
            0.0F,
            0.0F,
            0.0F,
            1.0F,
            200,
            42L,
            EffectAudience.dimensionWide()
        );
    }
}
