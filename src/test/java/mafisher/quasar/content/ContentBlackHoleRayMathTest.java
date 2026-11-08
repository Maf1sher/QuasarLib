package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Headless mirror of the content black-hole ray marcher. Keep the constants and the integration order in
 * sync with {@code black_hole.fsh}; GPU shader behavior itself remains an in-game check.
 */
class ContentBlackHoleRayMathTest {
    private static final float HORIZON_RADIUS = 6.0F;
    private static final ContentBlackHoleSettings SETTINGS = ContentBlackHoleSettings.DEFAULT;
    private static final float LENS_GRAVITY = SETTINGS.lensGravity();
    private static final float LENS_STRENGTH = SETTINGS.lensStrength();
    private static final float DISK_OPACITY = 1.5F;
    private static final int MAX_STEPS = 160;
    private static final int HIGH_STEPS = ContentBlackHoleGeometry.stepCount(0, 2);
    private static final int LOW_STEPS = ContentBlackHoleGeometry.stepCount(0, 0);

    @Test
    void capturesRaysInsideThePhotonSphereAndRejectsOuterRays() {
        MarchResult inner = march(HORIZON_RADIUS, 160.0F, 2.2F, HIGH_STEPS, true);
        MarchResult outer = march(HORIZON_RADIUS, 160.0F, 3.2F, HIGH_STEPS, true);

        assertTrue(inner.captured(), "a ray at 2.2R must fall through the horizon");
        assertFalse(outer.captured(), "a ray at 3.2R must escape the capture region");
    }

    @Test
    void samplesTheDiskBothFaceOnAndEdgeOn() {
        MarchResult faceOn = march(HORIZON_RADIUS, 160.0F, 5.0F, HIGH_STEPS, true);
        MarchResult edgeOn = march(HORIZON_RADIUS, 160.0F, 0.0F, HIGH_STEPS, false);

        assertTrue(faceOn.diskSamples() > 0, "face-on rays must cross the volumetric disk");
        assertTrue(faceOn.alpha() > 0.2, "face-on disk must retain visible optical depth");
        assertTrue(edgeOn.diskSamples() > 0, "edge-on rays must traverse the volumetric disk");
        assertTrue(edgeOn.alpha() > 0.2, "edge-on disk must retain visible optical depth");
    }

    @Test
    void diskTraversalDoesNotDependOnCameraDistanceOrLowQuality() {
        for (float distance : new float[] {160.0F, 500.0F, 1000.0F}) {
            for (int steps : new int[] {LOW_STEPS, HIGH_STEPS}) {
                MarchResult result = march(HORIZON_RADIUS, distance, 5.0F, steps, true);
                assertTrue(result.diskSamples() > 0,
                    "disk must be sampled at distance " + distance + " with " + steps + " steps");
                assertTrue(result.alpha() > 0.2,
                    "disk must stay visible at distance " + distance + " with " + steps + " steps");
            }
        }
    }

    @Test
    void captureRegionIsScaleInvariant() {
        for (float scale : new float[] {3.0F, 6.0F, 12.0F}) {
            MarchResult inner = march(scale, 160.0F, 2.2F, 112, true);
            MarchResult outer = march(scale, 160.0F, 3.2F, 112, true);

            assertTrue(inner.captured(), "capture at 2.2R for scale " + scale);
            assertFalse(outer.captured(), "escape at 3.2R for scale " + scale);
        }
    }

    private static MarchResult march(float scale, float distance, float impactFactor, int steps,
        boolean faceOn) {
        float horizon = scale;
        float diskInner = SETTINGS.diskInnerFactor() * scale;
        float diskOuter = SETTINGS.diskOuterFactor() * scale;
        float thickness = SETTINGS.diskThicknessFactor() * scale;
        float entryRadius = Math.max(diskOuter * 1.5F, scale * 16.0F);

        double[] origin;
        double[] direction;
        if (faceOn) {
            origin = new double[] {0.0, distance, 0.0};
            double impact = impactFactor * scale;
            double angle = Math.asin(Math.min(1.0, impact / distance));
            direction = new double[] {Math.sin(angle), -Math.cos(angle), 0.0};
        } else {
            origin = new double[] {0.0, 0.0, distance};
            direction = new double[] {0.0, 0.0, -1.0};
        }

        double entry = raySphereEntry(origin, direction, entryRadius);
        if (entry < 0.0) {
            return new MarchResult(false, 0, 0.0);
        }

        double[] position = {
            origin[0] + direction[0] * entry,
            origin[1] + direction[1] * entry,
            origin[2] + direction[2] * entry
        };
        double[] velocity = direction.clone();
        double alpha = 0.0;
        int diskSamples = 0;
        boolean captured = false;

        for (int stepIndex = 0; stepIndex < MAX_STEPS && stepIndex < steps; stepIndex++) {
            double distanceToHole = length(position);
            if (distanceToHole < horizon * 1.03) {
                captured = true;
                break;
            }
            if (distanceToHole > horizon * 32.0 && dot(position, velocity) > 0.0) {
                break;
            }

            double stepLength = distanceToHole > horizon * 8.0
                ? clamp(0.12 * distanceToHole, 0.5 * horizon, 1.2 * horizon)
                : clamp(0.04 * distanceToHole, 0.02 * horizon, 0.25 * horizon);
            double[] previous = position.clone();
            position[0] += velocity[0] * stepLength;
            position[1] += velocity[1] * stepLength;
            position[2] += velocity[2] * stepLength;

            double squaredDistance = Math.max(dot(position, position), horizon * horizon * 0.2);
            double inverseLength = 1.0 / Math.max(length(position), 1.0E-6);
            double accelerationScale = -(horizon * LENS_GRAVITY) / squaredDistance;
            velocity[0] += position[0] * inverseLength * accelerationScale * stepLength * LENS_STRENGTH;
            velocity[1] += position[1] * inverseLength * accelerationScale * stepLength * LENS_STRENGTH;
            velocity[2] += position[2] * inverseLength * accelerationScale * stepLength * LENS_STRENGTH;
            normalize(velocity);

            double[] sample = {
                (previous[0] + position[0]) * 0.5,
                (previous[1] + position[1]) * 0.5,
                (previous[2] + position[2]) * 0.5
            };
            double height = sample[1];
            double radial = Math.sqrt(sample[0] * sample[0] + sample[2] * sample[2]);
            double halfThickness = Math.max(thickness * (0.35 + 0.65
                * Math.min(1.0, radial / diskOuter)), 0.001);
            double vertical = Math.exp(-(height * height) / (halfThickness * halfThickness));
            if (radial > diskInner && radial < diskOuter && vertical > 0.001) {
                diskSamples++;
                double opacity = 1.0 - Math.exp(-stepLength * DISK_OPACITY);
                alpha += opacity * (1.0 - alpha);
            }
        }

        return new MarchResult(captured, diskSamples, alpha);
    }

    private static double raySphereEntry(double[] origin, double[] direction, double radius) {
        double b = dot(origin, direction);
        double c = dot(origin, origin) - radius * radius;
        double discriminant = b * b - c;
        if (discriminant < 0.0) {
            return -1.0;
        }
        double root = Math.sqrt(discriminant);
        double nearHit = -b - root;
        double farHit = -b + root;
        if (farHit < 0.0) {
            return -1.0;
        }
        return Math.max(nearHit, 0.0);
    }

    private static double dot(double[] left, double[] right) {
        return left[0] * right[0] + left[1] * right[1] + left[2] * right[2];
    }

    private static double length(double[] value) {
        return Math.sqrt(dot(value, value));
    }

    private static void normalize(double[] value) {
        double length = Math.max(length(value), 1.0E-6);
        value[0] /= length;
        value[1] /= length;
        value[2] /= length;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record MarchResult(boolean captured, int diskSamples, double alpha) {
    }
}
