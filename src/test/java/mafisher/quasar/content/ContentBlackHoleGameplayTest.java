package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectBudgets;
import mafisher.quasar.effect.EffectDebrisSpawn;
import mafisher.quasar.effect.VisualEffectSpec;
import mafisher.quasar.effect.gameplay.GameplayEffectSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ContentBlackHoleGameplayTest {
    @Test
    void radiiScaleWithTheHorizonAndStayWithinForceLimits() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        assertEquals(6.0, settings.coreRadius(6.0F), 1.0E-6);
        assertEquals(108.0, settings.attractionRadius(6.0F), 1.0E-6);
        assertEquals(1_024.0, settings.attractionRadius(1_000.0F), 1.0E-6);
        assertEquals(30.0, settings.craterRadius(2.5F), 1.0E-6);
        assertEquals(72.0, settings.craterRadius(6.0F), 1.0E-6);
        assertEquals(144.0, settings.craterRadius(12.0F), 1.0E-6);
        assertEquals(768.0, settings.craterRadius(64.0F), 1.0E-6);
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
    void groundFrontProjectsAnElevatedCoreOntoTheSurface() {
        assertEquals(72.0, ContentBlackHoleGameplayController.groundFrontRadius(
            72.0, 72.0, 72.0, 64.0, 64.0
        ), 1.0E-6);
        double elevated = Math.sqrt(72.0 * 72.0 - 30.0 * 30.0);
        assertEquals(elevated, ContentBlackHoleGameplayController.groundFrontRadius(
            72.0, 72.0, 72.0, 94.0, 64.0
        ), 1.0E-6);
        assertEquals(elevated, ContentBlackHoleGameplayController.groundFrontRadius(
            72.0, 72.0, 72.0, 34.0, 64.0
        ), 1.0E-6);
        assertEquals(0.0, ContentBlackHoleGameplayController.groundFrontRadius(
            30.0, 72.0, 72.0, 200.0, 64.0
        ), 1.0E-6);
    }

    @Test
    void tearSettingsAreValidated() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        assertEquals(64, settings.tearRaysPerTick());
    }

    @Test
    void debrisDiskRadiiStayOrderedAtTheOpeningBeat() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        for (float scale : new float[]{2.0F, 5.0F, 6.0F, 12.0F, 64.0F}) {
            double coreRadius = settings.coreRadius(scale);
            double craterRadius = settings.craterRadius(scale);
            double inflowRadius = Math.min(
                1_024.0, Math.max(coreRadius, settings.attractionRadius(scale) * 0.9)
            );
            double groundRadius = Math.min(inflowRadius, craterRadius);
            double innerRadius = ContentBlackHoleGameplayController.debrisDiskInnerRadius(
                coreRadius, groundRadius
            );

            assertTrue(innerRadius <= groundRadius, "scale " + scale);
            assertDoesNotThrow(() -> new EffectDebrisSpawn.Disk(
                new Vec3(0.0, 64.0, 0.0), innerRadius, groundRadius, 5.0
            ));
        }
    }

    @Test
    void gameplaySpecMirrorsTheVisualDurationAndScale() {
        VisualEffectSpec visual = new VisualEffectSpec(
            ContentEffectIds.BLACK_HOLE,
            new Vec3(1.0, 2.0, 3.0),
            10.0F,
            8.0F,
            0.0F,
            12.0F,
            1_200,
            7L,
            EffectAudience.dimensionWide(),
            ContentBlackHoleAudio.plan(new Vec3(1.0, 2.0, 3.0), 1_200)
        );

        GameplayEffectSpec spec = ContentCommands.blackHoleGameplaySpec(visual);

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
