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
            integration.firstDiskRadius(), integration.lastDiskRadius(),
            integration.nearestDiskEntry());
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
        double closestRadius = position.length();
        double nearestEntry = 0.0;
        boolean captured = false;
        int steps = 0;
        int crossings = 0;
        double firstDiskRadius = Double.NaN;
        double lastDiskRadius = Double.NaN;
        double nearestDiskEntry = Double.NaN;

        for (int stepIndex = 0; stepIndex < maxSteps; stepIndex++) {
            double radius = position.length();
            if (radius < closestRadius) {
                closestRadius = radius;
                nearestEntry = position.distance(cameraRelative);
            }
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
                        double entry = hit.distance(cameraRelative);
                        nearestDiskEntry = Double.isNaN(nearestDiskEntry)
                            ? entry : Math.min(nearestDiskEntry, entry);
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
        Trace trace = new Trace(captured, outgoing, impact, closestApproach, nearestEntry, steps);
        return new Integration(trace, crossings, firstDiskRadius, lastDiskRadius, nearestDiskEntry);
    }

    /**
     * Mirrors the water-plane refraction in {@code black_hole.fsh}: bends the view ray
     * at the surface before the effect is traced.
     *
     * <p>{@code origin} is the camera position relative to the black hole and
     * {@code surfaceY} is the water surface relative to the camera. The returned
     * origin is relative to the hole, so it can be fed straight into the geodesic
     * integration. Keeping those frames distinct avoids shifting the trace origin
     * by the camera-to-hole distance.</p>
     */
    static WaterRay refractedAtWater(Vector3d origin, Vector3d direction, double surfaceY,
        boolean surfaceActive, boolean cameraSubmerged, double ageTicks) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(direction, "direction");
        Vector3d straight = new Vector3d(direction).normalize();
        if (!surfaceActive || Math.abs(straight.y) < 0.001) {
            return WaterRay.straight(origin, straight);
        }
        double surfaceDistance = surfaceY / straight.y;
        if (surfaceDistance <= 0.0) {
            return WaterRay.straight(origin, straight);
        }
        Vector3d hit = new Vector3d(origin).fma(surfaceDistance, straight);
        // The hit is already relative to the hole; adding the hole offset again
        // would make the ripple phase slide with the camera.
        Vector3d normal = waterSurfaceNormal(hit.x, hit.z, ageTicks);
        if (normal.dot(straight) > 0.0) {
            normal.negate();
        }
        double incidence = clamp(Math.abs(straight.dot(normal)), 0.0, 1.0);
        double eta = cameraSubmerged ? 1.333 : 1.0 / 1.333;
        Vector3d bent = refract(straight, normal, eta);
        if (bent.lengthSquared() < 1.0E-6) {
            // Total internal reflection from below the surface: the above-water
            // effect is not visible in this direction. The caller hides it.
            return new WaterRay(origin, straight, surfaceDistance, normal, incidence, true);
        }
        return new WaterRay(hit, bent.normalize(), surfaceDistance, normal, incidence, false);
    }

    /** Mirrors {@code waterSurfaceNormal} in {@code black_hole.fsh}. */
    static Vector3d waterSurfaceNormal(double x, double z, double ageTicks) {
        double time = ageTicks * 0.05;
        double slopeX = Math.sin(x * 0.62 + time * 1.7)
            + 0.6 * Math.sin(x * 1.37 - z * 0.41 - time * 1.1)
            + 0.35 * Math.sin(z * 2.10 + time * 2.3);
        double slopeZ = Math.cos(z * 0.58 - time * 1.5)
            + 0.6 * Math.cos(z * 1.29 + x * 0.37 + time * 0.9)
            + 0.35 * Math.cos(x * 1.90 - time * 2.1);
        // Keep the synthetic ripples subtle; the shader uses the same 0.02 slope.
        return new Vector3d(slopeX * 0.02, 1.0, slopeZ * 0.02).normalize();
    }

    /** Mirrors {@code fresnelWater} in {@code black_hole.fsh}. */
    static double fresnelReflectance(double cosIncidence, double incidentIor, double transmittedIor) {
        double cosI = clamp(Math.abs(cosIncidence), 0.0, 1.0);
        double sinT2 = (incidentIor * incidentIor) / (transmittedIor * transmittedIor)
            * (1.0 - cosI * cosI);
        if (sinT2 >= 1.0) {
            return 1.0;
        }
        double cosT = Math.sqrt(1.0 - sinT2);
        double rs = (incidentIor * cosI - transmittedIor * cosT)
            / Math.max(incidentIor * cosI + transmittedIor * cosT, 1.0E-9);
        double rp = (incidentIor * cosT - transmittedIor * cosI)
            / Math.max(incidentIor * cosT + transmittedIor * cosI, 1.0E-9);
        return clamp(0.5 * (rs * rs + rp * rp), 0.0, 1.0);
    }

    /** Mirrors the composite's surface transmission: TIR is opaque, otherwise Fresnel. */
    static double surfaceTransmittance(WaterRay ray, boolean cameraSubmerged) {
        Objects.requireNonNull(ray, "ray");
        if (ray.surfaceDistance() <= 0.0) {
            return 1.0;
        }
        if (ray.totalInternalReflection()) {
            return 0.0;
        }
        double incidentIor = cameraSubmerged ? 1.333 : 1.0;
        double transmittedIor = cameraSubmerged ? 1.0 : 1.333;
        return 1.0 - fresnelReflectance(ray.incidenceCosine(), incidentIor, transmittedIor);
    }

    /**
     * Mirrors the composite's fog distance: only the submerged leg receives the
     * active water fog.
     */
    static double fogDistance(WaterRay ray, double entryDistance, double effectEntry,
        boolean cameraSubmerged) {
        Objects.requireNonNull(ray, "ray");
        if (ray.surfaceDistance() > 0.0) {
            return cameraSubmerged ? ray.surfaceDistance() : Math.max(effectEntry, 0.0);
        }
        return Math.max(entryDistance, 0.0);
    }

    private static Vector3d refract(Vector3d incident, Vector3d normal, double eta) {
        double dot = incident.dot(normal);
        double k = 1.0 - eta * eta * (1.0 - dot * dot);
        if (k < 0.0) {
            return new Vector3d();
        }
        return new Vector3d(incident).mul(eta).fma(-(eta * dot + Math.sqrt(k)), normal);
    }

    /**
     * Per-pixel visibility rule mirrored by the shader: the scene must be at least
     * as far as the nearest traced surface at that pixel (closest approach or the
     * first emitting disk crossing). Bounding-volume entries are deliberately not
     * used because they sit in front of the real surface.
     */
    static boolean isVisibleAtDepth(double sceneDistance, double effectEntry, double depthBias) {
        return sceneDistance >= Math.max(0.0, effectEntry) - depthBias;
    }

    /** Combines the traced path entry with the nearest disk crossing, when present. */
    static double nearestSurfaceEntry(Trace trace, double nearestDiskEntry) {
        Objects.requireNonNull(trace, "trace");
        return Double.isNaN(nearestDiskEntry)
            ? trace.nearestEntry() : Math.min(trace.nearestEntry(), nearestDiskEntry);
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

    record Trace(boolean captured, Vector3d outgoingDirection, double impact, double closestApproach,
        double nearestEntry, int steps) {
        Trace {
            Objects.requireNonNull(outgoingDirection, "outgoingDirection");
            outgoingDirection = new Vector3d(outgoingDirection);
        }

        @Override
        public Vector3d outgoingDirection() {
            return new Vector3d(this.outgoingDirection);
        }
    }

    record DiskTrace(Trace trace, int crossings, double firstDiskRadius, double lastDiskRadius,
        double nearestDiskEntry) {
        DiskTrace {
            Objects.requireNonNull(trace, "trace");
        }
    }

    /**
     * Hole-relative refracted ray, the camera-to-surface distance, and the surface
     * normal. A negative surface distance means the ray did not cross the plane.
     */
    record WaterRay(Vector3d origin, Vector3d direction, double surfaceDistance,
        Vector3d normal, double incidenceCosine, boolean totalInternalReflection) {
        WaterRay {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(normal, "normal");
            origin = new Vector3d(origin);
            direction = new Vector3d(direction);
            normal = new Vector3d(normal);
        }

        static WaterRay straight(Vector3d origin, Vector3d direction) {
            return new WaterRay(origin, direction, -1.0,
                new Vector3d(0.0, 1.0, 0.0), 1.0, false);
        }

        @Override
        public Vector3d origin() {
            return new Vector3d(this.origin);
        }

        @Override
        public Vector3d direction() {
            return new Vector3d(this.direction);
        }

        @Override
        public Vector3d normal() {
            return new Vector3d(this.normal);
        }
    }

    private record Integration(Trace trace, int crossings, double firstDiskRadius, double lastDiskRadius,
        double nearestDiskEntry) {
    }

    private record Disk(Vector3d normal, double inner, double outer) {
    }
}
