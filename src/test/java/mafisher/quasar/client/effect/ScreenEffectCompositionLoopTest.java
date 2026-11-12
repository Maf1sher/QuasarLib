package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScreenEffectCompositionLoopTest {
    @Test
    void failingMiddleBatchDoesNotStopLaterBatches() {
        RenderTarget initial = target("initial");
        RenderTarget first = target("first");
        RenderTarget second = target("second");
        ScreenEffectBatch failing = mock(ScreenEffectBatch.class);
        List<ScreenEffectBatch> batches = List.of(
            mock(ScreenEffectBatch.class), failing, mock(ScreenEffectBatch.class));
        List<String> renders = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        RenderTarget result = ScreenEffectCompositionLoop.render(
            batches, initial, first, second,
            (batch, source, destination) -> {
                renders.add(label(source, destination));
                if (batch == failing) {
                    throw new IllegalStateException("boom");
                }
            },
            (batch, source, failedDestination, failure) -> failures.add(label(source, failedDestination)),
            batch -> {
            }
        );

        assertSame(second, result);
        assertEquals(List.of("initial->first", "first->second", "first->second"), renders);
        assertEquals(List.of("first->second"), failures);
    }

    @Test
    void allBatchesFailingPresentsTheInitialTarget() {
        RenderTarget initial = target("initial");
        RenderTarget first = target("first");
        RenderTarget second = target("second");
        List<ScreenEffectBatch> batches = List.of(mock(ScreenEffectBatch.class),
            mock(ScreenEffectBatch.class));
        List<String> failures = new ArrayList<>();
        List<String> successes = new ArrayList<>();

        RenderTarget result = ScreenEffectCompositionLoop.render(
            batches, initial, first, second,
            (batch, source, destination) -> {
                throw new IllegalStateException("boom");
            },
            (batch, source, failedDestination, failure) -> failures.add(label(source, failedDestination)),
            batch -> successes.add("success")
        );

        assertSame(initial, result);
        assertEquals(List.of("initial->first", "initial->first"), failures);
        assertEquals(List.of(), successes);
    }

    @Test
    void successAfterFailureResumesThePingPongSequence() {
        RenderTarget initial = target("initial");
        RenderTarget first = target("first");
        RenderTarget second = target("second");
        ScreenEffectBatch failing = mock(ScreenEffectBatch.class);
        List<ScreenEffectBatch> batches = List.of(failing,
            mock(ScreenEffectBatch.class), mock(ScreenEffectBatch.class));
        List<String> renders = new ArrayList<>();

        RenderTarget result = ScreenEffectCompositionLoop.render(
            batches, initial, first, second,
            (batch, source, destination) -> {
                renders.add(label(source, destination));
                if (batch == failing) {
                    throw new IllegalStateException("boom");
                }
            },
            (batch, source, failedDestination, failure) -> {
            },
            batch -> {
            }
        );

        assertSame(second, result);
        assertEquals(List.of("initial->first", "initial->first", "first->second"), renders);
    }

    @Test
    void noFailuresAlternatesCompositionTargets() {
        RenderTarget initial = target("initial");
        RenderTarget first = target("first");
        RenderTarget second = target("second");
        List<ScreenEffectBatch> batches = List.of(mock(ScreenEffectBatch.class),
            mock(ScreenEffectBatch.class), mock(ScreenEffectBatch.class), mock(ScreenEffectBatch.class));
        List<String> renders = new ArrayList<>();

        RenderTarget result = ScreenEffectCompositionLoop.render(
            batches, initial, first, second,
            (batch, source, destination) -> renders.add(label(source, destination)),
            (batch, source, failedDestination, failure) -> {
            },
            batch -> {
            }
        );

        assertSame(second, result);
        assertEquals(List.of("initial->first", "first->second", "second->first", "first->second"), renders);
    }

    @Test
    void rejectsDuplicateTargets() {
        RenderTarget initial = target("initial");
        RenderTarget first = target("first");

        assertThrows(IllegalArgumentException.class, () -> ScreenEffectCompositionLoop.render(
            List.of(), initial, first, first,
            (batch, source, destination) -> {
            },
            (batch, source, failedDestination, failure) -> {
            },
            batch -> {
            }
        ));
    }

    private static RenderTarget target(String name) {
        RenderTarget target = mock(RenderTarget.class);
        when(target.toString()).thenReturn(name);
        return target;
    }

    private static String label(RenderTarget source, RenderTarget destination) {
        return source + "->" + destination;
    }
}
