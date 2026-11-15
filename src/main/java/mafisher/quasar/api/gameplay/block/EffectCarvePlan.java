package mafisher.quasar.api.gameplay.block;

import java.util.Objects;
import mafisher.quasar.api.effect.budget.EffectBudgets;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.world.EffectBlockVolume;
import net.minecraft.world.level.LevelHeightAccessor;
import org.jspecify.annotations.Nullable;

/**
 * Immutable, headless plan for one destructive carve.
 *
 * <p>{@link #scale()} is the factor applied to the authored volume. It is {@code 1} when the
 * authored size fits the timeline and engine caps and shrinks otherwise. The authored volume
 * remains the only source of the requested size; the plan never enlarges it. {@code CENTER_OUT}
 * shrinks uniformly. {@code TOP_DOWN} shrinks only a cylinder radius and keeps its authored
 * vertical extent.</p>
 *
 * <p>{@link #scaleAt(float)} is the normalized radial visual front inside the effective volume.
 * It advances with removable terrain volume between the core finish and {@link #workEnd()}, so a
 * sphere grows in every direction at once. {@link #frontYAt(float)} is the equivalent absolute
 * world height for a top-down burn. The front follows authored progress even when the editor
 * finishes early; {@link EffectCarveSession} keeps "edits finished" separate.</p>
 */
@SuppressWarnings({
    "PMD.TooManyMethods",
    "checkstyle:MethodLength",
    "checkstyle:ParameterNumber"
})
public final class EffectCarvePlan {
    private static final float MIN_WINDOW = 0.05F;
    /** Keep at least this much of the authored window for relight and client reveal. */
    private static final float MIN_REVEAL_PROGRESS = 0.10F;
    /** Ensure short effects still reserve this many ticks for the reveal. */
    private static final float MIN_REVEAL_TICKS = 40.0F;
    /** Never let the exact core pass starve the main carve for more than this share. */
    private static final float MAX_CORE_WINDOW_FRACTION = 0.35F;
    private static final int MAX_CORE_BLOCK_BATCH = 4_096;
    private static final double EPSILON = 1.0E-6;

    private final EffectCarveSpec spec;
    private final EffectBulkBlockEditor.Ordering ordering;
    private final double scale;
    private final EffectBlockVolume requestedVolume;
    private final EffectBlockVolume volume;
    private final @Nullable EffectBlockVolume coreVolume;
    private final float windowStart;
    private final float growthStart;
    private final float workEnd;
    private final int durationTicks;
    private final int availableTicks;
    private final int sectionBudget;
    private final int estimatedSections;
    private final int estimatedTickets;
    private final int estimatedSolidSections;
    private final int sectionsPerTick;
    private final boolean budgetClamped;
    private final boolean intersectsTerrain;
    private final double floorY;
    private final double surfaceY;
    private final double frontTopY;
    private final double frontBottomY;
    private final int estimatedCoreBlocks;
    private final int coreBlockBudget;
    private final double startingScale;
    private final double startingVolume;
    private final double endVolume;
    private final double startingGeometry;
    private final double endGeometry;

    EffectCarvePlan(
        EffectCarveSpec spec,
        double scale,
        float windowStart,
        float growthStart,
        float workEnd,
        int durationTicks,
        int availableTicks,
        int sectionBudget,
        int estimatedSections,
        int estimatedTickets,
        int estimatedSolidSections,
        int sectionsPerTick,
        boolean budgetClamped,
        boolean intersectsTerrain,
        double floorY,
        double surfaceY,
        int estimatedCoreBlocks,
        int coreBlockBudget
    ) {
        this.spec = spec;
        this.ordering = spec.ordering();
        this.scale = scale;
        this.requestedVolume = spec.volume();
        this.volume = CarveVolumeMath.scaleForOrdering(spec.volume(), scale, this.ordering);
        this.coreVolume = spec.coreVolume() == null
            ? null
            : CarveVolumeMath.scaleForOrdering(spec.coreVolume(), scale, this.ordering);
        this.windowStart = windowStart;
        this.growthStart = growthStart;
        this.workEnd = workEnd;
        this.durationTicks = Math.max(1, durationTicks);
        this.availableTicks = Math.max(1, availableTicks);
        this.sectionBudget = Math.max(1, sectionBudget);
        this.estimatedSections = Math.max(1, estimatedSections);
        this.estimatedTickets = Math.max(1, estimatedTickets);
        this.estimatedSolidSections = Math.max(1, estimatedSolidSections);
        this.sectionsPerTick = Math.max(1, sectionsPerTick);
        this.budgetClamped = budgetClamped;
        this.intersectsTerrain = intersectsTerrain;
        this.floorY = floorY;
        this.surfaceY = surfaceY;
        this.estimatedCoreBlocks = Math.max(0, estimatedCoreBlocks);
        this.coreBlockBudget = Math.max(0, coreBlockBudget);
        double rawTop = Math.min(this.volume.bounds().maxY(), surfaceY);
        double rawBottom = Math.max(this.volume.bounds().minY(), floorY);
        if (rawBottom >= rawTop) {
            this.frontTopY = rawBottom;
            this.frontBottomY = rawBottom;
        } else {
            this.frontTopY = rawTop;
            this.frontBottomY = rawBottom;
        }
        if (this.ordering == EffectBulkBlockEditor.Ordering.CENTER_OUT) {
            this.startingScale = startingScale(spec);
            this.startingVolume = CarveVolumeMath.pacingVolumeAtScale(
                this.volume, this.startingScale, this.surfaceY, this.floorY
            );
            this.endVolume = CarveVolumeMath.pacingVolume(
                this.volume, this.surfaceY, this.floorY
            );
            this.startingGeometry = CarveVolumeMath.geometricVolumeAtScale(
                this.volume, this.startingScale
            );
            this.endGeometry = CarveVolumeMath.geometricVolume(this.volume);
        } else {
            this.startingScale = 0.0;
            this.startingVolume = 0.0;
            this.endVolume = 0.0;
            this.startingGeometry = 0.0;
            this.endGeometry = 0.0;
        }
    }

    public static EffectCarvePlan plan(
        GameplayEffectContext context,
        EffectCarveSpec spec
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(spec, "spec");
        LevelHeightAccessor level = context.level();
        int duration = Math.max(1, context.spec().phases().totalDurationTicks());
        double floorY = level.getMinBuildHeight() + 1.0;
        double surfaceY = spec.surfaceY() == null
            ? Double.POSITIVE_INFINITY
            : spec.surfaceY();

        int configuredBudget = EffectBudgets.sectionEditsPerLevelTick();
        int callerBudget = context.spec().budget().maxSectionEdits();
        int sectionBudget = Math.max(1, Math.min(
            callerBudget > 0 ? callerBudget : configuredBudget,
            configuredBudget
        ));
        int callerBlockBudget = context.spec().budget().maxBlockChanges();
        int coreBlockBudget = callerBlockBudget <= 0
            ? 0
            : Math.max(1, Math.min(
                MAX_CORE_BLOCK_BATCH,
                Math.min(EffectBudgets.blockChangesPerLevelTick(), callerBlockBudget)
            ));

        float windowStart = clamp01(spec.windowStart());
        float windowEnd = clamp01(spec.windowEnd());
        float reserve = Math.max(MIN_REVEAL_PROGRESS, MIN_REVEAL_TICKS / (float)duration);
        float workEnd = Math.min(
            1.0F,
            Math.max(windowStart + MIN_WINDOW, windowEnd - reserve)
        );

        int estimatedCoreBlocks = 0;
        if (spec.coreVolume() != null && coreBlockBudget > 0) {
            estimatedCoreBlocks = (int)Math.ceil(
                CarveVolumeMath.pacingVolume(spec.coreVolume(), surfaceY, floorY)
            );
        }
        int coreTicks = estimatedCoreBlocks <= 0
            ? 0
            : (int)Math.ceil(estimatedCoreBlocks / (double)coreBlockBudget);
        float coreStart = clamp01(spec.coreStartProgress());
        float coreShare = Math.max(0.0F, workEnd - coreStart) * MAX_CORE_WINDOW_FRACTION;
        float coreFinish = coreStart + coreTicks / (float)duration;
        float coreDeadline = Math.min(workEnd, Math.min(coreFinish, coreStart + coreShare));
        float growthStart = estimatedCoreBlocks > 0
            ? Math.max(windowStart, coreDeadline)
            : windowStart;
        growthStart = Math.min(
            growthStart,
            Math.max(windowStart, workEnd - MIN_WINDOW)
        );
        int availableTicks = Math.max(
            1, Math.round((workEnd - growthStart) * duration)
        );

        double scale = CarvePlanFit.largestFittingScale(
            spec,
            level,
            availableTicks,
            sectionBudget,
            surfaceY,
            floorY
        );
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
            scale = CarvePlanFit.MIN_SCALE;
            volume = CarveVolumeMath.scaleForOrdering(
                spec.volume(), scale, spec.ordering()
            );
            metrics = CarveVolumeMath.countMetrics(
                volume,
                level,
                EffectBulkBlockEditor.maxSectionsPerEdit(),
                EffectBulkBlockEditor.maxTicketChunks()
            );
            if (metrics == null) {
                metrics = new CarveVolumeMath.Metrics(0, 0);
            }
        }
        double pacingVolume = CarveVolumeMath.pacingVolume(volume, surfaceY, floorY);
        int estimatedSolidSections = CarvePlanFit.estimatedSolidSections(
            spec.ordering() == EffectBulkBlockEditor.Ordering.TOP_DOWN,
            metrics.sections(),
            pacingVolume
        );
        int sectionsPerTick = Math.max(
            1, (int)Math.ceil(estimatedSolidSections / (double)availableTicks)
        );
        boolean budgetClamped = scale < 1.0 - 1.0E-6
            || sectionsPerTick > sectionBudget;

        return new EffectCarvePlan(
            spec,
            scale,
            windowStart,
            growthStart,
            workEnd,
            duration,
            availableTicks,
            sectionBudget,
            metrics.sections(),
            metrics.tickets(),
            estimatedSolidSections,
            sectionsPerTick,
            budgetClamped,
            pacingVolume > 0.0,
            floorY,
            surfaceY,
            estimatedCoreBlocks,
            coreBlockBudget
        );
    }

    /** Authored volume, exactly as supplied by the effect. */
    public EffectBlockVolume requestedVolume() {
        return this.requestedVolume;
    }

    EffectCarveSpec spec() {
        return this.spec;
    }

    int durationTicks() {
        return this.durationTicks;
    }

    /** Effective volume after the uniform fit; equals the authored volume when nothing clamps. */
    public EffectBlockVolume volume() {
        return this.volume;
    }

    /**
     * Effective size factor in {@code (0, 1]}: a uniform volume scale for {@code CENTER_OUT},
     * or the cylinder radius factor for {@code TOP_DOWN}.
     */
    public double scale() {
        return this.scale;
    }

    /** Destruction front selected by the authored spec. */
    public EffectBulkBlockEditor.Ordering ordering() {
        return this.ordering;
    }

    /** First top-down front height: the surface clipped to the effective volume. */
    public double frontTopY() {
        return this.frontTopY;
    }

    /** Last top-down front height: the protected floor clipped to the effective volume. */
    public double frontBottomY() {
        return this.frontBottomY;
    }

    /** Effective optional core volume, or {@code null}. */
    public @Nullable EffectBlockVolume coreVolume() {
        return this.coreVolume;
    }

    public float windowStart() {
        return this.windowStart;
    }

    /** Progress at which the main bulk carve starts. */
    public float growthStart() {
        return this.growthStart;
    }

    /** Last progress at which block edits may run; the remaining window is reveal-only. */
    public float workEnd() {
        return this.workEnd;
    }

    public int availableTicks() {
        return this.availableTicks;
    }

    public int sectionBudget() {
        return this.sectionBudget;
    }

    public int estimatedSections() {
        return this.estimatedSections;
    }

    public int estimatedTickets() {
        return this.estimatedTickets;
    }

    public int estimatedSolidSections() {
        return this.estimatedSolidSections;
    }

    public int sectionsPerTick() {
        return this.sectionsPerTick;
    }

    public boolean budgetClamped() {
        return this.budgetClamped;
    }

    /** Whether the effective volume contains any terrain between the world floor and surface. */
    public boolean intersectsTerrain() {
        return this.intersectsTerrain;
    }

    public int estimatedCoreBlocks() {
        return this.estimatedCoreBlocks;
    }

    public int coreBlockBudget() {
        return this.coreBlockBudget;
    }

    public boolean hasCoreWork() {
        return this.coreVolume != null
            && this.coreBlockBudget > 0
            && this.estimatedCoreBlocks > 0;
    }

    /** Fraction of the main carve window completed at {@code progress}. */
    public float fractionAt(float progress) {
        float span = this.workEnd - this.growthStart;
        if (span <= 0.0F) {
            return 1.0F;
        }
        return clamp01((progress - this.growthStart) / span);
    }

    /**
     * Normalized radial front inside the effective volume at {@code progress}. It starts at the
     * effective core size, advances with removable volume, and reaches {@code 1} at {@link
     * #workEnd()}. Only {@code CENTER_OUT} plans have a radial front; use {@link #frontYAt(float)}
     * for a top-down burn.
     */
    public double scaleAt(float progress) {
        if (this.ordering != EffectBulkBlockEditor.Ordering.CENTER_OUT) {
            throw new IllegalStateException("scaleAt is a radial front; use frontYAt");
        }
        if (progress <= this.growthStart) {
            return this.startingScale;
        }
        float fraction = fractionAt(progress);
        if (fraction <= 0.0F) {
            return this.startingScale;
        }
        if (fraction >= 1.0F || this.startingScale >= 1.0) {
            return 1.0;
        }

        if (this.endVolume > this.startingVolume + 1.0E-6) {
            double target = this.startingVolume
                + (this.endVolume - this.startingVolume) * fraction;
            return CarveVolumeMath.scaleForVolume(
                this.volume,
                target,
                this.startingScale,
                1.0,
                this.surfaceY,
                this.floorY
            );
        }

        if (this.endGeometry <= this.startingGeometry + 1.0E-6) {
            return this.startingScale + (1.0 - this.startingScale) * fraction;
        }
        double target = this.startingGeometry
            + (this.endGeometry - this.startingGeometry) * fraction;
        return CarveVolumeMath.scaleForVolume(
            this.volume,
            target,
            this.startingScale,
            1.0,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY
        );
    }

    /**
     * Absolute world height of the top-down front at {@code progress}. It starts at {@link
     * #frontTopY()}, descends by actually removable terrain volume, and reaches {@link
     * #frontBottomY()} at {@link #workEnd()}. A volume with no terrain descends linearly.
     */
    public double frontYAt(float progress) {
        if (this.ordering != EffectBulkBlockEditor.Ordering.TOP_DOWN) {
            throw new IllegalStateException("frontYAt is a top-down front; use scaleAt");
        }
        if (progress <= this.growthStart) {
            return this.frontTopY;
        }
        float fraction = fractionAt(progress);
        if (fraction <= 0.0F) {
            return this.frontTopY;
        }
        if (fraction >= 1.0F) {
            return this.frontBottomY;
        }
        double span = this.frontTopY - this.frontBottomY;
        if (span <= EPSILON) {
            return this.frontBottomY;
        }
        double total = CarveVolumeMath.pacingVolume(
            this.volume, this.frontTopY, this.frontBottomY
        );
        if (total <= EPSILON) {
            return this.frontTopY - span * fraction;
        }
        double targetBelow = total * (1.0 - fraction);
        double low = this.frontBottomY;
        double high = this.frontTopY;
        for (int step = 0; step < CarvePlanFit.SEARCH_STEPS; step++) {
            double middle = (low + high) * 0.5;
            double below = CarveVolumeMath.pacingVolume(
                this.volume, middle, this.frontBottomY
            );
            if (below < targetBelow) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return (low + high) * 0.5;
    }

    private static double startingScale(EffectCarveSpec spec) {
        if (spec.coreVolume() == null) {
            return 0.0;
        }
        double mainRadius = CarveVolumeMath.maxRadius(spec.volume());
        if (mainRadius <= 0.0) {
            return 0.0;
        }
        return Math.max(
            0.0,
            Math.min(1.0, CarveVolumeMath.maxRadius(spec.coreVolume()) / mainRadius)
        );
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
