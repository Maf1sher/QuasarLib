package mafisher.quasar.client.effect.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import mafisher.quasar.api.client.ScreenEffectMedium;
import mafisher.quasar.api.client.ScreenEffectMediumCrossing;
import mafisher.quasar.api.client.ScreenEffectMediumPath;
import mafisher.quasar.api.client.ScreenEffectMediumSpan;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Vector3d;

/**
 * Traces medium spans along the straight camera-to-effect line.
 *
 * <p>Sampling is bounded: at most {@link #MAX_SAMPLES} segments, no shorter than
 * {@link #MIN_STEP} blocks. A transition found between two samples is refined by
 * bisection so the refraction plane and the fog length do not jitter. The
 * remaining precision limit is a fluid thinner than one step; that is a
 * documented degradation, never a rejected effect.</p>
 */
@ApiStatus.Internal
public final class ScreenEffectMediumTracer {
    /** Maximum number of sampled segments per camera-to-effect line. */
    public static final int MAX_SAMPLES = 128;
    /** Minimum segment length in blocks for close effects. */
    public static final double MIN_STEP = 0.25;
    static final int BISECTION_STEPS = 6;
    private static final double TOP_SURFACE_EPSILON = 0.05;

    private ScreenEffectMediumTracer() {
    }

    /** Returns the ordered medium path from {@code from} to {@code to}. */
    public static ScreenEffectMediumPath trace(Vector3d from, Vector3d to,
        ScreenEffectMediumSampler sampler) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(sampler, "sampler");
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(length > 0.0) || !Double.isFinite(length)) {
            return ScreenEffectMediumPath.EMPTY;
        }
        double ux = dx / length;
        double uy = dy / length;
        double uz = dz / length;
        int samples = (int)Math.min(MAX_SAMPLES,
            Math.max(1.0, Math.ceil(length / MIN_STEP)));
        double step = length / samples;

        List<ScreenEffectMediumSpan> spans = new ArrayList<>();
        List<ScreenEffectMediumCrossing> crossings = new ArrayList<>();
        ScreenEffectMedium previousMedium = mediumAt(sampler, from.x, from.y, from.z);
        double previousX = from.x;
        double previousY = from.y;
        double previousZ = from.z;
        double spanStart = 0.0;

        for (int i = 0; i < samples; i++) {
            double distance = (i + 0.5) * step;
            double x = from.x + ux * distance;
            double y = from.y + uy * distance;
            double z = from.z + uz * distance;
            ScreenEffectMedium medium = mediumAt(sampler, x, y, z);
            if (medium != previousMedium) {
                double lower = i == 0 ? 0.0 : (i - 0.5) * step;
                double crossing = refine(from, ux, uy, uz, lower, distance, previousMedium, sampler);
                float surfaceY = medium != ScreenEffectMedium.NONE
                    ? surfaceY(sampler, x, y, z)
                    : surfaceY(sampler, previousX, previousY, previousZ);
                boolean topSurface = isTopSurface(from.y + uy * crossing, surfaceY);
                crossings.add(new ScreenEffectMediumCrossing(
                    crossing, previousMedium, medium, surfaceY, topSurface));
                if (crossing > spanStart) {
                    spans.add(new ScreenEffectMediumSpan(previousMedium, spanStart, crossing));
                }
                spanStart = crossing;
                previousMedium = medium;
            }
            previousX = x;
            previousY = y;
            previousZ = z;
        }

        // The endpoint is sampled explicitly so an effect directly inside a fluid
        // still contributes its submerged leg even when the last midpoint misses it.
        ScreenEffectMedium endMedium = mediumAt(sampler, to.x, to.y, to.z);
        if (endMedium != previousMedium) {
            double lower = (samples - 0.5) * step;
            double crossing = refine(from, ux, uy, uz, lower, length, previousMedium, sampler);
            float surfaceY = endMedium != ScreenEffectMedium.NONE
                ? surfaceY(sampler, to.x, to.y, to.z)
                : surfaceY(sampler, previousX, previousY, previousZ);
            boolean topSurface = isTopSurface(from.y + uy * crossing, surfaceY);
            crossings.add(new ScreenEffectMediumCrossing(
                crossing, previousMedium, endMedium, surfaceY, topSurface));
            if (crossing > spanStart) {
                spans.add(new ScreenEffectMediumSpan(previousMedium, spanStart, crossing));
            }
            spanStart = crossing;
            previousMedium = endMedium;
        }

        if (length > spanStart) {
            spans.add(new ScreenEffectMediumSpan(previousMedium, spanStart, length));
        } else if (spans.isEmpty()) {
            spans.add(new ScreenEffectMediumSpan(previousMedium, 0.0, length));
        }
        return new ScreenEffectMediumPath(spans, crossings);
    }

    private static double refine(Vector3d from, double ux, double uy, double uz,
        double lower, double upper, ScreenEffectMedium lowerMedium,
        ScreenEffectMediumSampler sampler) {
        for (int i = 0; i < BISECTION_STEPS; i++) {
            double middle = (lower + upper) * 0.5;
            if (mediumAt(sampler, from.x + ux * middle, from.y + uy * middle,
                from.z + uz * middle) == lowerMedium) {
                lower = middle;
            } else {
                upper = middle;
            }
        }
        return (lower + upper) * 0.5;
    }

    private static ScreenEffectMedium mediumAt(ScreenEffectMediumSampler sampler,
        double x, double y, double z) {
        ScreenEffectMedium medium = sampler.mediumAt(x, y, z);
        return medium == null ? ScreenEffectMedium.NONE : medium;
    }

    private static float surfaceY(ScreenEffectMediumSampler sampler,
        double x, double y, double z) {
        float surface = sampler.surfaceY(x, y, z);
        return Float.isFinite(surface) ? surface : Float.NaN;
    }

    /**
     * A horizontal refraction plane only exists when the ray crosses the fluid top.
     * An aquarium entered through its glass side is a side crossing: the top column
     * height would place the plane somewhere the ray never crosses.
     */
    private static boolean isTopSurface(double crossingY, float surfaceY) {
        return Float.isFinite(surfaceY)
            && Math.abs(crossingY - surfaceY) <= TOP_SURFACE_EPSILON;
    }
}
