package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import mafisher.quasar.api.client.render.EffectScreenLodProfile;
import org.junit.jupiter.api.Test;

class EffectScreenLodProfileTest {
    @Test
    void fullProfileKeepsOneLevel() {
        assertSame(EffectScreenLodProfile.FULL, EffectScreenLodProfile.of());
        assertEquals(1, EffectScreenLodProfile.FULL.levelCount());
        assertEquals(0, EffectScreenLodProfile.FULL.selectCoverage(0.0));
        assertEquals(0, EffectScreenLodProfile.FULL.selectCoverage(1.0));
    }

    @Test
    void selectsBandsAtTheirCoverageBoundaries() {
        EffectScreenLodProfile profile = EffectScreenLodProfile.of(0.5, 0.1);

        assertEquals(3, profile.levelCount());
        assertEquals(0.5, profile.minimumCoverage(0));
        assertEquals(0.1, profile.minimumCoverage(1));
        assertEquals(0.5, profile.lossThreshold(0));
        assertEquals(0.9, profile.lossThreshold(1));
        assertEquals(0, profile.selectCoverage(1.0));
        assertEquals(0, profile.selectCoverage(0.5));
        assertEquals(1, profile.selectCoverage(0.4999));
        assertEquals(1, profile.selectCoverage(0.1));
        assertEquals(2, profile.selectCoverage(0.0999));
        assertEquals(2, profile.selectCoverage(0.0));
        assertEquals(profile, EffectScreenLodProfile.of(0.5, 0.1));
        assertEquals(profile.hashCode(), EffectScreenLodProfile.of(0.5, 0.1).hashCode());
    }

    @Test
    void acceptsFullScreenAndAPartialLevelSet() {
        assertEquals(0, EffectScreenLodProfile.of(1.0).selectCoverage(1.0));
        assertEquals(1, EffectScreenLodProfile.of(1.0).selectCoverage(0.9999));

        double[] thresholds = {1.0, 0.8, 0.6, 0.4, 0.2, 0.1, 0.05, 0.01};
        assertEquals(9, EffectScreenLodProfile.of(thresholds).levelCount());
    }

    @Test
    void rejectsInvalidThresholds() {
        assertThrows(IllegalArgumentException.class, () -> EffectScreenLodProfile.of(0.0));
        assertThrows(IllegalArgumentException.class, () -> EffectScreenLodProfile.of(-0.1));
        assertThrows(IllegalArgumentException.class, () -> EffectScreenLodProfile.of(1.1));
        assertThrows(IllegalArgumentException.class, () -> EffectScreenLodProfile.of(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> EffectScreenLodProfile.of(0.1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> EffectScreenLodProfile.of(0.5, 0.5));
        assertThrows(IllegalArgumentException.class,
            () -> EffectScreenLodProfile.of(0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1));
        assertThrows(NullPointerException.class, () -> new EffectScreenLodProfile(null));
        assertThrows(NullPointerException.class,
            () -> new EffectScreenLodProfile(Collections.singletonList(null)));
    }

    @Test
    void rejectsInvalidSelectionValues() {
        EffectScreenLodProfile profile = EffectScreenLodProfile.of(0.5);

        assertThrows(IllegalArgumentException.class, () -> profile.selectCoverage(-0.1));
        assertThrows(IllegalArgumentException.class, () -> profile.selectCoverage(1.1));
        assertThrows(IllegalArgumentException.class, () -> profile.selectCoverage(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> profile.selectLoss(-0.1));
        assertThrows(IllegalArgumentException.class, () -> profile.selectLoss(1.1));
        assertThrows(IllegalArgumentException.class,
            () -> profile.selectLoss(Double.POSITIVE_INFINITY));
    }
}
