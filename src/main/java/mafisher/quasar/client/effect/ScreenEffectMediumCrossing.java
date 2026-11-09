package mafisher.quasar.client.effect;

import java.util.Objects;

/**
 * One medium transition along the camera-to-effect line.
 *
 * @param distance distance from the camera in blocks
 * @param from the medium before the crossing
 * @param to the medium after the crossing
 * @param surfaceY absolute world Y of the fluid top at the crossing, or
 *     {@link Float#NaN} when the crossed medium is not a water surface
 * @param topSurface whether the ray crossed the horizontal top of the fluid
 *     column instead of a side or bottom face
 */
public record ScreenEffectMediumCrossing(
    double distance,
    ScreenEffectMedium from,
    ScreenEffectMedium to,
    float surfaceY,
    boolean topSurface
) {
    public ScreenEffectMediumCrossing {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from == to) {
            throw new IllegalArgumentException("A crossing must change the medium");
        }
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("Crossing distance must be finite and non-negative");
        }
        if (Float.isInfinite(surfaceY)) {
            throw new IllegalArgumentException("Crossing surface height must be finite or NaN");
        }
    }
}
