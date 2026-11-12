package mafisher.quasar.client.effect.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EffectGpuTimerTest {

    @Test
    void reportsCombinedWorldAndCompositionCostOncePerFrame() {
        FakeBackend backend = new FakeBackend();
        backend.queue(100L, 40L);
        EffectGpuTimer timer = new EffectGpuTimer(backend);

        frame(timer);

        assertEquals(140L, timer.consumeCompletedNanos());
        assertEquals(-1L, timer.consumeCompletedNanos());
        assertTrue(backend.pending.isEmpty());
    }

    @Test
    void publishesAFrameOnlyAfterBothResultsAreAvailable() {
        FakeBackend backend = new FakeBackend();
        backend.autoResolve = false;
        backend.queue(100L, 40L);
        EffectGpuTimer timer = new EffectGpuTimer(backend);

        frame(timer);
        assertEquals(-1L, timer.consumeCompletedNanos());

        backend.completePending();
        frame(timer);

        assertEquals(140L, timer.consumeCompletedNanos());
    }

    @Test
    void treatsAPhaseWithoutWorkAsZero() {
        FakeBackend backend = new FakeBackend();
        backend.queue(30L);
        EffectGpuTimer timer = new EffectGpuTimer(backend);

        timer.beginWorld();
        timer.endWorld();
        timer.endFrame();

        assertEquals(30L, timer.consumeCompletedNanos());
    }

    @Test
    void discardsUnresolvedFramesWhenTheRingWraps() {
        FakeBackend backend = new FakeBackend();
        backend.autoResolve = false;
        EffectGpuTimer timer = new EffectGpuTimer(backend, 2);

        for (int index = 0; index < 4; index++) {
            frame(timer);
        }

        assertEquals(-1L, timer.consumeCompletedNanos());
        assertEquals(2, backend.pending.size());
        assertEquals(6, backend.deleted.size());
    }

    @Test
    void ignoresDuplicatePhaseCalls() {
        FakeBackend backend = new FakeBackend();
        backend.queue(20L, 5L);
        EffectGpuTimer timer = new EffectGpuTimer(backend);

        timer.beginWorld();
        timer.beginWorld();
        assertEquals(1, backend.beginCalls);
        timer.endWorld();
        timer.endWorld();
        timer.beginLevel();
        timer.beginLevel();
        timer.endLevel();
        timer.endFrame();

        assertEquals(25L, timer.consumeCompletedNanos());
    }

    @Test
    void doesNothingWhenTimerQueriesAreUnsupported() {
        FakeBackend backend = new FakeBackend();
        backend.supported = false;
        EffectGpuTimer timer = new EffectGpuTimer(backend);

        frame(timer);

        assertEquals(0, backend.beginCalls);
        assertEquals(-1L, timer.consumeCompletedNanos());
        assertTrue(backend.pending.isEmpty());
        assertTrue(backend.deleted.isEmpty());
    }

    @Test
    void dropsFramesWhenAPhaseCannotStart() {
        FakeBackend backend = new FakeBackend();
        backend.allowBegin = false;
        EffectGpuTimer timer = new EffectGpuTimer(backend);

        frame(timer);

        assertEquals(-1L, timer.consumeCompletedNanos());
        assertTrue(backend.pending.isEmpty());
    }

    @Test
    void resetReleasesOutstandingQueries() {
        FakeBackend backend = new FakeBackend();
        backend.autoResolve = false;
        EffectGpuTimer timer = new EffectGpuTimer(backend);
        frame(timer);
        assertEquals(2, backend.pending.size());

        timer.reset();

        assertTrue(backend.pending.isEmpty());
        assertEquals(2, backend.deleted.size());
        assertEquals(-1L, timer.consumeCompletedNanos());
    }

    private static void frame(EffectGpuTimer timer) {
        timer.beginWorld();
        timer.endWorld();
        timer.beginLevel();
        timer.endLevel();
        timer.endFrame();
    }

    private static final class FakeBackend implements GpuQueryBackend {
        private final ArrayDeque<Long> results = new ArrayDeque<>();
        private final Map<Integer, Long> pending = new LinkedHashMap<>();
        private final Map<Integer, Long> completed = new LinkedHashMap<>();
        private final List<Integer> deleted = new ArrayList<>();
        private boolean supported = true;
        private boolean autoResolve = true;
        private boolean allowBegin = true;
        private int nextQuery = 1;
        private int activeQuery;
        private int beginCalls;

        void queue(long worldNanos, long levelNanos) {
            this.results.add(worldNanos);
            this.results.add(levelNanos);
        }

        void queue(long nanos) {
            this.results.add(nanos);
        }

        void completePending() {
            this.completed.putAll(this.pending);
            this.pending.clear();
        }

        @Override
        public boolean supported() {
            return this.supported;
        }

        @Override
        public int beginQuery() {
            this.beginCalls++;
            if (!this.supported || !this.allowBegin || this.activeQuery != 0) {
                return 0;
            }
            this.activeQuery = this.nextQuery++;
            return this.activeQuery;
        }

        @Override
        public void endQuery(int query) {
            if (this.activeQuery != query) {
                throw new AssertionError("endQuery for a query that is not active");
            }
            long value = this.results.isEmpty() ? 0L : this.results.removeFirst();
            if (this.autoResolve) {
                this.completed.put(this.activeQuery, value);
            } else {
                this.pending.put(this.activeQuery, value);
            }
            this.activeQuery = 0;
        }

        @Override
        public boolean isResultAvailable(int query) {
            return this.completed.containsKey(query);
        }

        @Override
        public long resultNanos(int query) {
            return this.completed.remove(query);
        }

        @Override
        public void deleteQuery(int query) {
            this.deleted.add(query);
            this.pending.remove(query);
            this.completed.remove(query);
        }
    }
}
