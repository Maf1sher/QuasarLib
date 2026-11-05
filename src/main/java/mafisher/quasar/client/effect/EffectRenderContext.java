package mafisher.quasar.client.effect;

import mafisher.quasar.effect.EffectTransform;
import java.util.Objects;

/** Immutable, validated per-instance data supplied to a client visual effect renderer. */
public record EffectRenderContext(
    VisualEffectInstance effect,
    EffectTransform transform,
    float ageTicks,
    float progress,
    float partialTick
) {
    public EffectRenderContext {
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(transform, "transform");
        if (!Float.isFinite(ageTicks) || ageTicks < 0.0F
            || !Float.isFinite(progress) || progress < 0.0F || progress > 1.0F
            || !Float.isFinite(partialTick) || partialTick < 0.0F || partialTick > 1.0F) {
            throw new IllegalArgumentException("Invalid visual effect render context");
        }
    }

    public float scale() {
        return this.effect.payload().scale();
    }

    public long seed() {
        return this.effect.payload().seed();
    }

    public int durationTicks() {
        return this.effect.payload().durationTicks();
    }
}
