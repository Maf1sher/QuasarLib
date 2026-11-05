package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WaveDistortionSettingsTest {
    @Test
    void acceptsBoundedFiniteValues() {
        assertDoesNotThrow(() -> new WaveDistortionSettings(0.0F, 0.1F, 0.0F));
        assertDoesNotThrow(() -> new WaveDistortionSettings(64.0F, 128.0F, 1.0F));
    }

    @Test
    void rejectsInvalidAmplitudeFrequencyAndSpeed() {
        assertThrows(IllegalArgumentException.class, () -> new WaveDistortionSettings(-0.1F, 12.0F, 0.03F));
        assertThrows(IllegalArgumentException.class, () -> new WaveDistortionSettings(64.1F, 12.0F, 0.03F));
        assertThrows(IllegalArgumentException.class, () -> new WaveDistortionSettings(8.0F, 0.0F, 0.03F));
        assertThrows(IllegalArgumentException.class,
            () -> new WaveDistortionSettings(8.0F, 12.0F, Float.NaN));
    }
}
