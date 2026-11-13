package mafisher.quasar.api.effect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EffectCurveTest {
    @Test
    void clampsOutsideTheKeyframeRange() {
        EffectCurve curve = EffectCurve.scalar()
            .keyframe(0.2F, 1.0F, EffectEasing.LINEAR)
            .keyframe(0.8F, 9.0F, EffectEasing.LINEAR)
            .build();

        assertEquals(1.0F, curve.sampleFloat(-1.0F), 0.0F);
        assertEquals(1.0F, curve.sampleFloat(0.2F), 0.0F);
        assertEquals(9.0F, curve.sampleFloat(0.8F), 0.0F);
        assertEquals(9.0F, curve.sampleFloat(2.0F), 0.0F);
    }

    @Test
    void easingModesMapProgressInsideTheUnitInterval() {
        assertEquals(0.25F, EffectEasing.EASE_IN.apply(0.5F), 1.0E-6F);
        assertEquals(0.75F, EffectEasing.EASE_OUT.apply(0.5F), 1.0E-6F);
        assertEquals(0.5F, EffectEasing.EASE_IN_OUT.apply(0.5F), 1.0E-6F);
        assertEquals(0.125F, EffectEasing.EASE_IN_CUBIC.apply(0.5F), 1.0E-6F);
        assertEquals(0.875F, EffectEasing.EASE_OUT_CUBIC.apply(0.5F), 1.0E-6F);
        assertEquals(1.0F, EffectEasing.LINEAR.apply(2.0F), 0.0F);
    }

    @Test
    void appliesTheEasingOfTheStartingKeyframe() {
        EffectCurve curve = EffectCurve.scalar()
            .keyframe(0.0F, 0.0F, EffectEasing.EASE_OUT_CUBIC)
            .keyframe(1.0F, 10.0F, EffectEasing.LINEAR)
            .build();

        assertEquals(8.75F, curve.sampleFloat(0.5F), 1.0E-5F);
    }

    @Test
    void resolvesAnchorRelativeKeyframesAtBuildTime() {
        EffectCurve curve = EffectCurve.scalar()
            .keyframe("start", 0.0F, 0.0F, EffectEasing.LINEAR)
            .keyframe("end", 0.1F, 10.0F, EffectEasing.LINEAR)
            .build(Map.of("start", 0.2F, "end", 0.8F));

        assertEquals(5.0F, curve.sampleFloat(0.55F), 1.0E-5F);
        assertEquals(0.2F, curve.firstProgress(), 1.0E-6F);
        assertEquals(0.9F, curve.lastProgress(), 1.0E-6F);
    }

    @Test
    void rejectsUnknownAnchorsAndAnchorlessBuilds() {
        EffectCurve.Builder builder = EffectCurve.scalar()
            .keyframe("missing", 0.0F, 1.0F, EffectEasing.LINEAR);

        assertThrows(IllegalStateException.class, () -> builder.build());
        assertThrows(IllegalArgumentException.class, () -> builder.build(Map.of()));
    }

    @Test
    void sortsKeyframesAndRejectsDuplicatePositions() {
        EffectCurve curve = EffectCurve.scalar()
            .keyframe(0.8F, 8.0F, EffectEasing.LINEAR)
            .keyframe(0.2F, 2.0F, EffectEasing.LINEAR)
            .build();

        assertEquals(0.2F, curve.firstProgress(), 1.0E-6F);
        assertEquals(0.8F, curve.lastProgress(), 1.0E-6F);

        assertThrows(IllegalArgumentException.class, () -> EffectCurve.scalar()
            .keyframe(0.5F, 1.0F, EffectEasing.LINEAR)
            .keyframe(0.5F, 2.0F, EffectEasing.LINEAR)
            .build());
    }

    @Test
    void interpolatesEveryComponentOfVectorAndColourCurves() {
        EffectCurve curve = EffectCurve.vec3()
            .keyframe(0.0F, EffectCurve.Value.vec3(1.0F, 2.0F, 3.0F), EffectEasing.LINEAR)
            .keyframe(1.0F, EffectCurve.Value.vec3(5.0F, 6.0F, 7.0F), EffectEasing.LINEAR)
            .build();
        float[] out = new float[3];

        curve.sample(0.5F, out);

        assertArrayEquals(new float[] {3.0F, 4.0F, 5.0F}, out, 1.0E-6F);
        assertThrows(IllegalStateException.class, () -> curve.sampleFloat(0.5F));
    }

    @Test
    void samplesIntoAReusableBufferWithoutChangingTheCurve() {
        EffectCurve curve = EffectCurve.color()
            .keyframe(0.0F, EffectCurve.Value.color(0.0F, 0.0F, 0.0F, 0.0F), EffectEasing.LINEAR)
            .keyframe(1.0F, EffectCurve.Value.color(1.0F, 1.0F, 1.0F, 1.0F), EffectEasing.LINEAR)
            .build();
        float[] out = new float[4];

        curve.sample(0.25F, out);
        assertArrayEquals(new float[] {0.25F, 0.25F, 0.25F, 0.25F}, out, 1.0E-6F);

        assertThrows(IllegalArgumentException.class, () -> curve.sample(0.25F, new float[3]));
    }

    @Test
    void rejectsTypeMismatchesAndInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> EffectCurve.scalar()
            .keyframe(0.0F, EffectCurve.Value.vec3(1.0F, 2.0F, 3.0F), EffectEasing.LINEAR)
            .build());
        assertThrows(IllegalArgumentException.class, () -> EffectCurve.Value.scalar(Float.NaN));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectCurve.Value(EffectCurve.ValueType.FLOAT, 1.0F, 1.0F, 0.0F, 0.0F));
        assertThrows(IllegalArgumentException.class, () -> EffectCurve.scalar()
            .keyframe(Float.NaN, 1.0F, EffectEasing.LINEAR));
    }

    @Test
    void enforcesTheKeyframeLimit() {
        EffectCurve.Builder builder = EffectCurve.scalar();

        assertThrows(IllegalArgumentException.class, () -> {
            for (int index = 0; index <= EffectCurve.MAX_KEYFRAMES; index++) {
                float progress = index / (float)(EffectCurve.MAX_KEYFRAMES + 1);
                builder.keyframe(progress, index, EffectEasing.LINEAR);
            }
        });
    }

    @Test
    void exposesImmutableKeyframes() {
        EffectCurve curve = EffectCurve.scalar()
            .keyframe(0.0F, 0.0F, EffectEasing.LINEAR)
            .build();

        assertThrows(UnsupportedOperationException.class,
            () -> curve.keyframes().add(new EffectCurve.Keyframe(1.0F,
                EffectCurve.Value.scalar(1.0F), EffectEasing.LINEAR)));
        assertEquals(List.of(curve.keyframes().getFirst()), curve.keyframes());
    }
}
