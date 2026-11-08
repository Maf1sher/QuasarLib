package mafisher.quasar.client.effect;

import java.util.Objects;

/**
 * Immutable per-effect medium snapshot for a screen operation.
 *
 * <p>Surface heights are camera-relative Y coordinates and may be {@link Float#NaN}
 * when the surface is unknown or the medium is not water.</p>
 */
public record ScreenEffectMediumState(
    ScreenEffectMedium cameraMedium,
    ScreenEffectMedium effectMedium,
    float cameraSurfaceY,
    float effectSurfaceY
) {
    /** Air around both the camera and the effect. */
    public static final ScreenEffectMediumState NONE = new ScreenEffectMediumState(
        ScreenEffectMedium.NONE, ScreenEffectMedium.NONE, Float.NaN, Float.NaN
    );

    public ScreenEffectMediumState {
        Objects.requireNonNull(cameraMedium, "cameraMedium");
        Objects.requireNonNull(effectMedium, "effectMedium");
        if (Float.isInfinite(cameraSurfaceY) || Float.isInfinite(effectSurfaceY)) {
            throw new IllegalArgumentException("Medium surface heights must be finite or NaN");
        }
    }

    /** Returns whether the camera is inside a fogging medium. */
    public boolean cameraSubmerged() {
        return this.cameraMedium != ScreenEffectMedium.NONE;
    }

    /** Returns whether the effect is inside a fogging medium. */
    public boolean effectSubmerged() {
        return this.effectMedium != ScreenEffectMedium.NONE;
    }

    /** Returns whether either side is inside a fogging medium. */
    public boolean submerged() {
        return this.cameraSubmerged() || this.effectSubmerged();
    }
}
