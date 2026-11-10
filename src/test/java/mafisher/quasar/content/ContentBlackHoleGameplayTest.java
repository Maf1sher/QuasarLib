package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.effect.EffectAudience;
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
        assertEquals(72.0, settings.craterDepth(6.0F), 1.0E-6);
        assertEquals(144.0, settings.craterRadius(12.0F), 1.0E-6);
        assertEquals(ContentBlackHoleGameplaySettings.MAX_CRATER_RADIUS,
            settings.craterRadius(1_000.0F), 1.0E-6);
        assertEquals(ContentBlackHoleGameplaySettings.MAX_CRATER_DEPTH,
            settings.craterDepth(1_000.0F), 1.0E-6);
    }

    @Test
    void tearRadiusGrowsFromTheCoreToTheAttractionRadius() {
        double start = 8.0;
        double end = 108.0;
        assertEquals(start, ContentBlackHoleGameplayController.tearRadius(
            start, end, ContentBlackHoleCinematics.HORIZON_END
        ), 1.0E-6);
        assertEquals(end, ContentBlackHoleGameplayController.tearRadius(start, end, 0.6F), 1.0E-6);

        double previous = start;
        for (float progress = 0.1F; progress <= 0.6F; progress += 0.02F) {
            double radius = ContentBlackHoleGameplayController.tearRadius(start, end, progress);
            assertTrue(radius >= previous - 1.0E-6, "progress " + progress);
            assertTrue(radius <= end + 1.0E-6, "progress " + progress);
            previous = radius;
        }
    }

    @Test
    void tearSettingsAreValidated() {
        ContentBlackHoleGameplaySettings settings = ContentBlackHoleGameplaySettings.DEFAULT;
        assertEquals(1.35F, settings.tearStartFactor(), 1.0E-6F);
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
        assertEquals(8, spec.budget().maxSectionEdits());
        assertTrue(spec.budget().maxDebrisPieces() >= 1_024);
    }

    @Test
    void contentBlackHoleIdStaysInTheContentNamespace() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "black_hole"),
            ContentEffectIds.BLACK_HOLE);
    }
}
