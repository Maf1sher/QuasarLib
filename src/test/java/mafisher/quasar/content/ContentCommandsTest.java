package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.VisualEffectSpec;
import mafisher.quasar.effect.scale.SegmentedVisualEffectSpec;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ContentCommandsTest {
    @Test
    void segmentedSpecUsesContentRuneDefaults() {
        SegmentedVisualEffectSpec spec = ContentCommands.segmentedSpec(
            new Vec3(10.0, 70.0, -4.0),
            90.0F,
            4,
            4,
            10.0,
            42L
        );

        assertEquals(ContentEffectIds.RUNE_CIRCLE, spec.effectId());
        assertEquals(new Vec3(10.0, 68.6, -4.0), spec.center());
        assertEquals(90.0F, spec.yaw());
        assertEquals(5.0F, spec.segmentScale());
        assertEquals(16, spec.segmentCount());
        assertEquals(1200, spec.durationTicks());
        assertEquals(42L, spec.seed());
        assertInstanceOf(EffectAudience.Nearby.class, spec.audience());
        assertEquals(512.0, ((EffectAudience.Nearby)spec.audience()).radius());
    }

    @Test
    void blackHoleSpecPlacesTheShowpieceOnTheViewRay() {
        VisualEffectSpec spec = ContentCommands.blackHoleSpec(
            new Vec3(10.0, 70.0, -4.0),
            0.0F,
            0.0F,
            160.0,
            12.0F,
            ContentBlackHoleGeometry.VARIANT_AMBER,
            42L
        );

        assertEquals(ContentEffectIds.BLACK_HOLE, spec.effectId());
        assertEquals(10.0, spec.position().x, 0.0001);
        assertEquals(70.0, spec.position().y, 0.0001);
        assertEquals(156.0, spec.position().z, 0.0001);
        assertEquals(0.0F, spec.yaw(), 0.0001F);
        assertEquals(8.0F, spec.pitch(), 0.0001F);
        assertEquals(12.0F, spec.scale(), 0.0001F);
        assertEquals(1200, spec.durationTicks());
        assertEquals(42L, spec.seed());
        assertInstanceOf(EffectAudience.DimensionWide.class, spec.audience());
        assertFalse(spec.audio().isEmpty());
    }

    @Test
    void blackHoleSpecKeepsDownwardRaysOnTheViewRay() {
        VisualEffectSpec spec = ContentCommands.blackHoleSpec(
            new Vec3(0.0, 70.0, 0.0),
            0.0F,
            30.0F,
            100.0,
            12.0F,
            ContentBlackHoleGeometry.VARIANT_AMBER,
            1L
        );

        assertEquals(20.0, spec.position().y, 0.01);
        assertEquals(100.0 * Math.cos(Math.toRadians(30.0)), spec.position().z, 0.01);
    }

    @Test
    void blackHoleSpecRejectsInvalidDistanceAndScale() {
        assertThrows(IllegalArgumentException.class, () -> ContentCommands.blackHoleSpec(
            Vec3.ZERO, 0.0F, 0.0F, 0.0, 12.0F,
            ContentBlackHoleGeometry.VARIANT_AMBER, 1L
        ));
        assertThrows(IllegalArgumentException.class, () -> ContentCommands.blackHoleSpec(
            Vec3.ZERO, 0.0F, 0.0F, 160.0, 0.0F,
            ContentBlackHoleGeometry.VARIANT_AMBER, 1L
        ));
    }

    @Test
    void blackHoleSpecEncodesThePaletteVariantInTheSeed() {
        VisualEffectSpec gold = ContentCommands.blackHoleSpec(
            Vec3.ZERO, 0.0F, 0.0F, 160.0, 6.0F,
            ContentBlackHoleGeometry.VARIANT_GOLD, 42L
        );
        VisualEffectSpec neon = ContentCommands.blackHoleSpec(
            Vec3.ZERO, 0.0F, 0.0F, 160.0, 6.0F,
            ContentBlackHoleGeometry.VARIANT_NEON, 42L
        );

        assertEquals(ContentBlackHoleGeometry.VARIANT_GOLD,
            ContentBlackHoleGeometry.variant(gold.seed()));
        assertEquals(ContentBlackHoleGeometry.VARIANT_NEON,
            ContentBlackHoleGeometry.variant(neon.seed()));
        assertEquals(0.0F, ContentBlackHoleGeometry.chargeForSeed(gold.seed(), 0.55F), 0.0001F);
        assertEquals(1.0F, ContentBlackHoleGeometry.chargeForSeed(neon.seed(), 0.55F), 0.0001F);
    }

    @Test
    void rejectsGridsAboveTheSegmentCap() {
        assertThrows(IllegalArgumentException.class, () -> ContentCommands.segmentedSpec(
            Vec3.ZERO,
            0.0F,
            16,
            16,
            10.0,
            1L
        ));
    }
}
