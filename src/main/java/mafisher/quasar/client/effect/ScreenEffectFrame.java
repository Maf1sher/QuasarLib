package mafisher.quasar.client.effect;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.renderer.RenderType;

/**
 * One visible screen-effect cell prepared for the current frame. Frames that share an owner, renderer, settings, mask,
 * and mask render type are combined into one {@link ScreenEffectBatch}.
 */
record ScreenEffectFrame(
    UUID ownerId,
    EffectRenderContext context,
    ScreenEffectRenderer renderer,
    ScreenEffectSettings settings,
    ScreenEffectMask mask,
    RenderType maskRenderType
) {
    ScreenEffectFrame {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(maskRenderType, "maskRenderType");
    }
}
