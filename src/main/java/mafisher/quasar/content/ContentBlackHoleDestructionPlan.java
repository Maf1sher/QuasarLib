package mafisher.quasar.content;

import mafisher.quasar.effect.gameplay.EffectBulkBlockEditor;

/**
 * Pure, headless destruction plan for the content black hole.
 *
 * <p>The authored scale is the only source of the requested crater size. The plan then fits
 * that shape into the available timeline and engine budgets: the ticket cap limits the radius
 * and the time/section budgets limit the depth. Work is paced by removed volume and ends with
 * a reveal reserve before {@code DESTABILISE}, so the closing phase contains no block edits.</p>
 */
final class ContentBlackHoleDestructionPlan {
    private static final double SECTION_BLOCKS = 16.0 * 16.0 * 16.0;
    /** Whole-section estimates miss per-block boundary sections; keep a safety margin. */
    private static final double SECTION_SAFETY = 1.2;
    /** Chunk tickets are estimated from a circle; keep room for section-grid corners. */
    private static final double TICKET_SAFETY = 0.9;
    private static final float MIN_WINDOW = 0.05F;
    /** Keep at least this much of the open phase for relight and client reveal. */
    private static final float MIN_REVEAL_PROGRESS = 0.10F;
    /** Ensure short effects still reserve this many ticks for the reveal. */
    private static final float MIN_REVEAL_TICKS = 40.0F;

    private ContentBlackHoleDestructionPlan() {
    }

    record Plan(
        double requestedRadius,
        double requestedDepth,
        double radius,
        double depth,
        float startProgress,
        /** Last progress at which blocks may be edited; the rest of the open phase is reveal-only. */
        float workEndProgress,
        int availableTicks,
        int sectionBudget,
        int solidSections,
        int estimatedSections,
        int sectionsPerTick,
        boolean budgetClamped
    ) {
        /** Fraction of the destruction window that should be complete at {@code progress}. */
        double fractionAt(float progress) {
            float span = this.workEndProgress - this.startProgress;
            if (span <= 0.0F) {
                return 1.0;
            }
            return clamp01((progress - this.startProgress) / span);
        }

        /** Analytic radial front scale for a uniformly scaled ellipsoid. */
        double frontScaleAt(float progress) {
            return Math.cbrt(fractionAt(progress));
        }

        int targetSectionsAt(float progress, int totalSections) {
            return (int)Math.ceil(Math.max(0, totalSections) * fractionAt(progress));
        }
    }

    static Plan plan(
        ContentBlackHoleGameplaySettings settings,
        float scale,
        int durationTicks,
        int sectionBudget,
        float coreFinishProgress,
        double centerY,
        double groundY,
        double minBuildHeight
    ) {
        double requestedRadius = settings.craterRadius(scale);
        double maxDepth = Math.max(1.0, centerY - minBuildHeight - 1.0);
        double requestedDepth = Math.min(settings.craterDepth(scale), maxDepth);

        float end = ContentBlackHoleCinematics.DESTABILISE;
        int duration = Math.max(1, durationTicks);
        float revealReserve = Math.max(MIN_REVEAL_PROGRESS, MIN_REVEAL_TICKS / duration);
        float destructionEnd = Math.max(
            ContentBlackHoleCinematics.HORIZON_END + MIN_WINDOW,
            end - revealReserve
        );
        float start = Math.max(ContentBlackHoleCinematics.HORIZON_END, coreFinishProgress);
        if (start >= destructionEnd - MIN_WINDOW) {
            start = Math.max(
                ContentBlackHoleCinematics.HORIZON_END,
                destructionEnd - MIN_WINDOW
            );
        }
        float workEnd = destructionEnd;
        int availableTicks = Math.max(1, Math.round((workEnd - start) * duration));
        int budget = Math.max(1, sectionBudget);

        int sectionCap = EffectBulkBlockEditor.maxSectionsPerEdit();
        int ticketCap = Math.max(1, (int)Math.floor(
            EffectBulkBlockEditor.maxTicketChunks() * TICKET_SAFETY
        ));
        double ticketRadius = Math.sqrt(ticketCap * 256.0 / Math.PI);
        double radius = Math.min(requestedRadius, ticketRadius);
        double depth = requestedDepth;

        int solidAtRequested = estimatedSections(
            requestedRadius, requestedDepth, centerY, groundY, minBuildHeight
        );
        int totalAtRequested = estimatedTotalSections(requestedRadius, requestedDepth);
        long timeSections = (long)availableTicks * budget;
        double radiusScaleSquared = (requestedRadius / radius) * (requestedRadius / radius);
        double timeDepth = requestedDepth
            * (timeSections / (double)Math.max(1, solidAtRequested))
            * radiusScaleSquared;
        double sectionDepth = requestedDepth
            * (sectionCap / (double)Math.max(1, totalAtRequested))
            * radiusScaleSquared;
        depth = Math.max(1.0, Math.min(requestedDepth, Math.min(timeDepth, sectionDepth)));

        int solidEstimated = estimatedSections(radius, depth, centerY, groundY, minBuildHeight);
        int totalEstimated = estimatedTotalSections(radius, depth);
        if (solidEstimated > timeSections) {
            depth = Math.max(1.0, depth * timeSections / (double)solidEstimated);
            solidEstimated = estimatedSections(radius, depth, centerY, groundY, minBuildHeight);
            totalEstimated = estimatedTotalSections(radius, depth);
        }
        if (totalEstimated > sectionCap) {
            depth = Math.max(1.0, depth * sectionCap / (double)totalEstimated);
            solidEstimated = estimatedSections(radius, depth, centerY, groundY, minBuildHeight);
            totalEstimated = Math.min(sectionCap, estimatedTotalSections(radius, depth));
        }
        int sectionsPerTick = Math.max(1, (int)Math.ceil(solidEstimated / (double)availableTicks));

        return new Plan(
            requestedRadius,
            requestedDepth,
            radius,
            depth,
            start,
            workEnd,
            availableTicks,
            budget,
            solidEstimated,
            totalEstimated,
            sectionsPerTick,
            radius < requestedRadius - 1.0E-6 || depth < requestedDepth - 1.0E-6
        );
    }

    /** Restores the timeline fields that survive a session resume. */
    static Plan restored(
        double radius,
        double depth,
        float start,
        float workEnd,
        int sectionBudget,
        int solidSections,
        int estimatedSections
    ) {
        return new Plan(
            radius,
            depth,
            radius,
            depth,
            start,
            workEnd,
            0,
            Math.max(1, sectionBudget),
            Math.max(0, solidSections),
            Math.max(0, estimatedSections),
            1,
            false
        );
    }

    /**
     * Estimates the number of 16x16x16 sections that intersect the part of the ellipsoid below
     * the surface. The crater centre normally sits on the ground, so only the lower half counts;
     * a hole placed inside a cave counts the part that is actually underground.
     */
    static int estimatedSections(
        double radius,
        double depth,
        double centerY,
        double groundY,
        double minBuildHeight
    ) {
        if (radius <= 0.0 || depth <= 0.0) {
            return 1;
        }
        double fullVolume = (4.0 / 3.0) * Math.PI * radius * depth * radius;
        double top = Math.min(centerY + depth, groundY);
        double bottom = Math.max(centerY - depth, minBuildHeight);
        double verticalFraction = top <= bottom ? 0.0 : (top - bottom) / (2.0 * depth);
        double volume = fullVolume * clamp01(verticalFraction);
        return Math.max(1, (int)Math.ceil(volume / SECTION_BLOCKS * SECTION_SAFETY));
    }

    /** Estimates every section in the ellipsoid, including air above the surface. */
    static int estimatedTotalSections(double radius, double depth) {
        if (radius <= 0.0 || depth <= 0.0) {
            return 1;
        }
        double volume = (4.0 / 3.0) * Math.PI * radius * depth * radius;
        return Math.max(1, (int)Math.ceil(volume / SECTION_BLOCKS * SECTION_SAFETY));
    }

    /** Sections the controller must edit this tick to finish remaining work by the hard end. */
    static int requiredSectionsPerTick(int remainingSections, int remainingTicks) {
        if (remainingSections <= 0) {
            return 0;
        }
        return (int)Math.ceil(remainingSections / (double)Math.max(1, remainingTicks));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
