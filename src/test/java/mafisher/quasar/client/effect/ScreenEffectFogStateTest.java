package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.client.ScreenEffectFogState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ScreenEffectFogStateTest {
    @Test
    void noneIsInactive() {
        assertFalse(ScreenEffectFogState.NONE.active());
        assertEquals(0.0F, ScreenEffectFogState.NONE.factor(100.0), 0.0001F);
    }

    @Test
    void factorRampsBetweenStartAndEnd() {
        ScreenEffectFogState fog = new ScreenEffectFogState(10.0F, 50.0F, new Vec3(0.1, 0.2, 0.3));

        assertTrue(fog.active());
        assertEquals(0.0F, fog.factor(5.0), 0.0001F);
        assertEquals(0.0F, fog.factor(10.0), 0.0001F);
        assertEquals(0.5F, fog.factor(30.0), 0.0001F);
        assertEquals(1.0F, fog.factor(50.0), 0.0001F);
        assertEquals(1.0F, fog.factor(100.0), 0.0001F);
    }

    @Test
    void waterFogHidesDistantEffects() {
        // Vanilla water fog: start -8, end 96 * waterVision.
        ScreenEffectFogState fog = new ScreenEffectFogState(-8.0F, 96.0F, new Vec3(0.1, 0.3, 0.5));

        assertTrue(fog.active());
        assertTrue(fog.factor(2.0) < 0.05F);
        assertTrue(fog.factor(48.0) > 0.2F && fog.factor(48.0) < 0.6F);
        assertEquals(1.0F, fog.factor(160.0), 0.0001F);
    }

    @Test
    void rejectsNonFiniteValues() {
        assertThrows(IllegalArgumentException.class,
            () -> new ScreenEffectFogState(Float.NaN, 10.0F, Vec3.ZERO));
        assertThrows(IllegalArgumentException.class,
            () -> new ScreenEffectFogState(0.0F, Float.POSITIVE_INFINITY, Vec3.ZERO));
        assertThrows(IllegalArgumentException.class,
            () -> new ScreenEffectFogState(0.0F, 10.0F, new Vec3(Double.NaN, 0.0, 0.0)));
    }
}
