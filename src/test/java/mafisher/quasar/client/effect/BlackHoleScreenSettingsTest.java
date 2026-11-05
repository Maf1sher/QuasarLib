package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BlackHoleScreenSettingsTest {
    @Test
    void acceptsBoundedSettings() {
        assertDoesNotThrow(() -> new BlackHoleScreenSettings(
            BlackHoleScreenSettings.MAX_LENS_STRENGTH,
            BlackHoleScreenSettings.MAX_LENS_RADIUS_SCALE,
            BlackHoleScreenSettings.MAX_DISTORTION_PIXELS,
            BloomSettings.DEBUG_DEFAULT));
    }

    @Test
    void rejectsInvalidLensSettings() {
        assertThrows(IllegalArgumentException.class,
            () -> new BlackHoleScreenSettings(-0.01F, 1.0F, 12.0F, BloomSettings.DEBUG_DEFAULT));
        assertThrows(IllegalArgumentException.class,
            () -> new BlackHoleScreenSettings(0.5F, 0.0F, 12.0F, BloomSettings.DEBUG_DEFAULT));
        assertThrows(IllegalArgumentException.class,
            () -> new BlackHoleScreenSettings(0.5F, 1.0F, Float.NaN, BloomSettings.DEBUG_DEFAULT));
        assertThrows(NullPointerException.class,
            () -> new BlackHoleScreenSettings(0.5F, 1.0F, 12.0F, null));
    }
}
