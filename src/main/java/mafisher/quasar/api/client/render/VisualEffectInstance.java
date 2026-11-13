package mafisher.quasar.api.client.render;

import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectParameterState;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import net.minecraft.resources.ResourceLocation;

/** Client-side copy of one server-authored effect instance. */
public record VisualEffectInstance(
    StartVisualEffectPayload payload,
    EffectTransform previousTransform,
    EffectTransform currentTransform,
    long previousTransformGameTime,
    long currentTransformGameTime,
    EffectParameterState parameters
) {
    public VisualEffectInstance {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(previousTransform, "previousTransform");
        Objects.requireNonNull(currentTransform, "currentTransform");
        Objects.requireNonNull(parameters, "parameters");
    }

    public VisualEffectInstance(StartVisualEffectPayload payload) {
        this(payload, payload.startGameTime());
    }

    public VisualEffectInstance(StartVisualEffectPayload payload, long transformGameTime) {
        this(payload, payload.transform(), payload.transform(), transformGameTime, transformGameTime,
            EffectParameterState.EMPTY);
    }

    /** Source-compatible constructor for callers that do not carry parameter overrides. */
    public VisualEffectInstance(
        StartVisualEffectPayload payload,
        EffectTransform previousTransform,
        EffectTransform currentTransform,
        long previousTransformGameTime,
        long currentTransformGameTime
    ) {
        this(payload, previousTransform, currentTransform, previousTransformGameTime,
            currentTransformGameTime, EffectParameterState.EMPTY);
    }

    public VisualEffectInstance withTransform(EffectTransform transform, long updateGameTime) {
        Objects.requireNonNull(transform, "transform");
        if (updateGameTime < this.currentTransformGameTime) {
            return this;
        }
        if (updateGameTime == this.currentTransformGameTime) {
            return new VisualEffectInstance(this.payload, this.previousTransform, transform,
                this.previousTransformGameTime, this.currentTransformGameTime, this.parameters);
        }
        return new VisualEffectInstance(this.payload, this.currentTransform, transform,
            this.currentTransformGameTime, updateGameTime, this.parameters);
    }

    /** Returns a copy with the full authoritative parameter override state replaced. */
    public VisualEffectInstance withParameters(EffectParameterState parameters) {
        Objects.requireNonNull(parameters, "parameters");
        return new VisualEffectInstance(this.payload, this.previousTransform, this.currentTransform,
            this.previousTransformGameTime, this.currentTransformGameTime, parameters);
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
