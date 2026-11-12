package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import mafisher.quasar.api.client.BloomSettings;
import org.junit.jupiter.api.Test;

class BloomSettingsTest {
    @Test
    void acceptsBoundedFiniteValues() {
        assertDoesNotThrow(() -> new BloomSettings(0.0F, 0.0F, 0.0F));
        assertDoesNotThrow(() -> new BloomSettings(
            BloomSettings.MAX_THRESHOLD, BloomSettings.MAX_INTENSITY, BloomSettings.MAX_RADIUS_PIXELS));
    }

    @Test
    void rejectsInvalidThresholdIntensityAndRadius() {
        assertThrows(IllegalArgumentException.class, () -> new BloomSettings(-0.1F, 1.0F, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> new BloomSettings(
            BloomSettings.MAX_THRESHOLD + 0.1F, 1.0F, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> new BloomSettings(0.5F, -0.1F, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> new BloomSettings(
            0.5F, BloomSettings.MAX_INTENSITY + 0.1F, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> new BloomSettings(0.5F, 1.0F, -0.1F));
        assertThrows(IllegalArgumentException.class, () -> new BloomSettings(
            0.5F, 1.0F, BloomSettings.MAX_RADIUS_PIXELS + 0.1F));
        assertThrows(IllegalArgumentException.class, () -> new BloomSettings(Float.NaN, 1.0F, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> new BloomSettings(0.5F, Float.POSITIVE_INFINITY, 8.0F));
        assertThrows(IllegalArgumentException.class, () -> new BloomSettings(0.5F, 1.0F, Float.NaN));
    }
}
