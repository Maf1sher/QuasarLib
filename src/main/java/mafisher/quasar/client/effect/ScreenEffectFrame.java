package mafisher.quasar.client.effect;

import java.util.Objects;

/** A visible, active screen-effect contribution prepared for the current render frame. */
record ScreenEffectFrame(
    EffectRenderContext context,
    ScreenEffectRenderer renderer,
    ScreenEffectSettings settings,
    ScreenEffectMask mask
) {
    ScreenEffectFrame {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(mask, "mask");
    }
}
