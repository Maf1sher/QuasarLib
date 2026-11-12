package mafisher.quasar.client.effect;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Objects;
import mafisher.quasar.compat.RenderEnvironment;
import mafisher.quasar.effect.EffectQualityTier;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.GlStateBackup;
import org.joml.Matrix4f;

/**
 * Per-frame inputs and draw helpers available to a registered
 * {@link ScreenEffectOperationRenderer}.
 *
 * <p>The operation reads the current composition from {@link #sourceColor()},
 * the untouched captured scene from {@link #rawSceneColor()}, mask coverage and
 * scene depth from {@link #maskTarget()}, and writes its final result to
 * {@link #destination()}. Batches run farthest-first, so the source already
 * contains every effect behind this one. The compositor swaps source and
 * destination after every operation. Contexts are valid only for the current
 * callback.</p>
 */
public final class ScreenEffectOperationContext {
    private final ScreenEffectSettings settings;
    private final EffectRenderContext effect;
    private final ScreenEffectMask mask;
    private final ScreenEffectCameraState camera;
    private final EffectQualityTier quality;
    private final RenderEnvironment environment;
    private final ScreenEffectFogState fogState;
    private final ScreenEffectMediumState mediumState;
    private final ScreenEffectMediumPath mediumPath;
    private final RenderTarget opaqueColor;
    private final RenderTarget foregroundDepth;
    private final RenderTarget sourceColor;
    private final RenderTarget rawSceneColor;
    private final RenderTarget destination;
    private final RenderTarget maskTarget;
    private final ScreenEffectScratchTargetPool scratchTargets;

    ScreenEffectOperationContext(ScreenEffectSettings settings, EffectRenderContext effect,
        ScreenEffectMask mask, ScreenEffectCameraState camera, EffectQualityTier quality,
        RenderEnvironment environment, ScreenEffectFogState fogState, ScreenEffectMediumState mediumState,
        ScreenEffectMediumPath mediumPath, RenderTarget opaqueColor, RenderTarget foregroundDepth,
        RenderTarget sourceColor, RenderTarget rawSceneColor,
        RenderTarget destination, RenderTarget maskTarget, ScreenEffectScratchTargetPool scratchTargets) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.effect = Objects.requireNonNull(effect, "effect");
        this.mask = Objects.requireNonNull(mask, "mask");
        this.camera = Objects.requireNonNull(camera, "camera");
        this.quality = Objects.requireNonNull(quality, "quality");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.fogState = Objects.requireNonNull(fogState, "fogState");
        this.mediumState = Objects.requireNonNull(mediumState, "mediumState");
        this.mediumPath = Objects.requireNonNull(mediumPath, "mediumPath");
        this.opaqueColor = opaqueColor;
        this.foregroundDepth = foregroundDepth;
        this.sourceColor = Objects.requireNonNull(sourceColor, "sourceColor");
        this.rawSceneColor = Objects.requireNonNull(rawSceneColor, "rawSceneColor");
        this.destination = Objects.requireNonNull(destination, "destination");
        this.maskTarget = Objects.requireNonNull(maskTarget, "maskTarget");
        this.scratchTargets = Objects.requireNonNull(scratchTargets, "scratchTargets");
        if (sourceColor == destination) {
            throw new IllegalArgumentException("Source and destination targets must differ");
        }
    }

    /** Returns the selected operation. */
    public ScreenEffectOperation operation() {
        return this.settings.operation();
    }

    /** Returns the effect instance, transform, age, progress, and partial tick. */
    public EffectRenderContext effect() {
        return this.effect;
    }

    /** Returns mask color and strength for this effect. */
    public ScreenEffectMask mask() {
        return this.mask;
    }

    /** Returns the operation-owned settings payload, or {@code null} when absent. */
    public Object settings() {
        return this.settings.settings();
    }

    /** Returns the settings payload as the requested type. */
    public <T> T settings(Class<T> type) {
        return this.settings.settings(type);
    }

    /** Returns immutable camera position, matrices, viewport, and partial tick. */
    public ScreenEffectCameraState camera() {
        return this.camera;
    }

    /** Returns the quality tier selected for this frame. */
    public EffectQualityTier quality() {
        return this.quality;
    }

    /** Returns the resolved compatibility environment for this frame. */
    public RenderEnvironment environment() {
        return this.environment;
    }

    /** Returns the active fog snapshot captured from the render stack. */
    public ScreenEffectFogState fogState() {
        return this.fogState;
    }

    /**
     * Returns the camera and effect medium snapshot for this operation.
     *
     * <p>This is endpoint state: use {@link #mediumPath()} for the ordered
     * crossings along the camera-to-effect line.</p>
     */
    public ScreenEffectMediumState medium() {
        return this.mediumState;
    }

    /** Returns the ordered medium spans and crossings along the camera-to-effect line. */
    public ScreenEffectMediumPath mediumPath() {
        return this.mediumPath;
    }

    /** Returns the pre-translucent scene color, or {@code null} when unavailable. */
    public RenderTarget opaqueColor() {
        return this.opaqueColor;
    }

    /** Returns the scene depth including translucent terrain, or {@code null} when unavailable. */
    public RenderTarget foregroundDepth() {
        return this.foregroundDepth;
    }

    /** Returns whether a pre-translucent color copy is available this frame. */
    public boolean hasOpaqueColor() {
        return this.opaqueColor != null && this.opaqueColor.getColorTextureId() > 0;
    }

    /** Returns whether a depth copy that includes translucent terrain is available this frame. */
    public boolean hasForegroundDepth() {
        return this.foregroundDepth != null && this.foregroundDepth.getDepthTextureId() > 0;
    }

    /** Returns the color captured before this operation; later batches see earlier output. */
    public RenderTarget sourceColor() {
        return this.sourceColor;
    }

    /**
     * Returns the untouched scene captured before any operation in this frame.
     * Use it only when an effect must deliberately ignore earlier screen effects.
     * Lens, refraction, and background-warp passes should sample
     * {@link #sourceColor()} instead: batches compose farthest-first, so the
     * accumulated source contains the effects behind this one and cannot feed
     * back into itself because source and destination are distinct targets.
     */
    public RenderTarget rawSceneColor() {
        return this.rawSceneColor;
    }

    /** Returns the target the operation must write as its final result. */
    public RenderTarget destination() {
        return this.destination;
    }

    /** Returns the mask color target; its depth attachment contains scene depth. */
    public RenderTarget maskTarget() {
        return this.maskTarget;
    }

    /**
     * Acquires a frame-scoped color target. The operation may release it
     * explicitly for reuse; unreleased targets are released after the callback.
     * Targets unused for more than a frame are destroyed by the compositor.
     */
    public TextureTarget acquireScratchTarget(int width, int height) {
        return this.scratchTargets.acquire(width, height);
    }

    /** Releases a target previously returned by {@link #acquireScratchTarget(int, int)}. */
    public void releaseScratchTarget(TextureTarget target) {
        this.scratchTargets.release(target);
    }

    /**
     * Binds the standard composite inputs from the current source color, raw
     * scene, and mask target: {@code SceneSampler}, {@code RawSceneSampler},
     * {@code MaskSampler}, {@code DepthSampler}, {@code BlurSampler},
     * {@code MaskColor}, and {@code MaskStrength}.
     */
    public void bindCompositeInputs(ShaderInstance shader) {
        this.bindCompositeInputs(shader, this.sourceColor);
    }

    /**
     * Binds the standard composite inputs using {@code scene} as the scene
     * color source. Use this for multi-pass operations and then rebind
     * {@code BlurSampler} or another sampler as needed.
     */
    public void bindCompositeInputs(ShaderInstance shader, RenderTarget scene) {
        Objects.requireNonNull(shader, "shader");
        Objects.requireNonNull(scene, "scene");
        this.setSampler(shader, "SceneSampler", scene.getColorTextureId());
        this.setSampler(shader, "RawSceneSampler", this.rawSceneColor.getColorTextureId());
        this.setSampler(shader, "MaskSampler", this.maskTarget.getColorTextureId());
        this.setSampler(shader, "DepthSampler", this.maskTarget.getDepthTextureId());
        this.setSampler(shader, "BlurSampler", scene.getColorTextureId());
        this.setUniform(shader, "MaskColor", this.mask.red(), this.mask.green(), this.mask.blue());
        this.setUniform(shader, "MaskStrength", this.mask.strength());
    }

    /**
     * Binds the pre-translucent scene color and the depth copy that includes
     * translucent terrain as {@code OpaqueColorSampler} and
     * {@code ForegroundDepthSampler}. Falls back to the raw scene and the opaque
     * mask depth so a shader never samples a released texture.
     */
    public void bindForegroundInputs(ShaderInstance shader) {
        Objects.requireNonNull(shader, "shader");
        this.setSampler(shader, "OpaqueColorSampler", this.hasOpaqueColor()
            ? this.opaqueColor.getColorTextureId() : this.rawSceneColor.getColorTextureId());
        this.setSampler(shader, "ForegroundDepthSampler", this.hasForegroundDepth()
            ? this.foregroundDepth.getDepthTextureId() : this.maskTarget.getDepthTextureId());
    }

    /**
     * Binds {@code destination}, forces the composition draw state, applies the
     * shader, and draws one fullscreen quad. The changed enable, blend, depth,
     * cull, color-mask, and scissor state is restored; callers still own target
     * lifetime.
     */
    public void drawFullscreen(RenderTarget destination, ShaderInstance shader) {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(shader, "shader");
        GlStateBackup state = new GlStateBackup();
        GlStateManager._backupGlState(state);

        boolean shaderApplied = false;
        try {
            EffectRenderState.bindTarget(destination);
            RenderSystem.disableScissor();
            RenderSystem.disableCull();
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(false);
            RenderSystem.disableDepthTest();
            RenderSystem.disableBlend();
            shader.apply();
            shaderApplied = true;
            BufferBuilder builder = RenderSystem.renderThreadTesselator()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            builder.addVertex(0.0F, 0.0F, 0.0F);
            builder.addVertex(1.0F, 0.0F, 0.0F);
            builder.addVertex(1.0F, 1.0F, 0.0F);
            builder.addVertex(0.0F, 1.0F, 0.0F);
            BufferUploader.draw(builder.buildOrThrow());
        } finally {
            if (shaderApplied) {
                shader.clear();
            }
            GlStateManager._restoreGlState(state);
        }
    }

    /** Sets a float uniform, failing when the shader does not declare it. */
    public void setUniform(ShaderInstance shader, String name, float value) {
        AbstractUniform uniform = uniform(shader, name);
        uniform.set(value);
    }

    /** Sets a two-component uniform, failing when the shader does not declare it. */
    public void setUniform(ShaderInstance shader, String name, float x, float y) {
        AbstractUniform uniform = uniform(shader, name);
        uniform.set(x, y);
    }

    /** Sets a three-component uniform, failing when the shader does not declare it. */
    public void setUniform(ShaderInstance shader, String name, float x, float y, float z) {
        AbstractUniform uniform = uniform(shader, name);
        uniform.set(x, y, z);
    }

    /** Sets a three-component uniform from a world position, failing when undeclared. */
    public void setUniform(ShaderInstance shader, String name, Vec3 value) {
        Objects.requireNonNull(value, "value");
        this.setUniform(shader, name, (float) value.x, (float) value.y, (float) value.z);
    }

    /** Sets a 4x4 matrix uniform, failing when the shader does not declare it. */
    public void setUniform(ShaderInstance shader, String name, Matrix4f value) {
        Objects.requireNonNull(value, "value");
        uniform(shader, name).set(value);
    }

    /**
     * Binds the camera inputs used by depth-reconstruction screen shaders:
     * {@code InvProjMat}, {@code InvViewMat}, {@code CameraPosition},
     * {@code ViewportWidth}, and {@code ViewportHeight}.
     *
     * <p>The shader must declare all five uniforms. Forward {@code ProjMat} and
     * {@code ModelViewMat} are supplied by {@link ShaderInstance#apply()}.</p>
     */
    public void bindCameraInputs(ShaderInstance shader) {
        Objects.requireNonNull(shader, "shader");
        this.setUniform(shader, "InvProjMat", this.camera.inverseProjectionMatrix());
        this.setUniform(shader, "InvViewMat", this.camera.inverseViewMatrix());
        this.setUniform(shader, "CameraPosition", this.camera.position());
        this.setUniform(shader, "ViewportWidth", this.camera.viewportWidth());
        this.setUniform(shader, "ViewportHeight", this.camera.viewportHeight());
    }

    /** Binds a sampler by name. */
    public void setSampler(ShaderInstance shader, String name, int textureId) {
        Objects.requireNonNull(shader, "shader");
        Objects.requireNonNull(name, "name");
        shader.setSampler(name, textureId);
    }

    /** Copies source color into destination with a nearest-filter full-target blit. */
    public void blitColor(RenderTarget source, RenderTarget destination) {
        ScreenTargetBlit.color(source, destination);
    }

    private static AbstractUniform uniform(ShaderInstance shader, String name) {
        Objects.requireNonNull(shader, "shader");
        Objects.requireNonNull(name, "name");
        return Objects.requireNonNull(shader.getUniform(name), name + " uniform");
    }
}
