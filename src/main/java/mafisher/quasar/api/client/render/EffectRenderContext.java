package mafisher.quasar.api.client.render;

import java.util.Objects;
import mafisher.quasar.api.compat.RenderEnvironment;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectSpace;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Immutable, validated per-instance data supplied to a client visual effect renderer. */
@SuppressWarnings("checkstyle:ParameterNumber")
public record EffectRenderContext(
    VisualEffectInstance effect,
    EffectTransform transform,
    float ageTicks,
    float progress,
    float partialTick,
    EffectLod lod,
    EffectQualityTier quality,
    RenderEnvironment environment,
    @Nullable EffectCameraSnapshot camera,
    float daylight
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
        if (!Float.isFinite(daylight)) {
            throw new IllegalArgumentException("Daylight must be finite");
        }
        daylight = Math.max(0.0F, Math.min(1.0F, daylight));
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
            RenderEnvironment.VANILLA, null, 0.0F);
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
            RenderEnvironment.VANILLA, null, 0.0F);
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
            RenderEnvironment.VANILLA, null, 0.0F);
    }

    /** Keeps source compatibility for callers that supplied an environment. */
    public EffectRenderContext(
        VisualEffectInstance effect,
        EffectTransform transform,
        float ageTicks,
        float progress,
        float partialTick,
        EffectLod lod,
        EffectQualityTier quality,
        RenderEnvironment environment
    ) {
        this(effect, transform, ageTicks, progress, partialTick, lod, quality, environment, null, 0.0F);
    }

    /** Returns whether this context carries the live frame camera snapshot. */
    public boolean hasCamera() {
        return this.camera != null;
    }

    /**
     * Builds the effect space from this frame's camera snapshot.
     *
     * @throws IllegalStateException when the context was created without a live camera
     */
    public EffectSpace effectSpace() {
        EffectCameraSnapshot snapshot = this.camera;
        if (snapshot == null) {
            throw new IllegalStateException(
                "EffectRenderContext has no camera snapshot; use the client render manager or select "
                    + "EffectSpace.from(transform, scale, cameraPosition) explicitly");
        }
        Vec3 position = snapshot.position();
        return EffectSpace.from(this.transform, this.scale(), new EffectPoint(
            position.x, position.y, position.z
        ));
    }

    /** Returns a copy with the engine-selected level of detail for this frame. */
    public EffectRenderContext withLod(EffectLod lod) {
        return new EffectRenderContext(this.effect, this.transform, this.ageTicks, this.progress,
            this.partialTick, lod, this.quality, this.environment, this.camera, this.daylight);
    }

    /** Returns a copy with the engine-selected quality tier for this frame. */
    public EffectRenderContext withQuality(EffectQualityTier quality) {
        return new EffectRenderContext(this.effect, this.transform, this.ageTicks, this.progress,
            this.partialTick, this.lod, quality, this.environment, this.camera, this.daylight);
    }

    /** Returns a copy with the resolved compatibility environment for this frame. */
    public EffectRenderContext withEnvironment(RenderEnvironment environment) {
        return new EffectRenderContext(this.effect, this.transform, this.ageTicks, this.progress,
            this.partialTick, this.lod, this.quality, environment, this.camera, this.daylight);
    }

    /** Returns the server-authored parameter overrides for this instance. */
    public mafisher.quasar.api.effect.EffectParameterState parameters() {
        return this.effect.parameters();
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
