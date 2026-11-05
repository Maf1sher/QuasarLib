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
     * Returns a render type that writes texture alpha as coverage into the screen-effect mask target. Use it only from
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
}
