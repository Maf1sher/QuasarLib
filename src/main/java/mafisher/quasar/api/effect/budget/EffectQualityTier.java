package mafisher.quasar.api.effect.budget;

/**
 * Client presentation quality tier. Lower tiers lower level of detail sooner, reduce screen-composition
 * resolution, and let renderers trim cosmetic layers. Tiers never reject an effect and never change server
 * authority or persisted state.
 *
 * <p>{@link #HIGH} preserves the full-detail behavior used before quality tiers existed. {@link #ULTRA} keeps
 * full detail on screen and selects distance LOD slightly later.</p>
 */
public enum EffectQualityTier {
    /** Minimum presentation quality; screen composition runs at half resolution. */
    LOW(2.0F, 0.50F),
    /** Reduced presentation quality; screen composition runs at three-quarter resolution. */
    MEDIUM(1.25F, 0.75F),
    /** Full presentation quality with baseline LOD distances. */
    HIGH(1.0F, 1.0F),
    /** Full presentation quality; distance LOD switches later. */
    ULTRA(0.75F, 1.0F);

    private final float lodDistanceMultiplier;
    private final float screenScale;

    EffectQualityTier(float lodDistanceMultiplier, float screenScale) {
        this.lodDistanceMultiplier = lodDistanceMultiplier;
        this.screenScale = screenScale;
    }

    /** Ordinal level; higher is better. */
    public int level() {
        return this.ordinal();
    }

    /** Multiplier applied to the normalized LOD surface gap; larger values switch LOD sooner. */
    public float lodDistanceMultiplier() {
        return this.lodDistanceMultiplier;
    }

    /** Scale applied to screen-composition render targets in {@code (0, 1]}. */
    public float screenScale() {
        return this.screenScale;
    }

    /** Returns whether this tier is at least {@code other}. */
    public boolean atLeast(EffectQualityTier other) {
        return this.ordinal() >= other.ordinal();
    }
}
