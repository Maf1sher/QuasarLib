package mafisher.quasar.content.orbital;

/**
 * Content-owned beam geometry and footprint settings shared by the renderer and the screen
 * operation.
 *
 * <p>The values are immutable and independent of the animation. All per-frame motion is driven by
 * the curves in {@link ContentOrbitalLaserCinematics}; these settings only describe the static
 * volume that must be culled and rendered.</p>
 *
 * @param topHeight beam extent above the resolved column base
 * @param coreRadiusFactor solid plasma-core radius as a fraction of the requested radius
 * @param glowRadiusFactor additive plasma-envelope radius as a fraction of the requested radius
 * @param shockMaxRadiusFactor maximum shock-ring radius as a fraction of the requested radius
 * @param flashRadiusFactor impact-flash radius as a fraction of the requested radius
 */
public record ContentOrbitalLaserBeamSettings(
    float topHeight,
    float coreRadiusFactor,
    float glowRadiusFactor,
    float shockMaxRadiusFactor,
    float flashRadiusFactor
) {
    /** Default tuning used by the content command and preview. */
    public static final ContentOrbitalLaserBeamSettings DEFAULT = new ContentOrbitalLaserBeamSettings(
        ContentOrbitalLaserSettings.BEAM_TOP_HEIGHT,
        ContentOrbitalLaserSettings.BEAM_CORE_RADIUS_FACTOR,
        ContentOrbitalLaserSettings.BEAM_GLOW_RADIUS_FACTOR,
        ContentOrbitalLaserSettings.SHOCK_MAX_RADIUS_FACTOR,
        ContentOrbitalLaserSettings.IMPACT_FLASH_RADIUS_FACTOR
    );

    public ContentOrbitalLaserBeamSettings {
        requirePositiveFinite(topHeight, "topHeight");
        requirePositiveFinite(coreRadiusFactor, "coreRadiusFactor");
        requirePositiveFinite(glowRadiusFactor, "glowRadiusFactor");
        requirePositiveFinite(shockMaxRadiusFactor, "shockMaxRadiusFactor");
        requirePositiveFinite(flashRadiusFactor, "flashRadiusFactor");
    }

    /** Returns the largest radial footprint any phase can draw. */
    public float maximumFootprintFactor() {
        return Math.max(
            Math.max(this.glowRadiusFactor, this.shockMaxRadiusFactor),
            Math.max(this.flashRadiusFactor, this.coreRadiusFactor)
        );
    }

    private static void requirePositiveFinite(float value, String name) {
        if (!Float.isFinite(value) || value <= 0.0F) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
