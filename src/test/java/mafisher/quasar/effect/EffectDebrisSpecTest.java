package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectDebrisSpawn;
import mafisher.quasar.api.effect.EffectDebrisSpec;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EffectDebrisSpecTest {
    private final BlockState state = state();

    @Test
    void ballisticBurstAppliesAStableUpwardBias() {
        EffectDebrisSpec spec = EffectDebrisSpec.ballistic(
            new Vec3(1.0, 64.0, 2.0), this.state, 12, 0.5, 40, EffectAudience.nearby(128.0)
        );

        assertEquals(12, spec.pieces());
        assertEquals(1.0, spec.direction().y);
        assertEquals(0.6, spec.spread());
        assertEquals(0.08, spec.gravity());
    }

    @Test
    void rejectsInvalidBursts() {
        assertThrows(IllegalArgumentException.class, () -> new EffectDebrisSpec(
            Vec3.ZERO, this.state, 0, Vec3.ZERO, 0.5, 1.0, 0.0, 20, EffectAudience.nearby(128.0)
        ));
        assertThrows(IllegalArgumentException.class, () -> new EffectDebrisSpec(
            Vec3.ZERO, this.state, 1, Vec3.ZERO, 0.5, 1.0, 0.0, 0, EffectAudience.nearby(128.0)
        ));
        assertThrows(IllegalArgumentException.class, () -> new EffectDebrisSpec(
            Vec3.ZERO, this.state, 1, Vec3.ZERO, 0.5, 1.5, 0.0, 20, EffectAudience.nearby(128.0)
        ));
        assertThrows(IllegalArgumentException.class, () -> new EffectDebrisSpec(
            new Vec3(Double.NaN, 0.0, 0.0), this.state, 1, Vec3.ZERO, 0.5, 1.0, 0.0, 20,
            EffectAudience.nearby(128.0)
        ));
        BlockState air = mock(BlockState.class);
        when(air.isAir()).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> new EffectDebrisSpec(
            Vec3.ZERO, air, 1, Vec3.ZERO, 0.5, 1.0, 0.0, 20, EffectAudience.nearby(128.0)
        ));
    }

    @Test
    void rejectsInvalidSurfaceSpawns() {
        assertThrows(IllegalArgumentException.class,
            () -> new EffectDebrisSpawn.Surface(Vec3.ZERO, 4.0, 2.0, 0.5));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectDebrisSpawn.Surface(Vec3.ZERO, -1.0, 2.0, 0.5));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectDebrisSpawn.Surface(Vec3.ZERO, 0.0, 2_000.0, 0.5));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectDebrisSpawn.Surface(Vec3.ZERO, 0.0, 2.0, -0.5));
        assertEquals(0.5, new EffectDebrisSpawn.Surface(Vec3.ZERO, 1.0, 8.0, 0.5).lift());
    }

    private static BlockState state() {
        BlockState state = mock(BlockState.class);
        when(state.isAir()).thenReturn(false);
        return state;
    }
}
