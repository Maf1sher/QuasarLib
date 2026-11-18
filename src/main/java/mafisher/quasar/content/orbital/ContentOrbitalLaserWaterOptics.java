package mafisher.quasar.content.orbital;

import java.util.Objects;
import mafisher.quasar.api.client.screen.ScreenEffectMedium;
import mafisher.quasar.api.client.screen.ScreenEffectMediumPath;
import mafisher.quasar.api.client.screen.ScreenEffectMediumSpan;

/**
 * Content-owned water-volume attenuation for the orbital beam.
 *
 * <p>The medium path only describes the straight camera-to-impact line. A beam is a
 * volume: water in front of the far samples must absorb them even when the line to
 * the impact point is dry. The stage applies {@link #transmission(float)} per
 * ray-march sample behind the post-translucent water surface, and this class holds
 * the CPU mirror of the shipped constants for tests.</p>
 */
final class ContentOrbitalLaserWaterOptics {
    /** Absorption per block of water, mirrored by orbital_laser_optics.glsl. */
    static final float ABSORPTION = 0.22F;
    /** Lower bound so deep water still shows the beam silhouette. */
    static final float MIN_TRANSMISSION = 0.10F;
    /** Path length cap; longer submerged paths do not darken further. */
    static final float MAX_PATH = 18.0F;
    /** Fallback volume for blue foreground water that the camera-to-impact trace missed. */
    static final float DEFAULT_PATH = 6.0F;
    /** Surface loss applied once at the air/water boundary. */
    static final float SURFACE_TRANSMISSION = 0.45F;
    /** Depth ripple modulation so the attenuation does not look flat. */
    static final float RIPPLE_STRENGTH = 0.25F;
    /** Upper luminance for water-covered emission, so the HDR core cannot stay white. */
    static final float MAX_LUMINANCE = 0.30F;
    /** Extra attenuation for any confirmed translucent foreground, including glass. */
    static final float FOREGROUND_SCALE = 0.35F;
    /** Upper luminance for any confirmed translucent foreground. */
    static final float FOREGROUND_MAX_LUMINANCE = 0.40F;
    /** Bloom absorption per block of water. */
    static final float BLOOM_ABSORPTION = 0.12F;
    /** Lower bound for water-dimmed bloom. */
    static final float BLOOM_FLOOR = 0.35F;

    private ContentOrbitalLaserWaterOptics() {
    }

    /** Returns the total water length along a medium path. */
    static float pathLength(ScreenEffectMediumPath path) {
        Objects.requireNonNull(path, "path");
        double total = 0.0;
        for (ScreenEffectMediumSpan span : path.spans()) {
            if (span.medium() == ScreenEffectMedium.WATER) {
                total += Math.max(0.0, span.endDistance() - span.startDistance());
            }
        }
        return total >= Float.MAX_VALUE ? Float.MAX_VALUE : (float)total;
    }

    /** Returns the beam transmission after passing through {@code pathLength} blocks of water. */
    static float transmission(float pathLength) {
        if (!Float.isFinite(pathLength) || pathLength <= 0.0F) {
            return 1.0F;
        }
        float capped = Math.min(pathLength, MAX_PATH);
        float volume = (float)Math.exp(-capped * ABSORPTION);
        return Math.max(MIN_TRANSMISSION, volume * SURFACE_TRANSMISSION);
    }

    /** Returns the bloom intensity scale for a water path. */
    static float bloomScale(float pathLength) {
        if (!Float.isFinite(pathLength) || pathLength <= 0.0F) {
            return 1.0F;
        }
        float capped = Math.min(pathLength, MAX_PATH);
        return Math.max(BLOOM_FLOOR, (float)Math.exp(-capped * BLOOM_ABSORPTION));
    }
}
