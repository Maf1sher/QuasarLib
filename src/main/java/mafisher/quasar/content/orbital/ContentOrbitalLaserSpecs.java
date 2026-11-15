package mafisher.quasar.content.orbital;

import java.util.UUID;
import mafisher.quasar.api.effect.EffectLifetimePolicy;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.effect.budget.EffectPriority;
import mafisher.quasar.api.gameplay.CompanionSpec;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.OwnerPolicy;
import mafisher.quasar.api.gameplay.block.EffectBulkBlockEditor;
import mafisher.quasar.api.gameplay.block.EffectCarveSpec;
import mafisher.quasar.api.gameplay.world.EffectBlockVolume;
import mafisher.quasar.content.ContentEffectIds;
import mafisher.quasar.content.orbital.ContentOrbitalLaserTarget.TargetColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Builds the gameplay session, companion, and preview specs for the orbital laser. */
public final class ContentOrbitalLaserSpecs {
    private ContentOrbitalLaserSpecs() {
    }

    /** Builds the six-phase gameplay session anchored at the resolved surface column. */
    public static GameplayEffectSpec gameplay(
        @Nullable UUID ownerId,
        TargetColumn target,
        int radius,
        long seed
    ) {
        return new GameplayEffectSpec(
            ContentEffectIds.ORBITAL_LASER,
            ownerId,
            target.center(),
            ContentOrbitalLaserSettings.phasePlan(),
            seed,
            ContentOrbitalLaserSettings.gameplayBudget(),
            OwnerPolicy.STOP_WHEN_UNAVAILABLE,
            ContentOrbitalLaserAudio.plan(target.center()),
            EffectPriority.NORMAL,
            ContentOrbitalLaserSettings.requireRadius(radius)
        );
    }

    /**
     * Builds the full-depth top-down cylinder used by the destructive session.
     *
     * <p>The volume spans from the authored surface column to the protected world floor. It is
     * the only source of the requested radius; {@code EffectCarvePlan} may shrink that radius to
     * fit the timeline and section budget, but never the vertical extent.</p>
     */
    static EffectCarveSpec carveSpec(GameplayEffectContext context) {
        Vec3 anchor = context.spec().anchor();
        double floorY = context.level().getMinBuildHeight() + 1.0;
        double topY = Math.max(floorY, anchor.y);
        return new EffectCarveSpec(
            EffectBlockVolume.cylinder(
                new EffectPoint(anchor.x, anchor.y, anchor.z),
                context.spec().scale(),
                floorY,
                topY
            ),
            Blocks.AIR.defaultBlockState(),
            true,
            true,
            EffectBulkBlockEditor.SyncPolicy.PROGRESSIVE,
            ContentOrbitalLaserSettings.carveWindowStart(),
            ContentOrbitalLaserSettings.carveWindowEnd(),
            null,
            0.0F,
            anchor.y,
            EffectBulkBlockEditor.Ordering.TOP_DOWN
        );
    }

    /** Builds the one showpiece companion owned by a gameplay session. */
    public static CompanionSpec companion() {
        return CompanionSpec.builder(
                ContentEffectIds.ORBITAL_LASER, ContentOrbitalLaserSettings.AUDIENCE
            )
            .offset(0.0, ContentOrbitalLaserSettings.MARKER_LIFT, 0.0)
            .scale(1.0F)
            .priority(EffectPriority.NORMAL)
            .lifetime(EffectLifetimePolicy.CANCEL_WITH_SCOPE)
            .build();
    }

    /** Builds the visual-only show preview used by {@code orbital-laser visual}. */
    public static VisualEffectSpec preview(TargetColumn target, int radius, long seed) {
        return VisualEffectSpec.builder(
                ContentEffectIds.ORBITAL_LASER,
                target.markerPosition(),
                ContentOrbitalLaserSettings.TOTAL_TICKS,
                seed,
                ContentOrbitalLaserSettings.AUDIENCE
            )
            .scale(ContentOrbitalLaserSettings.requireRadius(radius))
            .audio(ContentOrbitalLaserAudio.plan(target.center()))
            .build();
    }
}
