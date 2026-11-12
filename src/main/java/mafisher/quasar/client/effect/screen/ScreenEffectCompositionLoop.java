package mafisher.quasar.client.effect.screen;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import mafisher.quasar.client.effect.render.ScreenEffectBatch;

/**
 * Runs ordered screen-effect batches through the ping-pong composition targets.
 *
 * <p>A failing batch is reported and skipped without advancing the composition source, so later
 * batches still run. The caller owns logging, state restoration, and resetting the failed
 * destination before it is reused.</p>
 */
final class ScreenEffectCompositionLoop {
    private ScreenEffectCompositionLoop() {
    }

    static RenderTarget render(List<ScreenEffectBatch> batches, RenderTarget initial, RenderTarget first,
        RenderTarget second, BatchRenderer renderer, FailureHandler failure, Consumer<ScreenEffectBatch> success) {
        Objects.requireNonNull(batches, "batches");
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(success, "success");
        if (initial == first || initial == second || first == second) {
            throw new IllegalArgumentException("Composition targets must be distinct");
        }

        RenderTarget current = initial;
        RenderTarget next = first;
        for (ScreenEffectBatch batch : batches) {
            try {
                renderer.render(batch, current, next);
                current = next;
                next = next == first ? second : first;
                success.accept(batch);
            } catch (RuntimeException exception) {
                failure.onFailure(batch, current, next, exception);
            }
        }
        return current;
    }

    @FunctionalInterface
    interface BatchRenderer {
        void render(ScreenEffectBatch batch, RenderTarget source, RenderTarget destination);
    }

    @FunctionalInterface
    interface FailureHandler {
        void onFailure(ScreenEffectBatch batch, RenderTarget source, RenderTarget failedDestination,
            RuntimeException failure);
    }
}
