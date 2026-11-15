package mafisher.quasar.client.effect.inspect;

import org.jetbrains.annotations.ApiStatus;

/**
 * Pure wireframe geometry for inspector gizmos. Every method emits independent line segments
 * through {@link LineSink}; no Minecraft or OpenGL types are used, so the shapes and their caps are
 * covered by headless tests.
 */
@ApiStatus.Internal
public final class InspectGeometry {
    private static final int CIRCLE_SEGMENTS = 24;
    private static final double MIN_LENGTH = 1.0E-6;

    private InspectGeometry() {
    }

    /** Receives one independent line segment. */
    @FunctionalInterface
    public interface LineSink {
        void line(double x1, double y1, double z1, double x2, double y2, double z2);
    }

    /** One finite point in world space. */
    public record Point(double x, double y, double z) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Inspect point must be finite");
            }
        }

        Point add(Point other, double scale) {
            return new Point(this.x + other.x * scale, this.y + other.y * scale, this.z + other.z * scale);
        }

        Point subtract(Point other) {
            return new Point(this.x - other.x, this.y - other.y, this.z - other.z);
        }

        Point cross(Point other) {
            return new Point(
                this.y * other.z - this.z * other.y,
                this.z * other.x - this.x * other.z,
                this.x * other.y - this.y * other.x
            );
        }

        double length() {
            return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        }

        Point normalize() {
            double length = this.length();
            if (length < MIN_LENGTH) {
                return null;
            }
            return new Point(this.x / length, this.y / length, this.z / length);
        }
    }

    public static void box(LineSink sink, Point min, Point max) {
        requireSink(sink);
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            return;
        }
        Point[] corners = {
            new Point(min.x(), min.y(), min.z()),
            new Point(max.x(), min.y(), min.z()),
            new Point(max.x(), min.y(), max.z()),
            new Point(min.x(), min.y(), max.z()),
            new Point(min.x(), max.y(), min.z()),
            new Point(max.x(), max.y(), min.z()),
            new Point(max.x(), max.y(), max.z()),
            new Point(min.x(), max.y(), max.z())
        };
        int[][] edges = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] edge : edges) {
            line(sink, corners[edge[0]], corners[edge[1]]);
        }
    }

    public static void sphere(LineSink sink, Point center, double radius) {
        requireSink(sink);
        if (!positive(radius)) {
            return;
        }
        circle(sink, center, new Point(1.0, 0.0, 0.0), radius);
        circle(sink, center, new Point(0.0, 1.0, 0.0), radius);
        circle(sink, center, new Point(0.0, 0.0, 1.0), radius);
    }

    public static void cylinder(LineSink sink, Point base, double radius, double height) {
        requireSink(sink);
        if (!positive(radius) || !positive(height)) {
            return;
        }
        Point top = new Point(base.x(), base.y() + height, base.z());
        circle(sink, base, new Point(0.0, 1.0, 0.0), radius);
        circle(sink, top, new Point(0.0, 1.0, 0.0), radius);
        for (int index = 0; index < 4; index++) {
            double angle = index * Math.PI / 2.0;
            Point offset = new Point(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            line(sink, base.add(offset, 1.0), top.add(offset, 1.0));
        }
    }

    public static void beam(LineSink sink, Point start, Point end, double radius) {
        requireSink(sink);
        Point direction = end.subtract(start).normalize();
        if (direction == null) {
            return;
        }
        line(sink, start, end);
        if (positive(radius)) {
            circle(sink, start, direction, radius);
            circle(sink, end, direction, radius);
        }
    }

    /**
     * Draws two spherical-cap cones around {@code direction}. {@code length} is the visual cap
     * radius, so the shape stays bounded for every angle up to 180 degrees.
     */
    public static void cone(LineSink sink, Point apex, Point direction, double length,
        float innerDegrees, float outerDegrees) {
        requireSink(sink);
        Point axis = direction.normalize();
        if (axis == null || !positive(length)) {
            return;
        }
        line(sink, apex, apex.add(axis, length));
        drawConeRing(sink, apex, axis, length, innerDegrees);
        if (outerDegrees != innerDegrees) {
            drawConeRing(sink, apex, axis, length, outerDegrees);
        }
    }

    public static void circle(LineSink sink, Point center, Point normal, double radius) {
        requireSink(sink);
        Point axis = normal.normalize();
        if (axis == null || !positive(radius)) {
            return;
        }
        Point helper = Math.abs(axis.x()) < 0.9
            ? new Point(1.0, 0.0, 0.0) : new Point(0.0, 1.0, 0.0);
        Point u = axis.cross(helper).normalize();
        if (u == null) {
            return;
        }
        Point v = axis.cross(u).normalize();
        if (v == null) {
            return;
        }
        Point previous = null;
        for (int index = 0; index <= CIRCLE_SEGMENTS; index++) {
            double angle = index * Math.PI * 2.0 / CIRCLE_SEGMENTS;
            Point point = center.add(u, Math.cos(angle) * radius).add(v, Math.sin(angle) * radius);
            if (previous != null) {
                line(sink, previous, point);
            }
            previous = point;
        }
    }

    private static void drawConeRing(LineSink sink, Point apex, Point axis, double length,
        float degrees) {
        if (!Float.isFinite(degrees) || degrees <= 0.0F) {
            return;
        }
        double angle = Math.toRadians(Math.min(180.0F, degrees));
        double radius = Math.sin(angle) * length;
        if (!positive(radius)) {
            return;
        }
        Point center = apex.add(axis, Math.cos(angle) * length);
        circle(sink, center, axis, radius);
        Point helper = Math.abs(axis.x()) < 0.9
            ? new Point(1.0, 0.0, 0.0) : new Point(0.0, 1.0, 0.0);
        Point u = axis.cross(helper).normalize();
        if (u == null) {
            return;
        }
        Point v = axis.cross(u).normalize();
        if (v == null) {
            return;
        }
        line(sink, apex, center.add(u, radius));
        line(sink, apex, center.add(v, radius));
        line(sink, apex, center.add(u, -radius));
        line(sink, apex, center.add(v, -radius));
    }

    private static void line(LineSink sink, Point start, Point end) {
        sink.line(start.x(), start.y(), start.z(), end.x(), end.y(), end.z());
    }

    private static boolean positive(double value) {
        return Double.isFinite(value) && value > MIN_LENGTH;
    }

    private static void requireSink(LineSink sink) {
        if (sink == null) {
            throw new NullPointerException("sink");
        }
    }
}
