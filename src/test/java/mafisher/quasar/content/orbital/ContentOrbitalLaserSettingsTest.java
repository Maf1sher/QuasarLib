package mafisher.quasar.content.orbital;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectLifetimePolicy;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.gameplay.CompanionSpec;
import mafisher.quasar.api.gameplay.EffectPhasePlan;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.OwnerPolicy;
import mafisher.quasar.api.gameplay.tick.EffectTickBudget;
import mafisher.quasar.content.ContentEffectIds;
import mafisher.quasar.content.orbital.ContentOrbitalLaserTarget.TargetColumn;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ContentOrbitalLaserSettingsTest {
    @Test
    void keepsTheAuthoredSixPhaseTimeline() {
        EffectPhasePlan plan = ContentOrbitalLaserSettings.phasePlan();

        assertEquals(6, plan.phaseCount());
        assertEquals(220, plan.totalDurationTicks());
        assertEquals(
            List.of("target", "descent", "impact", "sustain", "collapse", "aftermath"),
            plan.phases().stream().map(phase -> phase.name()).toList()
        );
        assertEquals(0, plan.phaseStartTick(ContentOrbitalLaserSettings.PHASE_TARGET));
        assertEquals(40, plan.phaseStartTick(ContentOrbitalLaserSettings.PHASE_DESCENT));
        assertEquals(60, plan.phaseStartTick(ContentOrbitalLaserSettings.PHASE_IMPACT));
        assertEquals(64, plan.phaseStartTick(ContentOrbitalLaserSettings.PHASE_SUSTAIN));
        assertEquals(164, plan.phaseStartTick(ContentOrbitalLaserSettings.PHASE_COLLAPSE));
        assertEquals(194, plan.phaseStartTick(ContentOrbitalLaserSettings.PHASE_AFTERMATH));
    }

    @Test
    void acceptsOnlyTheCommandRadiusRange() {
        assertDoesNotThrow(() -> ContentOrbitalLaserSettings.requireRadius(
            ContentOrbitalLaserSettings.MIN_RADIUS
        ));
        assertDoesNotThrow(() -> ContentOrbitalLaserSettings.requireRadius(
            ContentOrbitalLaserSettings.MAX_RADIUS
        ));
        assertThrows(IllegalArgumentException.class, () -> ContentOrbitalLaserSettings.requireRadius(
            ContentOrbitalLaserSettings.MIN_RADIUS - 1
        ));
        assertThrows(IllegalArgumentException.class, () -> ContentOrbitalLaserSettings.requireRadius(
            ContentOrbitalLaserSettings.MAX_RADIUS + 1
        ));
    }

    @Test
    void exposesTheCarveWindowAndDestructiveBudgets() {
        assertEquals(60.0F / 220.0F,
            ContentOrbitalLaserSettings.carveWindowStart(), 1.0E-6F);
        assertEquals(164.0F / 220.0F,
            ContentOrbitalLaserSettings.carveWindowEnd(), 1.0E-6F);

        EffectTickBudget budget = ContentOrbitalLaserSettings.gameplayBudget();
        assertEquals(ContentOrbitalLaserSettings.MAX_BEAM_TARGETS_EXAMINED,
            budget.maxTargetsExamined());
        assertEquals(1, budget.maxHitboxCalls());
        assertEquals(ContentOrbitalLaserSettings.MAX_SHOCKWAVE_TARGETS,
            budget.maxForceTargets());
        assertEquals(ContentOrbitalLaserSettings.MAX_SECTION_EDITS,
            budget.maxSectionEdits());
    }

    @Test
    void anchorsTheSessionAtTheAimedBlockCentreAndLiftsTheMarker() {
        TargetColumn target = new TargetColumn(10, -4, 71);

        assertEquals(new Vec3(10.5, 71.0, -3.5), target.center());
        assertEquals(71.0 + ContentOrbitalLaserSettings.MARKER_LIFT,
            target.markerPosition().y, 1.0E-9);
        assertEquals(70, target.targetBlockY());
    }

    @Test
    void buildsTheGameplaySpecFromTheResolvedColumn() {
        UUID owner = UUID.randomUUID();
        TargetColumn target = new TargetColumn(4, 8, 64);
        GameplayEffectSpec spec = ContentOrbitalLaserSpecs.gameplay(owner, target, 6, 42L);

        assertEquals(ContentEffectIds.ORBITAL_LASER, spec.typeId());
        assertEquals(owner, spec.ownerId());
        assertEquals(target.center(), spec.anchor());
        assertEquals(6.0F, spec.scale());
        assertEquals(220, spec.phases().totalDurationTicks());
        assertEquals(OwnerPolicy.STOP_WHEN_UNAVAILABLE, spec.ownerPolicy());
        assertFalse(spec.audio().isEmpty());
        assertEquals(7, spec.audio().cueCount());
    }

    @Test
    void buildsOneShowpieceCompanion() {
        CompanionSpec companion = ContentOrbitalLaserSpecs.companion();

        assertEquals(ContentEffectIds.ORBITAL_LASER, companion.effectId());
        assertEquals(ContentOrbitalLaserSettings.MARKER_LIFT, companion.offset().y(), 1.0E-9);
        assertEquals(0.0, companion.offset().x(), 1.0E-9);
        assertEquals(0.0, companion.offset().z(), 1.0E-9);
        assertEquals(1.0F, companion.scaleMultiplier());
        assertEquals(EffectLifetimePolicy.CANCEL_WITH_SCOPE, companion.lifetime());
        assertInstanceOf(EffectAudience.DimensionWide.class, companion.audience());
        assertTrue(companion.audio().isEmpty());
    }

    @Test
    void buildsTheVisualOnlyPreviewWithoutASession() {
        TargetColumn target = new TargetColumn(4, 8, 64);
        VisualEffectSpec preview = ContentOrbitalLaserSpecs.preview(target, 6, 42L);

        assertEquals(ContentEffectIds.ORBITAL_LASER, preview.effectId());
        assertEquals(target.markerPosition(), preview.position());
        assertEquals(6.0F, preview.scale());
        assertEquals(220, preview.durationTicks());
        assertEquals(42L, preview.seed());
        assertInstanceOf(EffectAudience.DimensionWide.class, preview.audience());
        assertFalse(preview.audio().isEmpty());
        assertEquals(7, preview.audio().cueCount());
    }

    @Test
    void rejectsOutOfRangeRadiusForDirectCallers() {
        TargetColumn target = new TargetColumn(0, 0, 64);

        assertThrows(IllegalArgumentException.class, () -> ContentOrbitalLaserSpecs.gameplay(
            null, target, 1, 1L
        ));
        assertThrows(IllegalArgumentException.class, () -> ContentOrbitalLaserSpecs.preview(
            target, 33, 1L
        ));
        assertNull(ContentOrbitalLaserSpecs.gameplay(null, target, 2, 1L).ownerId());
    }
}
