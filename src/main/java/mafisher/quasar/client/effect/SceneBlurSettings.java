package mafisher.quasar.client.effect;

/** Bounded pixel radius for a mask-scoped scene blur. */
public record SceneBlurSettings(float radiusPixels) {
    public static final float MAX_RADIUS_PIXELS = 32.0F;
    public static final SceneBlurSettings DEBUG_DEFAULT = new SceneBlurSettings(8.0F);

    public SceneBlurSettings {
        if (!Float.isFinite(radiusPixels) || radiusPixels < 0.0F || radiusPixels > MAX_RADIUS_PIXELS) {
            throw new IllegalArgumentException("Invalid scene blur radius");
        }
    }
}
