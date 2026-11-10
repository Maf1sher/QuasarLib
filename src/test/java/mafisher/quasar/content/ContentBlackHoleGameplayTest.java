package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.effect.EffectAudience;
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
    }

    @Test
    void contentBlackHoleIdStaysInTheContentNamespace() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "black_hole"),
            ContentEffectIds.BLACK_HOLE);
    }
}
