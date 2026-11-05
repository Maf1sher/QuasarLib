package mafisher.quasar.client.effect;

import mafisher.quasar.effect.network.StartVisualEffectPayload;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Client-side copy of one server-authored effect instance. */
public record VisualEffectInstance(StartVisualEffectPayload payload) {
    public VisualEffectInstance {
        Objects.requireNonNull(payload, "payload");
    }

    public UUID instanceId() {
        return this.payload.instanceId();
    }

    public ResourceLocation effectId() {
        return this.payload.effectId();
    }
}
