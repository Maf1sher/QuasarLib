package mafisher.quasar.content;

import java.util.Objects;
import org.joml.Vector3d;

/**
 * Pure CPU mirror of the content black-hole null-geodesic integration. The shader
 * uses the same acceleration and step rules; this class exists so shadow,
 * deflection, disk-crossing, and close-range behaviour can be tested headlessly.
 */
final class ContentBlackHoleGeodesic {
    /** Critical impact parameter for a Schwarzschild photon: 3 * sqrt(3) / 2. */
    static final double B_CRITICAL = 3.0 * Math.sqrt(3.0) / 2.0;
    private static final double HORIZON_EPSILON = 1.02;

    private ContentBlackHoleGeodesic() {
    }

    static Trace trace(Vector3d cameraRelative, Vector3d direction, double horizonRadius, int maxSteps) {
        return integrate(cameraRelative, direction, horizonRadius, maxSteps, null).trace();
    }

    static DiskTrace traceWithDisk(Vector3d cameraRelative, Vector3d direction, double horizonRadius,
        Vector3d diskNormal, double diskInnerRadius, double diskOuterRadius, int maxSteps) {
        Objects.requireNonNull(diskNormal, "diskNormal");
        if (!(diskOuterRadius > diskInnerRadius) || diskInnerRadius < 0.0) {
            throw new IllegalArgumentException("Invalid disk radii");
        }
        Integration integration = integrate(cameraRelative, direction, horizonRadius, maxSteps,
            new Disk(new Vector3d(diskNormal).normalize(), diskInnerRadius, diskOuterRadius));
        return new DiskTrace(integration.trace(), integration.crossings(),
            integration.firstDiskRadius(), integration.lastDiskRadius());
    }

    private static Integration integrate(Vector3d cameraRelative, Vector3d direction, double horizonRadius,
        int maxSteps, Disk disk) {
        Objects.requireNonNull(cameraRelative, "cameraRelative");
        Objects.requireNonNull(direction, "direction");
        if (!(horizonRadius > 0.0) || !Double.isFinite(horizonRadius)) {
            throw new IllegalArgumentException("Horizon radius must be finite and positive");
        }
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("Step budget must be positive");
        }

        Vector3d position = new Vector3d(cameraRelative);
        Vector3d velocity = new Vector3d(direction).normalize();
        Vector3d angularMomentum = new Vector3d(position).cross(velocity);
        double impact = angularMomentum.length();
        double h2 = impact * impact;
        double escapeRadius = Math.max(48.0 * horizonRadius, position.length() * 1.25);
        double closestApproach = position.length();
        boolean captured = false;
        int steps = 0;
        int crossings = 0;
        double firstDiskRadius = Double.NaN;
        double lastDiskRadius = Double.NaN;

        for (int stepIndex = 0; stepIndex < maxSteps; stepIndex++) {
            double radius = position.length();
            if (radius <= horizonRadius * HORIZON_EPSILON) {
                captured = true;
                break;
            }
            if (radius > escapeRadius && position.dot(velocity) > 0.0) {
                break;
            }

            double dt = stepLength(radius, horizonRadius) / Math.max(velocity.length(), 0.25);
            Vector3d a1 = acceleration(position, h2, horizonRadius);
            Vector3d v2 = new Vector3d(velocity).fma(0.5 * dt, a1);
            Vector3d p2 = new Vector3d(position).fma(0.5 * dt, velocity);
            Vector3d a2 = acceleration(p2, h2, horizonRadius);
            Vector3d v3 = new Vector3d(velocity).fma(0.5 * dt, a2);
            Vector3d p3 = new Vector3d(position).fma(0.5 * dt, v2);
            Vector3d a3 = acceleration(p3, h2, horizonRadius);
            Vector3d v4 = new Vector3d(velocity).fma(dt, a3);
            Vector3d p4 = new Vector3d(position).fma(dt, v3);
            Vector3d a4 = acceleration(p4, h2, horizonRadius);

            Vector3d nextPosition = new Vector3d(position).fma(dt / 6.0,
                new Vector3d(velocity).add(v2).add(v2).add(v3).add(v3).add(v4));
            Vector3d nextVelocity = new Vector3d(velocity).fma(dt / 6.0,
                new Vector3d(a1).add(a2).add(a2).add(a3).add(a3).add(a4));

            if (disk != null) {
                double before = position.dot(disk.normal);
                double after = nextPosition.dot(disk.normal);
                if (before * after <= 0.0 && Math.abs(before - after) > 1.0E-9) {
                    double t = before / (before - after);
                    Vector3d hit = new Vector3d(position).lerp(nextPosition, t);
                    Vector3d radial = new Vector3d(hit)
                        .fma(-hit.dot(disk.normal), disk.normal);
                    double radialLength = radial.length();
                    if (radialLength >= disk.inner && radialLength <= disk.outer) {
                        if (crossings == 0) {
                            firstDiskRadius = radialLength;
                        }
                        lastDiskRadius = radialLength;
                        crossings++;
                    }
                }
            }

            position.set(nextPosition);
            velocity.set(nextVelocity);
            closestApproach = Math.min(closestApproach, position.length());
            steps = stepIndex + 1;
        }

        if (position.length() <= horizonRadius * HORIZON_EPSILON) {
            captured = true;
        }
        Vector3d outgoing = captured ? new Vector3d(velocity) : new Vector3d(velocity).normalize();
        Trace trace = new Trace(captured, outgoing, impact, closestApproach, steps);
        return new Integration(trace, crossings, firstDiskRadius, lastDiskRadius);
    }

    /**
     * Per-pixel visibility rule mirrored by the shader: the scene must be at least
     * as far as the effect surface at that pixel (ring, disk, or lens shell).
     */
    static boolean isVisibleAtDepth(double sceneDistance, double effectEntry, double depthBias) {
        return sceneDistance >= Math.max(0.0, effectEntry) - depthBias;
    }

    private static double stepLength(double radius, double horizonRadius) {
        if (radius < 6.0 * horizonRadius) {
            return clamp(0.045 * radius, 0.008 * horizonRadius, 0.20 * horizonRadius);
        }
        return clamp(0.10 * radius, 0.02 * horizonRadius, 1.5 * horizonRadius);
    }

    private static Vector3d acceleration(Vector3d position, double h2, double horizonRadius) {
        double r2 = position.lengthSquared();
        double r = Math.sqrt(r2);
        double scale = -1.5 * horizonRadius * h2 / Math.max(r2 * r2 * r, 1.0E-12);
        return new Vector3d(position).mul(scale);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record Trace(boolean captured, Vector3d outgoingDirection, double impact, double closestApproach, int steps) {
        Trace {
            Objects.requireNonNull(outgoingDirection, "outgoingDirection");
            outgoingDirection = new Vector3d(outgoingDirection);
        }

        @Override
        public Vector3d outgoingDirection() {
            return new Vector3d(this.outgoingDirection);
        }
    }

    record DiskTrace(Trace trace, int crossings, double firstDiskRadius, double lastDiskRadius) {
        DiskTrace {
            Objects.requireNonNull(trace, "trace");
        }
    }

    private record Integration(Trace trace, int crossings, double firstDiskRadius, double lastDiskRadius) {
    }

    private record Disk(Vector3d normal, double inner, double outer) {
    }
}
