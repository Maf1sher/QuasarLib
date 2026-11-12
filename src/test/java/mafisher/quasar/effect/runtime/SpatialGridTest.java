package mafisher.quasar.effect.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SpatialGridTest {
    @Test
    void visitsOnlyValuesInsideTheInclusiveRadius() {
        SpatialGrid<Point> grid = new SpatialGrid<>(Point::position);
        grid.add(new Point("center", new Vec3(0.0, 0.0, 0.0)));
        grid.add(new Point("edge", new Vec3(10.0, 0.0, 0.0)));
        grid.add(new Point("outside", new Vec3(10.1, 0.0, 0.0)));
        grid.add(new Point("vertical", new Vec3(0.0, 10.0, 0.0)));

        List<String> visited = new ArrayList<>();
        grid.forEachNear(Vec3.ZERO, 10.0, point -> visited.add(point.id()));

        assertEquals(Set.of("center", "edge", "vertical"), Set.copyOf(visited));
        assertEquals(3, visited.size());
    }

    @Test
    void handlesNegativeCoordinatesAndCellBoundaries() {
        SpatialGrid<Point> grid = new SpatialGrid<>(Point::position);
        grid.add(new Point("left", new Vec3(-63.9, 0.0, -63.9)));
        grid.add(new Point("right", new Vec3(-64.1, 0.0, -64.1)));
        grid.add(new Point("far", new Vec3(-200.0, 0.0, 200.0)));

        List<String> visited = new ArrayList<>();
        grid.forEachNear(new Vec3(-64.0, 0.0, -64.0), 1.0, point -> visited.add(point.id()));

        assertEquals(Set.of("left", "right"), Set.copyOf(visited));
        assertEquals(2, visited.size());
    }

    @Test
    void zeroRadiusVisitsOnlyTheExactPosition() {
        SpatialGrid<Point> grid = new SpatialGrid<>(Point::position);
        grid.add(new Point("here", new Vec3(5.0, 2.0, 5.0)));
        grid.add(new Point("near", new Vec3(5.0, 2.5, 5.0)));

        List<String> visited = new ArrayList<>();
        grid.forEachNear(new Vec3(5.0, 2.0, 5.0), 0.0, point -> visited.add(point.id()));

        assertEquals(List.of("here"), visited);
    }

    @Test
    void eachValueIsVisitedOnceAcrossCells() {
        SpatialGrid<Point> grid = new SpatialGrid<>(Point::position);
        for (int index = 0; index < 50; index++) {
            grid.add(new Point("p" + index, new Vec3(index * 7.0, 0.0, index * 3.0)));
        }

        List<String> visited = new ArrayList<>();
        grid.forEachNear(new Vec3(100.0, 0.0, 100.0), 512.0, point -> visited.add(point.id()));

        assertEquals(50, visited.size());
        assertEquals(50, visited.stream().distinct().count());
        assertEquals(50, grid.size());
    }

    @Test
    void emptyGridHasNoValues() {
        SpatialGrid<Point> grid = new SpatialGrid<>(Point::position);

        List<String> visited = new ArrayList<>();
        grid.forEachNear(Vec3.ZERO, 64.0, point -> visited.add(point.id()));

        assertTrue(visited.isEmpty());
        assertEquals(0, grid.size());
    }

    private record Point(String id, Vec3 position) {
    }
}
