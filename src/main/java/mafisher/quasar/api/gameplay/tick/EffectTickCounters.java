package mafisher.quasar.api.gameplay.tick;

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
    int blockChangesVetoed,
    int worldSamples,
    int forceTargetsExamined,
    int forceTargetsAffected,
    int debrisPieces,
    int sectionsEdited
) {
    public static final EffectTickCounters EMPTY =
        new EffectTickCounters(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    public EffectTickCounters {
        if (targetsExamined < 0 || damaged < 0 || damaged > targetsExamined
            || hitboxCalls < 0 || entitiesSpawned < 0
            || blockChangesQueued < 0 || blockChangesApplied < 0 || blockChangesVetoed < 0
            || worldSamples < 0 || sectionsEdited < 0
            || forceTargetsExamined < 0 || forceTargetsAffected < 0
            || forceTargetsAffected > forceTargetsExamined || debrisPieces < 0) {
            throw new IllegalArgumentException("Effect tick counters cannot be negative or inconsistent");
        }
    }

    /** Source-compatible constructor for effects that do not use bulk section edits. */
    public EffectTickCounters(
        int targetsExamined,
        int damaged,
        int hitboxCalls,
        int entitiesSpawned,
        int blockChangesQueued,
        int blockChangesApplied,
        int blockChangesVetoed,
        int worldSamples,
        int forceTargetsExamined,
        int forceTargetsAffected,
        int debrisPieces
    ) {
        this(
            targetsExamined,
            damaged,
            hitboxCalls,
            entitiesSpawned,
            blockChangesQueued,
            blockChangesApplied,
            blockChangesVetoed,
            worldSamples,
            forceTargetsExamined,
            forceTargetsAffected,
            debrisPieces,
            0
        );
    }

    /** Source-compatible constructor for effects that do not use forces or debris. */
    public EffectTickCounters(
        int targetsExamined,
        int damaged,
        int hitboxCalls,
        int entitiesSpawned,
        int blockChangesQueued,
        int blockChangesApplied,
        int blockChangesVetoed,
        int worldSamples
    ) {
        this(targetsExamined, damaged, hitboxCalls, entitiesSpawned, blockChangesQueued,
            blockChangesApplied, blockChangesVetoed, worldSamples, 0, 0, 0);
    }

    /** Source-compatible constructor for effects that do not change blocks or sample the world. */
    public EffectTickCounters(int targetsExamined, int damaged, int hitboxCalls, int entitiesSpawned) {
        this(targetsExamined, damaged, hitboxCalls, entitiesSpawned, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Source-compatible constructor without protection vetoes or world samples. */
    public EffectTickCounters(
        int targetsExamined,
        int damaged,
        int hitboxCalls,
        int entitiesSpawned,
        int blockChangesQueued,
        int blockChangesApplied
    ) {
        this(targetsExamined, damaged, hitboxCalls, entitiesSpawned,
            blockChangesQueued, blockChangesApplied, 0, 0, 0, 0, 0);
    }

    /** Source-compatible constructor without world samples. */
    public EffectTickCounters(
        int targetsExamined,
        int damaged,
        int hitboxCalls,
        int entitiesSpawned,
        int blockChangesQueued,
        int blockChangesApplied,
        int blockChangesVetoed
    ) {
        this(targetsExamined, damaged, hitboxCalls, entitiesSpawned,
            blockChangesQueued, blockChangesApplied, blockChangesVetoed, 0, 0, 0, 0);
    }
}
