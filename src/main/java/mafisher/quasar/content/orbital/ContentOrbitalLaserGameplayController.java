package mafisher.quasar.content.orbital;

import mafisher.quasar.api.gameplay.EffectCompanion;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectController;
import org.jspecify.annotations.Nullable;

/**
 * Server-owned target lock for the orbital laser.
 *
 * <p>Stage 6.1 starts the six-phase session and one marker companion. It performs no block edits,
 * damage, or world sampling; later stages fill the descent, impact, sustain, and collapse phases.</p>
 */
public final class ContentOrbitalLaserGameplayController implements GameplayEffectController {
    private @Nullable EffectCompanion companion;

    @Override
    public void onStart(GameplayEffectContext context) {
        this.companion = EffectCompanion.start(context, ContentOrbitalLaserSpecs.companion());
    }

    @Override
    public void onResume(GameplayEffectContext context) {
        this.companion = EffectCompanion.restore(context, ContentOrbitalLaserSpecs.companion());
    }

    @Override
    public void onTick(GameplayEffectContext context) {
        if (this.companion != null) {
            this.companion.tick(context);
        }
    }
}
