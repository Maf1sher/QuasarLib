package mafisher.quasar.content.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import mafisher.quasar.TestConfig;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.block.EffectBulkBlockEditor;
import mafisher.quasar.api.gameplay.block.EffectCarvePlan;
import mafisher.quasar.api.gameplay.block.EffectCarveSpec;
import mafisher.quasar.api.gameplay.world.EffectBlockVolume;
import mafisher.quasar.content.orbital.ContentOrbitalLaserTarget.TargetColumn;
import mafisher.quasar.testing.GlobalStateTest;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class ContentOrbitalLaserSpecsTest {
    private static final int SURFACE_Y = 319;

    @BeforeEach
    void setUp() {
        TestConfig.loadDefaults();
    }

    @AfterEach
    void tearDown() {
        TestConfig.unload();
    }

    @Test
    void gameplayAndPreviewCarryThePhaseScore() {
        GameplayEffectSpec gameplay = ContentOrbitalLaserSpecs.gameplay(
            null, new TargetColumn(0, 0, SURFACE_Y), 6, 42L
        );

        assertFalse(gameplay.audio().isEmpty());
        assertEquals(7, gameplay.audio().cueCount());
        assertTrue(gameplay.audio().fitsWithin(ContentOrbitalLaserSettings.TOTAL_TICKS));
        assertEquals(
            gameplay.audio().cues(),
            ContentOrbitalLaserSpecs.preview(
                new TargetColumn(0, 0, SURFACE_Y), 6, 42L
            ).audio().cues()
        );
    }

    @Test
    void carveSpecPreservesDepthWhenTheBudgetShrinksTheRadius() {
        TestConfig.load(config -> config.set("section_edits_per_level_tick", 4));
        ServerLevel level = mock(ServerLevel.class);
        when(level.getMinBuildHeight()).thenReturn(-64);
        when(level.getMaxBuildHeight()).thenReturn(320);
        when(level.getMinSection()).thenReturn(-4);
        when(level.getMaxSection()).thenReturn(20);
        GameplayEffectSpec spec = ContentOrbitalLaserSpecs.gameplay(
            null, new TargetColumn(0, 0, SURFACE_Y), 32, 42L
        );
        GameplayEffectContext context = mock(GameplayEffectContext.class);
        when(context.level()).thenReturn(level);
        when(context.spec()).thenReturn(spec);

        EffectCarveSpec carve = ContentOrbitalLaserSpecs.carveSpec(context);
        EffectCarvePlan plan = EffectCarvePlan.plan(context, carve);

        assertEquals(EffectBulkBlockEditor.Ordering.TOP_DOWN, carve.ordering());
        assertTrue(plan.scale() < 1.0,
            "scale=" + plan.scale() + " sections=" + plan.estimatedSections()
                + " available=" + plan.availableTicks() + " budget=" + plan.sectionBudget()
                + " tickets=" + plan.estimatedTickets() + " clamp=" + plan.budgetClamped());
        EffectBlockVolume.Cylinder effective = (EffectBlockVolume.Cylinder)plan.volume();
        assertEquals(-63.0, effective.minY(), 1.0E-6);
        assertEquals(SURFACE_Y, effective.maxY(), 1.0E-6);
        assertEquals(32.0 * plan.scale(), effective.radius(), 1.0E-6);
        assertEquals(-63.0, plan.frontBottomY(), 1.0E-6);
        assertEquals(SURFACE_Y, plan.frontTopY(), 1.0E-6);
    }
}
