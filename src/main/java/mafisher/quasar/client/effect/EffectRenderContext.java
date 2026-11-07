package mafisher.quasar.client.effect;

import mafisher.quasar.effect.EffectQualityTier;
import mafisher.quasar.effect.EffectTransform;
import java.util.Objects;

/** Immutable, validated per-instance data supplied to a client visual effect renderer. */
public record EffectRenderContext(
    VisualEffectInstance effect,
    EffectTransform transform,
    float ageTicks,
    float progress,
    float partialTick,
    EffectLod lod,
    EffectQualityTier quality
) {
    public EffectRenderContext {
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(lod, "lod");
        Objects.requireNonNull(quality, "quality");
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
        this(effect, transform, ageTicks, progress, partialTick, EffectLod.FULL, EffectQualityTier.HIGH);
    }

    /** Keeps source compatibility for callers that only supplied a LOD selection. */
    public EffectRenderContext(
        VisualEffectInstance effect,
        EffectTransform transform,
        float ageTicks,
        float progress,
        float partialTick,
        EffectLod lod
    ) {
        this(effect, transform, ageTicks, progress, partialTick, lod, EffectQualityTier.HIGH);
    }

    /** Returns a copy with the engine-selected level of detail for this frame. */
    public EffectRenderContext withLod(EffectLod lod) {
        return new EffectRenderContext(this.effect, this.transform, this.ageTicks, this.progress,
            this.partialTick, lod, this.quality);
    }

    /** Returns a copy with the engine-selected quality tier for this frame. */
    public EffectRenderContext withQuality(EffectQualityTier quality) {
        return new EffectRenderContext(this.effect, this.transform, this.ageTicks, this.progress,
            this.partialTick, this.lod, quality);
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
