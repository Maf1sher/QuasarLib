package mafisher.quasar.client.effect.quality;

/**
 * Minimal timer-query backend used by {@link EffectGpuTimer}. Keeping OpenGL calls behind this interface leaves the
 * frame-pairing and discard logic headlessly testable without a GL context.
 */
interface GpuQueryBackend {
    /** Returns whether timer queries can be issued on the current thread. */
    boolean supported();

    /**
     * Creates and starts one elapsed-time query.
     *
     * @return the query handle, or {@code 0} when no query can be started right now
     */
    int beginQuery();

    /** Ends {@code query} if it is still the active elapsed-time query. */
    void endQuery(int query);

    /** Returns whether the result of {@code query} can be read without blocking. */
    boolean isResultAvailable(int query);

    /** Reads a result that {@link #isResultAvailable(int)} reported as available. */
    long resultNanos(int query);

    /** Releases a query handle. */
    void deleteQuery(int query);
}
