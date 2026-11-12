package mafisher.quasar.api.gameplay;

/**
 * Work completed by one {@link EffectBlockCursor#visit} call.
 *
 * @param examined blocks inside the volume charged to the world-sample budget
 * @param matched blocks accepted by the caller's filter and handed to the visitor
 * @param truncated {@code true} when the call stopped at its block or sample budget
 * @param finished {@code true} when the cursor has visited the whole volume
 * @param paused {@code true} when an unloaded chunk stopped the cursor; retry later
 */
public record EffectBlockCursorResult(
    int examined,
    int matched,
    boolean truncated,
    boolean finished,
    boolean paused
) {
    public EffectBlockCursorResult {
        if (examined < 0 || matched < 0 || matched > examined) {
            throw new IllegalArgumentException("Cursor counts must be non-negative and matched <= examined");
        }
    }

    public static final EffectBlockCursorResult EMPTY =
        new EffectBlockCursorResult(0, 0, false, false, false);
}
