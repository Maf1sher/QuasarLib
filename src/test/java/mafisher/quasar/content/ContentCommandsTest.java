package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import mafisher.quasar.effect.EffectAudience;
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
