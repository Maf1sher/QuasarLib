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
    EffectAudioSpec audio,
    EffectPriority priority
) {
    public VisualEffectSpec {
        if (effectId == null || position == null || audience == null || audio == null || priority == null) {
            throw new NullPointerException("Effect ID, position, audience, audio, and priority are required");
        }
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
            || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)
            || !Float.isFinite(scale) || scale <= 0.0F || scale > 128.0F
            || !EffectTimeline.isValidDuration(durationTicks)) {
            throw new IllegalArgumentException("Invalid visual effect parameters");
        }
        audio.requireFitsWithin(durationTicks);
    }

    /** Source-compatible constructor for effects that use the default priority. */
    public VisualEffectSpec(
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
        this(effectId, position, yaw, pitch, roll, scale, durationTicks, seed, audience, audio,
            EffectPriority.NORMAL);
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
        this(effectId, position, yaw, pitch, roll, scale, durationTicks, seed, audience, EffectAudioSpec.NONE,
            EffectPriority.NORMAL);
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
        this(effectId, position, yaw, pitch, 0.0F, scale, durationTicks, seed, audience, EffectAudioSpec.NONE,
            EffectPriority.NORMAL);
    }

    public EffectTransform transform() {
        return new EffectTransform(new EffectPoint(this.position.x, this.position.y, this.position.z),
            this.yaw, this.pitch, this.roll);
    }
}
