package mafisher.quasar.client.effect;

import java.util.Objects;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Supported render-type helpers for consumer visual effects. The internal {@link EffectRenderTypes} factory remains
 * an implementation detail; these methods are the public contract.
 */
public final class QuasarRenderTypes {
    private QuasarRenderTypes() {
    }

    /**
     * Returns a translucent, quad-oriented world render type that writes color without depth. The type renders into
     * the particles target, uses Minecraft's translucent shader, and preserves the depth behavior that prevents water
     * and glass from showing a square occlusion artifact.
     *
     * <p>The texture is sampled as RGBA and is mipmapped. The returned type is cached per texture.</p>
     *
     * @param texture the effect texture
     * @return a world-visual render type
     */
    public static RenderType worldVisual(ResourceLocation texture) {
        return EffectRenderTypes.visual(Objects.requireNonNull(texture, "texture"));
    }

    /**
     * Returns a world render type that uses {@code shader} with the default world options.
     *
     * @param shader the consumer-owned shader
     * @param texture the effect texture
     * @return a shader-backed world render type
     * @see EffectRenderTypeOptions#WORLD_DEFAULT
     */
    public static RenderType worldVisual(EffectShader shader, ResourceLocation texture) {
        return worldVisual(shader, texture, EffectRenderTypeOptions.WORLD_DEFAULT);
    }

    /**
     * Returns a world render type that uses {@code shader} and the supplied render-state options.
     *
     * <p>The vertex format comes from the shader. The returned type is cached by shader, texture, and options.</p>
     *
     * @param shader the consumer-owned shader
     * @param texture the effect texture
     * @param options the render-state options
     * @return a shader-backed world render type
     */
    public static RenderType worldVisual(EffectShader shader, ResourceLocation texture,
        EffectRenderTypeOptions options) {
        return EffectRenderTypes.shaderWorld(
            Objects.requireNonNull(shader, "shader"),
            Objects.requireNonNull(texture, "texture"),
            Objects.requireNonNull(options, "options")
        );
    }

    /**
     * Returns a render type that writes texture coverage into the screen-effect mask target. Use it only from
     * {@link ScreenEffectRenderer#screenMaskRenderType(EffectRenderContext)} while the compositor has bound its mask
     * target; returning it from a world pass is unsupported.
     *
     * <p>The textured quad is emitted with the {@code BLOCK} vertex format. The returned type is cached per texture.</p>
     *
     * @param texture the mask texture
     * @return a screen-mask render type
     */
    public static RenderType screenEffectMask(ResourceLocation texture) {
        return EffectRenderTypes.screenMask(Objects.requireNonNull(texture, "texture"));
    }

    /**
     * Returns a screen-mask render type that uses {@code shader} with the default mask options.
     *
     * @param shader the consumer-owned shader
     * @param texture the mask texture
     * @return a shader-backed screen-mask render type
     * @see EffectRenderTypeOptions#MASK_DEFAULT
     */
    public static RenderType screenEffectMask(EffectShader shader, ResourceLocation texture) {
        return screenEffectMask(shader, texture, EffectRenderTypeOptions.MASK_DEFAULT);
    }

    /**
     * Returns a screen-mask render type that uses {@code shader} and the supplied render-state options.
     *
     * <p>The output always targets the compositor mask buffer, regardless of the supplied options.</p>
     *
     * @param shader the consumer-owned shader
     * @param texture the mask texture
     * @param options the render-state options
     * @return a shader-backed screen-mask render type
     */
    public static RenderType screenEffectMask(EffectShader shader, ResourceLocation texture,
        EffectRenderTypeOptions options) {
        return EffectRenderTypes.shaderMask(
            Objects.requireNonNull(shader, "shader"),
            Objects.requireNonNull(texture, "texture"),
            Objects.requireNonNull(options, "options")
        );
    }
}
