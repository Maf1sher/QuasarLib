package mafisher.quasar.api.gameplay.block;

import mafisher.quasar.api.gameplay.GameplayEffectContext;
import net.minecraft.nbt.CompoundTag;

/** Versioned NBT codec for {@link EffectCarvePlan}; kept out of the public math class. */
final class CarvePlanNbt {
    private static final int PLAN_VERSION = 1;

    private CarvePlanNbt() {
    }

    static void write(EffectCarvePlan plan, CompoundTag tag) {
        tag.putInt("plan_version", PLAN_VERSION);
        tag.putInt("volume_hash", plan.spec().volume().hashCode());
        tag.putString("ordering", plan.ordering().name());
        tag.putDouble("scale", plan.scale());
        tag.putFloat("window_start", plan.windowStart());
        tag.putFloat("growth_start", plan.growthStart());
        tag.putFloat("work_end", plan.workEnd());
        tag.putInt("duration_ticks", plan.durationTicks());
        tag.putInt("available_ticks", plan.availableTicks());
        tag.putInt("section_budget", plan.sectionBudget());
        tag.putInt("estimated_sections", plan.estimatedSections());
        tag.putInt("estimated_tickets", plan.estimatedTickets());
        tag.putInt("estimated_solid_sections", plan.estimatedSolidSections());
        tag.putInt("sections_per_tick", plan.sectionsPerTick());
        tag.putBoolean("budget_clamped", plan.budgetClamped());
        tag.putBoolean("intersects_terrain", plan.intersectsTerrain());
        tag.putInt("estimated_core_blocks", plan.estimatedCoreBlocks());
        tag.putInt("core_block_budget", plan.coreBlockBudget());
        tag.putBoolean("has_surface", plan.spec().surfaceY() != null);
        if (plan.spec().surfaceY() != null) {
            tag.putDouble("surface_y", plan.spec().surfaceY());
        }
    }

    static EffectCarvePlan restore(
        GameplayEffectContext context,
        EffectCarveSpec spec,
        CompoundTag tag
    ) {
        if (tag.getInt("plan_version") != PLAN_VERSION
            || tag.getInt("volume_hash") != spec.volume().hashCode()) {
            return EffectCarvePlan.plan(context, spec);
        }
        if (tag.contains("ordering")) {
            EffectBulkBlockEditor.Ordering ordering;
            try {
                ordering = EffectBulkBlockEditor.Ordering.valueOf(tag.getString("ordering"));
            } catch (IllegalArgumentException ignored) {
                return EffectCarvePlan.plan(context, spec);
            }
            if (ordering != spec.ordering()) {
                return EffectCarvePlan.plan(context, spec);
            }
        } else if (spec.ordering() != EffectBulkBlockEditor.Ordering.CENTER_OUT) {
            return EffectCarvePlan.plan(context, spec);
        }
        double scale = tag.getDouble("scale");
        if (!Double.isFinite(scale) || scale <= 0.0 || scale > 1.0) {
            return EffectCarvePlan.plan(context, spec);
        }
        float windowStart = tag.getFloat("window_start");
        float growthStart = tag.getFloat("growth_start");
        float workEnd = tag.getFloat("work_end");
        if (!Float.isFinite(windowStart) || !Float.isFinite(growthStart)
            || !Float.isFinite(workEnd) || workEnd <= growthStart) {
            return EffectCarvePlan.plan(context, spec);
        }
        double surfaceY = tag.getBoolean("has_surface")
            ? tag.getDouble("surface_y")
            : Double.POSITIVE_INFINITY;
        return new EffectCarvePlan(
            spec,
            scale,
            windowStart,
            growthStart,
            workEnd,
            Math.max(1, tag.getInt("duration_ticks")),
            Math.max(1, tag.getInt("available_ticks")),
            Math.max(1, tag.getInt("section_budget")),
            Math.max(1, tag.getInt("estimated_sections")),
            Math.max(1, tag.getInt("estimated_tickets")),
            Math.max(1, tag.getInt("estimated_solid_sections")),
            Math.max(1, tag.getInt("sections_per_tick")),
            tag.getBoolean("budget_clamped"),
            tag.getBoolean("intersects_terrain"),
            context.level().getMinBuildHeight() + 1.0,
            surfaceY,
            Math.max(0, tag.getInt("estimated_core_blocks")),
            Math.max(0, tag.getInt("core_block_budget"))
        );
    }
}
