package mafisher.quasar.api.client.screen;

import java.util.Objects;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectSpace;

/**
 * Camera-relative, per-layer depth encoding and visibility for screen effects.
 *
 * <p>The CPU methods mirror {@code quasar_lib:shaders/include/screen_visibility.glsl}.
 * Construct one instance per effect and frame, then call the methods for each
 * pixel and layer; never compare one shared effect depth or a projected centre.
 * The type is Minecraft-independent; consumers can use it for CPU-side checks
 * and tests without a render context.</p>
 *
 * <p>{@code sceneDistance} is reconstructed from the pre-translucent opaque
 * depth copy. {@code foregroundDistance} is reconstructed from the optional
 * post-translucent copy; when that capture is missing, pass
 * {@code foregroundValid = false} and the shader's color-delta fallback decides
 * whether a translucent layer is present. Missing captures must never sample a
 * released target.</p>
 */
public record ScreenEffectVisibility(float distance, float radius) {
    public ScreenEffectVisibility {
        if (!Float.isFinite(distance) || distance < 0.0F) {
            throw new IllegalArgumentException("Effect distance must be finite and non-negative");
        }
        if (!Float.isFinite(radius) || radius < 0.0F) {
            throw new IllegalArgumentException("Effect radius must be finite and non-negative");
        }
    }

    /**
     * Builds visibility from the shared effect frame.
     *
     * @param space the camera-relative effect space
     * @param radius the effect or lens radius in blocks
     * @return visibility for the current effect frame
     */
    public static ScreenEffectVisibility from(EffectSpace space, float radius) {
        Objects.requireNonNull(space, "space");
        EffectPoint origin = space.cameraRelativeOrigin();
        double squared = origin.x() * origin.x()
            + origin.y() * origin.y()
            + origin.z() * origin.z();
        return new ScreenEffectVisibility((float)Math.sqrt(squared), radius);
    }

    /**
     * Returns the logarithmic depth range for one layer.
     *
     * <p>The value is always at least {@code 1}, so encoding is defined even for
     * an effect at the camera with a zero radius.</p>
     */
    public float layerScale(ScreenEffectLayer layer) {
        Objects.requireNonNull(layer, "layer");
        return Math.max(this.distance + layer.rangeFactor() * this.radius, 1.0F);
    }

    /**
     * Encodes a camera-relative layer entry into {@code [0, 1]}.
     *
     * <p>Logarithmic encoding keeps sub-block precision at horizon distances
     * where a linear 8-bit value cannot.</p>
     */
    public float encode(ScreenEffectLayer layer, float entryDistance) {
        float scale = this.layerScale(layer);
        if (Float.isNaN(entryDistance)) {
            return 0.0F;
        }
        if (entryDistance == Float.POSITIVE_INFINITY) {
            return 1.0F;
        }
        float entry = Math.max(entryDistance, 0.0F);
        return clamp((float)(Math.log1p(entry) / Math.log1p(scale)));
    }

    /** Decodes a value written by {@link #encode(ScreenEffectLayer, float)}. */
    public float decode(ScreenEffectLayer layer, float encoded) {
        float scale = this.layerScale(layer);
        float clamped = clamp(encoded);
        return Math.max((float)Math.expm1(clamped * Math.log1p(scale)), 0.0F);
    }

    /**
     * Tests one pixel against the pre-translucent opaque depth.
     *
     * <p>This overload is the full visibility test for {@link ScreenEffectLayer#EMISSION},
     * {@link ScreenEffectLayer#EDGE}, {@link ScreenEffectLayer#FILL}, and
     * {@link ScreenEffectLayer#SHADOW}.</p>
     */
    public boolean visible(ScreenEffectLayer layer, float entryDistance, float sceneDistance,
        float depthBias) {
        Objects.requireNonNull(layer, "layer");
        if (Float.isNaN(entryDistance) || Float.isNaN(sceneDistance)) {
            return false;
        }
        float entry = Math.max(entryDistance, 0.0F);
        return sceneDistance >= entry - bias(depthBias);
    }

    /**
     * Tests one pixel against opaque depth and the optional post-translucent
     * foreground depth and color delta.
     *
     * <p>Only {@link ScreenEffectLayer#LENS} is hard-gated by a translucent
     * foreground. Emission and solid layers stay visible and are attenuated by
     * the shader tint instead of disappearing behind glass or water.</p>
     */
    public boolean visible(ScreenEffectLayer layer, float entryDistance, float sceneDistance,
        float foregroundDistance, boolean foregroundValid, boolean foregroundDelta,
        float depthBias) {
        if (!this.visible(layer, entryDistance, sceneDistance, depthBias)) {
            return false;
        }
        if (layer != ScreenEffectLayer.LENS) {
            return true;
        }
        if (foregroundDelta) {
            return false;
        }
        if (!foregroundValid || Float.isNaN(foregroundDistance)) {
            return true;
        }
        float entry = Math.max(entryDistance, 0.0F);
        return foregroundDistance >= entry - bias(depthBias);
    }

    private static float bias(float depthBias) {
        return Float.isFinite(depthBias) ? Math.max(depthBias, 0.0F) : 0.0F;
    }

    private static float clamp(float value) {
        if (Float.isNaN(value)) {
            return 0.0F;
        }
        return Math.min(Math.max(value, 0.0F), 1.0F);
    }
}
