package mafisher.quasar.api.client.screen;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.compat.RenderEnvironment;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectSpace;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

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
public interface ScreenEffectOperationContext {
    /** Returns the selected operation. */
    ScreenEffectOperation operation();

    /** Returns the effect instance, transform, age, progress, and partial tick. */
    EffectRenderContext effect();

    /** Returns the server-authored parameter overrides for this effect instance. */
    default mafisher.quasar.api.effect.EffectParameterState parameters() {
        return this.effect().parameters();
    }

    /** Returns mask color and strength for this effect. */
    ScreenEffectMask mask();

    /** Returns the operation-owned settings payload, or {@code null} when absent. */
    @Nullable Object settings();

    /** Returns the settings payload as the requested type. */
    <T> T settings(Class<T> type);

    /** Returns immutable camera position, matrices, viewport, and partial tick. */
    ScreenEffectCameraState camera();

    /**
     * Builds the effect space from the camera snapshot and the effect transform.
     *
     * <p>This is the same camera-relative frame used by world passes in the same frame.</p>
     */
    default EffectSpace effectSpace() {
        Vec3 position = this.camera().position();
        return EffectSpace.from(this.effect().transform(), this.effect().scale(),
            new EffectPoint(position.x, position.y, position.z));
    }

    /** Returns the clamped ambient daylight factor for this effect. */
    default float daylight() {
        return this.effect().daylight();
    }

    /** Returns the quality tier selected for this frame. */
    EffectQualityTier quality();

    /** Returns the resolved compatibility environment for this frame. */
    RenderEnvironment environment();

    /** Returns the active fog snapshot captured from the render stack. */
    ScreenEffectFogState fogState();

    /**
     * Returns the camera and effect medium snapshot for this operation.
     *
     * <p>This is endpoint state: use {@link #mediumPath()} for the ordered
     * crossings along the camera-to-effect line.</p>
     */
    ScreenEffectMediumState medium();

    /** Returns the ordered medium spans and crossings along the camera-to-effect line. */
    ScreenEffectMediumPath mediumPath();

    /** Returns the pre-translucent scene color, or {@code null} when unavailable. */
    @Nullable RenderTarget opaqueColor();

    /** Returns the scene depth including translucent terrain, or {@code null} when unavailable. */
    @Nullable RenderTarget foregroundDepth();

    /** Returns whether a pre-translucent color copy is available this frame. */
    boolean hasOpaqueColor();

    /** Returns whether a depth copy that includes translucent terrain is available this frame. */
    boolean hasForegroundDepth();

    /** Returns the color captured before this operation; later batches see earlier output. */
    RenderTarget sourceColor();

    /**
     * Returns the untouched scene captured before any operation in this frame.
     * Use it only when an effect must deliberately ignore earlier screen effects.
     * Lens, refraction, and background-warp passes should sample
     * {@link #sourceColor()} instead: batches compose farthest-first, so the
     * accumulated source contains the effects behind this one and cannot feed
     * back into itself because source and destination are distinct targets.
     */
    RenderTarget rawSceneColor();

    /** Returns the target the operation must write as its final result. */
    RenderTarget destination();

    /** Returns the mask color target; its depth attachment contains scene depth. */
    RenderTarget maskTarget();

    /**
     * Acquires a frame-scoped color target. The operation may release it
     * explicitly for reuse; unreleased targets are released after the callback.
     * Targets unused for more than a frame are destroyed by the compositor.
     */
    TextureTarget acquireScratchTarget(int width, int height);

    /** Releases a target previously returned by {@link #acquireScratchTarget(int, int)}. */
    void releaseScratchTarget(TextureTarget target);

    /**
     * Binds the standard composite inputs from the current source color, raw
     * scene, and mask target: {@code SceneSampler}, {@code RawSceneSampler},
     * {@code MaskSampler}, {@code DepthSampler}, {@code BlurSampler},
     * {@code MaskColor}, and {@code MaskStrength}.
     */
    void bindCompositeInputs(ShaderInstance shader);

    /**
     * Binds the standard composite inputs using {@code scene} as the scene
     * color source. Use this for multi-pass operations and then rebind
     * {@code BlurSampler} or another sampler as needed.
     */
    void bindCompositeInputs(ShaderInstance shader, RenderTarget scene);

    /**
     * Binds the pre-translucent scene color and the depth copy that includes
     * translucent terrain as {@code OpaqueColorSampler} and
     * {@code ForegroundDepthSampler}. Falls back to the raw scene and the opaque
     * mask depth so a shader never samples a released texture.
     */
    void bindForegroundInputs(ShaderInstance shader);

    /**
     * Binds {@code destination}, forces the composition draw state, applies the
     * shader, and draws one fullscreen quad. The changed enable, blend, depth,
     * cull, color-mask, and scissor state is restored; callers still own target
     * lifetime.
     */
    void drawFullscreen(RenderTarget destination, ShaderInstance shader);

    /** Sets a float uniform, failing when the shader does not declare it. */
    void setUniform(ShaderInstance shader, String name, float value);

    /** Sets a two-component uniform, failing when the shader does not declare it. */
    void setUniform(ShaderInstance shader, String name, float x, float y);

    /** Sets a three-component uniform, failing when the shader does not declare it. */
    void setUniform(ShaderInstance shader, String name, float x, float y, float z);

    /** Sets a four-component uniform, failing when the shader does not declare it. */
    void setUniform(ShaderInstance shader, String name, float x, float y, float z, float w);

    /** Sets a three-component uniform from a world position, failing when undeclared. */
    void setUniform(ShaderInstance shader, String name, Vec3 value);

    /** Sets a 4x4 matrix uniform, failing when the shader does not declare it. */
    void setUniform(ShaderInstance shader, String name, Matrix4f value);

    /**
     * Binds the camera inputs used by depth-reconstruction screen shaders:
     * {@code InvProjMat}, {@code InvViewMat}, {@code CameraPosition},
     * {@code ViewportWidth}, and {@code ViewportHeight}.
     *
     * <p>The shader must declare all five uniforms. Forward {@code ProjMat} and
     * {@code ModelViewMat} are supplied by {@link ShaderInstance#apply()}.</p>
     */
    void bindCameraInputs(ShaderInstance shader);

    /** Binds a sampler by name. */
    void setSampler(ShaderInstance shader, String name, int textureId);

    /** Copies source color into destination with a nearest-filter full-target blit. */
    void blitColor(RenderTarget source, RenderTarget destination);
}
