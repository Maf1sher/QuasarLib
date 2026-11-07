package mafisher.quasar.client.effect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.renderer.RenderType;

/**
 * A set of compatible screen-effect frames rendered into one mask pass and composed by one operation. Standalone
 * effects form a batch of one; segmented cells batch per coordinator when their renderer, settings, mask, and mask
 * render type match.
 */
record ScreenEffectBatch(
    ScreenEffectRenderer renderer,
    ScreenEffectSettings settings,
    ScreenEffectMask mask,
    EffectRenderContext operationContext,
    RenderType maskRenderType,
    List<ScreenEffectFrame> frames
) {
    ScreenEffectBatch {
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(mask, "mask");
        Objects.requireNonNull(operationContext, "operationContext");
        Objects.requireNonNull(maskRenderType, "maskRenderType");
        frames = List.copyOf(frames);
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("A screen effect batch requires at least one frame");
        }
    }

    /**
     * Groups frames by owner and render inputs while preserving first-seen batch order and frame order inside each
     * batch. Only compatible cells share a batch, so renderers that vary settings or masks per cell keep separate
     * operations.
     */
    static List<ScreenEffectBatch> group(List<ScreenEffectFrame> frames) {
        Objects.requireNonNull(frames, "frames");
        Map<BatchKey, List<ScreenEffectFrame>> grouped = new LinkedHashMap<>();
        for (ScreenEffectFrame frame : frames) {
            Objects.requireNonNull(frame, "frame");
            BatchKey key = new BatchKey(frame.ownerId(), frame.renderer(), frame.settings(),
                frame.mask(), frame.maskRenderType());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(frame);
        }

        List<ScreenEffectBatch> batches = new ArrayList<>(grouped.size());
        for (List<ScreenEffectFrame> group : grouped.values()) {
            ScreenEffectFrame first = group.getFirst();
            batches.add(new ScreenEffectBatch(first.renderer(), first.settings(), first.mask(),
                first.context(), first.maskRenderType(), group));
        }
        return List.copyOf(batches);
    }

    private record BatchKey(UUID ownerId, ScreenEffectRenderer renderer, ScreenEffectSettings settings,
        ScreenEffectMask mask, RenderType maskRenderType) {
    }
}
