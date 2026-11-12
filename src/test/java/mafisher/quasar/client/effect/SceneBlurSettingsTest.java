package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import mafisher.quasar.api.client.SceneBlurSettings;
import org.junit.jupiter.api.Test;

class SceneBlurSettingsTest {
    @Test
    void acceptsBoundedFiniteRadii() {
        assertDoesNotThrow(() -> new SceneBlurSettings(0.0F));
        assertDoesNotThrow(() -> new SceneBlurSettings(SceneBlurSettings.MAX_RADIUS_PIXELS));
    }

    @Test
    void rejectsNegativeOversizedAndNonFiniteRadii() {
        assertThrows(IllegalArgumentException.class, () -> new SceneBlurSettings(-0.1F));
        assertThrows(IllegalArgumentException.class,
            () -> new SceneBlurSettings(SceneBlurSettings.MAX_RADIUS_PIXELS + 0.1F));
        assertThrows(IllegalArgumentException.class, () -> new SceneBlurSettings(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new SceneBlurSettings(Float.POSITIVE_INFINITY));
    }
}
