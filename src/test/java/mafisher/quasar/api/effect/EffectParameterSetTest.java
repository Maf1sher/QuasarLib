package mafisher.quasar.api.effect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import mafisher.quasar.api.gameplay.EffectPhase;
import mafisher.quasar.api.gameplay.EffectPhasePlan;
import org.junit.jupiter.api.Test;

class EffectParameterSetTest {
    @Test
    void samplesCurvesAndDefaults() {
        EffectParameterSet set = parameters();

        assertEquals(5.0F, set.sampleFloat("horizon", 0.25F), 1.0E-6F);
        assertEquals(0.5F, set.sampleFloat("charge", 0.25F), 0.0F);
        assertEquals(0.5F, set.anchor("outro"), 0.0F);
        assertEquals(4, set.size());
    }

    @Test
    void typedOverridesTakePrecedenceAndMismatchedOverridesAreIgnored() {
        EffectParameterSet set = parameters();
        EffectParameterState state = EffectParameterState.builder()
            .putFloat("horizon", 42.0F)
            .put("charge", EffectCurve.Value.vec3(1.0F, 2.0F, 3.0F))
            .build();

        assertEquals(42.0F, set.sampleFloat("horizon", 0.25F, state), 0.0F);
        assertEquals(0.5F, set.sampleFloat("charge", 0.25F, state), 0.0F);
    }

    @Test
    void samplesVectorsIntoAReusableBuffer() {
        EffectParameterSet set = parameters();
        float[] out = new float[3];

        set.sample("axis", 0.5F, out);

        assertArrayEquals(new float[] {1.0F, 0.5F, 0.0F}, out, 1.0E-6F);
        assertThrows(IllegalArgumentException.class, () -> set.sample("axis", 0.5F, new float[2]));
        assertThrows(IllegalStateException.class, () -> set.sampleFloat("axis", 0.5F));
    }

    @Test
    void derivesAnchorsFromPhaseTiming() {
        EffectPhasePlan phases = EffectPhasePlan.of(
            new EffectPhase("intro", 10),
            new EffectPhase("steady", 30)
        );

        EffectParameterSet set = EffectParameterSet.builder()
            .anchors(phases)
            .scalar("value", 0.0F)
            .build();

        assertEquals(0.0F, set.anchor("intro"), 0.0F);
        assertEquals(0.25F, set.anchor("steady"), 0.0F);
    }

    @Test
    void rejectsDuplicateNamesAndUnknownAnchors() {
        assertThrows(IllegalArgumentException.class, () -> EffectParameterSet.builder()
            .anchor("phase", 0.0F)
            .anchor("phase", 0.5F));
        assertThrows(IllegalArgumentException.class, () -> EffectParameterSet.builder()
            .scalar("value", 0.0F)
            .scalar("value", 1.0F));
        assertThrows(IllegalArgumentException.class, () -> EffectParameterSet.builder()
            .curve("value", EffectCurve.scalar()
                .keyframe("missing", 0.0F, 1.0F, EffectEasing.LINEAR))
            .build());
    }

    @Test
    void rejectsAnEmptySetAndInvalidLookups() {
        assertThrows(IllegalStateException.class, () -> EffectParameterSet.builder().build());

        EffectParameterSet set = parameters();
        assertThrows(IllegalArgumentException.class, () -> set.parameter("missing"));
        assertThrows(IllegalArgumentException.class, () -> set.anchor("missing"));
        assertThrows(NullPointerException.class, () -> set.anchorOrNull(null));
    }

    @Test
    void exposesAnImmutableParameterListAndAnchors() {
        EffectParameterSet set = parameters();

        assertThrows(UnsupportedOperationException.class,
            () -> set.parameters().add(set.parameters().getFirst()));
        assertThrows(UnsupportedOperationException.class,
            () -> set.anchors().add(new EffectPhaseAnchor("extra", 0.5F)));
    }

    private static EffectParameterSet parameters() {
        return EffectParameterSet.builder()
            .anchor("intro", 0.0F)
            .anchor("outro", 0.5F)
            .curve("horizon", EffectCurve.scalar()
                .keyframe("intro", 0.0F, 0.0F, EffectEasing.LINEAR)
                .keyframe("outro", 0.0F, 10.0F, EffectEasing.LINEAR))
            .scalar("charge", 0.5F)
            .curve("axis", EffectCurve.vec3()
                .keyframe(0.0F, EffectCurve.Value.vec3(1.0F, 0.0F, 0.0F), EffectEasing.LINEAR)
                .keyframe(1.0F, EffectCurve.Value.vec3(1.0F, 1.0F, 0.0F), EffectEasing.LINEAR))
            .color("tint", 1.0F, 1.0F, 1.0F, 1.0F)
            .build();
    }
}
