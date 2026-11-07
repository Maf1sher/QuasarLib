package mafisher.quasar.client.effect;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/** Pure camera-to-effect LOD selection shared by the client manager and headless tests. */
final class EffectLodSelection {
    private EffectLodSelection() {
    }

    static EffectLod select(EffectLodProfile profile, Vec3 cameraPosition, Vec3 effectPosition,
        double spatialRadius) {
        Objects.requireNonNull(cameraPosition, "cameraPosition");
        Objects.requireNonNull(effectPosition, "effectPosition");
        return select(profile, cameraPosition.x, cameraPosition.y, cameraPosition.z,
            effectPosition.x, effectPosition.y, effectPosition.z, spatialRadius);
    }

    /** Allocation-free variant used by the per-frame render path. */
    static EffectLod select(EffectLodProfile profile, double cameraX, double cameraY, double cameraZ,
        double effectX, double effectY, double effectZ, double spatialRadius) {
        Objects.requireNonNull(profile, "profile");
        double dx = effectX - cameraX;
        double dy = effectY - cameraY;
        double dz = effectZ - cameraZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(distance)) {
            return EffectLod.FULL;
        }
        double gapInRadii = 0.0;
        if (Double.isFinite(spatialRadius) && spatialRadius > 0.0) {
            gapInRadii = Math.max(0.0, distance - spatialRadius) / spatialRadius;
            if (!Double.isFinite(gapInRadii)) {
                gapInRadii = Double.MAX_VALUE;
            }
        }
        return new EffectLod(profile.select(gapInRadii), distance, gapInRadii);
    }
}
