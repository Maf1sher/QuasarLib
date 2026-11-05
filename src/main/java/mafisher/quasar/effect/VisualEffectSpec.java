package mafisher.quasar.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Immutable server-authored start parameters for one client-rendered world effect. */
public record VisualEffectSpec(
    ResourceLocation effectId,
    Vec3 position,
    float yaw,
    float pitch,
    float scale,
    int durationTicks,
    long seed,
    EffectAudience audience
) {
    public VisualEffectSpec {
        if (effectId == null || position == null || audience == null) {
            throw new NullPointerException("Effect ID, position, and audience are required");
        }
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
            || !Float.isFinite(yaw) || !Float.isFinite(pitch)
            || !Float.isFinite(scale) || scale <= 0.0F || scale > 128.0F
            || durationTicks < 1 || durationTicks > 72_000) {
            throw new IllegalArgumentException("Invalid visual effect parameters");
        }
    }
}
