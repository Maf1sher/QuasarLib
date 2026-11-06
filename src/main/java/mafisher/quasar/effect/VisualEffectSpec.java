package mafisher.quasar.effect;

import mafisher.quasar.effect.audio.EffectAudioSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Immutable server-authored start parameters for one client-rendered world effect. */
public record VisualEffectSpec(
    ResourceLocation effectId,
    Vec3 position,
    float yaw,
    float pitch,
    float roll,
    float scale,
    int durationTicks,
    long seed,
    EffectAudience audience,
    EffectAudioSpec audio
) {
    public VisualEffectSpec {
        if (effectId == null || position == null || audience == null || audio == null) {
            throw new NullPointerException("Effect ID, position, audience, and audio are required");
        }
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
            || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)
            || !Float.isFinite(scale) || scale <= 0.0F || scale > 128.0F
            || durationTicks < 1 || durationTicks > 72_000) {
            throw new IllegalArgumentException("Invalid visual effect parameters");
        }
        audio.requireFitsWithin(durationTicks);
    }

    public VisualEffectSpec(
        ResourceLocation effectId,
        Vec3 position,
        float yaw,
        float pitch,
        float roll,
        float scale,
        int durationTicks,
        long seed,
        EffectAudience audience
    ) {
        this(effectId, position, yaw, pitch, roll, scale, durationTicks, seed, audience, EffectAudioSpec.NONE);
    }

    public VisualEffectSpec(
        ResourceLocation effectId,
        Vec3 position,
        float yaw,
        float pitch,
        float scale,
        int durationTicks,
        long seed,
        EffectAudience audience
    ) {
        this(effectId, position, yaw, pitch, 0.0F, scale, durationTicks, seed, audience, EffectAudioSpec.NONE);
    }

    public EffectTransform transform() {
        return new EffectTransform(new EffectPoint(this.position.x, this.position.y, this.position.z),
            this.yaw, this.pitch, this.roll);
    }
}
