package mafisher.quasar.content.rune;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ContentRuneCircleSettingsTest {
    @Test
    void defaultLayersAreValid() {
        ContentRuneCircleSettings settings = ContentRuneCircleSettings.DEFAULT;

        assertEquals(1.0F, settings.outer().scale(), 0.0001F);
        assertEquals(0.72F, settings.middle().scale(), 0.0001F);
        assertEquals(0.43F, settings.inner().scale(), 0.0001F);
        assertEquals(0.26F, settings.inner().height(), 0.0001F);
    }

    @Test
    void rejectsInvalidLayer() {
        assertThrows(IllegalArgumentException.class, () -> new ContentRuneCircleSettings.ContentRuneLayer(
            0.0F, 0.1F, 0.5F, 0.5F, 0.5F, 1.0F, 0.1F
        ));
        assertThrows(IllegalArgumentException.class, () -> new ContentRuneCircleSettings.ContentRuneLayer(
            1.0F, -0.1F, 0.5F, 0.5F, 0.5F, 1.0F, 0.1F
        ));
        assertThrows(IllegalArgumentException.class, () -> new ContentRuneCircleSettings.ContentRuneLayer(
            1.0F, 0.1F, 1.1F, 0.5F, 0.5F, 1.0F, 0.1F
        ));
    }
}
