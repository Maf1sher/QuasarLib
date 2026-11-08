package mafisher.quasar.client.effect;

import java.util.Objects;
import mafisher.quasar.compat.RenderEnvironment;
import mafisher.quasar.effect.EffectQualityTier;
import mafisher.quasar.effect.EffectTransform;

/** Immutable, validated per-instance data supplied to a client visual effect renderer. */
public record EffectRenderContext(
    VisualEffectInstance effect,
    EffectTransform transform,
    float ageTicks,
    float progress,
    float partialTick,
    EffectLod lod,
    EffectQualityTier quality,
    RenderEnvironment environment
) {
    public EffectRenderContext {
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(lod, "lod");
        Objects.requireNonNull(quality, "quality");
        Objects.requireNonNull(environment, "environment");
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
        this(effect, transform, ageTicks, progress, partialTick, EffectLod.FULL, EffectQualityTier.HIGH,
            RenderEnvironment.VANILLA);
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
        this(effect, transform, ageTicks, progress, partialTick, lod, EffectQualityTier.HIGH,
            RenderEnvironment.VANILLA);
    }

    /** Keeps source compatibility for callers that supplied LOD and quality only. */
    public EffectRenderContext(
        VisualEffectInstance effect,
        EffectTransform transform,
        float ageTicks,
        float progress,
        float partialTick,
        EffectLod lod,
        EffectQualityTier quality
    ) {
        this(effect, transform, ageTicks, progress, partialTick, lod, quality,
            RenderEnvironment.VANILLA);
    }

    /** Returns a copy with the engine-selected level of detail for this frame. */
    public EffectRenderContext withLod(EffectLod lod) {
        return new EffectRenderContext(this.effect, this.transform, this.ageTicks, this.progress,
            this.partialTick, lod, this.quality, this.environment);
    }

    /** Returns a copy with the engine-selected quality tier for this frame. */
    public EffectRenderContext withQuality(EffectQualityTier quality) {
        return new EffectRenderContext(this.effect, this.transform, this.ageTicks, this.progress,
            this.partialTick, this.lod, quality, this.environment);
    }

    /** Returns a copy with the resolved compatibility environment for this frame. */
    public EffectRenderContext withEnvironment(RenderEnvironment environment) {
        return new EffectRenderContext(this.effect, this.transform, this.ageTicks, this.progress,
            this.partialTick, this.lod, this.quality, environment);
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
