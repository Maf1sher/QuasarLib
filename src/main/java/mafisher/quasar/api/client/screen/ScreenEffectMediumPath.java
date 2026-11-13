package mafisher.quasar.api.client.screen;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ordered fluid spans and crossings along the straight camera-to-effect line.
 *
 * <p>All distances are in blocks from the camera. The engine uses the nearest
 * water crossing as the refraction plane and the submerged length as the medium
 * fog distance, so a lake between two air endpoints still changes the image.
 * Multiple crossings are supported for fog; the shader still refracts at one
 * plane, the nearest one.</p>
 */
public final class ScreenEffectMediumPath {
    /** A path with no medium at all. */
    public static final ScreenEffectMediumPath EMPTY =
        new ScreenEffectMediumPath(List.of(), List.of());

    private final List<ScreenEffectMediumSpan> spans;
    private final List<ScreenEffectMediumCrossing> crossings;
    private final double length;

    public ScreenEffectMediumPath(List<ScreenEffectMediumSpan> spans,
        List<ScreenEffectMediumCrossing> crossings) {
        Objects.requireNonNull(spans, "spans");
        Objects.requireNonNull(crossings, "crossings");
        this.spans = List.copyOf(spans);
        this.crossings = List.copyOf(crossings);
        double end = 0.0;
        boolean first = true;
        for (ScreenEffectMediumSpan span : this.spans) {
            if (first) {
                if (span.startDistance() != 0.0) {
                    throw new IllegalArgumentException("A medium path must start at the camera");
                }
                first = false;
            } else if (span.startDistance() < end) {
                throw new IllegalArgumentException("Medium spans must be ordered");
            }
            end = span.endDistance();
        }
        this.length = end;
        double previousCrossing = -1.0;
        for (ScreenEffectMediumCrossing crossing : this.crossings) {
            if (crossing.distance() < previousCrossing) {
                throw new IllegalArgumentException("Medium crossings must be ordered");
            }
            if (crossing.distance() > this.length) {
                throw new IllegalArgumentException("A medium crossing must lie inside the path");
            }
            previousCrossing = crossing.distance();
        }
    }

    /** Returns the ordered spans, starting at the camera. */
    public List<ScreenEffectMediumSpan> spans() {
        return this.spans;
    }

    /** Returns the ordered medium transitions. */
    public List<ScreenEffectMediumCrossing> crossings() {
        return this.crossings;
    }

    /** Returns the camera-to-effect distance in blocks. */
    public double length() {
        return this.length;
    }

    /** Returns whether the path contains no medium spans. */
    public boolean isEmpty() {
        return this.spans.isEmpty();
    }

    /** Returns the total distance travelled through a fogging medium. */
    public double submergedLength() {
        double total = 0.0;
        for (ScreenEffectMediumSpan span : this.spans) {
            if (span.medium() != ScreenEffectMedium.NONE) {
                total += span.endDistance() - span.startDistance();
            }
        }
        return total;
    }

    /** Returns the medium covering a distance from the camera, or {@link ScreenEffectMedium#NONE}. */
    public ScreenEffectMedium mediumAt(double distance) {
        if (!Double.isFinite(distance) || distance < 0.0) {
            return ScreenEffectMedium.NONE;
        }
        for (ScreenEffectMediumSpan span : this.spans) {
            if (distance < span.startDistance()) {
                return ScreenEffectMedium.NONE;
            }
            if (distance <= span.endDistance()) {
                return span.medium();
            }
        }
        return ScreenEffectMedium.NONE;
    }

    /** Returns the nearest crossing that enters or leaves water. */
    public Optional<ScreenEffectMediumCrossing> firstWaterCrossing() {
        for (ScreenEffectMediumCrossing crossing : this.crossings) {
            if (crossing.from() == ScreenEffectMedium.WATER
                || crossing.to() == ScreenEffectMedium.WATER) {
                return Optional.of(crossing);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the nearest water crossing that can drive a single screen-space
     * refraction plane.
     *
     * <p>The crossing must be a horizontal top-surface crossing, and its water span
     * must reach the camera or the effect. A bounded water body between two dry
     * endpoints has an entry and an exit; one plane cannot model both, so it only
     * contributes fog and foreground tint.</p>
     */
    public Optional<ScreenEffectMediumCrossing> firstOpenWaterCrossing() {
        for (ScreenEffectMediumCrossing crossing : this.crossings) {
            if (crossing.topSurface() && waterSpanTouchesAnEndpoint(crossing)) {
                return Optional.of(crossing);
            }
        }
        return Optional.empty();
    }

    private boolean waterSpanTouchesAnEndpoint(ScreenEffectMediumCrossing crossing) {
        double epsilon = 1.0E-9;
        for (ScreenEffectMediumSpan span : this.spans) {
            if (span.medium() != ScreenEffectMedium.WATER) {
                continue;
            }
            if (crossing.to() == ScreenEffectMedium.WATER
                && Math.abs(span.startDistance() - crossing.distance()) <= epsilon) {
                return Math.abs(span.endDistance() - this.length) <= epsilon;
            }
            if (crossing.from() == ScreenEffectMedium.WATER
                && Math.abs(span.endDistance() - crossing.distance()) <= epsilon) {
                return Math.abs(span.startDistance()) <= epsilon;
            }
        }
        return false;
    }

    /** Returns whether the path enters or leaves water. */
    public boolean hasWaterCrossing() {
        return firstWaterCrossing().isPresent();
    }
}
