package mafisher.quasar.content.blackhole;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.budget.EffectBudgets;
import mafisher.quasar.api.effect.debris.EffectDebrisAttractor;
import mafisher.quasar.api.effect.debris.EffectDebrisSpawn;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.block.EffectSurfaceTear;
import mafisher.quasar.content.ContentEffectIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ContentBlackHoleGameplayTest {
    @Test
    void radiiScaleWithTheHorizonAndStayWithinForceLimits() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        double scale = 6.0;
        assertEquals(scale * settings.coreRadiusFactor(), settings.coreRadius((float)scale), 1.0E-6);
        assertEquals(
            Math.min(1_024.0, scale * settings.attractionRadiusFactor()),
            settings.attractionRadius((float)scale),
            1.0E-6
        );
        assertTrue(settings.attractionRadius(1_000.0F) <= 1_024.0);

        for (float value : new float[]{0.5F, 2.5F, 6.0F, 12.0F, 64.0F}) {
            assertTrue(settings.craterRadius(value) >= settings.coreRadius(value),
                "scale " + value);
            assertTrue(settings.craterRadius(value) >= 4.0, "scale " + value);
        }
    }

    @Test
    void tearSettingsAreValidated() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;

        assertTrue(settings.tearRaysPerTick() > 0);
        assertTrue(settings.tearRaysPerTick() <= 512);
        assertTrue(settings.tearDebrisBurstsPerTick() >= 0);
        assertTrue(settings.tearDebrisBurstsPerTick() <= 16);
        assertTrue(settings.tearDebrisPieces() >= 1);
        assertTrue(settings.tearDebrisPieces() <= 64);
    }

    @Test
    void tearTuningFitsThePrimitiveContract() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        float scale = 6.0F;
        double coreRadius = settings.coreRadius(scale);
        double craterRadius = settings.craterRadius(scale);
        double band = Math.max(2.0, Math.min(craterRadius, coreRadius * 0.5));
        EffectDebrisAttractor attractor = ContentBlackHoleDebris.attractor(
            new Vec3(0.0, 64.0, 0.0), new Vec3(0.0, 1.0, 0.0), coreRadius
        );

        assertDoesNotThrow(() -> new EffectSurfaceTear(new EffectSurfaceTear.Settings(
            band,
            settings.tearRaysPerTick(),
            Math.max(2.0, coreRadius * 0.5),
            1
        )));
        assertDoesNotThrow(() -> new EffectSurfaceTear.Debris(
            EffectAudience.nearby(64.0),
            attractor,
            new EffectDebrisSpawn.Point(),
            settings.tearDebrisPieces(),
            settings.debrisFallSpeed(),
            ContentBlackHoleDebris.MIN_LIFETIME_TICKS,
            settings.debrisMaxLifetimeTicks(),
            settings.debrisPieceScale(),
            settings.tearDebrisBurstsPerTick(),
            ContentBlackHoleCinematics.IMPLOSION
        ));
    }

    @Test
    void debrisAttractorFallsStraightIntoTheCore() {
        EffectDebrisAttractor attractor = ContentBlackHoleDebris.attractor(
            new Vec3(10.0, 64.0, -20.0), new Vec3(0.0, 1.0, 0.0), 6.0
        );

        assertEquals(0.0, attractor.swirl(), 0.0);
        assertEquals(ContentBlackHoleDebris.RADIAL_PULL, attractor.radialPull(), 0.0);
        assertEquals(6.0, attractor.horizonRadius(), 0.0);
    }

    @Test
    void gameplaySpecMirrorsTheVisualDurationAndScale() {
        VisualEffectSpec visual = VisualEffectSpec.builder(ContentEffectIds.BLACK_HOLE, new Vec3(1.0, 2.0, 3.0), 1_200, 7L, EffectAudience.dimensionWide())
            .rotation(10.0F, 8.0F, 0.0F)
            .scale(12.0F)
            .audio(ContentBlackHoleAudio.plan(new Vec3(1.0, 2.0, 3.0), 1_200))
            .build();

        GameplayEffectSpec spec = ContentBlackHoleGameplaySpec.of(visual);

        assertEquals(ContentEffectIds.BLACK_HOLE, spec.typeId());
        assertEquals(visual.durationTicks(), spec.phases().totalDurationTicks());
        assertEquals(visual.scale(), spec.scale(), 1.0E-6F);
        assertTrue(spec.budget().maxBlockChanges() > 0);
        assertTrue(spec.budget().maxWorldSamples() > 0);
        assertEquals(EffectBudgets.sectionEditsPerLevelTick(), spec.budget().maxSectionEdits());
        assertTrue(spec.budget().maxDebrisPieces() >= 1_024);
    }

    @Test
    void contentBlackHoleIdStaysInTheContentNamespace() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "black_hole"),
            ContentEffectIds.BLACK_HOLE);
    }
}
