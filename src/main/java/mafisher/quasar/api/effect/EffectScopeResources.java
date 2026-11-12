package mafisher.quasar.api.effect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** LIFO cleanup ledger with idempotent close and keyed cancellation semantics. */
final class EffectScopeResources implements AutoCloseable {
    private final Map<Object, Runnable> cleanups = new LinkedHashMap<>();
    private boolean closed;

    void track(Runnable cleanup) {
        track(new Object(), cleanup);
    }

    void track(Object key, Runnable cleanup) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(cleanup, "cleanup");
        if (this.closed) {
            cleanup.run();
            return;
        }
        this.cleanups.put(key, cleanup);
    }

    void untrack(Object key) {
        Objects.requireNonNull(key, "key");
        this.cleanups.remove(key);
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        List<Runnable> pending = new ArrayList<>(this.cleanups.values());
        this.cleanups.clear();
        RuntimeException failure = null;
        for (int index = pending.size() - 1; index >= 0; index--) {
            try {
                pending.get(index).run();
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
