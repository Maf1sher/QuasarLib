package mafisher.quasar.client.effect.frame;
import org.jetbrains.annotations.ApiStatus;

/**
 * Estimates server game time on the client from payload timestamps. Payloads carry absolute server
 * game times; comparing them with client game time directly drops updates whenever the two clocks
 * drift apart. Instead, every payload refreshes an offset and the estimate is clamped to be
 * monotonic, so a client clock correction can never move an effect timeline backwards.
 */
@ApiStatus.Internal
public final class ClientEffectClock {
    private static boolean sampled;
    private static long offsetTicks;
    private static long lastEstimate = Long.MIN_VALUE;

    private ClientEffectClock() {
    }

    /** Records one server timestamp together with the client time at which it was received. */
    public static synchronized void observe(long serverGameTime, long clientGameTime) {
        if (!sampled) {
            sampled = true;
            lastEstimate = Long.MIN_VALUE;
        }
        // Network latency makes the sample lag the true server clock, so estimates never run ahead.
        offsetTicks = serverGameTime - clientGameTime;
    }

    /** Returns the monotonic estimate of server game time for the current client tick. */
    public static synchronized long estimate(long clientGameTime) {
        if (!sampled) {
            return clientGameTime;
        }
        long estimate = clientGameTime + offsetTicks;
        if (lastEstimate != Long.MIN_VALUE && estimate < lastEstimate) {
            estimate = lastEstimate;
        }
        lastEstimate = estimate;
        return estimate;
    }

    /** Clears the sampled offset when the client leaves a level or disconnects. */
    public static synchronized void reset() {
        sampled = false;
        offsetTicks = 0L;
        lastEstimate = Long.MIN_VALUE;
    }
}
