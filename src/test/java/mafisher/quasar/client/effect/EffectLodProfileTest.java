package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class EffectLodProfileTest {
    @Test
    void fullProfileKeepsOneLevel() {
        assertSame(EffectLodProfile.FULL, EffectLodProfile.of());
        assertEquals(1, EffectLodProfile.FULL.levelCount());
        assertEquals(0, EffectLodProfile.FULL.select(0.0));
        assertEquals(0, EffectLodProfile.FULL.select(10_000.0));
    }

    @Test
    void selectsBandsAtTheirBoundaries() {
        EffectLodProfile profile = EffectLodProfile.of(2.0, 8.0);

        assertEquals(3, profile.levelCount());
        assertEquals(2.0, profile.threshold(0));
        assertEquals(8.0, profile.threshold(1));
        assertEquals(0, profile.select(0.0));
        assertEquals(0, profile.select(2.0));
        assertEquals(1, profile.select(2.0001));
        assertEquals(1, profile.select(8.0));
        assertEquals(2, profile.select(8.0001));
        assertEquals(2, profile.select(1_000.0));
        assertEquals(profile, EffectLodProfile.of(2.0, 8.0));
        assertEquals(profile.hashCode(), EffectLodProfile.of(2.0, 8.0).hashCode());
    }

    @Test
    void acceptsZeroThresholdAndAFullLevelSet() {
        assertEquals(0, EffectLodProfile.of(0.0).select(0.0));
        assertEquals(1, EffectLodProfile.of(0.0).select(0.0001));

        double[] thresholds = {0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0};
        assertEquals(9, EffectLodProfile.of(thresholds).levelCount());
    }

    @Test
    void rejectsInvalidThresholds() {
        assertThrows(IllegalArgumentException.class, () -> EffectLodProfile.of(-1.0));
        assertThrows(IllegalArgumentException.class, () -> EffectLodProfile.of(Double.NaN));
        assertThrows(IllegalArgumentException.class,
            () -> EffectLodProfile.of(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> EffectLodProfile.of(2.0, 2.0));
        assertThrows(IllegalArgumentException.class, () -> EffectLodProfile.of(2.0, 1.0));
        assertThrows(IllegalArgumentException.class,
            () -> EffectLodProfile.of(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0));
        assertThrows(NullPointerException.class, () -> new EffectLodProfile(null));
        assertThrows(NullPointerException.class,
            () -> new EffectLodProfile(java.util.Collections.singletonList(null)));
    }

    @Test
    void rejectsInvalidSelectionDistance() {
        EffectLodProfile profile = EffectLodProfile.of(1.0);

        assertThrows(IllegalArgumentException.class, () -> profile.select(-0.1));
        assertThrows(IllegalArgumentException.class, () -> profile.select(Double.NaN));
        assertThrows(IllegalArgumentException.class,
            () -> profile.select(Double.POSITIVE_INFINITY));
    }

    @Test
    void validatesSelectedLod() {
        assertThrows(IllegalArgumentException.class, () -> new EffectLod(-1, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new EffectLod(0, -1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new EffectLod(0, 0.0, -1.0));
        assertThrows(IllegalArgumentException.class, () -> new EffectLod(0, Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new EffectLod(0, 0.0, Double.NaN));
    }
}
