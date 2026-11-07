package mafisher.quasar.client.effect;

import mafisher.quasar.effect.EffectTransform;
import java.util.Objects;

/** Immutable, validated per-instance data supplied to a client visual effect renderer. */
public record EffectRenderContext(
    VisualEffectInstance effect,
    EffectTransform transform,
    float ageTicks,
    float progress,
    float partialTick,
    EffectLod lod
) {
    public EffectRenderContext {
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(lod, "lod");
        if (!Float.isFinite(ageTicks) || ageTicks < 0.0F
            || !Float.isFinite(progress) || progress < 0.0F || progress > 1.0F
            || !Float.isFinite(partialTick) || partialTick < 0.0F || partialTick > 1.0F) {
            throw new IllegalArgumentException("Invalid visual effect render context");
        }
    }

    /** Keeps source compatibility for callers that do not run through the client manager. */
    public EffectRenderContext(
        VisualEffectInstance effect,
        EffectTransform transform,
        float ageTicks,
        float progress,
        float partialTick
    ) {
        this(effect, transform, ageTicks, progress, partialTick, EffectLod.FULL);
    }

    /** Returns a copy with the engine-selected level of detail for this frame. */
    public EffectRenderContext withLod(EffectLod lod) {
        return new EffectRenderContext(this.effect, this.transform, this.ageTicks, this.progress,
            this.partialTick, lod);
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
