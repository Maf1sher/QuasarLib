package mafisher.quasar.content.blackhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.budget.EffectBudgets;
import mafisher.quasar.api.effect.debris.EffectDebrisAttractor;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
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
    void tearRadiusNeverExceedsTheFinalCrater() {
        double craterRadius = 144.0;
        double frontRadius = 8.0;
        double band = 6.0;
        assertEquals(14.0, ContentBlackHoleGameplayController.tearRadius(
            frontRadius, craterRadius, band
        ), 1.0E-6);
        assertEquals(craterRadius, ContentBlackHoleGameplayController.tearRadius(
            craterRadius, craterRadius, band
        ), 1.0E-6);

        double previous = 0.0;
        for (double front = 0.0; front <= craterRadius; front += 4.0) {
            double radius = ContentBlackHoleGameplayController.tearRadius(
                front, craterRadius, band
            );
            assertTrue(radius >= previous - 1.0E-6, "front " + front);
            assertTrue(radius <= craterRadius + 1.0E-6, "front " + front);
            previous = radius;
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
    void debrisAttractorFallsStraightIntoTheCore() {
        EffectDebrisAttractor attractor = ContentBlackHoleDebris.attractor(
            new Vec3(10.0, 64.0, -20.0), new Vec3(0.0, 1.0, 0.0), 6.0
        );

        assertEquals(0.0, attractor.swirl(), 0.0);
        assertEquals(ContentBlackHoleDebris.RADIAL_PULL, attractor.radialPull(), 0.0);
        assertEquals(6.0, attractor.horizonRadius(), 0.0);
    }

    @Test
    void debrisLifetimeKeepsTheFallReadable() {
        int minimum = ContentBlackHoleDebris.MIN_LIFETIME_TICKS;
        int configured = ContentBlackHoleDebris.lifetimeTicks(64.0, 1.0, 120);

        assertTrue(configured >= minimum && configured <= 120);
        assertEquals(minimum, ContentBlackHoleDebris.lifetimeTicks(1.0, 1.0, 120));
        assertEquals(120, ContentBlackHoleDebris.lifetimeTicks(1_000.0, 1.0, 120));
        assertEquals(minimum, ContentBlackHoleDebris.lifetimeTicks(-5.0, 1.0, 120));
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
