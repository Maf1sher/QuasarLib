package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ContentRuneGlowSettingsTest {
    @Test
    void defaultIsValid() {
        ContentRuneGlowSettings settings = ContentRuneGlowSettings.DEFAULT;

        assertEquals(0.55F, settings.strength(), 0.0001F);
        assertEquals(0.05F, settings.pulseSpeed(), 0.0001F);
        assertEquals(1.0F, settings.blue(), 0.0001F);
    }

    @Test
    void rejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class,
            () -> new ContentRuneGlowSettings(-0.1F, 0.05F, 0.1F, 0.2F, 0.3F));
        assertThrows(IllegalArgumentException.class,
            () -> new ContentRuneGlowSettings(4.1F, 0.05F, 0.1F, 0.2F, 0.3F));
        assertThrows(IllegalArgumentException.class,
            () -> new ContentRuneGlowSettings(0.5F, 1.1F, 0.1F, 0.2F, 0.3F));
        assertThrows(IllegalArgumentException.class,
            () -> new ContentRuneGlowSettings(0.5F, 0.05F, 1.1F, 0.2F, 0.3F));
    }
}
