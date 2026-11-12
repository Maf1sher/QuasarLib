package mafisher.quasar.client.effect.render;

import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.client.EffectRenderContext;
import mafisher.quasar.api.client.ScreenEffectMask;
import mafisher.quasar.api.client.ScreenEffectRenderer;
import mafisher.quasar.api.client.ScreenEffectSettings;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.ApiStatus;

/**
 * One visible screen-effect cell prepared for the current frame. Frames that share an owner, renderer, settings, mask,
 * and mask render type are combined into one {@link ScreenEffectBatch}.
 */
@ApiStatus.Internal
public record ScreenEffectFrame(
    UUID ownerId,
    EffectRenderContext context,
    ScreenEffectRenderer renderer,
    ScreenEffectSettings settings,
    ScreenEffectMask mask,
    RenderType maskRenderType
) {
    public ScreenEffectFrame {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(maskRenderType, "maskRenderType");
    }
}
