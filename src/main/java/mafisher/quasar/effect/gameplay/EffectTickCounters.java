package mafisher.quasar.effect.gameplay;

/**
 * Work completed by one gameplay effect tick. Block-change application counts are recorded after
 * the tick callbacks, so they are not visible to a controller during {@code onTick}.
 */
public record EffectTickCounters(
    int targetsExamined,
    int damaged,
    int hitboxCalls,
    int entitiesSpawned,
    int blockChangesQueued,
    int blockChangesApplied,
    int blockChangesVetoed
) {
    public static final EffectTickCounters EMPTY = new EffectTickCounters(0, 0, 0, 0, 0, 0, 0);

    public EffectTickCounters {
        if (targetsExamined < 0 || damaged < 0 || damaged > targetsExamined
            || hitboxCalls < 0 || entitiesSpawned < 0
            || blockChangesQueued < 0 || blockChangesApplied < 0 || blockChangesVetoed < 0) {
            throw new IllegalArgumentException("Effect tick counters cannot be negative or inconsistent");
        }
    }

    /** Source-compatible constructor for effects that do not change blocks. */
    public EffectTickCounters(int targetsExamined, int damaged, int hitboxCalls, int entitiesSpawned) {
        this(targetsExamined, damaged, hitboxCalls, entitiesSpawned, 0, 0, 0);
    }

    /** Source-compatible constructor without protection vetoes. */
    public EffectTickCounters(
        int targetsExamined,
        int damaged,
        int hitboxCalls,
        int entitiesSpawned,
        int blockChangesQueued,
        int blockChangesApplied
    ) {
        this(targetsExamined, damaged, hitboxCalls, entitiesSpawned,
            blockChangesQueued, blockChangesApplied, 0);
    }
}
