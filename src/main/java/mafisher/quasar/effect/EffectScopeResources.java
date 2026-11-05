package mafisher.quasar.effect;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** LIFO cleanup ledger with idempotent close semantics. */
final class EffectScopeResources implements AutoCloseable {
    private final Deque<Runnable> cleanups = new ArrayDeque<>();
    private boolean closed;

    void track(Runnable cleanup) {
        Objects.requireNonNull(cleanup, "cleanup");
        if (this.closed) {
            cleanup.run();
            return;
        }
        this.cleanups.push(cleanup);
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        RuntimeException failure = null;
        while (!this.cleanups.isEmpty()) {
            try {
                this.cleanups.pop().run();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
