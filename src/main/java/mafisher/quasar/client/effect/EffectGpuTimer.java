package mafisher.quasar.client.effect;

import java.util.Arrays;
import java.util.Objects;

/**
 * Non-blocking GPU timer for the engine's world pass and screen-composition pass.
 *
 * <p>Each frame owns one slot in a small ring. Both phases issue one elapsed-time query; the slot publishes a
 * combined cost only after both results are readable, so the client never stalls on
 * {@code GL_QUERY_RESULT}. A phase that does no work counts as zero, while a phase that could not start drops the
 * frame. Slots that do not resolve before the ring wraps are discarded, keeping the number of live queries
 * bounded. This class contains no OpenGL calls and is covered by headless tests with a fake backend.
 *
 * <p>Only the render thread may begin, end, poll, or reset phases; the controller defers resets requested by
 * lifecycle events on other threads.
 */
final class EffectGpuTimer {
    private static final int DEFAULT_SLOTS = 4;

    private final GpuQueryBackend backend;
    private final int[] worldQueries;
    private final int[] levelQueries;
    private final long[] worldNanos;
    private final long[] levelNanos;
    private final boolean[] worldActive;
    private final boolean[] levelActive;
    private final boolean[] worldTried;
    private final boolean[] levelTried;
    private final boolean[] worldIssued;
    private final boolean[] levelIssued;
    private final boolean[] reported;
    private final long[] frameIds;
    private long nextFrameId;
    private long lastReportedFrameId = -1L;
    private long completedNanos = -1L;
    private int slot;
    private boolean disabled;

    EffectGpuTimer(GpuQueryBackend backend) {
        this(backend, DEFAULT_SLOTS);
    }

    EffectGpuTimer(GpuQueryBackend backend, int slots) {
        if (slots < 1) {
            throw new IllegalArgumentException("slots must be positive");
        }
        this.backend = Objects.requireNonNull(backend, "backend");
        this.worldQueries = new int[slots];
        this.levelQueries = new int[slots];
        this.worldNanos = new long[slots];
        this.levelNanos = new long[slots];
        this.worldActive = new boolean[slots];
        this.levelActive = new boolean[slots];
        this.worldTried = new boolean[slots];
        this.levelTried = new boolean[slots];
        this.worldIssued = new boolean[slots];
        this.levelIssued = new boolean[slots];
        this.reported = new boolean[slots];
        this.frameIds = new long[slots];
        Arrays.fill(this.worldNanos, -1L);
        Arrays.fill(this.levelNanos, -1L);
        this.frameIds[0] = ++this.nextFrameId;
    }

    /** Starts the world-pass query for the current frame. */
    void beginWorld() {
        if (this.disabled || this.worldActive[this.slot] || this.worldQueries[this.slot] != 0
            || this.worldIssued[this.slot]) {
            return;
        }
        this.worldTried[this.slot] = true;
        if (!this.backend.supported()) {
            this.disabled = true;
            return;
        }
        int query = this.backend.beginQuery();
        if (query != 0) {
            this.worldQueries[this.slot] = query;
            this.worldActive[this.slot] = true;
            this.worldIssued[this.slot] = true;
        }
    }

    /** Ends the world-pass query for the current frame. */
    void endWorld() {
        if (!this.worldActive[this.slot]) {
            return;
        }
        this.backend.endQuery(this.worldQueries[this.slot]);
        this.worldActive[this.slot] = false;
    }

    /** Starts the screen-composition query for the current frame. */
    void beginLevel() {
        if (this.disabled || this.levelActive[this.slot] || this.levelQueries[this.slot] != 0
            || this.levelIssued[this.slot]) {
            return;
        }
        this.levelTried[this.slot] = true;
        if (!this.backend.supported()) {
            this.disabled = true;
            return;
        }
        int query = this.backend.beginQuery();
        if (query != 0) {
            this.levelQueries[this.slot] = query;
            this.levelActive[this.slot] = true;
            this.levelIssued[this.slot] = true;
        }
    }

    /** Ends the screen-composition query for the current frame. */
    void endLevel() {
        if (!this.levelActive[this.slot]) {
            return;
        }
        this.backend.endQuery(this.levelQueries[this.slot]);
        this.levelActive[this.slot] = false;
    }

    /**
     * Marks the end of the frame: closes any query left open by an exception path, polls every slot for completed
     * results, and advances to the next frame slot. Phases that did not run count as zero.
     */
    void endFrame() {
        if (this.levelActive[this.slot]) {
            this.backend.endQuery(this.levelQueries[this.slot]);
            this.levelActive[this.slot] = false;
        }
        if (this.worldActive[this.slot]) {
            this.backend.endQuery(this.worldQueries[this.slot]);
            this.worldActive[this.slot] = false;
        }
        this.poll();
        this.advance();
    }

    /**
     * Returns the newest frame cost that became readable since the previous call, or {@code -1} when no result is
     * ready. The value combines the world and composition GPU passes of that frame.
     */
    long consumeCompletedNanos() {
        long value = this.completedNanos;
        this.completedNanos = -1L;
        return value;
    }

    /** Releases outstanding queries and starts a fresh frame ring. */
    void reset() {
        for (int index = 0; index < this.worldQueries.length; index++) {
            discardSlot(index);
        }
        this.slot = 0;
        this.nextFrameId = 1L;
        this.lastReportedFrameId = -1L;
        this.completedNanos = -1L;
        this.disabled = false;
        this.frameIds[0] = this.nextFrameId;
    }

    private void poll() {
        for (int index = 0; index < this.worldQueries.length; index++) {
            if (this.frameIds[index] <= 0L) {
                continue;
            }
            this.worldNanos[index] = drain(this.worldQueries, this.worldNanos, index);
            this.levelNanos[index] = drain(this.levelQueries, this.levelNanos, index);
            if (this.reported[index] || failed(this.worldTried, this.worldIssued, index)
                || failed(this.levelTried, this.levelIssued, index)) {
                continue;
            }
            if ((this.worldTried[index] && this.worldNanos[index] < 0L)
                || (this.levelTried[index] && this.levelNanos[index] < 0L)) {
                continue;
            }
            long world = this.worldTried[index] ? this.worldNanos[index] : 0L;
            long level = this.levelTried[index] ? this.levelNanos[index] : 0L;
            this.reported[index] = true;
            if (this.frameIds[index] > this.lastReportedFrameId) {
                this.lastReportedFrameId = this.frameIds[index];
                this.completedNanos = Math.max(0L, world + level);
            }
        }
    }

    private static boolean failed(boolean[] tried, boolean[] issued, int index) {
        return tried[index] && !issued[index];
    }

    private long drain(int[] queries, long[] results, int index) {
        int query = queries[index];
        if (query == 0 || !this.backend.isResultAvailable(query)) {
            return results[index];
        }
        long nanos = Math.max(0L, this.backend.resultNanos(query));
        this.backend.deleteQuery(query);
        queries[index] = 0;
        return nanos;
    }

    private void advance() {
        int next = (this.slot + 1) % this.worldQueries.length;
        discardSlot(next);
        this.slot = next;
        this.frameIds[next] = ++this.nextFrameId;
    }

    private void discardSlot(int index) {
        if (this.worldQueries[index] != 0) {
            this.backend.deleteQuery(this.worldQueries[index]);
        }
        if (this.levelQueries[index] != 0) {
            this.backend.deleteQuery(this.levelQueries[index]);
        }
        this.worldQueries[index] = 0;
        this.levelQueries[index] = 0;
        this.worldNanos[index] = -1L;
        this.levelNanos[index] = -1L;
        this.worldActive[index] = false;
        this.levelActive[index] = false;
        this.worldTried[index] = false;
        this.levelTried[index] = false;
        this.worldIssued[index] = false;
        this.levelIssued[index] = false;
        this.reported[index] = false;
        this.frameIds[index] = 0L;
    }
}
