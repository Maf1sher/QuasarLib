package mafisher.quasar.content.orbital;

import java.util.Objects;
import mafisher.quasar.api.effect.EffectParameterState;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.damage.EffectDamageShape;
import mafisher.quasar.api.effect.damage.EffectHitbox;
import mafisher.quasar.api.effect.force.EffectForceField;
import mafisher.quasar.api.effect.force.EffectForceResult;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectEndReason;
import mafisher.quasar.api.gameplay.block.EffectBulkBlockEditor;
import mafisher.quasar.api.gameplay.block.EffectCarvePlan;
import mafisher.quasar.api.gameplay.block.EffectCarveSession;
import mafisher.quasar.api.gameplay.block.EffectCarveSpec;
import mafisher.quasar.api.gameplay.world.EffectBlockVolume;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-owned terrain, damage, and shockwave behavior of one orbital-laser session.
 *
 * <p>The authored volume is a vertical cylinder from the aimed surface to the protected world
 * floor. {@link EffectCarvePlan} may shrink only its radius to fit the timeline and section
 * budget; the effective radius drives the beam, damage cylinder, and shockwave. The carve window
 * starts at impact and ends before collapse, leaving the reveal reserve to the primitive.</p>
 */
@SuppressWarnings("PMD.TooManyMethods")
final class ContentOrbitalLaserDestruction {
    private static final Logger LOGGER =
        LoggerFactory.getLogger(ContentOrbitalLaserDestruction.class);
    private static final String CARVE_SESSION = "carve_session";
    private static final String IMPACT_APPLIED = "impact_applied";
    private static final double MIN_SHAPE_RADIUS = 0.05;

    private final GameplayEffectContext context;
    private final Vec3 anchor;
    private final EffectCarvePlan plan;
    private final EffectCarveSession carveSession;
    private final DamageSource damageSource;
    private final EffectHitbox damageHitbox;
    private final float radiusScale;
    private final double effectiveRadius;
    private final double surfaceY;

    private boolean impactApplied;
    private boolean truncatedLogged;
    private boolean closed;

    private ContentOrbitalLaserDestruction(
        GameplayEffectContext context,
        EffectCarvePlan plan,
        EffectCarveSession carveSession
    ) {
        this.context = Objects.requireNonNull(context, "context");
        this.plan = Objects.requireNonNull(plan, "plan");
        this.carveSession = Objects.requireNonNull(carveSession, "carveSession");
        this.anchor = context.spec().anchor();
        this.effectiveRadius = radiusOf(plan.volume());
        double requested = context.spec().scale();
        this.radiusScale = requested <= 0.0
            ? 0.0F
            : clampRadiusScale(this.effectiveRadius / requested);
        this.surfaceY = this.anchor.y;
        this.damageSource = context.level().damageSources().magic();
        this.damageHitbox = new EffectHitbox(
            damageShape(plan.frontTopY()),
            ContentOrbitalLaserSettings.BEAM_DAMAGE,
            ContentOrbitalLaserSettings.BEAM_DAMAGE_REHIT_TICKS,
            ContentOrbitalLaserSettings.MAX_BEAM_DAMAGE_TARGETS
        );
    }

    /** Plans and starts the carve. No block edits run before the impact window opens. */
    static ContentOrbitalLaserDestruction start(GameplayEffectContext context) {
        EffectCarveSpec spec = ContentOrbitalLaserSpecs.carveSpec(context);
        EffectCarvePlan plan = EffectCarvePlan.plan(context, spec);
        EffectCarveSession session = EffectCarveSession.start(context, spec, plan);
        ContentOrbitalLaserDestruction destruction =
            new ContentOrbitalLaserDestruction(context, session.plan(), session);
        destruction.persist(context);
        destruction.logPlan();
        return destruction;
    }

    /** Restores the carve and impact flag, or starts a fresh idempotent carve for legacy saves. */
    static ContentOrbitalLaserDestruction restore(GameplayEffectContext context) {
        EffectCarveSpec spec = ContentOrbitalLaserSpecs.carveSpec(context);
        CompoundTag state = context.persistentState();
        CompoundTag sessionTag = state.getCompound(CARVE_SESSION);
        EffectCarveSession session;
        if (sessionTag.isEmpty()) {
            session = EffectCarveSession.start(context, spec);
        } else {
            session = EffectCarveSession.restore(context, spec, sessionTag);
        }
        ContentOrbitalLaserDestruction destruction =
            new ContentOrbitalLaserDestruction(context, session.plan(), session);
        destruction.impactApplied = state.getBoolean(IMPACT_APPLIED);
        destruction.persist(context);
        destruction.logPlan();
        return destruction;
    }

    /** Advances the carve, applies sustained damage, and persists the resumable state. */
    void tick(GameplayEffectContext context) {
        if (this.closed) {
            return;
        }
        this.carveSession.tick();
        if (context.phaseIndex() == ContentOrbitalLaserSettings.PHASE_SUSTAIN) {
            this.damageTick(context);
        }
        this.persist(context);
    }

    /** Applies the one-shot impact shockwave. Duplicate calls and resumes are ignored. */
    void applyImpact(GameplayEffectContext context) {
        if (this.impactApplied || this.closed) {
            return;
        }
        this.impactApplied = true;
        double radius = Math.max(
            MIN_SHAPE_RADIUS,
            this.effectiveRadius * ContentOrbitalLaserSettings.SHOCKWAVE_RADIUS_FACTOR
        );
        EffectForceField field = EffectForceField.shockwave(
            new EffectPoint(this.anchor.x, this.anchor.y, this.anchor.z),
            radius,
            ContentOrbitalLaserSettings.SHOCKWAVE_STRENGTH,
            ContentOrbitalLaserSettings.SHOCKWAVE_LIFT,
            ContentOrbitalLaserSettings.SHOCKWAVE_FALLOFF,
            ContentOrbitalLaserSettings.MAX_SHOCKWAVE_TARGETS
        );
        EffectForceResult result = context.applyForceField(field, this::mayAffect);
        if (result.truncated() && !this.truncatedLogged) {
            this.truncatedLogged = true;
            LOGGER.warn(
                "Orbital-laser shockwave reached its target budget at radius {}; {} entities affected",
                radius,
                result.affected()
            );
        }
        this.persist(context);
    }

    /** Beam depth below the surface anchor, synced to the actual session front. */
    float frontDepth() {
        return (float)Math.max(0.0, this.surfaceY - frontY());
    }

    /** Server-authored beam and front values carried by the companion visual. */
    EffectParameterState visualParameters() {
        return EffectParameterState.builder()
            .putFloat(ContentOrbitalLaserCinematics.BEAM_RADIUS_SCALE, this.radiusScale)
            .putFloat(ContentOrbitalLaserCinematics.TIP_DEPTH, this.frontDepth())
            .build();
    }

    double effectiveRadius() {
        return this.effectiveRadius;
    }

    private void damageTick(GameplayEffectContext context) {
        if (context.ageTicks() % ContentOrbitalLaserSettings.BEAM_DAMAGE_INTERVAL_TICKS != 0) {
            return;
        }
        this.damageHitbox.setShape(damageShape(frontY()));
        context.applyHitbox(this.damageHitbox, this.damageSource, this::mayAffect);
    }

    private EffectDamageShape.Cylinder damageShape(double frontY) {
        double front = Math.max(this.plan.frontBottomY(), Math.min(this.plan.frontTopY(), frontY));
        double top = Math.max(front + 1.0, this.surfaceY
            + ContentOrbitalLaserSettings.BEAM_TOP_HEIGHT);
        return new EffectDamageShape.Cylinder(
            new EffectPoint(this.anchor.x, front, this.anchor.z),
            Math.max(MIN_SHAPE_RADIUS,
                this.effectiveRadius * ContentOrbitalLaserSettings.BEAM_DAMAGE_RADIUS_FACTOR),
            top - front
        );
    }

    private double frontY() {
        return Math.max(
            this.plan.frontBottomY(),
            Math.min(this.plan.frontTopY(), this.carveSession.frontY())
        );
    }

    private boolean mayAffect(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator()) {
            return false;
        }
        return !(entity instanceof Player player) || !player.isCreative();
    }

    private void persist(GameplayEffectContext context) {
        context.updatePersistentState(tag -> {
            tag.put(CARVE_SESSION, this.carveSession.save());
            tag.putBoolean(IMPACT_APPLIED, this.impactApplied);
        });
    }

    private void logPlan() {
        LOGGER.info(
            "Orbital-laser carve planned: requested radius {}, effective radius {}, "
                + "depth {}..{}, window {}..{}, sections {} (solid {}), budget {}/tick, "
                + "{} needed",
            this.context.spec().scale(),
            this.effectiveRadius,
            this.plan.frontBottomY(),
            this.plan.frontTopY(),
            this.plan.growthStart(),
            this.plan.workEnd(),
            this.plan.estimatedSections(),
            this.plan.estimatedSolidSections(),
            this.plan.sectionBudget(),
            this.plan.sectionsPerTick()
        );
        if (this.plan.budgetClamped()) {
            LOGGER.warn(
                "Orbital-laser carve radius reduced from {} to {} to fit the timeline and "
                    + "section budget; depth is preserved",
                this.context.spec().scale(),
                this.effectiveRadius
            );
        }
    }

    /** Closes the carve, flushing the reveal on natural expiry or when rollback is unavailable. */
    void close(GameplayEffectContext context, GameplayEffectEndReason reason) {
        if (this.closed) {
            return;
        }
        this.closed = true;
        EffectBulkBlockEditor.Progress carved = this.carveSession.progress();
        if (!carved.finished() && !this.carveSession.complete()) {
            LOGGER.warn(
                "Orbital-laser destruction stopped early: {} of {} sections, {} blocks",
                carved.completedSections(),
                carved.totalSections(),
                carved.clearedBlocks()
            );
        }
        boolean releaseReveal = reason == GameplayEffectEndReason.EXPIRED
            || !context.canRollbackChanges();
        this.carveSession.close(releaseReveal);
    }

    private static float clampRadiusScale(double scale) {
        if (!Double.isFinite(scale)) {
            return 0.0F;
        }
        return (float)Math.max(0.0, Math.min(1.0, scale));
    }

    private static double radiusOf(EffectBlockVolume volume) {
        if (volume instanceof EffectBlockVolume.Cylinder cylinder) {
            return Math.max(0.0, cylinder.radius());
        }
        return 0.0;
    }
}
