package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContentBlackHoleSettingsTest {
    @Test
    void defaultSettingsAreValid() {
        ContentBlackHoleSettings settings = ContentBlackHoleSettings.DEFAULT;

        assertTrue(settings.diskInnerFactor() > 0.0F);
        assertTrue(settings.diskOuterFactor() > settings.diskInnerFactor());
        assertTrue(settings.diskThicknessFactor() >= 0.0F);
        assertTrue(settings.diskThicknessFactor() <= 0.5F);
        assertTrue(settings.lensStrength() >= 0.0F && settings.lensStrength() <= 1.0F);
        assertTrue(settings.lensWarpFactor() >= 2.6F && settings.lensWarpFactor() <= 12.0F);
        assertTrue(settings.maskRadiusFactor() > 0.0F && settings.maskRadiusFactor() <= 32.0F);
        assertTrue(settings.bloomStrength() >= 0.0F && settings.bloomStrength() <= 2.0F);
    }

    @Test
    void rejectsInvalidRanges() {
        assertThrows(IllegalArgumentException.class,
            () -> settings(3.0F, 2.0F, 0.1F, 1.0F, 5.0F, 1.35F, 4.0F, 1.0F, 0.5F, 13.0F, 0.65F));
        assertThrows(IllegalArgumentException.class,
            () -> settings(3.0F, 11.0F, -0.1F, 1.0F, 5.0F, 1.35F, 4.0F, 1.0F, 0.5F, 13.0F, 0.65F));
        assertThrows(IllegalArgumentException.class,
            () -> settings(3.0F, 11.0F, 0.1F, -0.1F, 5.0F, 1.35F, 4.0F, 1.0F, 0.5F, 13.0F, 0.65F));
        assertThrows(IllegalArgumentException.class,
            () -> settings(3.0F, 11.0F, 0.1F, 1.1F, 5.0F, 1.35F, 4.0F, 1.0F, 0.5F, 13.0F, 0.65F));
        assertThrows(IllegalArgumentException.class,
            () -> settings(3.0F, 11.0F, 0.1F, 1.0F, 2.5F, 1.35F, 4.0F, 1.0F, 0.5F, 13.0F, 0.65F));
        assertThrows(IllegalArgumentException.class,
            () -> settings(3.0F, 11.0F, 0.1F, 1.0F, 12.5F, 1.35F, 4.0F, 1.0F, 0.5F, 13.0F, 0.65F));
        assertThrows(IllegalArgumentException.class,
            () -> settings(3.0F, 11.0F, 0.1F, 1.0F, 5.0F, 1.35F, 4.0F, 1.0F, 1.1F, 13.0F, 0.65F));
        assertThrows(IllegalArgumentException.class,
            () -> settings(3.0F, 11.0F, 0.1F, 1.0F, 5.0F, 1.35F, 4.0F, 1.0F, 0.5F, 0.0F, 0.65F));
        assertThrows(IllegalArgumentException.class,
            () -> settings(3.0F, 11.0F, 0.1F, 1.0F, 5.0F, 1.35F, 4.0F, 1.0F, 0.5F, 13.0F, 2.1F));
    }

    private static ContentBlackHoleSettings settings(float inner, float outer, float thickness,
        float lens, float warp, float disk, float ring, float turbulence, float charge,
        float mask, float bloom) {
        return new ContentBlackHoleSettings(inner, outer, thickness, lens, warp, disk, ring,
            turbulence, charge, mask, bloom);
    }
}
