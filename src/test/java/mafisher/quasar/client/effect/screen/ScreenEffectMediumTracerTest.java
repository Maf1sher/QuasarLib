package mafisher.quasar.client.effect.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.client.ScreenEffectMedium;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * Headless checks for the camera-to-effect medium path. Endpoint sampling missed
 * a lake between two air points and mis-ordered an air pocket between two water
 * points; the tracer must report the whole ordered path.
 */
class ScreenEffectMediumTracerTest {
    private static final double WATER_TOP = 6.0;
    private static final double TOLERANCE = 0.02;

    @Test
    void dryPathHasNoCrossing() {
        var path = ScreenEffectMediumTracer.trace(
            new Vector3d(0.0, 10.0, 0.0), new Vector3d(0.0, 10.0, 100.0),
            waterBelow(WATER_TOP));

        assertEquals(100.0, path.length(), 1.0E-6);
        assertEquals(0.0, path.submergedLength(), 1.0E-6);
        assertTrue(path.crossings().isEmpty());
        assertFalse(path.hasWaterCrossing());
    }

    @Test
    void lakeBetweenDryEndpointsIsTracked() {
        var path = ScreenEffectMediumTracer.trace(
            new Vector3d(-20.0, 0.0, 0.0), new Vector3d(20.0, 0.0, 0.0),
            lakeBetween(-5.0, 5.0, WATER_TOP));

        assertEquals(40.0, path.length(), 1.0E-6);
        assertEquals(10.0, path.submergedLength(), TOLERANCE);
        assertEquals(2, path.crossings().size());
        var entry = path.firstWaterCrossing().orElseThrow();
        assertEquals(15.0, entry.distance(), TOLERANCE);
        assertEquals(ScreenEffectMedium.NONE, entry.from());
        assertEquals(ScreenEffectMedium.WATER, entry.to());
        assertEquals(WATER_TOP, entry.surfaceY(), 1.0E-6);
        assertFalse(entry.topSurface(),
            "an elevated lake is entered through its side, not through the top");
        assertEquals(ScreenEffectMedium.NONE, path.mediumAt(10.0));
        assertEquals(ScreenEffectMedium.WATER, path.mediumAt(20.0));
        assertEquals(ScreenEffectMedium.NONE, path.mediumAt(30.0));
    }

    @Test
    void submergedCameraExitsAtTheSurface() {
        var path = ScreenEffectMediumTracer.trace(
            new Vector3d(0.0, 2.0, 0.0), new Vector3d(0.0, 10.0, 0.0),
            waterBelow(WATER_TOP));

        assertEquals(ScreenEffectMedium.WATER, path.mediumAt(0.0));
        assertEquals(4.0, path.submergedLength(), TOLERANCE);
        var crossing = path.firstWaterCrossing().orElseThrow();
        assertEquals(4.0, crossing.distance(), TOLERANCE);
        assertEquals(ScreenEffectMedium.WATER, crossing.from());
        assertEquals(ScreenEffectMedium.NONE, crossing.to());
        assertEquals(WATER_TOP, crossing.surfaceY(), 1.0E-6);
        assertTrue(crossing.topSurface(), "the submerged camera exits through the top");
    }

    @Test
    void submergedEffectEntersAtTheSurface() {
        var path = ScreenEffectMediumTracer.trace(
            new Vector3d(0.0, 10.0, 0.0), new Vector3d(0.0, 2.0, 0.0),
            waterBelow(WATER_TOP));

        assertEquals(4.0, path.submergedLength(), TOLERANCE);
        var crossing = path.firstWaterCrossing().orElseThrow();
        assertEquals(4.0, crossing.distance(), TOLERANCE);
        assertEquals(ScreenEffectMedium.NONE, crossing.from());
        assertEquals(ScreenEffectMedium.WATER, crossing.to());
        assertEquals(WATER_TOP, crossing.surfaceY(), 1.0E-6);
        assertTrue(crossing.topSurface(), "the effect is entered through the top");
    }

    @Test
    void tankSideEntryIsNotATopSurface() {
        ScreenEffectMediumSampler tank = new ScreenEffectMediumSampler() {
            @Override
            public ScreenEffectMedium mediumAt(double x, double y, double z) {
                return x > 5.0 && x < 15.0 && y < WATER_TOP
                    ? ScreenEffectMedium.WATER : ScreenEffectMedium.NONE;
            }

            @Override
            public float surfaceY(double x, double y, double z) {
                return x > 5.0 && x < 15.0 && y < WATER_TOP
                    ? (float)WATER_TOP : Float.NaN;
            }
        };

        var path = ScreenEffectMediumTracer.trace(
            new Vector3d(0.0, 3.0, 0.0), new Vector3d(20.0, 3.0, 0.0), tank);

        var crossing = path.firstWaterCrossing().orElseThrow();
        assertFalse(crossing.topSurface(),
            "entering through the glass side must not use the water-top plane");
        assertTrue(path.firstOpenWaterCrossing().isEmpty());
        assertEquals(10.0, path.submergedLength(), TOLERANCE);
    }

    @Test
    void multipleLakesSumTheirSubmergedLength() {
        var path = ScreenEffectMediumTracer.trace(
            new Vector3d(-40.0, 0.0, 0.0), new Vector3d(40.0, 0.0, 0.0),
            lakeBetween(-20.0, -10.0, WATER_TOP, 10.0, 20.0));

        assertEquals(20.0, path.submergedLength(), TOLERANCE);
        assertEquals(4, path.crossings().size());
        assertEquals(20.0, path.firstWaterCrossing().orElseThrow().distance(), TOLERANCE);
    }

    @Test
    void lavaCountsForFogButIsNotARefractionSurface() {
        var path = ScreenEffectMediumTracer.trace(
            new Vector3d(0.0, 10.0, 0.0), new Vector3d(0.0, 0.0, 0.0),
            new ScreenEffectMediumSampler() {
                @Override
                public ScreenEffectMedium mediumAt(double x, double y, double z) {
                    return y < 5.0 ? ScreenEffectMedium.LAVA : ScreenEffectMedium.NONE;
                }
            });

        assertEquals(5.0, path.submergedLength(), TOLERANCE);
        assertFalse(path.hasWaterCrossing());
    }

    @Test
    void zeroLengthPathIsEmpty() {
        assertTrue(ScreenEffectMediumTracer.trace(
            new Vector3d(1.0, 2.0, 3.0), new Vector3d(1.0, 2.0, 3.0),
            waterBelow(WATER_TOP)).isEmpty());
    }

    @Test
    void samplingStaysWithinBudget() {
        int[] calls = {0};
        ScreenEffectMediumSampler counting = (x, y, z) -> {
            calls[0]++;
            return ScreenEffectMedium.NONE;
        };

        var path = ScreenEffectMediumTracer.trace(
            new Vector3d(0.0, 100.0, 0.0), new Vector3d(0.0, 100.0, 1_000_000.0),
            counting);

        assertEquals(1_000_000.0, path.length(), 1.0E-3);
        assertEquals(ScreenEffectMediumTracer.MAX_SAMPLES + 2, calls[0]);
    }

    @Test
    void crossingIsRefinedBetweenSamples() {
        var path = ScreenEffectMediumTracer.trace(
            new Vector3d(0.0, 0.0, 0.0), new Vector3d(0.0, 100.0, 0.0),
            waterBelow(50.0));

        var crossing = path.firstWaterCrossing().orElseThrow();
        assertEquals(50.0, crossing.distance(), 0.02);
        assertEquals(50.0, path.submergedLength(), 0.02);
    }

    @Test
    void surfaceHeightIsOnlyQueriedAtCrossings() {
        int[] surfaceCalls = {0};
        ScreenEffectMediumSampler submerged = new ScreenEffectMediumSampler() {
            @Override
            public ScreenEffectMedium mediumAt(double x, double y, double z) {
                return ScreenEffectMedium.WATER;
            }

            @Override
            public float surfaceY(double x, double y, double z) {
                surfaceCalls[0]++;
                return (float)WATER_TOP;
            }
        };

        var path = ScreenEffectMediumTracer.trace(
            new Vector3d(0.0, 2.0, 0.0), new Vector3d(0.0, 4.0, 0.0), submerged);

        assertEquals(0, surfaceCalls[0]);
        assertEquals(2.0, path.submergedLength(), 1.0E-9);
        assertFalse(path.hasWaterCrossing());
    }

    private static ScreenEffectMediumSampler waterBelow(double surfaceY) {
        return new ScreenEffectMediumSampler() {
            @Override
            public ScreenEffectMedium mediumAt(double x, double y, double z) {
                return y < surfaceY ? ScreenEffectMedium.WATER : ScreenEffectMedium.NONE;
            }

            @Override
            public float surfaceY(double x, double y, double z) {
                return y < surfaceY ? (float)surfaceY : Float.NaN;
            }
        };
    }

    private static ScreenEffectMediumSampler lakeBetween(double firstStart, double firstEnd,
        double surfaceY, double... moreRanges) {
        return new ScreenEffectMediumSampler() {
            @Override
            public ScreenEffectMedium mediumAt(double x, double y, double z) {
                if (y < surfaceY && inRange(firstStart, firstEnd, x)) {
                    return ScreenEffectMedium.WATER;
                }
                for (int i = 0; i + 1 < moreRanges.length; i += 2) {
                    if (y < surfaceY && inRange(moreRanges[i], moreRanges[i + 1], x)) {
                        return ScreenEffectMedium.WATER;
                    }
                }
                return ScreenEffectMedium.NONE;
            }

            @Override
            public float surfaceY(double x, double y, double z) {
                return y < surfaceY ? (float)surfaceY : Float.NaN;
            }
        };
    }

    private static boolean inRange(double start, double end, double value) {
        return value > start && value < end;
    }
}
