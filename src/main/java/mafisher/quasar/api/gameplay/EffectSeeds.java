package mafisher.quasar.api.gameplay;

/** Derives independent deterministic tick seeds from one effect seed. */
final class EffectSeeds {
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private EffectSeeds() {
    }

    static long forTick(long seed, int ageTicks) {
        if (ageTicks < 0) {
            throw new IllegalArgumentException("Effect age cannot be negative");
        }
        long value = seed + GOLDEN_GAMMA * (ageTicks + 1L);
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
