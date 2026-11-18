package mafisher.quasar.content.orbital;

import mafisher.quasar.api.effect.EffectParameterState;
import mafisher.quasar.api.gameplay.EffectCompanion;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectController;
import mafisher.quasar.api.gameplay.GameplayEffectEndReason;
import org.jspecify.annotations.Nullable;

/**
 * Server-owned orbital-laser session: one marker/beam companion plus the full-depth burn.
 *
 * <p>The carve plan is created before the companion starts, so the effective radius is stable for
 * the whole show. The visual remains anchored at the requested radius; server parameter overrides
 * carry the effective radius scale and the actual front depth. Damage and the impact shockwave
 * stay server-authoritative.</p>
 */
public final class ContentOrbitalLaserGameplayController implements GameplayEffectController {
    private static final float DEPTH_EPSILON = 0.01F;

    private @Nullable EffectCompanion companion;
    private @Nullable ContentOrbitalLaserDestruction destruction;
    private @Nullable EffectParameterState lastParameters;
    private float lastTipDepth = -1.0F;

    @Override
    public void onStart(GameplayEffectContext context) {
        this.destruction = ContentOrbitalLaserDestruction.start(context);
        this.companion = EffectCompanion.start(context, ContentOrbitalLaserSpecs.companion());
        this.syncVisual(true);
    }

    @Override
    public void onResume(GameplayEffectContext context) {
        this.destruction = ContentOrbitalLaserDestruction.restore(context);
        this.companion = EffectCompanion.restore(context, ContentOrbitalLaserSpecs.companion());
        this.syncVisual(true);
        if (ContentOrbitalLaserSettings.isInPhase(
            context, ContentOrbitalLaserSettings.PHASE_NAME_IMPACT
        )) {
            this.destruction.applyImpact(context);
        }
    }

    @Override
    public void onPhaseEnter(GameplayEffectContext context, int phaseIndex) {
        if (this.destruction != null && ContentOrbitalLaserSettings.isInPhase(
            context.spec().phases(), phaseIndex, ContentOrbitalLaserSettings.PHASE_NAME_IMPACT
        )) {
            this.destruction.applyImpact(context);
        }
    }

    @Override
    public void onTick(GameplayEffectContext context) {
        if (this.destruction != null) {
            this.destruction.tick(context);
        }
        if (this.companion != null) {
            this.companion.tick(context);
        }
        this.syncVisual(false);
    }

    @Override
    public void onEnd(GameplayEffectContext context, GameplayEffectEndReason reason) {
        if (this.destruction != null) {
            this.destruction.close(context, reason);
            this.destruction = null;
        }
        this.companion = null;
        this.lastParameters = null;
        this.lastTipDepth = -1.0F;
    }

    /** Effective hole radius after the timeline and section-budget fit, or zero before start. */
    public double effectiveRadius() {
        return this.destruction == null ? 0.0 : this.destruction.effectiveRadius();
    }

    /** Current beam depth below the surface anchor; package-visible for content tests. */
    float frontDepth() {
        return this.destruction == null ? 0.0F : this.destruction.frontDepth();
    }

    private void syncVisual(boolean force) {
        if (this.companion == null || this.destruction == null
            || this.companion.status() != EffectCompanion.Status.ACTIVE) {
            return;
        }
        float depth = this.destruction.frontDepth();
        if (!force && Math.abs(depth - this.lastTipDepth) < DEPTH_EPSILON) {
            return;
        }
        EffectParameterState parameters = this.destruction.visualParameters();
        if (!force && parameters.equals(this.lastParameters)) {
            return;
        }
        this.lastParameters = parameters;
        this.lastTipDepth = depth;
        this.companion.updateParameters(parameters);
    }
}
