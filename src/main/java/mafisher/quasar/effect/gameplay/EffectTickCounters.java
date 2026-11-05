package mafisher.quasar.effect.gameplay;

/** Work completed by one gameplay effect tick. */
public record EffectTickCounters(int targetsExamined, int damaged, int hitboxCalls, int entitiesSpawned) {
    public static final EffectTickCounters EMPTY = new EffectTickCounters(0, 0, 0, 0);

    public EffectTickCounters {
        if (targetsExamined < 0 || damaged < 0 || damaged > targetsExamined
            || hitboxCalls < 0 || entitiesSpawned < 0) {
            throw new IllegalArgumentException("Effect tick counters cannot be negative or inconsistent");
        }
    }
}
