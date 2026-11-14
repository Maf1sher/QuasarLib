package mafisher.quasar.api.client.screen;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.util.Objects;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.api.client.render.EffectShader;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * Owns the emissions-only bloom and the final composite of a screen effect.
 *
 * <p>The operation supplies one or two layer passes. Every pass renders into an
 * owned scratch target, never into {@link ScreenEffectOperationContext#sourceColor()}
 * or {@link ScreenEffectOperationContext#destination()}; the standard composite
 * re-adds the scene exactly once, and a custom composite must do the same. A
 * separate emission pass is required when the effect layer contains lensed or
 * otherwise replaced background, because bloom must never sample that background.</p>
 *
 * <p>The standard composite applies mask coverage, solidity, medium fog, and one
 * background re-add. The layer pass owns per-pixel foreground visibility and
 * must write zero coverage where confirmed foreground wins. A custom composite
 * shader keeps the standard samplers and uniforms and may add content-specific
 * uniforms through {@link CompositeUniforms}. The composite floor is reported to the compositor;
 * pass scales are applied to the already-floored destination and therefore never
 * multiply with the quality-tier screen scale.</p>
 *
 * <p>This type is client-only, render-thread confined, and not thread-safe.</p>
 */
@SuppressWarnings({"PMD.TooManyMethods", "PMD.GodClass"})
public final class EmissiveComposite {
    /** Engine-owned shader used when the builder is not given a custom composite. */
    public static final EffectShader STANDARD_SHADER = EffectShader.create(
        ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, "emissive_composite"),
        DefaultVertexFormat.POSITION
    );
    private static final int LINEAR_FILTER = 0x2601;
    private static final float COMPOSITE_PASS = 3.0F;
    private static final float BLOOM_BRIGHT_PASS = 0.0F;
    private static final float BLOOM_HORIZONTAL_PASS = 1.0F;
    private static final float BLOOM_VERTICAL_PASS = 2.0F;

    private final EffectShader shader;
    private final @Nullable CompositeUniforms compositeUniforms;
    private final @Nullable BloomUniforms bloomUniforms;
    private final float emissiveScale;
    private final @Nullable BloomSettings bloom;
    private final boolean emissionFog;
    private final float solidity;
    private final float compositionFloor;
    private @Nullable Plan cachedPlan;

    private EmissiveComposite(Builder builder) {
        this.shader = builder.shader;
        this.compositeUniforms = builder.compositeUniforms;
        this.bloomUniforms = builder.bloomUniforms;
        this.emissiveScale = builder.emissiveScale;
        this.bloom = builder.bloom;
        this.emissionFog = builder.emissionFog;
        this.solidity = builder.solidity;
        this.compositionFloor = builder.compositionFloor;
    }

    /** Returns a builder using the engine standard composite shader. */
    public static Builder builder() {
        return new Builder(STANDARD_SHADER);
    }

    /** Returns a builder using a custom composite shader with the standard inputs. */
    public static Builder builder(EffectShader compositeShader) {
        return new Builder(Objects.requireNonNull(compositeShader, "compositeShader"));
    }

    /** Returns the shader that performs the final composite. */
    public EffectShader shader() {
        return this.shader;
    }

    /** Returns the layer scale relative to the already-floored destination. */
    public float emissiveScale() {
        return this.emissiveScale;
    }

    /** Returns bloom settings, or {@code null} when bloom is disabled. */
    public @Nullable BloomSettings bloom() {
        return this.bloom;
    }

    /** Returns whether medium fog is applied to emission and bloom. */
    public boolean emissionFog() {
        return this.emissionFog;
    }

    /** Returns the solid-footprint replacement factor in {@code [0, 1]}. */
    public float solidity() {
        return this.solidity;
    }

    /** Returns the requested composition scale floor in {@code [0, 1]}. */
    public float compositionFloor() {
        return this.compositionFloor;
    }

    /**
     * Renders one emission-only layer and composites it over the scene.
     *
     * <p>Use this overload only when the layer contains no background
     * replacement; bloom samples this target directly.</p>
     */
    public void render(ScreenEffectOperationContext context, LayerPass emissionPass) {
        render(context, emissionPass, null);
    }

    /**
     * Renders the effect layer, an optional emission-only layer for bloom, and
     * the final composite.
     *
     * @param context the current screen-operation context
     * @param effectPass the full effect layer written to the composite target
     * @param emissionPass the emission-only layer used as the bloom source, or
     *     {@code null} when {@code effectPass} is already emission-only
     */
    public void render(ScreenEffectOperationContext context, LayerPass effectPass,
        @Nullable LayerPass emissionPass) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(effectPass, "effectPass");
        Plan plan = this.planFor(context.destination().width, context.destination().height,
            context.quality());

        TextureTarget effect = context.acquireScratchTarget(plan.effectWidth, plan.effectHeight);
        effect.setFilterMode(LINEAR_FILTER);
        TextureTarget emission = null;
        TextureTarget bloomTarget = null;
        TextureTarget bloomScratch = null;
        try {
            effectPass.render(context, effect);

            TextureTarget bloomSource = effect;
            if (emissionPass != null) {
                emission = context.acquireScratchTarget(plan.bloomWidth, plan.bloomHeight);
                emission.setFilterMode(LINEAR_FILTER);
                emissionPass.render(context, emission);
                bloomSource = emission;
            }

            if (plan.bloom) {
                bloomTarget = context.acquireScratchTarget(plan.bloomWidth, plan.bloomHeight);
                bloomScratch = context.acquireScratchTarget(plan.bloomWidth, plan.bloomHeight);
                bloomTarget.setFilterMode(LINEAR_FILTER);
                bloomScratch.setFilterMode(LINEAR_FILTER);
                this.renderBloom(context, bloomSource, bloomTarget, bloomScratch, plan);
            }

            this.drawComposite(context, effect, bloomTarget);
        } finally {
            release(context, bloomScratch);
            release(context, bloomTarget);
            release(context, emission);
            release(context, effect);
        }
    }

    private void renderBloom(ScreenEffectOperationContext context, RenderTarget source,
        TextureTarget first, TextureTarget second, Plan plan) {
        ShaderInstance shader = standardShader();
        this.bindBloomInputs(context, shader, source);
        context.setUniform(shader, "Pass", BLOOM_BRIGHT_PASS);
        context.setUniform(shader, "Horizontal", 0.0F);
        context.drawFullscreen(first, shader);
        if (!plan.blur) {
            return;
        }

        context.setSampler(shader, "BloomSampler", first.getColorTextureId());
        context.setUniform(shader, "Pass", BLOOM_HORIZONTAL_PASS);
        context.setUniform(shader, "Horizontal", 1.0F);
        context.drawFullscreen(second, shader);

        context.setSampler(shader, "BloomSampler", second.getColorTextureId());
        context.setUniform(shader, "Pass", BLOOM_VERTICAL_PASS);
        context.setUniform(shader, "Horizontal", 0.0F);
        context.drawFullscreen(first, shader);
    }

    private void bindBloomInputs(ScreenEffectOperationContext context, ShaderInstance shader,
        RenderTarget source) {
        BloomSettings settings = Objects.requireNonNull(this.bloom, "bloom");
        context.setSampler(shader, "EmissionSampler", source.getColorTextureId());
        context.setSampler(shader, "BloomSampler", source.getColorTextureId());
        context.setUniform(shader, "Threshold", settings.threshold());
        context.setUniform(shader, "Intensity", settings.intensity());
        context.setUniform(shader, "RadiusPixels",
            settings.radiusPixels() * context.quality().screenScale());
        context.setUniform(shader, "ViewportWidth", (float) context.destination().width);
        context.setUniform(shader, "ViewportHeight", (float) context.destination().height);
        if (this.bloomUniforms != null) {
            this.bloomUniforms.bind(context, shader);
        }
    }

    private void drawComposite(ScreenEffectOperationContext context, RenderTarget effect,
        @Nullable RenderTarget bloom) {
        ShaderInstance shader = this.shader.instance().orElseThrow(
            () -> new IllegalStateException("Emissive composite shader is not loaded: "
                + this.shader.id())
        );
        boolean standard = this.shader == STANDARD_SHADER;
        if (standard) {
            context.setUniform(shader, "Pass", COMPOSITE_PASS);
            context.setUniform(shader, "Solidity", this.solidity);
            context.setUniform(shader, "EmissionFog", this.emissionFog ? 1.0F : 0.0F);
        }

        context.setSampler(shader, "SceneSampler", context.sourceColor().getColorTextureId());
        context.setSampler(shader, "EffectSampler", effect.getColorTextureId());
        context.setSampler(shader, "BloomSampler",
            bloom != null ? bloom.getColorTextureId() : effect.getColorTextureId());
        context.setSampler(shader, "MaskSampler", context.maskTarget().getColorTextureId());
        if (standard) {
            context.setSampler(shader, "EmissionSampler", effect.getColorTextureId());
        }
        context.setUniform(shader, "MaskStrength", context.mask().strength());
        context.setUniform(shader, "ViewportWidth", (float) context.destination().width);
        context.setUniform(shader, "ViewportHeight", (float) context.destination().height);
        bindEmissionFog(context, shader);
        if (this.compositeUniforms != null) {
            this.compositeUniforms.bind(context, shader);
        }
        context.drawFullscreen(context.destination(), shader);
    }

    private static void bindEmissionFog(ScreenEffectOperationContext context,
        ShaderInstance shader) {
        ScreenEffectMediumOptics optics = ScreenEffectMediumOptics.from(context);
        ScreenEffectFogState fog = context.fogState();
        context.setUniform(shader, "MediumPathLength", optics.fogLength());
        context.setUniform(shader, "MediumFogStart", fog.start());
        context.setUniform(shader, "MediumFogEnd", fog.end());
        context.setUniform(shader, "MediumFogColor", (float) fog.color().x,
            (float) fog.color().y, (float) fog.color().z);
        context.setUniform(shader, "MediumFogActive", optics.fogActive() ? 1.0F : 0.0F);
    }

    private Plan planFor(int width, int height, EffectQualityTier quality) {
        Objects.requireNonNull(quality, "quality");
        Plan cached = this.cachedPlan;
        if (cached != null && cached.width == width && cached.height == height
            && cached.quality == quality) {
            return cached;
        }
        int effectWidth = scaledSize(width, this.emissiveScale);
        int effectHeight = scaledSize(height, this.emissiveScale);
        int bloomWidth = scaledSize(effectWidth, 0.5F);
        int bloomHeight = scaledSize(effectHeight, 0.5F);
        Plan plan = new Plan(width, height, quality, effectWidth, effectHeight,
            bloomWidth, bloomHeight, bloomEnabled(this.bloom), bloomUsesBlur(quality));
        this.cachedPlan = plan;
        return plan;
    }

    private static ShaderInstance standardShader() {
        return STANDARD_SHADER.instance().orElseThrow(
            () -> new IllegalStateException("Standard emissive composite shader is not loaded")
        );
    }

    private static void release(ScreenEffectOperationContext context,
        @Nullable TextureTarget target) {
        if (target != null) {
            context.releaseScratchTarget(target);
        }
    }

    static int scaledSize(int size, float scale) {
        if (size <= 0) {
            throw new IllegalArgumentException("Target size must be positive");
        }
        if (!Float.isFinite(scale) || scale <= 0.0F || scale > 1.0F) {
            throw new IllegalArgumentException("Target scale must be finite in (0, 1]");
        }
        return Math.max(1, Math.round(size * scale));
    }

    static boolean bloomEnabled(@Nullable BloomSettings bloom) {
        return bloom != null && bloom.intensity() > 0.0F;
    }

    static boolean bloomUsesBlur(EffectQualityTier quality) {
        Objects.requireNonNull(quality, "quality");
        return quality.atLeast(EffectQualityTier.MEDIUM);
    }

    static float compositionAlpha(float coverage, float solidMask, float solidity) {
        return clamp01(coverage) * clamp01(solidMask) * clamp01(solidity);
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
        return Math.min(Math.max(value, 0.0F), 1.0F);
    }

    /** Writes one effect layer into an owned color target. */
    @FunctionalInterface
    public interface LayerPass {
        void render(ScreenEffectOperationContext context, RenderTarget target);
    }

    /** Adds content-specific uniforms to the custom composite shader. */
    @FunctionalInterface
    public interface CompositeUniforms {
        void bind(ScreenEffectOperationContext context, ShaderInstance shader);
    }

    /** Overrides per-frame bloom uniforms after the builder defaults are bound. */
    @FunctionalInterface
    public interface BloomUniforms {
        void bind(ScreenEffectOperationContext context, ShaderInstance shader);
    }

    /** Builder that validates scales and composition policy once. */
    public static final class Builder {
        private final EffectShader shader;
        private @Nullable CompositeUniforms compositeUniforms;
        private @Nullable BloomUniforms bloomUniforms;
        private float emissiveScale = 1.0F;
        private @Nullable BloomSettings bloom;
        private boolean emissionFog = true;
        private float solidity;
        private float compositionFloor;

        private Builder(EffectShader shader) {
            this.shader = shader;
        }

        /** Sets the layer scale relative to the already-floored destination. */
        public Builder emissiveScale(float value) {
            if (!Float.isFinite(value) || value <= 0.0F || value > 1.0F) {
                throw new IllegalArgumentException("Emissive scale must be finite in (0, 1]");
            }
            this.emissiveScale = value;
            return this;
        }

        /** Sets the bloom settings; {@code null} disables bloom. */
        public Builder bloom(@Nullable BloomSettings value) {
            this.bloom = value;
            return this;
        }

        /** Overrides bloom uniforms per frame. */
        public Builder bloomUniforms(@Nullable BloomUniforms value) {
            this.bloomUniforms = value;
            return this;
        }

        /** Enables medium fog on emission and bloom; the scene is never fogged. */
        public Builder emissionFog(boolean value) {
            this.emissionFog = value;
            return this;
        }

        /** Sets the solid-footprint replacement factor. */
        public Builder solidity(float value) {
            if (!Float.isFinite(value) || value < 0.0F || value > 1.0F) {
                throw new IllegalArgumentException("Solidity must be finite in [0, 1]");
            }
            this.solidity = value;
            return this;
        }

        /** Sets the composition floor; {@code 0} keeps the quality-tier scale. */
        public Builder compositionFloor(float value) {
            if (!Float.isFinite(value) || value < 0.0F || value > 1.0F) {
                throw new IllegalArgumentException("Composition floor must be finite in [0, 1]");
            }
            this.compositionFloor = value;
            return this;
        }

        /** Adds content-specific uniforms to a custom composite shader. */
        public Builder compositeUniforms(@Nullable CompositeUniforms value) {
            this.compositeUniforms = value;
            return this;
        }

        /** Builds an immutable, reusable composite. */
        public EmissiveComposite build() {
            return new EmissiveComposite(this);
        }
    }

    private record Plan(int width, int height, EffectQualityTier quality, int effectWidth,
        int effectHeight, int bloomWidth, int bloomHeight, boolean bloom, boolean blur) {
    }
}
