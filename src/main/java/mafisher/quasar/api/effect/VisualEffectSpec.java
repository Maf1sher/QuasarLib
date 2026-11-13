package mafisher.quasar.api.effect;

import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.effect.budget.EffectLimits;
import mafisher.quasar.api.effect.budget.EffectPriority;
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
            || !Float.isFinite(scale) || scale <= 0.0F || scale > EffectLimits.MAX_SCALE
            || !EffectTimeline.isValidDuration(durationTicks)) {
            throw new IllegalArgumentException("Invalid visual effect parameters");
        }
        audio.requireFitsWithin(durationTicks);
    }

    /** Starts a builder with the required fields; optional fields default to no roll, scale 1, no audio. */
    public static Builder builder(
        ResourceLocation effectId,
        Vec3 position,
        int durationTicks,
        long seed,
        EffectAudience audience
    ) {
        return new Builder(effectId, position, durationTicks, seed, audience);
    }

    public EffectTransform transform() {
        return new EffectTransform(new EffectPoint(this.position.x, this.position.y, this.position.z),
            this.yaw, this.pitch, this.roll);
    }

    /** Builder for optional rotation, scale, audio, and priority in one explicit place. */
    public static final class Builder {
        private final ResourceLocation effectId;
        private final Vec3 position;
        private final int durationTicks;
        private final long seed;
        private final EffectAudience audience;
        private float yaw;
        private float pitch;
        private float roll;
        private float scale = 1.0F;
        private EffectAudioSpec audio = EffectAudioSpec.NONE;
        private EffectPriority priority = EffectPriority.NORMAL;

        private Builder(
            ResourceLocation effectId,
            Vec3 position,
            int durationTicks,
            long seed,
            EffectAudience audience
        ) {
            this.effectId = effectId;
            this.position = position;
            this.durationTicks = durationTicks;
            this.seed = seed;
            this.audience = audience;
        }

        public Builder rotation(float yaw, float pitch, float roll) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            return this;
        }

        public Builder scale(float scale) {
            this.scale = scale;
            return this;
        }

        public Builder audio(EffectAudioSpec audio) {
            this.audio = audio;
            return this;
        }

        public Builder priority(EffectPriority priority) {
            this.priority = priority;
            return this;
        }

        public VisualEffectSpec build() {
            return new VisualEffectSpec(
                this.effectId,
                this.position,
                this.yaw,
                this.pitch,
                this.roll,
                this.scale,
                this.durationTicks,
                this.seed,
                this.audience,
                this.audio,
                this.priority
            );
        }
    }
}
