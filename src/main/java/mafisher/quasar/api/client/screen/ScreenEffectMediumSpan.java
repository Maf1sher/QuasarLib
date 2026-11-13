package mafisher.quasar.api.client.screen;

import java.util.Objects;

/**
 * One ordered span of a camera-to-effect medium path.
 *
 * @param medium the fluid occupying the span
 * @param startDistance distance from the camera in blocks
 * @param endDistance distance from the camera in blocks
 */
public record ScreenEffectMediumSpan(
    ScreenEffectMedium medium,
    double startDistance,
    double endDistance
) {
    public ScreenEffectMediumSpan {
        Objects.requireNonNull(medium, "medium");
        if (!Double.isFinite(startDistance) || !Double.isFinite(endDistance)) {
            throw new IllegalArgumentException("Medium span distances must be finite");
        }
        if (startDistance < 0.0 || endDistance < startDistance) {
            throw new IllegalArgumentException("Medium span must be ordered and non-negative");
        }
    }
}
