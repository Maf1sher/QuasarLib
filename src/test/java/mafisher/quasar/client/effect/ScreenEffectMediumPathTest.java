package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import mafisher.quasar.api.client.ScreenEffectMedium;
import mafisher.quasar.api.client.ScreenEffectMediumCrossing;
import mafisher.quasar.api.client.ScreenEffectMediumPath;
import mafisher.quasar.api.client.ScreenEffectMediumSpan;
import org.junit.jupiter.api.Test;

class ScreenEffectMediumPathTest {
    @Test
    void emptyPathHasNoMedium() {
        assertTrue(ScreenEffectMediumPath.EMPTY.isEmpty());
        assertEquals(0.0, ScreenEffectMediumPath.EMPTY.length(), 0.0);
        assertEquals(0.0, ScreenEffectMediumPath.EMPTY.submergedLength(), 0.0);
        assertEquals(ScreenEffectMedium.NONE, ScreenEffectMediumPath.EMPTY.mediumAt(5.0));
        assertFalse(ScreenEffectMediumPath.EMPTY.hasWaterCrossing());
    }

    @Test
    void submergedLengthIgnoresAirSpans() {
        var path = new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 0.0, 20.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 20.0, 30.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 30.0, 100.0)
        ), List.of());

        assertEquals(10.0, path.submergedLength(), 1.0E-9);
        assertEquals(ScreenEffectMedium.NONE, path.mediumAt(10.0));
        assertEquals(ScreenEffectMedium.WATER, path.mediumAt(25.0));
        assertEquals(ScreenEffectMedium.NONE, path.mediumAt(50.0));
        assertEquals(100.0, path.length(), 1.0E-9);
    }

    @Test
    void firstWaterCrossingSkipsLava() {
        var path = new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 0.0, 4.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.LAVA, 4.0, 8.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 8.0, 12.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 12.0, 20.0)
        ), List.of(
            new ScreenEffectMediumCrossing(4.0, ScreenEffectMedium.NONE,
                ScreenEffectMedium.LAVA, Float.NaN, false),
            new ScreenEffectMediumCrossing(8.0, ScreenEffectMedium.LAVA,
                ScreenEffectMedium.WATER, 6.0F, true),
            new ScreenEffectMediumCrossing(12.0, ScreenEffectMedium.WATER,
                ScreenEffectMedium.NONE, 6.0F, true)
        ));

        assertEquals(8.0, path.firstWaterCrossing().orElseThrow().distance(), 1.0E-9);
        assertTrue(path.firstOpenWaterCrossing().isEmpty(),
            "a bounded water body has no single refraction plane");
    }

    @Test
    void openWaterCrossingRequiresTheWaterSpanToReachAnEndpoint() {
        var effectSubmerged = new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 0.0, 10.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 10.0, 40.0)
        ), List.of(
            new ScreenEffectMediumCrossing(10.0, ScreenEffectMedium.NONE,
                ScreenEffectMedium.WATER, 6.0F, true)
        ));
        var cameraSubmerged = new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 0.0, 10.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 10.0, 40.0)
        ), List.of(
            new ScreenEffectMediumCrossing(10.0, ScreenEffectMedium.WATER,
                ScreenEffectMedium.NONE, 6.0F, true)
        ));

        assertTrue(effectSubmerged.firstOpenWaterCrossing().isPresent());
        assertTrue(cameraSubmerged.firstOpenWaterCrossing().isPresent());
    }

    @Test
    void sideEntryWaterIsNotAPlane() {
        var path = new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 0.0, 10.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 10.0, 40.0)
        ), List.of(
            new ScreenEffectMediumCrossing(10.0, ScreenEffectMedium.NONE,
                ScreenEffectMedium.WATER, 6.0F, false)
        ));

        assertTrue(path.firstWaterCrossing().isPresent());
        assertTrue(path.firstOpenWaterCrossing().isEmpty());
    }

    @Test
    void rejectsAPathThatDoesNotStartAtTheCamera() {
        assertThrows(IllegalArgumentException.class, () -> new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 1.0, 5.0)
        ), List.of()));
    }

    @Test
    void rejectsOverlappingSpansAndOutsideCrossings() {
        assertThrows(IllegalArgumentException.class, () -> new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 0.0, 5.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 4.0, 9.0)
        ), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 0.0, 5.0)
        ), List.of(new ScreenEffectMediumCrossing(6.0, ScreenEffectMedium.WATER,
            ScreenEffectMedium.NONE, 6.0F, true))));
    }

    @Test
    void rejectsACrossingThatDoesNotChangeMedium() {
        assertThrows(IllegalArgumentException.class, () -> new ScreenEffectMediumCrossing(
            1.0, ScreenEffectMedium.WATER, ScreenEffectMedium.WATER, 6.0F, true));
    }
}
