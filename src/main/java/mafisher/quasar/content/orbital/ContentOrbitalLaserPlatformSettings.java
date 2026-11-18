package mafisher.quasar.content.orbital;

/**
 * Geometry of the orbital-laser ring platform.
 *
 * <p>These values are shared by the platform curves, the screen operation, and the renderer. The
 * platform is content-only and draws no entity, block, or collision. The screen-space SDF is
 * centred at {@link #PLATFORM_ALTITUDE}; the beam top equals the muzzle at
 * {@code PLATFORM_ALTITUDE - PLATFORM_MUZZLE_DROP} while the barrel is retracted. The charge phase
 * telescopes the barrel down by {@link #PLATFORM_BARREL_EXTENSION}, and the beam top follows the
 * moving muzzle so the shot always starts at the lip.</p>
 */
public final class ContentOrbitalLaserPlatformSettings {
    /** Height of the platform hub above the effect origin. */
    public static final float PLATFORM_ALTITUDE = 512.0F;
    /** Outer radius of the dodecagonal armored ring. */
    public static final float PLATFORM_OUTER_RADIUS = 56.0F;
    /** Inner radius of the counter-rotating ring. */
    public static final float PLATFORM_INNER_RADIUS = 34.0F;
    /** Distance from the platform altitude down to the retracted beam muzzle. */
    public static final float PLATFORM_MUZZLE_DROP = 18.0F;
    /** Downward barrel extension reached at the end of the charge phase. */
    public static final float PLATFORM_BARREL_EXTENSION = 3.0F;

    private ContentOrbitalLaserPlatformSettings() {
    }

    /**
     * Returns the world height of the muzzle for a normalized charge level.
     *
     * <p>The barrel telescopes downward as the charge builds, then retracts during the first ticks
     * of the descent. The beam top uses the same value, so the beam starts exactly at the lip
     * through the whole extension and recoil release.</p>
     */
    public static float muzzleHeight(float charge) {
        float clamped = Float.isFinite(charge) ? Math.max(0.0F, Math.min(1.0F, charge)) : 0.0F;
        return PLATFORM_ALTITUDE - PLATFORM_MUZZLE_DROP
            - clamped * PLATFORM_BARREL_EXTENSION;
    }
}
