package mafisher.quasar.api.gameplay;

import java.util.Objects;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.effect.EffectAttachment;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectLifetimePolicy;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.budget.EffectLimits;
import mafisher.quasar.api.effect.budget.EffectPriority;
import net.minecraft.resources.ResourceLocation;

/**
 * Authored description of the one visual/audio child owned by a gameplay session.
 *
 * <p>The child always uses the parent session's total duration and seed, so its progress and
 * deterministic content stay aligned with the session. Its scale is the parent scale multiplied by
 * {@link #scaleMultiplier()}. Position and rotation are authored in the parent frame and composed
 * with {@link EffectAttachment.RotationInheritance} rules.</p>
 *
 * <p>An empty audio spec keeps the child silent. A non-empty spec is carried by the child visual
 * and shares its budget, lifetime, and scope cleanup.</p>
 */
public record CompanionSpec(
    ResourceLocation effectId,
    EffectPoint offset,
    float yaw,
    float pitch,
    float roll,
    EffectAttachment.RotationInheritance rotationInheritance,
    float scaleMultiplier,
    EffectAudience audience,
    EffectAudioSpec audio,
    EffectPriority priority,
    EffectLifetimePolicy lifetime
) {
    public CompanionSpec {
        Objects.requireNonNull(effectId, "effectId");
        Objects.requireNonNull(offset, "offset");
        Objects.requireNonNull(rotationInheritance, "rotationInheritance");
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(audio, "audio");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(lifetime, "lifetime");
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)) {
            throw new IllegalArgumentException("Companion rotation must be finite");
        }
        if (!Float.isFinite(scaleMultiplier) || scaleMultiplier <= 0.0F
            || scaleMultiplier > EffectLimits.MAX_SCALE) {
            throw new IllegalArgumentException(
                "Companion scale multiplier must be finite and inside (0, "
                    + EffectLimits.MAX_SCALE + "]"
            );
        }
    }

    /** Starts a builder with the required effect ID and audience. */
    public static Builder builder(ResourceLocation effectId, EffectAudience audience) {
        return new Builder(effectId, audience);
    }

    /** The authored offset and relative rotation in the parent frame. */
    public EffectTransform localTransform() {
        return new EffectTransform(this.offset, this.yaw, this.pitch, this.roll);
    }

    public static final class Builder {
        private final ResourceLocation effectId;
        private final EffectAudience audience;
        private EffectPoint offset = EffectPoint.ZERO;
        private float yaw;
        private float pitch;
        private float roll;
        private EffectAttachment.RotationInheritance rotationInheritance =
            EffectAttachment.RotationInheritance.NONE;
        private float scaleMultiplier = 1.0F;
        private EffectAudioSpec audio = EffectAudioSpec.NONE;
        private EffectPriority priority = EffectPriority.NORMAL;
        private EffectLifetimePolicy lifetime = EffectLifetimePolicy.CANCEL_WITH_SCOPE;

        private Builder(ResourceLocation effectId, EffectAudience audience) {
            this.effectId = Objects.requireNonNull(effectId, "effectId");
            this.audience = Objects.requireNonNull(audience, "audience");
        }

        public Builder offset(EffectPoint offset) {
            this.offset = Objects.requireNonNull(offset, "offset");
            return this;
        }

        public Builder offset(double x, double y, double z) {
            return this.offset(new EffectPoint(x, y, z));
        }

        public Builder rotation(float yaw, float pitch, float roll) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            return this;
        }

        public Builder rotationInheritance(EffectAttachment.RotationInheritance inheritance) {
            this.rotationInheritance = Objects.requireNonNull(inheritance, "inheritance");
            return this;
        }

        /** Scale relative to the parent session; the parent scale is multiplied by this value. */
        public Builder scale(float multiplier) {
            this.scaleMultiplier = multiplier;
            return this;
        }

        public Builder audio(EffectAudioSpec audio) {
            this.audio = Objects.requireNonNull(audio, "audio");
            return this;
        }

        public Builder priority(EffectPriority priority) {
            this.priority = Objects.requireNonNull(priority, "priority");
            return this;
        }

        public Builder lifetime(EffectLifetimePolicy lifetime) {
            this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
            return this;
        }

        public CompanionSpec build() {
            return new CompanionSpec(
                this.effectId,
                this.offset,
                this.yaw,
                this.pitch,
                this.roll,
                this.rotationInheritance,
                this.scaleMultiplier,
                this.audience,
                this.audio,
                this.priority,
                this.lifetime
            );
        }
    }
}
