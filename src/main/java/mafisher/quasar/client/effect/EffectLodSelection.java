package mafisher.quasar.client.effect;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/** Pure camera-to-effect LOD selection shared by the client manager and headless tests. */
final class EffectLodSelection {
    private EffectLodSelection() {
    }

    static EffectLod select(EffectLodProfile profile, Vec3 cameraPosition, Vec3 effectPosition,
        double spatialRadius) {
        return select(profile, cameraPosition, effectPosition, spatialRadius, 1.0F);
    }

    static EffectLod select(EffectLodProfile profile, Vec3 cameraPosition, Vec3 effectPosition,
        double spatialRadius, float lodDistanceMultiplier) {
        Objects.requireNonNull(cameraPosition, "cameraPosition");
        Objects.requireNonNull(effectPosition, "effectPosition");
        return select(profile, cameraPosition.x, cameraPosition.y, cameraPosition.z,
            effectPosition.x, effectPosition.y, effectPosition.z, spatialRadius, lodDistanceMultiplier);
    }

    static EffectLod select(EffectLodProfile profile, double cameraX, double cameraY, double cameraZ,
        double effectX, double effectY, double effectZ, double spatialRadius) {
        return select(profile, cameraX, cameraY, cameraZ, effectX, effectY, effectZ, spatialRadius, 1.0F);
    }

    /** Allocation-free variant used by the per-frame render path. */
    static EffectLod select(EffectLodProfile profile, double cameraX, double cameraY, double cameraZ,
        double effectX, double effectY, double effectZ, double spatialRadius, float lodDistanceMultiplier) {
        Objects.requireNonNull(profile, "profile");
        if (!Float.isFinite(lodDistanceMultiplier) || lodDistanceMultiplier <= 0.0F) {
            throw new IllegalArgumentException("LOD distance multiplier must be finite and positive");
        }
        double dx = effectX - cameraX;
        double dy = effectY - cameraY;
        double dz = effectZ - cameraZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(distance)) {
            return EffectLod.FULL;
        }
        double gapInRadii = 0.0;
        if (Double.isFinite(spatialRadius) && spatialRadius > 0.0) {
            gapInRadii = Math.max(0.0, distance - spatialRadius) / spatialRadius * lodDistanceMultiplier;
            if (!Double.isFinite(gapInRadii)) {
                gapInRadii = Double.MAX_VALUE;
            }
        }
        return new EffectLod(profile.select(gapInRadii), distance, gapInRadii);
    }
}
