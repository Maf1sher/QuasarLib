package mafisher.quasar.content.blackhole;

/** Pure timeline and quality math for the content black hole. */
public final class ContentBlackHoleGeometry {
    public static final int VARIANT_AMBER = 0;
    public static final int VARIANT_GOLD = 1;
    public static final int VARIANT_NEON = 2;
    /** Traversal floor; LOD may not starve the disk/shadow march below this. */
    static final int MIN_STEPS = 64;
    /** Cosmetic trim applied per LOD level. */
    static final int LOD_STEP_REDUCTION = 16;
    private static final int VARIANT_SHIFT = 60;
    private static final long SEED_PAYLOAD_MASK = 0x0FFF_FFFF_FFFF_FFFFL;

    private ContentBlackHoleGeometry() {
    }

    /**
     * Content-owned palette selector carried in the top seed bits. The low bits remain free for the
     * procedural disk, so the same visual seed can be reused for each palette.
     */
    public static long withVariant(long seed, int variant) {
        if (variant < VARIANT_AMBER || variant > VARIANT_NEON) {
            throw new IllegalArgumentException("Unknown black-hole variant: " + variant);
        }
        return (seed & SEED_PAYLOAD_MASK) | ((long)variant << VARIANT_SHIFT);
    }

    static int variant(long seed) {
        return (int)((seed >>> VARIANT_SHIFT) & 0x3L);
    }

    /** Returns the shader charge for the seed variant, falling back to the renderer setting. */
    static float chargeForSeed(long seed, float fallback) {
        return switch (variant(seed)) {
            case VARIANT_GOLD -> 0.0F;
            case VARIANT_NEON -> 1.0F;
            default -> fallback;
        };
    }

    /**
     * Selects a raymarch step budget from the quality tier and engine LOD. The march starts at the
     * black-hole bounding sphere, so traversal no longer scales with camera distance; LOD may trim
     * cosmetic work but never starves the disk/shadow traversal.
     */
    static int stepCount(int lodLevel, int qualityLevel) {
        int base = switch (qualityLevel) {
            case 0 -> 96;
            case 1 -> 128;
            case 2 -> 160;
            default -> 192;
        };
        return Math.max(MIN_STEPS, base - Math.max(0, lodLevel) * LOD_STEP_REDUCTION);
    }
}
