package mafisher.quasar.client.effect;

import java.util.ArrayList;
import java.util.Comparator;
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

    /**
     * Returns the batches ordered farthest-first for composition. Later batches
     * draw over earlier ones, so a nearer effect must follow every effect behind
     * it; an operation that samples the accumulated source then sees the image
     * of the effects behind it instead of erasing them. A segmented batch is
     * placed by its nearest cell, and the owner id keeps equal distances
     * deterministic.
     */
    static List<ScreenEffectBatch> depthOrdered(List<ScreenEffectBatch> batches) {
        Objects.requireNonNull(batches, "batches");
        List<ScreenEffectBatch> ordered = new ArrayList<>(batches);
        ordered.sort(Comparator
            .comparingDouble((ScreenEffectBatch batch) -> batch.nearestCameraDistance())
            .reversed()
            .thenComparing(batch -> batch.frames().getFirst().ownerId()));
        return List.copyOf(ordered);
    }

    private double nearestCameraDistance() {
        double nearest = Double.POSITIVE_INFINITY;
        for (ScreenEffectFrame frame : this.frames) {
            nearest = Math.min(nearest, frame.context().lod().cameraDistance());
        }
        return nearest;
    }

    private record BatchKey(UUID ownerId, ScreenEffectRenderer renderer, ScreenEffectSettings settings,
        ScreenEffectMask mask, RenderType maskRenderType) {
    }
}
