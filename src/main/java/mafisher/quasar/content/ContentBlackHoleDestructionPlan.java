package mafisher.quasar.content;

import mafisher.quasar.effect.gameplay.EffectBulkBlockEditor;

/**
 * Pure, headless destruction plan for the content black hole.
 *
 * <p>The authored scale is the only source of the requested crater size. The plan then fits
 * one sphere into the available timeline and engine budgets. The front grows from the core
 * radius to the final radius while removed terrain volume advances linearly with time, so the
 * crater expands in every direction at once instead of deepening first. Work ends with a reveal
 * reserve before {@code DESTABILISE}, so the closing phase contains no block edits.</p>
 */
final class ContentBlackHoleDestructionPlan {
    private static final double SECTION_BLOCKS = 16.0 * 16.0 * 16.0;
    /** Whole-section estimates miss per-block boundary sections; keep a safety margin. */
    private static final double SECTION_SAFETY = 1.25;
    /** Chunk tickets are estimated from a circle; keep room for section-grid corners. */
    private static final double TICKET_SAFETY = 0.9;
    private static final float MIN_WINDOW = 0.05F;
    /** Keep at least this much of the open phase for relight and client reveal. */
    private static final float MIN_REVEAL_PROGRESS = 0.10F;
    /** Ensure short effects still reserve this many ticks for the reveal. */
    private static final float MIN_REVEAL_TICKS = 40.0F;
    private static final int VOLUME_SEARCH_STEPS = 40;

    private ContentBlackHoleDestructionPlan() {
    }

    record Plan(
        double requestedRadius,
        double radius,
        double coreRadius,
        double centerY,
        double groundY,
        double minBuildHeight,
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

        /**
         * Sphere radius at {@code progress}. The removed terrain volume advances linearly, then
         * the radius is recovered from the spherical-cap volume. If the sphere never reaches
         * terrain, the radius falls back to a linear growth so the visual still opens smoothly.
         */
        double radiusAt(float progress) {
            if (this.radius <= 0.0) {
                return 0.0;
            }
            double core = Math.max(0.0, Math.min(this.coreRadius, this.radius));
            if (core >= this.radius) {
                return this.radius;
            }
            double f = fractionAt(progress);
            if (f <= 0.0) {
                return core;
            }
            if (f >= 1.0) {
                return this.radius;
            }
            double startVolume = terrainVolume(core, this.centerY, this.groundY, this.minBuildHeight);
            double endVolume = terrainVolume(this.radius, this.centerY, this.groundY, this.minBuildHeight);
            if (endVolume <= startVolume + 1.0E-6) {
                return core + (this.radius - core) * f;
            }
            double targetVolume = startVolume + (endVolume - startVolume) * f;
            return radiusForVolume(
                targetVolume,
                core,
                this.radius,
                this.centerY,
                this.groundY,
                this.minBuildHeight
            );
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
        double coreRadius = settings.coreRadius(scale);
        double requestedRadius = Math.max(settings.craterRadius(scale), coreRadius);

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
        double sectionRadius = Math.cbrt(
            sectionCap * SECTION_BLOCKS / ((4.0 / 3.0) * Math.PI) / SECTION_SAFETY
        );
        double ceiling = Math.min(requestedRadius, Math.min(ticketRadius, sectionRadius));
        double timeRadius = largestRadiusForTime(
            coreRadius,
            ceiling,
            (long)availableTicks * budget,
            centerY,
            groundY,
            minBuildHeight
        );
        double radius = Math.max(coreRadius, Math.min(ceiling, timeRadius));

        int solidEstimated = estimatedSections(radius, centerY, groundY, minBuildHeight);
        int totalEstimated = estimatedTotalSections(radius);
        int sectionsPerTick = Math.max(1, (int)Math.ceil(solidEstimated / (double)availableTicks));
        boolean clamped = radius < requestedRadius - 1.0E-6
            || radius > ceiling + 1.0E-6
            || sectionsPerTick > budget;

        return new Plan(
            requestedRadius,
            radius,
            coreRadius,
            centerY,
            groundY,
            minBuildHeight,
            start,
            workEnd,
            availableTicks,
            budget,
            solidEstimated,
            totalEstimated,
            sectionsPerTick,
            clamped
        );
    }

    /** Restores the timeline fields that survive a session resume. */
    static Plan restored(
        double radius,
        double coreRadius,
        float start,
        float workEnd,
        int sectionBudget,
        int solidSections,
        int estimatedSections,
        double centerY,
        double groundY,
        double minBuildHeight
    ) {
        return new Plan(
            radius,
            radius,
            Math.max(0.0, coreRadius),
            centerY,
            groundY,
            minBuildHeight,
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
     * Volume of the part of a sphere below a horizontal plane.
     */
    static double volumeBelowPlane(double radius, double centerY, double planeY) {
        if (radius <= 0.0) {
            return 0.0;
        }
        double offset = planeY - centerY;
        if (offset <= -radius) {
            return 0.0;
        }
        if (offset >= radius) {
            return (4.0 / 3.0) * Math.PI * radius * radius * radius;
        }
        double capHeight = radius + offset;
        return Math.PI * capHeight * capHeight * (3.0 * radius - capHeight) / 3.0;
    }

    /** Terrain volume between the world floor and the sampled surface. */
    static double terrainVolume(
        double radius,
        double centerY,
        double groundY,
        double minBuildHeight
    ) {
        if (radius <= 0.0 || groundY <= minBuildHeight) {
            return 0.0;
        }
        double belowGround = volumeBelowPlane(radius, centerY, groundY);
        double belowFloor = volumeBelowPlane(radius, centerY, minBuildHeight + 1.0);
        return Math.max(0.0, belowGround - belowFloor);
    }

    /** Inverse of {@link #terrainVolume} over the requested radius interval. */
    static double radiusForVolume(
        double volume,
        double minRadius,
        double maxRadius,
        double centerY,
        double groundY,
        double minBuildHeight
    ) {
        double low = Math.max(0.0, minRadius);
        double high = Math.max(low, maxRadius);
        if (high <= low) {
            return high;
        }
        if (volume <= terrainVolume(low, centerY, groundY, minBuildHeight)) {
            return low;
        }
        if (volume >= terrainVolume(high, centerY, groundY, minBuildHeight)) {
            return high;
        }
        for (int step = 0; step < VOLUME_SEARCH_STEPS; step++) {
            double middle = (low + high) * 0.5;
            if (terrainVolume(middle, centerY, groundY, minBuildHeight) < volume) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return (low + high) * 0.5;
    }

    /**
     * Number of sections that intersect the part of the sphere between the world floor and the
     * surface. Caves and already-air blocks still count here; the editor skips them at edit time.
     */
    static int estimatedSections(
        double radius,
        double centerY,
        double groundY,
        double minBuildHeight
    ) {
        double volume = terrainVolume(radius, centerY, groundY, minBuildHeight);
        return Math.max(1, (int)Math.ceil(volume / SECTION_BLOCKS * SECTION_SAFETY));
    }

    /** Estimates every section in the sphere, including air above the surface. */
    static int estimatedTotalSections(double radius) {
        if (radius <= 0.0) {
            return 1;
        }
        double volume = (4.0 / 3.0) * Math.PI * radius * radius * radius;
        return Math.max(1, (int)Math.ceil(volume / SECTION_BLOCKS * SECTION_SAFETY));
    }

    /** Whether the sphere intersects any terrain between the world floor and the surface. */
    static boolean intersectsTerrain(
        double centerY,
        double radius,
        double groundY,
        double minBuildHeight
    ) {
        double floor = minBuildHeight + 1.0;
        return radius > 0.0
            && groundY >= floor
            && centerY + radius >= floor
            && centerY - radius <= groundY;
    }

    /** Whether the sphere crosses the sampled surface and can open a visible crater. */
    static boolean breaksSurface(double centerY, double radius, double groundY) {
        return radius > 0.0
            && centerY - radius <= groundY
            && centerY + radius >= groundY;
    }

    private static double largestRadiusForTime(
        double minRadius,
        double maxRadius,
        long sectionCapacity,
        double centerY,
        double groundY,
        double minBuildHeight
    ) {
        double low = Math.max(0.0, minRadius);
        double high = Math.max(low, maxRadius);
        if (estimatedSections(high, centerY, groundY, minBuildHeight) <= sectionCapacity) {
            return high;
        }
        if (estimatedSections(low, centerY, groundY, minBuildHeight) >= sectionCapacity) {
            return low;
        }
        for (int step = 0; step < VOLUME_SEARCH_STEPS; step++) {
            double middle = (low + high) * 0.5;
            if (estimatedSections(middle, centerY, groundY, minBuildHeight) <= sectionCapacity) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
