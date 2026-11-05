package mafisher.quasar.client.effect;

import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.network.StartVisualEffectPayload;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Client-side copy of one server-authored effect instance. */
public record VisualEffectInstance(
    StartVisualEffectPayload payload,
    EffectTransform previousTransform,
    EffectTransform currentTransform,
    long previousTransformGameTime,
    long currentTransformGameTime
) {
    public VisualEffectInstance {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(previousTransform, "previousTransform");
        Objects.requireNonNull(currentTransform, "currentTransform");
    }

    public VisualEffectInstance(StartVisualEffectPayload payload) {
        this(payload, payload.startGameTime());
    }

    public VisualEffectInstance(StartVisualEffectPayload payload, long transformGameTime) {
        this(payload, payload.transform(), payload.transform(), transformGameTime, transformGameTime);
    }

    public VisualEffectInstance withTransform(EffectTransform transform, long updateGameTime) {
        Objects.requireNonNull(transform, "transform");
        if (updateGameTime < this.currentTransformGameTime) {
            return this;
        }
        if (updateGameTime == this.currentTransformGameTime) {
            return new VisualEffectInstance(this.payload, this.previousTransform, transform,
                this.previousTransformGameTime, this.currentTransformGameTime);
        }
        return new VisualEffectInstance(this.payload, this.currentTransform, transform,
            this.currentTransformGameTime, updateGameTime);
    }

    public EffectTransform interpolatedTransform(long gameTime, float partialTick) {
        if (this.previousTransformGameTime == this.currentTransformGameTime) {
            return this.currentTransform;
        }
        double renderGameTime = gameTime + (double)partialTick - 1.0;
        float progress = (float)((renderGameTime - this.previousTransformGameTime)
            / (this.currentTransformGameTime - this.previousTransformGameTime));
        return EffectTransform.interpolate(this.previousTransform, this.currentTransform, progress);
    }

    public UUID instanceId() {
        return this.payload.instanceId();
    }

    public ResourceLocation effectId() {
        return this.payload.effectId();
    }
}
