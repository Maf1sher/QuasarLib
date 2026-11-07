package mafisher.quasar.effect.gameplay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import mafisher.quasar.effect.EffectForceField;
import mafisher.quasar.effect.EffectForceResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Applies one force field to server entities within the caller's per-tick budget. */
final class EffectForceApplication {
    private static final Comparator<Candidate> TARGET_ORDER =
        Comparator.comparingDouble(Candidate::distanceSquared)
            .thenComparing(candidate -> candidate.entity().getUUID());

    private EffectForceApplication() {
    }

    static EffectForceResult apply(
        ServerLevel level,
        EffectForceField field,
        Predicate<Entity> targetPolicy,
        EffectTickBudgetState budget
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(targetPolicy, "targetPolicy");
        Objects.requireNonNull(budget, "budget");
        if (level.isClientSide()) {
            throw new IllegalStateException("Effect force fields are server-authoritative");
        }

        var bounds = field.bounds();
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, new AABB(
            bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()
        ));
        if (entities.isEmpty()) {
            return EffectForceResult.NONE;
        }

        Vec3 fieldCenter = new Vec3(
            field.center().x(), field.center().y(), field.center().z()
        );
        List<Candidate> candidates = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            if (entity == null || entity.isRemoved() || !entity.isAlive() || !targetPolicy.test(entity)) {
                continue;
            }
            Vec3 center = entity.getBoundingBox().getCenter();
            candidates.add(new Candidate(
                entity,
                center,
                center.distanceToSqr(fieldCenter)
            ));
        }
        if (candidates.isEmpty()) {
            return EffectForceResult.NONE;
        }
        candidates.sort(TARGET_ORDER);

        int limit = Math.min(field.maxTargets(), budget.remainingForceTargets());
        int examined = 0;
        int affected = 0;
        boolean truncated = limit == 0;
        for (Candidate candidate : candidates) {
            Vec3 delta = field.velocityDeltaAt(candidate.center());
            if (delta.lengthSqr() <= 0.0) {
                continue;
            }
            if (examined >= limit || !budget.tryConsumeForceTarget()) {
                truncated = true;
                break;
            }
            examined++;
            candidate.entity().push(delta);
            budget.recordForceAffected();
            affected++;
        }
        return new EffectForceResult(examined, affected, truncated);
    }

    private record Candidate(Entity entity, Vec3 center, double distanceSquared) {
        private Candidate {
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(center, "center");
            if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0) {
                throw new IllegalArgumentException("Force candidate distance must be finite and non-negative");
            }
        }
    }
}
