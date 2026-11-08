package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScreenEffectMediumStateTest {
    @Test
    void noneIsDry() {
        assertFalse(ScreenEffectMediumState.NONE.submerged());
        assertFalse(ScreenEffectMediumState.NONE.cameraSubmerged());
        assertFalse(ScreenEffectMediumState.NONE.effectSubmerged());
    }

    @Test
    void detectsEitherSideSubmerged() {
        ScreenEffectMediumState cameraOnly = new ScreenEffectMediumState(
            ScreenEffectMedium.WATER, ScreenEffectMedium.NONE, -3.0F, Float.NaN
        );
        ScreenEffectMediumState effectOnly = new ScreenEffectMediumState(
            ScreenEffectMedium.NONE, ScreenEffectMedium.WATER, Float.NaN, 8.0F
        );

        assertTrue(cameraOnly.cameraSubmerged());
        assertFalse(cameraOnly.effectSubmerged());
        assertTrue(cameraOnly.submerged());
        assertFalse(effectOnly.cameraSubmerged());
        assertTrue(effectOnly.effectSubmerged());
        assertTrue(effectOnly.submerged());
    }

    @Test
    void rejectsInfiniteSurfaceHeights() {
        assertThrows(IllegalArgumentException.class, () -> new ScreenEffectMediumState(
            ScreenEffectMedium.WATER, ScreenEffectMedium.NONE, Float.POSITIVE_INFINITY, Float.NaN
        ));
    }
}
