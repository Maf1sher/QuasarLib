package mafisher.quasar.client.effect.quality;

import java.util.Objects;
import mafisher.quasar.api.client.render.EffectLod;
import mafisher.quasar.api.client.render.EffectLodProfile;
import mafisher.quasar.api.client.render.EffectScreenMetrics;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/** Pure camera-to-effect LOD selection shared by the client manager and headless tests. */
@ApiStatus.Internal
public final class EffectLodSelection {
    private EffectLodSelection() {
    }

    /** Raw camera measurements before a profile or quality multiplier is applied. */
    public record Raw(double cameraDistance, double gapInRadii) {
        public Raw {
            if (!Double.isFinite(cameraDistance) || cameraDistance < 0.0
                || !Double.isFinite(gapInRadii) || gapInRadii < 0.0) {
                throw new IllegalArgumentException("Invalid effect LOD measurements");
            }
        }
    }

    /**
     * Converts projected coverage into the detail-loss metric used by screen profiles. Lower tiers degrade sooner;
     * a missing projection returns full detail because the caller should use the distance fallback instead.
     */
    public static double screenLoss(EffectScreenMetrics metrics, EffectQualityTier quality) {
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(quality, "quality");
        if (!metrics.projected()) {
            return 0.0;
        }
        double loss = 1.0 - metrics.coverage() / quality.lodDistanceMultiplier();
        return Math.max(0.0, Math.min(1.0, loss));
    }

    /** Measures camera distance and the unscaled surface gap for one effect. */
    public static Raw measure(double cameraX, double cameraY, double cameraZ,
        double effectX, double effectY, double effectZ, double spatialRadius) {
        double dx = effectX - cameraX;
        double dy = effectY - cameraY;
        double dz = effectZ - cameraZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(distance)) {
            return new Raw(0.0, 0.0);
        }
        double gapInRadii = 0.0;
        if (Double.isFinite(spatialRadius) && spatialRadius > 0.0) {
            gapInRadii = Math.max(0.0, distance - spatialRadius) / spatialRadius;
            if (!Double.isFinite(gapInRadii)) {
                gapInRadii = Double.MAX_VALUE;
            }
        }
        return new Raw(distance, gapInRadii);
    }

    public static EffectLod select(EffectLodProfile profile, Vec3 cameraPosition, Vec3 effectPosition,
        double spatialRadius) {
        return select(profile, cameraPosition, effectPosition, spatialRadius, 1.0F);
    }

    public static EffectLod select(EffectLodProfile profile, Vec3 cameraPosition, Vec3 effectPosition,
        double spatialRadius, float lodDistanceMultiplier) {
        Objects.requireNonNull(cameraPosition, "cameraPosition");
        Objects.requireNonNull(effectPosition, "effectPosition");
        return select(profile, cameraPosition.x, cameraPosition.y, cameraPosition.z,
            effectPosition.x, effectPosition.y, effectPosition.z, spatialRadius, lodDistanceMultiplier);
    }

    public static EffectLod select(EffectLodProfile profile, double cameraX, double cameraY, double cameraZ,
        double effectX, double effectY, double effectZ, double spatialRadius) {
        return select(profile, cameraX, cameraY, cameraZ, effectX, effectY, effectZ, spatialRadius, 1.0F);
    }

    /** Allocation-free variant used by the per-frame render path. */
    public static EffectLod select(EffectLodProfile profile, double cameraX, double cameraY, double cameraZ,
        double effectX, double effectY, double effectZ, double spatialRadius, float lodDistanceMultiplier) {
        Objects.requireNonNull(profile, "profile");
        if (!Float.isFinite(lodDistanceMultiplier) || lodDistanceMultiplier <= 0.0F) {
            throw new IllegalArgumentException("LOD distance multiplier must be finite and positive");
        }
        Raw raw = measure(cameraX, cameraY, cameraZ, effectX, effectY, effectZ, spatialRadius);
        double gapInRadii = raw.gapInRadii();
        if (gapInRadii != Double.MAX_VALUE) {
            gapInRadii *= lodDistanceMultiplier;
            if (!Double.isFinite(gapInRadii)) {
                gapInRadii = Double.MAX_VALUE;
            }
        }
        return new EffectLod(profile.select(gapInRadii), raw.cameraDistance(), gapInRadii);
    }
}
