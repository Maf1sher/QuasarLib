package mafisher.quasar.client.effect.render;

/**
 * OpenGL-facing probe used by {@link ClientDepthOcclusion}. Implementations must never block on a
 * query result: {@link #issue} starts an asynchronous query and {@link #isResultAvailable} polls it.
 */
interface DepthProbeBackend {
    /** Returns whether the backend can issue occlusion queries on the current thread. */
    boolean supported();

    /**
     * Issues one conservative occlusion query for a world-space box against the request's depth
     * target.
     *
     * @param request reusable probe request, valid only for this call
     * @return a query handle, or {@code 0} when no probe could be issued
     */
    int issue(DepthProbeRequest request);

    /** Returns whether the query result can be read without blocking. */
    boolean isResultAvailable(int query);

    /** Reads a query result that {@link #isResultAvailable(int)} reported as available. */
    boolean anySamplesPassed(int query);

    /** Releases a query handle. */
    void deleteQuery(int query);

    /** Releases backend-owned GPU resources. */
    void reset();
}
