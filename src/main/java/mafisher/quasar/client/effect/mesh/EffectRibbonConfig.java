package mafisher.quasar.client.effect.mesh;

import java.util.Objects;
import mafisher.quasar.api.client.render.EffectRibbon;
import mafisher.quasar.api.effect.EffectCurve;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/** Validated immutable configuration built by the public {@link EffectRibbon} facade. */
@ApiStatus.Internal
public record EffectRibbonConfig(
    int maxPoints,
    int subdivisions,
    float startWidth,
    float endWidth,
    @Nullable EffectCurve widthCurve,
    EffectRibbon.UvMode uvMode,
    float textureLength
) {
    public EffectRibbonConfig {
        if (maxPoints < 2 || maxPoints > EffectRibbon.MAX_POINTS) {
            throw new IllegalArgumentException(
                "Ribbon max points must be between 2 and " + EffectRibbon.MAX_POINTS
            );
        }
        if (subdivisions < 1 || subdivisions > EffectRibbon.MAX_SUBDIVISIONS) {
            throw new IllegalArgumentException(
                "Ribbon subdivisions must be between 1 and " + EffectRibbon.MAX_SUBDIVISIONS
            );
        }
        if (!Float.isFinite(startWidth) || startWidth < 0.0F
            || startWidth > EffectRibbon.MAX_WIDTH) {
            throw new IllegalArgumentException(
                "Ribbon start width must be between 0 and " + EffectRibbon.MAX_WIDTH
            );
        }
        if (!Float.isFinite(endWidth) || endWidth < 0.0F || endWidth > EffectRibbon.MAX_WIDTH) {
            throw new IllegalArgumentException(
                "Ribbon end width must be between 0 and " + EffectRibbon.MAX_WIDTH
            );
        }
        Objects.requireNonNull(uvMode, "uvMode");
        if (!Float.isFinite(textureLength) || textureLength <= 0.0F) {
            throw new IllegalArgumentException(
                "Ribbon texture length must be finite and positive"
            );
        }
        if (widthCurve != null && widthCurve.type() != EffectCurve.ValueType.FLOAT) {
            throw new IllegalArgumentException("Ribbon taper curve must be a float curve");
        }
    }
}
