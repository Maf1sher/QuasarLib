package mafisher.quasar.content;

import java.util.Objects;

/** Layer scales, colors, and rotation speeds for the content rune circle. */
record ContentRuneCircleSettings(ContentRuneLayer outer, ContentRuneLayer middle,
    ContentRuneLayer inner) {
    static final ContentRuneCircleSettings DEFAULT = new ContentRuneCircleSettings(
        new ContentRuneLayer(1.0F, 0.06F, 0.62F, 0.90F, 1.0F, 0.90F, 0.030F),
        new ContentRuneLayer(0.72F, 0.16F, 0.82F, 0.97F, 1.0F, 0.72F, -0.045F),
        new ContentRuneLayer(0.43F, 0.26F, 1.0F, 1.0F, 1.0F, 0.55F, 0.065F)
    );

    ContentRuneCircleSettings {
        Objects.requireNonNull(outer, "outer");
        Objects.requireNonNull(middle, "middle");
        Objects.requireNonNull(inner, "inner");
    }

    record ContentRuneLayer(float scale, float height, float red, float green, float blue,
        float alpha, float rotationSpeed) {
        ContentRuneLayer {
            if (!Float.isFinite(scale) || scale <= 0.0F || !Float.isFinite(height) || height < 0.0F
                || !isUnit(red) || !isUnit(green) || !isUnit(blue) || !isUnit(alpha)
                || !Float.isFinite(rotationSpeed)) {
                throw new IllegalArgumentException("Invalid rune layer settings");
            }
        }

        private static boolean isUnit(float value) {
            return Float.isFinite(value) && value >= 0.0F && value <= 1.0F;
        }
    }
}
