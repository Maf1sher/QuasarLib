package mafisher.quasar.client.effect.inspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InspectGeometryTest {
    @Test
    void boxEmitsTwelveFiniteEdges() {
        List<double[]> lines = new ArrayList<>();

        InspectGeometry.box(collect(lines), point(0, 0, 0), point(2, 3, 4));

        assertEquals(12, lines.size());
        assertTrue(lines.stream().flatMapToDouble(line -> java.util.Arrays.stream(line))
            .allMatch(Double::isFinite));
    }

    @Test
    void sphereUsesThreeIndependentCircles() {
        List<double[]> lines = new ArrayList<>();

        InspectGeometry.sphere(collect(lines), point(1, 2, 3), 4.0);

        assertEquals(3 * 24, lines.size());
    }

    @Test
    void cylinderBeamAndConeStayBounded() {
        List<double[]> lines = new ArrayList<>();
        InspectGeometry.LineSink sink = collect(lines);

        InspectGeometry.cylinder(sink, point(0, 0, 0), 2.0, 5.0);
        int cylinder = lines.size();
        InspectGeometry.beam(sink, point(0, 0, 0), point(0, 0, 8), 1.5);
        int beam = lines.size() - cylinder;
        InspectGeometry.cone(sink, point(0, 0, 0), point(0, 0, 1), 10.0, 30.0F, 60.0F);
        int cone = lines.size() - cylinder - beam;

        assertTrue(cylinder > 0);
        assertTrue(beam > 0);
        assertTrue(cone > 0);
        assertTrue(lines.size() < 200);
    }

    @Test
    void degenerateInputsEmitNothing() {
        List<double[]> lines = new ArrayList<>();
        InspectGeometry.LineSink sink = collect(lines);

        InspectGeometry.sphere(sink, point(0, 0, 0), 0.0);
        InspectGeometry.cylinder(sink, point(0, 0, 0), -2.0, 1.0);
        InspectGeometry.beam(sink, point(0, 0, 0), point(0, 0, 0), 1.0);
        InspectGeometry.cone(sink, point(0, 0, 0), point(0, 0, 0), 4.0, 10.0F, 20.0F);
        InspectGeometry.circle(sink, point(0, 0, 0), point(0, 0, 0), 2.0);

        assertTrue(lines.isEmpty());
    }

    @Test
    void circleClosesItsLoop() {
        List<double[]> lines = new ArrayList<>();

        InspectGeometry.circle(collect(lines), point(0, 0, 0), point(0, 1, 0), 3.0);

        assertEquals(24, lines.size());
        double[] last = lines.getLast();
        assertEquals(lines.getFirst()[0], last[3], 1.0E-9);
        assertEquals(lines.getFirst()[1], last[4], 1.0E-9);
        assertEquals(lines.getFirst()[2], last[5], 1.0E-9);
    }

    @Test
    void pointRejectsNonFiniteCoordinates() {
        boolean rejected = false;
        try {
            new InspectGeometry.Point(Double.NaN, 0.0, 0.0);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected);
    }

    private static InspectGeometry.LineSink collect(List<double[]> lines) {
        return (x1, y1, z1, x2, y2, z2) -> lines.add(
            new double[] {x1, y1, z1, x2, y2, z2}
        );
    }

    private static InspectGeometry.Point point(double x, double y, double z) {
        return new InspectGeometry.Point(x, y, z);
    }
}
