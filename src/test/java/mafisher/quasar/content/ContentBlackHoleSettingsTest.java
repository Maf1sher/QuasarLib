package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ContentBlackHoleSettingsTest {
    @Test
    void defaultSettingsAreValid() {
        ContentBlackHoleSettings settings = ContentBlackHoleSettings.DEFAULT;

        assertEquals(3.0F, settings.diskInnerFactor(), 0.0001F);
        assertEquals(9.0F, settings.diskOuterFactor(), 0.0001F);
        assertEquals(0.14F, settings.diskThicknessFactor(), 0.0001F);
        assertEquals(15.0F, settings.maskRadiusFactor(), 0.0001F);
    }

    @Test
    void rejectsInvalidRanges() {
        assertThrows(IllegalArgumentException.class,
            () -> settings(3.0F, 2.0F, 0.1F, 1.8F, 1.35F, 4.0F, 1.0F, 0.5F, 13.0F, 0.65F));
        assertThrows(IllegalArgumentException.class,
            () -> settings(3.0F, 11.0F, -0.1F, 1.8F, 1.35F, 4.0F, 1.0F, 0.5F, 13.0F, 0.65F));
        assertThrows(IllegalArgumentException.class,
            () -> settings(3.0F, 11.0F, 0.1F, -0.1F, 1.35F, 4.0F, 1.0F, 0.5F, 13.0F, 0.65F));
        assertThrows(IllegalArgumentException.class,
            () -> settings(3.0F, 11.0F, 0.1F, 1.8F, 1.35F, 4.0F, 1.0F, 1.1F, 13.0F, 0.65F));
        assertThrows(IllegalArgumentException.class,
            () -> settings(3.0F, 11.0F, 0.1F, 1.8F, 1.35F, 4.0F, 1.0F, 0.5F, 0.0F, 0.65F));
        assertThrows(IllegalArgumentException.class,
            () -> settings(3.0F, 11.0F, 0.1F, 1.8F, 1.35F, 4.0F, 1.0F, 0.5F, 13.0F, 2.1F));
    }

    private static ContentBlackHoleSettings settings(float inner, float outer, float thickness,
        float lens, float disk, float ring, float turbulence, float charge, float mask, float bloom) {
        return new ContentBlackHoleSettings(inner, outer, thickness, lens, disk, ring, turbulence,
            charge, mask, bloom);
    }
}
