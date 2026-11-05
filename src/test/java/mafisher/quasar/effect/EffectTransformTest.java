package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EffectTransformTest {
    @Test
    void interpolatesPositionAndUsesTheShortestAngularPath() {
        EffectTransform from = new EffectTransform(EffectPoint.ZERO, 179.0F, 0.0F, 0.0F);
        EffectTransform to = new EffectTransform(new EffectPoint(10.0, 4.0, -2.0), -179.0F, 90.0F, 180.0F);

        EffectTransform halfway = EffectTransform.interpolate(from, to, 0.5F);

        assertEquals(new EffectPoint(5.0, 2.0, -1.0), halfway.position());
        assertEquals(180.0F, halfway.yaw());
        assertEquals(45.0F, halfway.pitch());
        assertEquals(-90.0F, halfway.roll());
    }

    @Test
    void clampsInterpolationAndRejectsNonFiniteTransforms() {
        EffectTransform from = new EffectTransform(EffectPoint.ZERO, 0.0F, 0.0F, 0.0F);
        EffectTransform to = new EffectTransform(new EffectPoint(1.0, 1.0, 1.0), 90.0F, 45.0F, 180.0F);

        assertEquals(from, EffectTransform.interpolate(from, to, -1.0F));
        assertEquals(to, EffectTransform.interpolate(from, to, 2.0F));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectTransform(EffectPoint.ZERO, Float.NaN, 0.0F, 0.0F));
        assertThrows(IllegalArgumentException.class,
            () -> EffectTransform.interpolate(from, to, Float.POSITIVE_INFINITY));
    }
}
