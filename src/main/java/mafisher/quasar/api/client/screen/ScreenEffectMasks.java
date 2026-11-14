package mafisher.quasar.api.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Objects;
import mafisher.quasar.api.client.render.EffectMesh;
import mafisher.quasar.api.client.render.EffectMeshes;
import mafisher.quasar.api.client.render.EffectRenderTypeOptions;
import mafisher.quasar.api.client.render.QuasarRenderTypes;
import mafisher.quasar.client.effect.render.EffectShaders;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Coverage-only mask helpers for screen effects.
 *
 * <p>A full-screen screen effect must not use a depth-tested world proxy: the
 * proxy edge clips the effect and a bounding volume paints over foreground
 * geometry. {@link #fullscreen()} returns a screen-space quad render type with
 * depth testing and depth writes disabled, and {@link #drawFullscreen} emits
 * that quad. Use it only from
 * {@link ScreenEffectRenderer#screenMaskRenderType(mafisher.quasar.api.client.render.EffectRenderContext)}
 * and pair it with the active mask target.</p>
 */
public final class ScreenEffectMasks {
    /** The render-state options used by {@link #fullscreen()}. */
    static final EffectRenderTypeOptions FULLSCREEN_OPTIONS = new EffectRenderTypeOptions(
        EffectRenderTypeOptions.Transparency.TRANSLUCENT,
        false,
        true,
        false,
        false,
        false,
        true
    );
    private static final ResourceLocation WHITE_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final int FULLBRIGHT_LIGHT = 0x00F000F0;
    private static final RenderType FULLSCREEN = QuasarRenderTypes.screenEffectMask(
        EffectShaders.SCREEN_FULLSCREEN_MASK, WHITE_TEXTURE, FULLSCREEN_OPTIONS);

    private ScreenEffectMasks() {
    }

    /**
     * Returns the cached coverage-only, full-screen mask render type.
     *
     * <p>The shader uses the screen-space vertex stage, so the world pose and
     * projection cannot clip the quad. The mask writes color and alpha without
     * depth test or depth write; coverage comes from {@link ScreenEffectMask}
     * strength in the operation.</p>
     */
    public static RenderType fullscreen() {
        return FULLSCREEN;
    }

    /**
     * Emits one full-screen coverage quad with {@code renderType}.
     *
     * <p>Pair this with a type returned by {@link #fullscreen()}. Do not end the
     * buffer batch: the compositor ends the mask batch after every cell.</p>
     */
    public static void drawFullscreen(RenderType renderType, PoseStack poseStack,
        MultiBufferSource bufferSource) {
        Objects.requireNonNull(renderType, "renderType");
        Objects.requireNonNull(poseStack, "poseStack");
        Objects.requireNonNull(bufferSource, "bufferSource");
        EffectMesh quad = EffectMeshes.verticalPlane();
        quad.emit(
            bufferSource.getBuffer(renderType),
            poseStack.last(),
            1.0F,
            1.0F,
            1.0F,
            1.0F,
            OverlayTexture.NO_OVERLAY,
            FULLBRIGHT_LIGHT
        );
    }
}
