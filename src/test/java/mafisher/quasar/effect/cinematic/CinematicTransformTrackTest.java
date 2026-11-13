package mafisher.quasar.effect.cinematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import mafisher.quasar.api.cinematic.CinematicKeyframe;
import mafisher.quasar.api.cinematic.CinematicTransformTrack;
import mafisher.quasar.api.effect.EffectEasing;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectTransform;
import org.junit.jupiter.api.Test;

class CinematicTransformTrackTest {
    private static final EffectTransform START = transform(0.0, 0.0F);
    private static final EffectTransform END = transform(10.0, 180.0F);

    @Test
    void clampsToTheNearestKeyframeOutsideTheTrack() {
        CinematicTransformTrack track = track(
            new CinematicKeyframe(10, START, EffectEasing.LINEAR),
            new CinematicKeyframe(20, END, EffectEasing.LINEAR)
        );

        assertEquals(START, track.sample(0));
        assertEquals(START, track.sample(10));
        assertEquals(END, track.sample(20));
        assertEquals(END, track.sample(200));
    }

    @Test
    void interpolatesPositionAndShortestAngle() {
        CinematicTransformTrack track = track(
            new CinematicKeyframe(0, START, EffectEasing.LINEAR),
            new CinematicKeyframe(10, END, EffectEasing.LINEAR)
        );

        EffectTransform halfway = track.sample(5);

        assertEquals(5.0, halfway.position().x(), 0.0001);
        assertEquals(-90.0F, halfway.yaw(), 0.0001F);
    }

    @Test
    void appliesTheEasingOfTheStartingKeyframe() {
        CinematicTransformTrack track = track(
            new CinematicKeyframe(0, transform(0.0, 0.0F), EffectEasing.EASE_OUT_CUBIC),
            new CinematicKeyframe(10, transform(10.0, 0.0F), EffectEasing.LINEAR)
        );

        double eased = track.sample(5).position().x();

        assertTrue(eased > 5.0, "ease-out should pass the linear midpoint");
        assertTrue(eased < 10.0, "ease-out must stay inside the segment");
    }

    @Test
    void sortsKeyframesAndRejectsDuplicateTicks() {
        CinematicTransformTrack track = track(
            new CinematicKeyframe(10, END, EffectEasing.LINEAR),
            new CinematicKeyframe(0, START, EffectEasing.LINEAR)
        );

        assertEquals(0, track.firstTick());
        assertEquals(10, track.lastTick());

        assertThrows(IllegalArgumentException.class, () -> track(
            new CinematicKeyframe(0, START, EffectEasing.LINEAR),
            new CinematicKeyframe(0, END, EffectEasing.LINEAR)
        ));
    }

    @Test
    void rejectsNegativeSamplingAndEmptyTracks() {
        CinematicTransformTrack track = track(
            new CinematicKeyframe(0, START, EffectEasing.LINEAR),
            new CinematicKeyframe(10, END, EffectEasing.LINEAR)
        );

        assertThrows(IllegalArgumentException.class, () -> track.sample(-1));
        assertThrows(IllegalArgumentException.class, () -> new CinematicTransformTrack("ring", List.of()));
    }

    private static CinematicTransformTrack track(CinematicKeyframe... keyframes) {
        return new CinematicTransformTrack("ring", List.of(keyframes));
    }

    private static EffectTransform transform(double x, float yaw) {
        return new EffectTransform(new EffectPoint(x, 64.0, 0.0), yaw, 0.0F, 0.0F);
    }
}
