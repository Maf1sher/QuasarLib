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
import mafisher.quasar.effect.EffectQualityTier;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Per-frame inputs and draw helpers available to a registered
 * {@link ScreenEffectOperationRenderer}.
 *
 * <p>The operation reads scene color from {@link #sourceColor()}, mask coverage
 * and scene depth from {@link #maskTarget()}, and writes its final result to
 * {@link #destination()}. The compositor swaps source and destination after
 * every operation. Contexts are valid only for the current callback.</p>
 */
public final class ScreenEffectOperationContext {
    private static final int READ_FRAMEBUFFER = 0x8CA8;
    private static final int DRAW_FRAMEBUFFER = 0x8CA9;
    private static final int COLOR_BUFFER_BIT = 0x00004000;
    private static final int NEAREST_FILTER = 0x2600;

    private final ScreenEffectSettings settings;
    private final EffectRenderContext effect;
    private final ScreenEffectMask mask;
    private final ScreenEffectCameraState camera;
    private final EffectQualityTier quality;
    private final RenderTarget sourceColor;
    private final RenderTarget destination;
    private final RenderTarget maskTarget;
    private final ScreenEffectScratchTargetPool scratchTargets;

    ScreenEffectOperationContext(ScreenEffectSettings settings, EffectRenderContext effect,
        ScreenEffectMask mask, ScreenEffectCameraState camera, EffectQualityTier quality,
        RenderTarget sourceColor, RenderTarget destination, RenderTarget maskTarget,
        ScreenEffectScratchTargetPool scratchTargets) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.effect = Objects.requireNonNull(effect, "effect");
        this.mask = Objects.requireNonNull(mask, "mask");
        this.camera = Objects.requireNonNull(camera, "camera");
        this.quality = Objects.requireNonNull(quality, "quality");
        this.sourceColor = Objects.requireNonNull(sourceColor, "sourceColor");
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

    /** Returns the color captured before this operation. */
    public RenderTarget sourceColor() {
        return this.sourceColor;
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
     * The returned target may be reused by a later operation.
     */
    public TextureTarget acquireScratchTarget(int width, int height) {
        return this.scratchTargets.acquire(width, height);
    }

    /** Releases a target previously returned by {@link #acquireScratchTarget(int, int)}. */
    public void releaseScratchTarget(TextureTarget target) {
        this.scratchTargets.release(target);
    }

    /**
     * Binds the standard composite inputs from the current source color and
     * mask target: {@code SceneSampler}, {@code MaskSampler},
     * {@code DepthSampler}, {@code BlurSampler}, {@code MaskColor}, and
     * {@code MaskStrength}.
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
        this.setSampler(shader, "MaskSampler", this.maskTarget.getColorTextureId());
        this.setSampler(shader, "DepthSampler", this.maskTarget.getDepthTextureId());
        this.setSampler(shader, "BlurSampler", scene.getColorTextureId());
        this.setUniform(shader, "MaskColor", this.mask.red(), this.mask.green(), this.mask.blue());
        this.setUniform(shader, "MaskStrength", this.mask.strength());
    }

    /**
     * Binds {@code destination}, resets color/depth/blend state, applies the
     * shader, and draws one fullscreen quad. Callers still own target lifetime.
     */
    public void drawFullscreen(RenderTarget destination, ShaderInstance shader) {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(shader, "shader");
        destination.bindWrite(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();

        boolean shaderApplied = false;
        try {
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
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        GlStateManager._glBindFramebuffer(READ_FRAMEBUFFER, source.frameBufferId);
        GlStateManager._glBindFramebuffer(DRAW_FRAMEBUFFER, destination.frameBufferId);
        GlStateManager._glBlitFrameBuffer(
            0, 0, source.width, source.height,
            0, 0, destination.width, destination.height,
            COLOR_BUFFER_BIT, NEAREST_FILTER
        );
    }

    private static AbstractUniform uniform(ShaderInstance shader, String name) {
        Objects.requireNonNull(shader, "shader");
        Objects.requireNonNull(name, "name");
        return Objects.requireNonNull(shader.getUniform(name), name + " uniform");
    }
}
