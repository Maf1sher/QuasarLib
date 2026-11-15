package mafisher.quasar.api.gameplay.block;

import mafisher.quasar.api.gameplay.world.EffectBlockVolume;
import net.minecraft.world.level.LevelHeightAccessor;

/**
 * Timeline and engine-cap fit search for one carve plan. Kept out of {@link EffectCarvePlan} so
 * the plan class stays focused on the authored schedule and front geometry.
 */
final class CarvePlanFit {
    static final double MIN_SCALE = 1.0E-6;
    static final int SEARCH_STEPS = 40;
    private static final int SECTION_BLOCKS = 16 * 16 * 16;
    /** Whole-section estimates miss per-block boundary sections; keep a safety margin. */
    private static final double SECTION_SAFETY = 1.25;

    private CarvePlanFit() {
    }

    /** Largest size factor that fits the timeline and exact section/ticket caps. */
    static double largestFittingScale(
        EffectCarveSpec spec,
        LevelHeightAccessor level,
        int availableTicks,
        int sectionBudget,
        double surfaceY,
        double floorY
    ) {
        if (fits(
            spec, 1.0, level, availableTicks, sectionBudget, surfaceY, floorY
        )) {
            return 1.0;
        }
        if (!fits(
            spec, MIN_SCALE, level, availableTicks, sectionBudget, surfaceY, floorY
        )) {
            return MIN_SCALE;
        }
        double low = MIN_SCALE;
        double high = 1.0;
        for (int step = 0; step < SEARCH_STEPS; step++) {
            double middle = (low + high) * 0.5;
            if (fits(
                spec, middle, level, availableTicks, sectionBudget, surfaceY, floorY
            )) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return low;
    }

    /** Conservative solid-section estimate used for the per-tick pace. */
    static int estimatedSolidSections(
        boolean topDown,
        int intersectingSections,
        double pacingVolume
    ) {
        if (topDown) {
            // A thin, deep shaft has few blocks but many sections; keep the depth estimate honest.
            return Math.max(1, intersectingSections);
        }
        return Math.max(
            1, (int)Math.ceil(pacingVolume / SECTION_BLOCKS * SECTION_SAFETY)
        );
    }

    private static boolean fits(
        EffectCarveSpec spec,
        double scale,
        LevelHeightAccessor level,
        int availableTicks,
        int sectionBudget,
        double surfaceY,
        double floorY
    ) {
        EffectBlockVolume volume = CarveVolumeMath.scaleForOrdering(
            spec.volume(), scale, spec.ordering()
        );
        CarveVolumeMath.Metrics metrics = CarveVolumeMath.countMetrics(
            volume,
            level,
            EffectBulkBlockEditor.maxSectionsPerEdit(),
            EffectBulkBlockEditor.maxTicketChunks()
        );
        if (metrics == null) {
            return false;
        }
        double solidSections;
        if (spec.ordering() == EffectBulkBlockEditor.Ordering.TOP_DOWN) {
            solidSections = metrics.sections();
        } else {
            double pacingVolume = CarveVolumeMath.pacingVolume(volume, surfaceY, floorY);
            solidSections = Math.ceil(pacingVolume / SECTION_BLOCKS * SECTION_SAFETY);
        }
        return solidSections <= (double)availableTicks * sectionBudget;
    }
}
