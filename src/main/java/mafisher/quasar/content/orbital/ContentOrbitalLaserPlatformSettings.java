package mafisher.quasar.content.orbital;

/**
 * Static geometry of the orbital-laser ring platform.
 *
 * <p>These values are shared by the platform curves, the screen operation, and the renderer. The
 * platform is content-only and draws no entity, block, or collision. The screen-space SDF is
 * centred at {@link #PLATFORM_ALTITUDE}; the beam top equals the muzzle at
 * {@code PLATFORM_ALTITUDE - PLATFORM_MUZZLE_DROP}.</p>
 */
public final class ContentOrbitalLaserPlatformSettings {
    /** Height of the platform hub above the effect origin. */
    public static final float PLATFORM_ALTITUDE = 512.0F;
    /** Outer radius of the dodecagonal armored ring. */
    public static final float PLATFORM_OUTER_RADIUS = 56.0F;
    /** Inner radius of the counter-rotating ring. */
    public static final float PLATFORM_INNER_RADIUS = 34.0F;
    /** Distance from the platform altitude down to the beam muzzle. */
    public static final float PLATFORM_MUZZLE_DROP = 18.0F;

    private ContentOrbitalLaserPlatformSettings() {
    }
}
